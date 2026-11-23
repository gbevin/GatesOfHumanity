import AppKit
import RealityKit
import Foundation
import os

// Checks of what the Swift of the app works out by itself, without the site or a device: see
// check-swift.sh, which compiles this with the files that it checks and a site that answers
// what a check tells it to.

let log = Logger(subsystem: "checks", category: "site")

struct Refusal: Error {
    let why: String

    init(_ why: String) {
        self.why = why
    }
}

final class Site {
    static let shared = Site()
    var answers: [String: String] = [:]

    func get(_ path: String) throws -> Data {
        guard let answer = answers[path] else {
            throw Refusal("The site answered \(path) with status 404")
        }
        return Data(answer.utf8)
    }
}

var failed = 0

func check<T: Equatable>(_ found: T, _ expected: T, _ what: String) {
    if found != expected {
        failed += 1
        print("FAILED \(what)\n  expected \(expected)\n  found    \(found)")
    }
}

// The name of a person reaches the site as it was saved, whatever is in it.
check(Saved(name: "Nora", born: "198803140625").widget("systemSmall"), "/widget/person/198803140625/systemSmall?name=Nora", "the address of a widget")
check(Saved(name: "Ann + Bob & C=1; D", born: "1").widget("s"), "/widget/person/1/s?name=Ann%20%2B%20Bob%20%26%20C%3D1%3B%20D", "a name with what an address reads otherwise")
check(Saved(name: "Zoë #1?", born: "1").widget("s"), "/widget/person/1/s?name=Zo%C3%AB%20%231?", "a name with letters and signs beyond the plain ones")

// A drawing, taken apart.
let drawing = """
    <svg width="158" height="158" xmlns="http://www.w3.org/2000/svg">
        <style>.gold { fill: gold; }</style>
        <defs><linearGradient id="sky"/></defs>
        <rect id="background" width="158" height="158" fill="url(#sky)"/>
        <a href="hdapp://app/wheel/birth-transit?person=A%20%26%20B&amp;x=1">
            <text class="gold" x="1">43.5 &amp; more</text>
            <text x="2" data-time="2026-11-15T22:13:00Z" data-text="until {EEEjmm} &lt;now&gt;">until Sun 22:13</text>
            <text></text>
        </a>
    </svg>
    """
let drawn = Drawn(svg: drawing, locale: Locale(identifier: "en_GB"), timeZone: TimeZone(identifier: "Asia/Tokyo")!)
check(drawn.url?.absoluteString ?? "", "hdapp://app/wheel/birth-transit?person=A%20%26%20B&x=1", "what a tap opens")
check(drawn.background ?? "", "<svg width=\"158\" height=\"158\" xmlns=\"http://www.w3.org/2000/svg\"><style>.gold { fill: gold; }</style><defs><linearGradient id=\"sky\"/></defs><rect id=\"background\" width=\"158\" height=\"158\" fill=\"url(#sky)\"/></svg>",
      "what's behind a widget, alone with the styles and the gradients")
check(drawn.foreground.contains("id=\"background\""), false, "the drawing without what's behind it")
check(drawn.foreground.contains("<linearGradient id=\"sky\"/>") && drawn.foreground.contains("43.5 &amp; more"), true, "the rest of the drawing as it was")
// The device writes the moment in its language and time zone: seven hours after ten at night in UTC is Monday in Tokyo.
check(drawn.words.hasPrefix("43.5 & more  until Mon") && drawn.words.contains("07:13") && drawn.words.hasSuffix("<now>"), true, "the words, with the moment as the device writes it: " + drawn.words)
check(drawn.foreground.contains("&lt;now&gt;</text>") && drawn.foreground.contains("x=\"2\" data-time="), true, "a written moment keeps what its text had and stays a text of a drawing")
let plain = Drawn(svg: "<svg><text>Sun 43.5</text></svg>")
check(plain.background == nil && plain.url == nil && plain.foreground == "<svg><text>Sun 43.5</text></svg>" && plain.words == "Sun 43.5", true, "a drawing with nothing behind it and nothing to open")

// What the site has a widget show.
Site.shared.answers = [
    "/widget/transits/systemSmall": "{\"reload\": \"2026-11-16T17:00:00Z\", \"until\": \"2026-11-18T17:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/one\"}, {\"date\": \"2026-11-15T22:13:00Z\", \"drawing\": \"/two\"}]}",
    "/one": "<svg><text>one</text></svg>", "/two": "<svg><text>two</text></svg>",
    "/widget/empty/systemSmall": "{\"reload\": \"2026-11-16T17:00:00Z\", \"until\": \"2026-11-18T17:00:00Z\", \"entries\": []}",
    "/widget/broken/systemSmall": "{\"reload\": \"2026-11-16T17:00:00Z\", \"until\": \"2026-11-18T17:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/missing\"}]}",
]
let timeline = try DrawnTimeline.asked("/widget/transits/systemSmall", first: false)
check(timeline.drawings.map(\.drawn.words), ["one", "two"], "a drawing for each moment")
check(ISO8601DateFormatter().string(from: timeline.reload) + " " + ISO8601DateFormatter().string(from: timeline.drawings[1].date), "2026-11-16T17:00:00Z 2026-11-15T22:13:00Z", "when a drawing begins and when to ask again")
check(try DrawnTimeline.asked("/widget/transits/systemSmall", first: true).drawings.map(\.drawn.words), ["one"], "only the first drawing")
for path in ["/widget/empty/systemSmall", "/widget/broken/systemSmall", "/widget/nothing/systemSmall"] {
    check((try? DrawnTimeline.asked(path, first: false)) == nil, true, "no timeline at \(path)")
}

// What the iPhone sends the watch.
Site.shared.answers = [
    "/widget/transits/systemSmall": "{\"reload\": \"2026-11-16T17:00:00Z\", \"until\": \"2026-11-18T17:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/one\"}, {\"date\": \"2026-11-15T22:13:00Z\", \"drawing\": \"/two\"}]}",
    "/widget/person/1/systemSmall?name=Ada": "{\"reload\": \"2026-11-16T09:00:00Z\", \"until\": \"2026-11-18T12:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/one\"}]}",
    "/widget/transits/accessoryCircular": "{\"reload\": \"2026-11-16T12:00:00Z\", \"until\": \"2026-11-18T09:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/two\"}, {\"date\": \"2026-11-16T03:00:00Z\", \"drawing\": \"/one\"}]}",
    "/widget/empty/systemSmall": "{\"reload\": \"2026-11-16T17:00:00Z\", \"until\": \"2026-11-18T17:00:00Z\", \"entries\": []}",
    "/widget/broken/systemSmall": "{\"reload\": \"2026-11-16T17:00:00Z\", \"until\": \"2026-11-18T17:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/missing\"}]}",
    "/one": "<svg><text>one</text></svg>", "/two": "<svg><text>two</text></svg>",
]
func moment(_ written: String) -> Date {
    ISO8601DateFormatter().date(from: written)!
}
Site.shared.answers["/widget/transits/watch"] = "{\"reload\": \"2026-11-16T12:00:00Z\", \"until\": \"2026-11-18T12:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/told\"}]}"
Site.shared.answers["/told"] = "{\"heading\": \"Transits now\", \"about\": [{\"title\": \"Transits\", \"texts\": [\"What a transit is.\"]}], \"rows\": [{\"label\": \"Sun\", \"value\": \"43.5\", \"name\": \"Insight\", \"until\": \"2026-11-15T22:13:00Z\", \"untilAs\": \"EEEjmm\", \"about\": [{\"title\": \"Sun\", \"texts\": [\"What the Sun is about.\"]}]}, {\"value\": \"1-8\", \"name\": \"Inspiration\", \"until\": \"2027-03-19T00:00:00Z\", \"untilAs\": \"MMMd\", \"about\": []}]}"
// a timeline of what a tap opens with a moment that the site doesn't say anything for
Site.shared.answers["/widget/half/watch"] = "{\"reload\": \"2026-11-16T01:00:00Z\", \"until\": \"2026-11-18T01:00:00Z\", \"entries\": [{\"date\": \"2026-11-15T17:00:00Z\", \"drawing\": \"/told\"}, {\"date\": \"2026-11-15T18:00:00Z\", \"drawing\": \"/one\"}]}"
let sent = try Drawings.asked(screens: [("Transits", "/widget/transits/systemSmall", "/widget/transits/watch"), ("Nobody", "/widget/empty/systemSmall", nil),
                                        ("Broken", "/widget/broken/systemSmall", nil), ("Gone", "/widget/gone/systemSmall", "/widget/transits/watch"),
                                        ("Ada", "/widget/person/1/systemSmall?name=Ada", "/widget/half/watch")],
                              complications: ["accessoryCircular": "/widget/transits/accessoryCircular", "accessoryInline": "/widget/gone/accessoryInline"]) { try Site.shared.get($0) }
check(sent.screens.map(\.title), ["Transits", "Ada"], "a screen for what the site draws, without what it doesn't")
check(sent.screens[0].entries, [Drawings.Entry(date: moment("2026-11-15T17:00:00Z"), svg: "<svg><text>one</text></svg>"),
                                Drawings.Entry(date: moment("2026-11-15T22:13:00Z"), svg: "<svg><text>two</text></svg>")], "every drawing of a screen with its moment")
let opened = sent.screens[0].details ?? []
check(opened.map(\.date), [moment("2026-11-15T17:00:00Z")], "what a tap on a screen opens, with moments of its own")
check(opened.first?.told.rows.map { "\($0.label ?? "no label") \($0.value) \($0.name) \($0.untilAs) \(ISO8601DateFormatter().string(from: $0.until))" } ?? [],
      ["Sun 43.5 Insight EEEjmm 2026-11-15T22:13:00Z", "no label 1-8 Inspiration MMMd 2027-03-19T00:00:00Z"], "its rows, with until when each lasts and how that's written")
check(sent.explained(opened.first?.told.rows.first?.about ?? []), [Drawings.Part(title: "Sun", texts: ["What the Sun is about."])], "what explains a row")
check([opened.first?.told.heading ?? ""] + sent.explained(opened.first?.told.about ?? []).flatMap(\.texts), ["Transits now", "What a transit is."], "and what explains the screen")
check(sent.parts.count, 2, "each of them once, however many moments and screens have it")
check(sent.explained([5, -1]), [], "nothing for what a row has no explanation of")
check(sent.screens[1].details == nil, true, "a screen without what a tap opens when the site doesn't say that for each of its moments")
check(Drawings.current(of: opened, at: .now)?.told.heading ?? "", "Transits now", "what a tap opens now")
check(Array(sent.complications.keys), ["accessoryCircular"], "what a watch face shows, in the sizes that the site draws")
check(sent.reload, moment("2026-11-16T09:00:00Z"), "the iPhone is asked again when the first of the timelines says, a screen's here")
check(try Drawings.asked(screens: [("Transits", "/widget/transits/systemSmall", "/widget/transits/watch")], complications: [:]) { try Site.shared.get($0) }.reload, moment("2026-11-16T12:00:00Z"),
      "or what a tap opens, when that's asked again sooner than its screen")
check(sent.until, moment("2026-11-18T09:00:00Z"), "what's true is known until the first of the timelines says, a watch face's here")
check(try Drawings(packed: sent.packed()), sent, "what's sent arrives as it was")
check((try? Drawings(packed: Data("something else".utf8))) == nil, true, "what isn't drawings isn't kept")
check((try? Drawings.asked(screens: [("Gone", "/widget/gone/systemSmall", nil)], complications: [:]) { try Site.shared.get($0) }) == nil, true, "nothing is sent without a screen")
let shown = sent.screens[0].entries
check(Drawings.current(of: shown, at: moment("2026-11-15T21:00:00Z"))?.svg ?? "", "<svg><text>one</text></svg>", "the drawing that began last")
check(Drawings.current(of: shown, at: moment("2026-11-15T22:13:00Z"))?.svg ?? "", "<svg><text>two</text></svg>", "a drawing from its moment on")
check(Drawings.current(of: shown, at: moment("2026-11-14T00:00:00Z"))?.svg ?? "", "<svg><text>one</text></svg>", "the first drawing before any began")
check(Drawings.current(of: [Drawings.Entry](), at: .now) == nil, true, "nothing to show without a drawing")

// An outline of the chart in the room becomes triangles that cover it and nothing else,
// whichever way it goes round and also where it turns inward.
func area(_ corners: [SIMD2<Float>], _ triangles: [Int]) -> Float {
    var area: Float = 0
    for first in stride(from: 0, to: triangles.count, by: 3) {
        let (a, b, c) = (corners[triangles[first]], corners[triangles[first + 1]], corners[triangles[first + 2]])
        // a triangle that goes round with the clock would take away from it
        area += ((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2
    }
    return area
}
let square: [SIMD2<Float>] = [[0, 0], [2, 0], [2, 2], [0, 2]]
check(Outline.triangles(square).count, 6, "a square is two triangles")
check(area(square, Outline.triangles(square)), 4, "which cover it")
check(Outline.against(Array(square.reversed())), square, "an outline that goes round with the clock is taken the other way")
check(Outline.against(square), square, "and one that goes round against it as it is")
// an L, which turns inward at one corner
let angled: [SIMD2<Float>] = [[0, 0], [3, 0], [3, 1], [1, 1], [1, 3], [0, 3]]
let cut = Outline.triangles(angled)
check(cut.count, 12, "an outline of six corners is four triangles")
check(area(angled, cut), 5, "which cover it and not the corner that it leaves out")
check(stride(from: 0, to: cut.count, by: 3).allSatisfy { area(angled, Array(cut[$0..<$0 + 3])) > 0 }, true, "each of them against the clock")
// also when the outline begins at the corner that turns inward, which isn't one to cut off
let begun = Array(angled[3...] + angled[..<3])
let inward = Outline.triangles(begun)
check(area(begun, inward), 5, "an outline that begins where it turns inward is covered too")
check(stride(from: 0, to: inward.count, by: 3).allSatisfy { area(begun, Array(inward[$0..<$0 + 3])) > 0 }, true, "by triangles that are all against the clock")
// a part of a ring, as the wheel has them
var ring: [SIMD2<Float>] = []
for step in 0...6 {
    ring.append(SIMD2(cos(Float(step) * .pi / 12), sin(Float(step) * .pi / 12)) * 0.7)
}
for step in (0...6).reversed() {
    ring.append(SIMD2(cos(Float(step) * .pi / 12), sin(Float(step) * .pi / 12)))
}
let ringed = Outline.triangles(Outline.against(ring))
check(ringed.count, 3 * (ring.count - 2), "a part of a ring is cut into triangles all the way")
var across: Float = 0
for index in ring.indices {
    let next = ring[(index + 1) % ring.count]
    across += (ring[index].x * next.y - next.x * ring[index].y) / 2
}
check(abs(area(Outline.against(ring), ringed) - abs(across)) < 0.0001, true, "which cover it")
check(Outline.triangles([[0, 0], [1, 0]]), [], "what isn't an outline has no triangles")

// The chart in the room: a shape that's still made of the same and hasn't far to go slides
// to where it is next, with what it belongs with, and everything else is there at once.
func move(_ with: String?, _ from: SIMD3<Float>?, _ to: SIMD3<Float>) -> Moves.Move {
    Moves.Move(with: with, from: from, to: to)
}
check(Moves.slide([]), [], "a chart of nothing has nothing that slides")
check(Moves.slide([move(nil, [0, 0, 0], [0.2, 0, 0]), move("moon", [0.5, 0.5, 0.5], [0.5, 0.9, 0.5]), move("moon", [0, 0, 0], [0, 0, 0])]), [true, true, true],
      "what hasn't far to go slides, and what stays where it is with it")
check(Moves.slide([move(nil, [0, 0, 0], [0.45, 0, 0]), move(nil, [0, 0, 0], [0.46, 0, 0]), move(nil, [0, 0, 0], [0.3, 0.3, 0.3])]), [true, false, false],
      "as far as slides at most does, further doesn't, whichever way it goes in the room")
check(Moves.slide([move("sun", [0.8, 0.1, 0], [-0.8, 0.1, 0]), move("sun", [0.8, 0, 0], [0.7, 0, 0]), move("moon", [0, 0, 0], [0.1, 0, 0]), move(nil, [0, 0, 0], [0.1, 0, 0])]),
      [false, false, true, true], "a planet that goes to the other side of the wheel is there at once with its stem, and the others slide")
check(Moves.slide([move("sun", nil, [0.1, 0, 0]), move("sun", [0, 0, 0], [0.1, 0, 0]), move(nil, nil, [0, 0, 0]), move(nil, [0, 0, 0], [0.1, 0, 0])]),
      [false, false, false, true], "a stem doesn't slide to a ball that was made anew, and what belongs with nothing only answers for itself")

// What a tap, a look or a drag lands on in the chart in the room is the shape itself: a ray
// through the room lands on a slab that lies along a diagonal where the slab is, and not
// beside it where the box around it is, as RealityKit would have it by itself.
func described(_ json: String) -> Shape {
    try! JSONDecoder().decode(Shape.self, from: Data(json.utf8))
}
MainActor.assumeIsolated {
    let view = ARView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
    let anchor = AnchorEntity(world: .zero)
    view.scene.addAnchor(anchor)
    let shapes = [
        described(##"{"kind": "slab", "id": "slab", "color": "#ffffff", "outline": [[0, 0], [1, 1], [0.9, 1.1], [-0.1, 0.1]], "depth": 0.02, "at": [0, 0, 0]}"##),
        described(##"{"kind": "rod", "id": "rod", "color": "#ffffff", "from": [3, 0, 0], "to": [3.4, 0, 0], "radius": 0.01}"##),
        described(##"{"kind": "ball", "id": "ball", "color": "#ffffff", "at": [5, 0, 0], "radius": 0.1}"##),
    ]
    for shape in shapes {
        let entity = Entity()
        entity.name = shape.id
        entity.transform = shape.placed()
        entity.components.set(CollisionComponent(shapes: [shape.touched()!]))
        anchor.addChild(entity)
    }
    RunLoop.main.run(until: Date().addingTimeInterval(0.5))
    func landed(_ x: Float, _ y: Float) -> [String] {
        view.scene.raycast(origin: [x, y, 5], direction: [0, 0, -1], length: 10).map(\.entity.name)
    }
    check(landed(0.45, 0.55), ["slab"], "a tap on a slab lands on it")
    check(landed(0.85, 0.15), [], "and one beside it doesn't, though it's within the box around the slab")
    check(landed(3.2, 0), ["rod"], "a tap on a rod lands on it")
    check(landed(3.2, 0.05) + landed(3.5, 0), [], "and not beside it or past its end")
    check(landed(5.05, 0.05), ["ball"], "a tap on a ball lands on it")
    check(landed(5.09, 0.09), [], "and not at the corner of the box around it")
    for kind in ["text", "halo", "light"] {
        check(described(##"{"kind": "\##(kind)", "id": "it", "color": "#ffffff", "at": [0, 0, 0], "outline": [[0, 0], [1, 0], [1, 1]]}"##).touched() == nil, true, "a \(kind) is nothing to land on by its description")
    }
    check(described(##"{"kind": "slab", "id": "it", "color": "#ffffff", "outline": [[0, 0], [1, 0]], "depth": 0.1}"##).touched() == nil, true, "nor is a slab without an outline")
}

// A shape that slides to where it is next is, on its way, where its description is on its
// way. A stem that goes a little aside stays a stem all the way: between where its two ends
// are, never longer than it was or will be, and never pointing elsewhere.
let stood = described(##"{"kind": "rod", "id": "stem", "color": "#e3a83c", "from": [0.8, 0, 0], "to": [0.8, 0.1, 0.05], "radius": 0.002}"##)
let stands = described(##"{"kind": "rod", "id": "stem", "color": "#e3a83c", "from": [0.7, 0, 0.3], "to": [0.72, 0.1, 0.34], "radius": 0.002}"##)
check(stood.toward(stands, 0).placed(), stood.placed(), "what hasn't left yet is where it was")
check(stood.toward(stands, 1), stands, "and what arrived is what it is next")
let halfway = stood.toward(stands, 0.5)
check(halfway.from! + halfway.to!, [0.75, 0, 0.15, 0.76, 0.1, 0.195], "halfway, each end of a stem is halfway")
for along in stride(from: Float(0), through: 1, by: 0.125) {
    let on = stood.toward(stands, along).placed()
    check(on.scale.y <= max(stood.placed().scale.y, stands.placed().scale.y) + 0.0001 && on.scale.x == 0.002, true, "a stem on its way is no longer or thicker than a stem")
    check(simd_dot(on.rotation.act([0, 1, 0]), [0, 1, 0]) > 0.85, true, "and stands as a stem does")
    check(abs(on.translation.y - 0.05) < 0.0001, true, "at the height of a stem")
}
let rolled = described(##"{"kind": "ball", "id": "ball", "color": "#000000", "at": [0, 0, 0], "radius": 0.03}"##)
    .toward(described(##"{"kind": "ball", "id": "ball", "color": "#000000", "at": [0.2, 0.1, 0], "radius": 0.03}"##), 0.25)
check(rolled.at!, [0.05, 0.025, 0], "a ball a quarter of the way is a quarter of the way")
check(rolled.placed().scale, [0.03, 0.03, 0.03], "and as large as it is")

if failed > 0 {
    print("\(failed) checks failed")
    exit(1)
}
print("The Swift of the app works out what it should")
