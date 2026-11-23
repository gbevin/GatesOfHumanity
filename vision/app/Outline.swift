import simd

// An outline as triangles, for the face of a slab of the chart in the room, see Volume. An
// outline may turn inward, as a part of a ring does, and go round either way.
enum Outline {
    // The outline as it goes round against the clock.
    static func against(_ corners: [SIMD2<Float>]) -> [SIMD2<Float>] {
        var area: Float = 0
        for index in corners.indices {
            let next = corners[(index + 1) % corners.count]
            area += corners[index].x * next.y - next.x * corners[index].y
        }
        return area < 0 ? Array(corners.reversed()) : corners
    }

    // The triangles of an outline that goes round against the clock, each by the places of its
    // three corners in it: one corner after the other is cut off that sticks out and has no
    // other corner inside it.
    static func triangles(_ corners: [SIMD2<Float>]) -> [Int] {
        func turn(_ a: SIMD2<Float>, _ b: SIMD2<Float>, _ c: SIMD2<Float>) -> Float {
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        }
        var left = Array(corners.indices)
        var triangles: [Int] = []
        while left.count > 3 {
            var cut = false
            for place in left.indices {
                let before = left[(place + left.count - 1) % left.count]
                let corner = left[place]
                let after = left[(place + 1) % left.count]
                let (a, b, c) = (corners[before], corners[corner], corners[after])
                guard turn(a, b, c) > 0 else {
                    continue
                }
                let holds = left.contains { other in
                    other != before && other != corner && other != after &&
                        turn(a, b, corners[other]) >= 0 && turn(b, c, corners[other]) >= 0 && turn(c, a, corners[other]) >= 0
                }
                if !holds {
                    triangles += [before, corner, after]
                    left.remove(at: place)
                    cut = true
                    break
                }
            }
            // an outline that crosses itself has no corner to cut
            if !cut {
                break
            }
        }
        return left.count == 3 ? triangles + left : triangles
    }
}
