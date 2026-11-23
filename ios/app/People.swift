import Foundation
import RIFE2NativeApp
import WidgetKit

// Hears from the pages who they saved, see app.js of the site, and keeps the names and the
// births where the widgets find them, since a widget can't read the storage of the web view.
// It's also what the pages keep their people elsewhere with, for the other devices of whoever
// uses the app: the pages work out who goes where, and this keeps and removes who they say,
// and tells them who's there when they ask and when that changed.
final class PeopleKept {
    // Who the pages saved, as a widget is asked for them.
    private struct Told: Decodable {
        let saved: [Saved]
    }

    // What a page asks of elsewhere.
    private struct Asked: Decodable {
        let ask: Bool?
        let keep: String?
        let person: Person?
        let remove: String?
    }

    // Told to whoever listens when the people that the widgets find are other ones than before.
    static let changed = Notification.Name("people")
    // What the widgets find is written off the main thread, one telling after the other.
    private static let keeping = DispatchQueue(label: "people")
    // How elsewhere can have changed: it's there again, someone may have been removed on
    // another device, or it's another one, of another account. A later one outweighs the ones
    // before it.
    private static let changes = ["arrived", "there", "another"]
    private let elsewhere: Elsewhere
    private var heard: NSObjectProtocol?
    // How elsewhere changed that no page heard of yet.
    private var unheard: String?
    // The pages to tell.
    weak var pages: NativePages?

    init(elsewhere: Elsewhere) {
        self.elsewhere = elsewhere
        let store = NSUbiquitousKeyValueStore.default
        heard = NotificationCenter.default.addObserver(forName: NSUbiquitousKeyValueStore.didChangeExternallyNotification, object: store, queue: .main) { [weak self] change in
            switch change.userInfo?[NSUbiquitousKeyValueStoreChangeReasonKey] as? Int {
            case NSUbiquitousKeyValueStoreServerChange:
                self?.tell("there")
            case NSUbiquitousKeyValueStoreInitialSyncChange:
                self?.tell("arrived")
            case NSUbiquitousKeyValueStoreAccountChange:
                self?.tell("another")
            default:
                log.error("iCloud has no room for more people")
            }
        }
        // iCloud is asked for what it has, which it tells of when that's other people than here.
        store.synchronize()
    }

    deinit {
        heard.map(NotificationCenter.default.removeObserver)
    }

    // The app is in front again.
    func again() {
        tell("arrived")
    }

    // Tells the page who's elsewhere and how that changed. A change that no page was there to
    // hear of is told to the next one that asks, since someone who was removed on another
    // device is only seen to be when the pages hear that it changed there.
    private func tell(_ how: String) {
        let told = Self.changes.last { $0 == how || $0 == unheard } ?? how
        unheard = told
        guard let written = try? JSONEncoder().encode(elsewhere.people()), let people = try? JSONSerialization.jsonObject(with: written) else {
            return
        }
        pages?.call("appElsewhere", told, people, elsewhere.most) { [weak self] _, heard in
            if heard as? Bool == true, self?.unheard == told {
                self?.unheard = nil
            }
        }
    }

    private static func read<Said: Decodable>(_ said: Any) -> Said? {
        guard JSONSerialization.isValidJSONObject(said), let written = try? JSONSerialization.data(withJSONObject: said) else {
            return nil
        }
        return try? JSONDecoder().decode(Said.self, from: written)
    }

    // A page asks something of elsewhere.
    func asked(_ said: Any) {
        guard let asked: Asked = Self.read(said) else {
            log.error("A page asked something of elsewhere that the app doesn't read")
            return
        }
        if let name = asked.keep, let person = asked.person {
            elsewhere.keep(person, named: name)
        }
        if let name = asked.remove {
            elsewhere.remove(name)
        }
        if asked.ask == true {
            tell("arrived")
        }
    }

    // A page says who it saved.
    func saved(_ said: Any) {
        guard let told: Told = Self.read(said) else {
            log.error("A page said something of its people that the app doesn't read")
            return
        }
        Self.keeping.async {
            if Saved.keep(told.saved) {
                WidgetCenter.shared.reloadTimelines(ofKind: "person")
                NotificationCenter.default.post(name: Self.changed, object: nil)
                log.info("\(told.saved.count) people kept for the widgets")
            }
        }
    }
}
