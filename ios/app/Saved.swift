import Foundation

// A person that the pages saved, as a widget is asked for them: under their name, with the
// moment of their birth in UTC as the site writes a moment in an address.
struct Saved: Codable, Hashable {
    let name: String
    let born: String

    // The app and its widgets are two programs with one folder between them, where the app
    // keeps who was saved for a widget to offer and to show.
    // A Mac names such a folder after the team that signs the app, which its Info.plist says.
    static let group = Bundle.main.object(forInfoDictionaryKey: "AppGroup") as? String ?? "group.com.uwyn.gatesofhumanity"

    private static var file: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?.appendingPathComponent("people.json")
    }

    // The address of this person's widget at the site, in a size. Their name is written with
    // what the site reads back as it was: it takes a plus for a space.
    func widget(_ size: String) -> String {
        let written = name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed.subtracting(CharacterSet(charactersIn: "+&=;"))) ?? ""
        return "/widget/person/\(born)/\(size)?name=\(written)"
    }

    static func all() -> [Saved] {
        guard let file, let kept = try? Data(contentsOf: file) else {
            return []
        }
        return (try? JSONDecoder().decode([Saved].self, from: kept)) ?? []
    }

    // Keeps the people when they're other ones than before, and says whether they were.
    static func keep(_ people: [Saved]) -> Bool {
        guard let file, people != all(), let kept = try? JSONEncoder().encode(people) else {
            return false
        }
        return (try? kept.write(to: file, options: .atomic)) != nil
    }
}
