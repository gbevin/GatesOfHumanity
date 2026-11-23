import SwiftUI

// The emblem of the icon: the wheel of the 64 gates with a few of them lit, around the outline
// of the body graph. It's drawn here with the measures of ios/icon.py, since only the system
// can show the picture of an icon.
struct Emblem: View {
    private static let lit: Set<Int> = [1, 8, 9, 20, 29, 36, 44, 45, 57]
    private static let gold = Gradient(colors: [Color(red: 0.98, green: 0.84, blue: 0.49), Color(red: 0.85, green: 0.57, blue: 0.16)])
    private static let ring = Color(red: 0.64, green: 0.64, blue: 0.87)
    private static let gates = [Color(red: 0.57, green: 0.57, blue: 0.78), Color(red: 0.50, green: 0.50, blue: 0.71)]
    private static let night = Color(red: 0.09, green: 0.09, blue: 0.27)

    var body: some View {
        Canvas { context, size in
            // The icon's outer ring has a radius of 436.
            let unit = min(size.width, size.height) / 2 / 440
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            func circle(_ radius: Double) -> Path {
                Path(ellipseIn: CGRect(x: center.x - radius * unit, y: center.y - radius * unit, width: 2 * radius * unit, height: 2 * radius * unit))
            }
            let top = CGPoint(x: center.x, y: center.y - 300 * unit)
            let bottom = CGPoint(x: center.x, y: center.y + 300 * unit)
            let golden = GraphicsContext.Shading.linearGradient(Self.gold, startPoint: top, endPoint: bottom)

            context.stroke(circle(436), with: .color(Self.ring.opacity(0.55)), lineWidth: 6 * unit)
            let step = 360.0 / 64
            let gap = 1.1
            for gate in 0..<64 {
                let start = Angle.degrees(Double(gate) * step + gap / 2 - 90)
                let end = Angle.degrees(Double(gate + 1) * step - gap / 2 - 90)
                var path = Path()
                path.addArc(center: center, radius: 416 * unit, startAngle: start, endAngle: end, clockwise: false)
                path.addArc(center: center, radius: 318 * unit, startAngle: end, endAngle: start, clockwise: true)
                path.closeSubpath()
                context.fill(path, with: Self.lit.contains(gate) ? golden : .color(Self.gates[gate % 2]))
            }
            context.fill(circle(300), with: .color(Self.night))
            context.stroke(circle(300), with: .color(Self.ring.opacity(0.6)), lineWidth: 6 * unit)

            // The outline around the body graph in wheel.svg, M 480 130 L 205 675 A 340 340 0 0 0 755 675 Z,
            // around its middle at 480, 472.5.
            let scale = 240 / 342.5 * unit
            func at(_ x: Double, _ y: Double) -> CGPoint {
                CGPoint(x: center.x + (x - 480) * scale, y: center.y + (y - 472.5) * scale)
            }
            let curve = at(480, 675 - (340 * 340 - 275 * 275).squareRoot())
            var outline = Path()
            outline.move(to: at(480, 130))
            outline.addLine(to: at(205, 675))
            outline.addArc(center: curve, radius: 340 * scale,
                           startAngle: .radians(atan2(at(205, 675).y - curve.y, at(205, 675).x - curve.x)),
                           endAngle: .radians(atan2(at(755, 675).y - curve.y, at(755, 675).x - curve.x)), clockwise: true)
            outline.closeSubpath()
            context.fill(outline, with: golden)
            context.stroke(outline, with: golden, style: StrokeStyle(lineWidth: 32 * unit, lineJoin: .round))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }
}
