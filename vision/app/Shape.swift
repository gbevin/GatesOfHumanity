import Foundation
import RealityKit
import simd

// A shape of the chart in the room, as the site describes it, see WheelVolume there: what it's
// made of, where it is, and what a tap, a look or a drag lands on when it's on it.
struct Shape: Decodable, Equatable {
    let kind: String
    let id: String
    let color: String
    var part: String?
    var glow: Float?
    var look: String?
    var quiet: Bool?
    var with: [String]?
    var opacity: Float?
    var behind: Float?
    var soft: Float?
    var strength: Float?
    var at: [Float]?
    var from: [Float]?
    var to: [Float]?
    var turned: [Float]?
    var radius: Float?
    var depth: Float?
    var size: Float?
    var outline: [[Float]]?
    var faces: Bool?
    var text: String?

    // What it's made of, apart from where it is: a shape that only moved keeps what was built.
    var made: Shape {
        var made = self
        made.at = nil
        // a rod is the same rod wherever it runs, a light is where it's from
        if kind != "light" {
            made.from = nil
        }
        made.to = nil
        made.turned = nil
        made.with = nil
        return made
    }

    // Where it is. A ball is one sphere at its size and a rod one cylinder at its length, so a
    // rod can grow from a thinner one.
    func placed(thicker: Float = 1) -> Transform {
        switch kind {
        case "ball":
            return Transform(scale: SIMD3(repeating: radius ?? 0), translation: SIMD3(at ?? [0, 0, 0]))
        case "rod":
            let from = SIMD3(self.from ?? [0, 0, 0])
            let to = SIMD3(self.to ?? [0, 0, 0])
            let length = simd_distance(from, to)
            let along = length > 0 ? simd_quatf(from: [0, 1, 0], to: (to - from) / length) : simd_quatf(angle: 0, axis: [0, 1, 0])
            let radius = (radius ?? 0) * thicker
            return Transform(scale: [radius, length, radius], rotation: along, translation: (from + to) / 2)
        case "light":
            // it shines from where it's from onto the middle
            let from = simd_normalize(SIMD3(self.from ?? [0, 1, 0]))
            return Transform(rotation: simd_quatf(from: [0, 0, -1], to: -from))
        default:
            // turned around x, then y, then z, in degrees
            let angles = (turned ?? [0, 0, 0]).map { $0 * .pi / 180 }
            let turn = simd_quatf(angle: angles[2], axis: [0, 0, 1]) * simd_quatf(angle: angles[1], axis: [0, 1, 0]) * simd_quatf(angle: angles[0], axis: [1, 0, 0])
            return Transform(rotation: turn, translation: SIMD3(at ?? [0, 0, 0]))
        }
    }
}

extension Shape {
    // What of it a tap, a look or a drag lands on, in its own measures: the shape itself. The
    // box around a slab, which is what RealityKit makes by itself, is far larger than a slab
    // that lies along a diagonal of the wheel, and a tap beside it would land on it. A text
    // is its letters, which only those that it was built from can say, and what's behind the
    // chart and the lights are nothing to land on.
    func touched() -> ShapeResource? {
        switch kind {
        case "ball":
            return .generateSphere(radius: 1)
        case "rod":
            return .generateBox(size: [2, 1, 2])
        case "slab":
            let corners = (outline ?? []).filter { $0.count == 2 }
            let half = (depth ?? 0) / 2
            return corners.count < 3 ? nil : .generateConvex(from: corners.map { SIMD3($0[0], $0[1], half) } + corners.map { SIMD3($0[0], $0[1], -half) })
        default:
            return nil
        }
    }
}

extension Shape {
    // It on its way to where it is next, this far along: every place of it that far between
    // where it was and where it will be. A rod on its way is the rod between where its two
    // ends are on theirs, which what's between two rods as they're turned and sized isn't:
    // halfway between a rod and the same rod a little aside, that's a rod of another length
    // that points elsewhere.
    func toward(_ next: Shape, _ along: Float) -> Shape {
        func between(_ was: [Float]?, _ will: [Float]?) -> [Float]? {
            guard let was, let will, was.count == will.count else {
                return will
            }
            return zip(was, will).map { $0 + ($1 - $0) * along }
        }
        var on = next
        on.at = between(at, next.at)
        on.from = between(from, next.from)
        on.to = between(to, next.to)
        return on
    }
}

