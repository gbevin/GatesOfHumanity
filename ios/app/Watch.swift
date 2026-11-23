import UIKit
import WatchConnectivity

// The watch of whoever has one, as this app sees it. The watch app has no web view and nothing
// that works out a chart, so it shows drawings that the site inside this app makes for it, see
// Drawings: the transits and the body graph of this moment and the charts of some of the saved
// people, each for some days ahead. They're sent together when the watch asks, when the saved
// people change, and when this app comes to the front after a while.
final class Watch: NSObject, WCSessionDelegate {
    static let shared = Watch()

    // What a watch face shows of the transits, in the sizes that the site draws them in.
    private static let complications = ["accessoryCircular", "accessoryRectangular", "accessoryInline"]
    // The site is asked for a few hundred drawings, away from the main thread and one sending at a time.
    private static let making = DispatchQueue(label: "watch")
    // What the watch has is good for a day. It asks by itself then, so this is only for a
    // watch that couldn't.
    private static let often: TimeInterval = 6 * 3600
    // How many bytes an answer to the watch takes at most.
    private static let answered = 60_000
    private var heard: NSObjectProtocol?
    private var front: NSObjectProtocol?

    // Starts hearing the watch, which also is what has iOS start this app for it when the
    // watch asks while the app isn't running.
    func start() {
        if let file = Probe.watch {
            Self.making.async {
                try? self.made()?.write(to: URL(fileURLWithPath: file), options: .atomic)
            }
        }
        // an iPad has no watch
        guard WCSession.isSupported() else {
            return
        }
        WCSession.default.delegate = self
        WCSession.default.activate()
        heard = NotificationCenter.default.addObserver(forName: PeopleKept.changed, object: nil, queue: nil) { [weak self] _ in
            self?.send()
        }
        // the app is in front again
        front = NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: nil) { [weak self] _ in
            self?.send(stale: true)
        }
    }

    // Sends the watch its drawings without it asking, when it has the app: always, or only
    // when the last ones are from a while ago. That's looked at where they're made, one sending
    // at a time, so the app that comes to the front twice at its start makes them once.
    private func send(stale: Bool = false) {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated, WCSession.default.isWatchAppInstalled else {
            return
        }
        Self.making.async {
            if stale, Date.now.timeIntervalSince(UserDefaults.standard.object(forKey: "watched") as? Date ?? .distantPast) <= Self.often {
                return
            }
            self.made().map(self.hand)
        }
    }

    // The drawings as they're sent, or nothing when the site didn't make them.
    private func made() -> Data? {
        do {
            // A screen is the drawing of the small widget, and a tap on it opens what the site
            // says for a watch: rows to read, with what explains each. A person's screen has their name for a title, so their
            // drawings are asked for without it.
            var screens: [(title: String, path: String, details: String?)] = [
                (title: "Transits", path: "/widget/transits/systemSmall", details: "/widget/transits/watch"),
                (title: "Body Graph", path: "/widget/bodygraph/systemSmall", details: "/widget/bodygraph/watch"),
            ]
            for person in Saved.all().prefix(Drawings.people) {
                screens.append((title: person.name, path: "/widget/person/\(person.born)/systemSmall", details: "/widget/person/\(person.born)/watch"))
            }
            let drawings = try Drawings.asked(screens: screens, complications: Dictionary(uniqueKeysWithValues: Self.complications.map { ($0, "/widget/transits/" + $0) })) {
                try Site.shared.get($0)
            }
            let packed = try drawings.packed()
            UserDefaults.standard.set(Date.now, forKey: "watched")
            log.info("\(drawings.screens.count) screens for the watch, \(packed.count) bytes")
            return packed
        } catch {
            log.error("The site didn't make the drawings of the watch: \(Refusal(error).why, privacy: .public)")
            return nil
        }
    }

    // Hands drawings over as what the watch should have of this app, which iOS gives it when
    // it can and of which only the last counts.
    private func hand(_ packed: Data) {
        do {
            do {
                try WCSession.default.updateApplicationContext([Drawings.sent: packed])
            } catch WCError.payloadTooLarge {
                // More than iOS takes that way goes as a file, in a folder of its own since a
                // file of that name may still be on its way.
                let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                let file = folder.appendingPathComponent(Drawings.sent)
                try packed.write(to: file)
                WCSession.default.outstandingFileTransfers.forEach { $0.cancel() }
                WCSession.default.transferFile(file, metadata: nil)
            }
        } catch {
            log.error("The watch couldn't be sent its drawings: \(error.localizedDescription, privacy: .public)")
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        if state == .activated {
            send(stale: true)
        }
    }

    // The watch asks with the iPhone in reach and waits for the answer, which is the drawings
    // themselves when they're no more than an answer takes.
    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        Self.making.async {
            let packed = self.made()
            if let packed, packed.count <= Self.answered {
                replyHandler([Drawings.sent: packed])
            } else {
                replyHandler([:])
                packed.map(self.hand)
            }
        }
    }

    // The watch asked while the iPhone wasn't in reach, which it is now.
    func session(_ session: WCSession, didReceiveUserInfo asked: [String: Any] = [:]) {
        Self.making.async {
            self.made().map(self.hand)
        }
    }

    func session(_ session: WCSession, didFinish transfer: WCSessionFileTransfer, error: Error?) {
        try? FileManager.default.removeItem(at: transfer.file.fileURL.deletingLastPathComponent())
    }

    func sessionDidBecomeInactive(_ session: WCSession) {
    }

    // Someone who changes watches gets a session for the other one.
    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }
}
