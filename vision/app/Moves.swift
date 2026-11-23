import simd

// How the chart in the room gets from one chart to the next. After a step through time a
// planet has a little further to go round the wheel, and slides there with its stem and its
// sign. In another chart it's elsewhere, or isn't the same ball at all, and what slid there
// would cross the chart on its way, or go there without what it belongs with: that's there
// at once.
enum Moves {
    // How far something goes at most and still slides, where the wheel is two across: as far
    // as the Moon goes in a day, with what planets beside it push it aside.
    static let far: Float = 0.45

    // A shape of the chart that's built next: what it belongs with, if anything, where it was
    // when it's still made of the same, and where it is now.
    struct Move {
        let with: String?
        let from: SIMD3<Float>?
        let to: SIMD3<Float>
    }

    // Which of the shapes slide to where they are now. One that isn't made of the same or
    // that has far to go doesn't, and neither does anything that belongs with it.
    static func slide(_ moves: [Move]) -> [Bool] {
        func near(_ move: Move) -> Bool {
            move.from.map { simd_distance($0, move.to) <= far } ?? false
        }
        let apart = Set(moves.filter { !near($0) }.compactMap(\.with))
        return moves.map { near($0) && !($0.with.map(apart.contains) ?? false) }
    }
}
