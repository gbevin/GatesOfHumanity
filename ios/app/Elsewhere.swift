import CryptoKit
import Foundation

// A person as the pages save them.
struct Person: Codable, Equatable {
    let datetime: String
    let locationId: String
    let locationCity: String
    var group: String?
}

// Where the people are kept beside this device, by name: iCloud, or what stands in for it in a
// check. The pages work out who's kept and who's removed there, see appSynced in app.js of the
// site, so that someone has their people on each of their devices.
protocol Elsewhere: AnyObject {
    // How many people there's room for.
    var most: Int { get }
    func people() -> [String: Person]
    func keep(_ person: Person, named name: String)
    func remove(_ name: String)
}

// The key-value store of iCloud, which is the same on the devices of an account and needs no
// server of the app's. It has a thing for each person, so that two devices that each save
// someone while they're apart both keep theirs. A key can't be as long as a name can, so it's
// made of the name and the name is kept with the person.
final class ICloud: Elsewhere {
    private struct Entry: Codable {
        let name: String
        let person: Person
    }

    // iCloud keeps a thousand and twenty-four things for an app.
    let most = 1000
    private let store = NSUbiquitousKeyValueStore.default

    private static func key(_ name: String) -> String {
        "person-" + SHA256.hash(data: Data(name.utf8)).prefix(20).map { String(format: "%02x", $0) }.joined()
    }

    func people() -> [String: Person] {
        var people: [String: Person] = [:]
        for (key, value) in store.dictionaryRepresentation where key.hasPrefix("person-") {
            if let data = value as? Data, let entry = try? JSONDecoder().decode(Entry.self, from: data) {
                people[entry.name] = entry.person
            }
        }
        return people
    }

    func keep(_ person: Person, named name: String) {
        if let data = try? JSONEncoder().encode(Entry(name: name, person: person)) {
            store.set(data, forKey: Self.key(name))
        }
    }

    func remove(_ name: String) {
        store.removeObject(forKey: Self.key(name))
    }
}
