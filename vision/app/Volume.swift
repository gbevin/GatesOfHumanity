import RealityKit
import SwiftUI
import WebKit

// A chart in the room, beside the window: the body graph inside the wheel of gates, with the
// planets over it. The site describes it as shapes, see WheelVolume there, at the address of
// the chart's image with volume in the place of image, and this builds them as they come:
// nothing here knows what a chart is. The page says which chart it shows, see app.js, so the
// shapes follow it: through time they slide to where they are next, see Moves.

private struct Described: Decodable {
    let room: [Float]
    let shapes: [Shape]
}

@MainActor
final class Volume: ObservableObject {
    static let window = "volume"
    // Whether the chart is in the room.
    @Published var open = false
    let root = Entity()
    // What stays as it is when the chart is turned round: the light on it.
    let fixed = Entity()
    weak var pages: Pages?
    private(set) var room = SIMD3<Float>(2, 2, 2)
    // The address of the shapes of the chart that the page shows.
    private var address: String?
    private var asked = 0
    private var built: [String: (shape: Shape, entity: Entity)] = [:]
    // The part of the chart that was tapped here and the shape that it was tapped on, while
    // the panel explains it: what's quiet is only there for what's chosen.
    private var chosen: String?
    private var tappedOn: String?
    private let sphere = MeshResource.generateSphere(radius: 1)
    private let cylinder = MeshResource.generateCylinder(height: 1, radius: 1)
    private static let asking = DispatchQueue(label: "volume", qos: .userInitiated)

    // A page says which chart it shows, by the address of its image. A page that shows
    // something else, as a table of transits, leaves the chart that's in the room there, as a
    // page that says nothing does.
    func heard(_ image: Any) {
        guard let image = image as? String, let shown = image.range(of: "/wheel/image/") else {
            return
        }
        address = "/wheel/volume/" + image[shown.upperBound...]
        if open {
            show()
        }
    }

    // Asks the site for the shapes of the chart and builds them, unless another chart was asked
    // for in the meantime.
    func show() {
        asked += 1
        let number = asked
        guard let address else {
            build([])
            if Probe.volume != nil {
                print("volume of no chart")
            }
            return
        }
        Self.asking.async {
            let answered = Probe.shapes.map { try? Data(contentsOf: URL(fileURLWithPath: $0)) } ?? (try? Site.shared.get(address))
            let described = answered.flatMap { try? JSONDecoder().decode(Described.self, from: $0) }
            DispatchQueue.main.async {
                guard number == self.asked else {
                    return
                }
                if let described, described.room.count == 3 {
                    self.room = SIMD3(described.room)
                }
                self.build(described?.shapes ?? [])
                self.objectWillChange.send()
                if let probed = Probe.volume {
                    // for a check, which also says what to tap
                    print("volume \(self.built.count) shapes of \(address)")
                    if let tapped = self.built[probed]?.entity {
                        self.tapped(tapped)
                    }
                }
            }
        }
    }

    // A shape that's still made of the same goes to where it is now, sliding there or at once,
    // see Moves, and the others are made anew. What's quiet comes and goes by itself.
    private func build(_ shapes: [Shape]) {
        let before = shapes.map { shape in built[shape.id].flatMap { $0.shape.made == shape.made ? $0 : nil } }
        let slides = Moves.slide(zip(shapes, before).map { shape, was in
            Moves.Move(with: shape.quiet == true ? nil : shape.part, from: was?.shape.placed().translation, to: shape.placed().translation)
        })
        var kept: Set<String> = []
        var moved = (sliding: 0, atOnce: 0)
        for (index, shape) in shapes.enumerated() {
            kept.insert(shape.id)
            if let was = before[index] {
                if was.shape != shape {
                    if slides[index], isThere(shape) {
                        // from where it is now, when it was still on its way
                        let from = sliding[shape.id].map { $0.from.toward($0.to, Self.eased($0.along)) } ?? was.shape
                        sliding[shape.id] = (from, shape, 0, false)
                        moved.sliding += 1
                    } else {
                        sliding[shape.id] = nil
                        was.entity.transform = shape.placed()
                        moved.atOnce += 1
                    }
                    built[shape.id] = (shape, was.entity)
                    was.entity.isEnabled = isThere(shape)
                }
                continue
            }
            sliding[shape.id] = nil
            built[shape.id]?.entity.removeFromParent()
            let entity = made(shape)
            entity.name = shape.id
            entity.transform = shape.placed()
            entity.isEnabled = isThere(shape)
            (shape.kind == "light" ? fixed : root).addChild(entity)
            built[shape.id] = (shape, entity)
        }
        #if targetEnvironment(simulator)
        if Probe.volume != nil, moved.sliding + moved.atOnce > 0 {
            // for vision/check.sh
            print("moved \(moved.sliding) sliding, \(moved.atOnce) at once")
        }
        #endif
        for id in built.keys where !kept.contains(id) {
            built.removeValue(forKey: id)?.entity.removeFromParent()
        }
        probed()
    }

    // Something was tapped: the page explains the part that it names.
    func tapped(_ entity: Entity) {
        var tapped: Entity? = entity
        while let each = tapped, built[each.name] == nil {
            tapped = each.parent
        }
        guard let shape = tapped.flatMap({ built[$0.name]?.shape }), let part = shape.part else {
            return
        }
        choose(part, on: shape.id)
        // a page without a chart explains nothing, and nothing stays chosen
        pages?.call("appChose", part) { _, explains in
            if explains as? Bool != true, self.chosen == part {
                self.choose(nil, on: nil)
            }
        }
    }

    // What the page explains changed. What was chosen here stays chosen while the page
    // explains one of the parts that it names, as after a step through time. Once the page
    // explains something else, which was chosen there, or nothing anymore, it isn't.
    func explained() {
        guard let chosen else {
            return
        }
        pages?.call("appExplains") { _, part in
            guard self.chosen == chosen else {
                return
            }
            if let part = part as? String, chosen.split(separator: " ").contains(Substring(part)) {
                return
            }
            self.choose(nil, on: nil)
        }
    }

    // How long something takes to slide to where it is next, in seconds, and how thin
    // something quiet is when it comes, beside how thick it gets.
    private static let slides: Float = 0.35
    private static let thread: Float = 0.05
    // What's on its way: from where to where, how far along it is, and whether it's growing
    // from a thread meanwhile.
    private var sliding: [String: (from: Shape, to: Shape, along: Float, grows: Bool)] = [:]

    // slow at first and at last
    private static func eased(_ along: Float) -> Float {
        along * along * (3 - 2 * along)
    }

    // Takes what's on its way a little further, as much as this many seconds do. Each shape
    // is where its description is on its way, see Shape, rather than between two ways of
    // turning and sizing it, which sends a rod elsewhere on its way.
    func advance(_ seconds: Float) {
        for (id, slide) in sliding {
            guard let entity = built[id]?.entity else {
                sliding[id] = nil
                continue
            }
            let along = min(1, slide.along + seconds / Self.slides)
            let eased = Self.eased(along)
            entity.transform = slide.from.toward(slide.to, eased).placed(thicker: slide.grows ? Self.thread + (1 - Self.thread) * eased : 1)
            sliding[id] = along < 1 ? (slide.from, slide.to, along, slide.grows) : nil
        }
    }

    // What's quiet comes for what's chosen, growing from a thread, and goes with it.
    private func choose(_ part: String?, on id: String?) {
        chosen = part
        tappedOn = id
        for (shape, entity) in built.values where shape.quiet == true {
            let comes = isThere(shape)
            if comes, !entity.isEnabled {
                entity.transform = shape.placed(thicker: Self.thread)
                entity.isEnabled = true
                sliding[shape.id] = (shape, shape, 0, true)
            } else if !comes {
                entity.isEnabled = false
                sliding[shape.id] = nil
            }
        }
        probed()
    }

    // For vision/check.sh: how much of what's quiet is there.
    private func probed() {
        #if targetEnvironment(simulator)
        if Probe.volume != nil {
            print("quiet \(built.values.filter { $0.shape.quiet == true && $0.entity.isEnabled }.count) there")
        }
        #endif
    }

    // Whether a shape is there: one that's quiet only while what's chosen names the part that
    // it names first, as a planet does and a gate of the wheel that the planet is in, or was
    // tapped on a shape that it's there with.
    private func isThere(_ shape: Shape) -> Bool {
        guard shape.quiet == true else {
            return true
        }
        if let tappedOn, shape.with?.contains(tappedOn) == true {
            return true
        }
        guard let named = shape.part?.split(separator: " ").first, let chosen else {
            return false
        }
        return chosen.split(separator: " ").contains(named)
    }

    private func made(_ shape: Shape) -> Entity {
        let entity: Entity
        switch shape.kind {
        case "ball":
            entity = ModelEntity(mesh: sphere, materials: [material(shape)])
        case "rod":
            entity = ModelEntity(mesh: cylinder, materials: [material(shape)])
        case "slab":
            let mesh = Self.slab((shape.outline ?? []).filter { $0.count == 2 }.map { SIMD2($0[0], $0[1]) }, depth: shape.depth ?? 0)
            entity = mesh.map { ModelEntity(mesh: $0, materials: [material(shape)]) } ?? Entity()
        case "text":
            entity = Entity()
            let color = UIColor(written: shape.color)
            let letters = MeshResource.generateText(shape.text ?? "", extrusionDepth: 0.0005, font: .systemFont(ofSize: CGFloat(shape.size ?? 0.03), weight: .semibold))
            let written = ModelEntity(mesh: letters, materials: [UnlitMaterial(color: color)])
            // around its place rather than from it on
            written.position = -letters.bounds.center
            entity.addChild(written)
            if shape.faces == true {
                // It's over the chart, with the room behind it, which is light or dark: the
                // same letters in the other shade, a little behind and aside, are what it's
                // read against. A text that doesn't face lies on what it's read against.
                var light: CGFloat = 0
                color.getWhite(&light, alpha: nil)
                let behind = ModelEntity(mesh: letters, materials: [UnlitMaterial(color: UIColor(white: light > 0.5 ? 0 : 1, alpha: 1))])
                let aside = (shape.size ?? 0.03) * 0.05
                behind.position = written.position + [aside, -aside, -0.0008]
                entity.addChild(behind)
                entity.components.set(BillboardComponent())
            }
        case "halo":
            // It faces whoever looks, from behind where it's at: so it's behind the chart from
            // every side, without anything that says where the looking is from.
            entity = Entity()
            entity.components.set(BillboardComponent())
            let radius = shape.radius ?? 1
            let outline = (shape.outline ?? []).filter { $0.count == 2 }.map { CGPoint(x: CGFloat($0[0]), y: CGFloat($0[1])) }
            if let soft = Self.soft(outline, within: CGFloat(radius), edge: CGFloat(shape.soft ?? 0), color: UIColor(written: shape.color).withAlphaComponent(CGFloat(shape.opacity ?? 1))) {
                var material = UnlitMaterial()
                material.color = .init(tint: .white, texture: .init(soft))
                material.blending = .transparent(opacity: 1.0)
                let behind = ModelEntity(mesh: .generatePlane(width: radius * 2, height: radius * 2), materials: [material])
                behind.position.z = -(shape.behind ?? 0)
                entity.addChild(behind)
            }
        case "light":
            entity = Entity()
            entity.components.set(DirectionalLightComponent(color: UIColor(written: shape.color), intensity: 1500 * (shape.strength ?? 1)))
        default:
            entity = Entity()
        }
        // What a tap, a look or a drag lands on. A drag turns the chart from anything solid of
        // it, and what names a part lights up when it's looked at and explains it when tapped.
        if let touched = shape.touched() {
            entity.components.set(CollisionComponent(shapes: [touched]))
            entity.components.set(InputTargetComponent())
        } else if shape.kind == "text", shape.part != nil {
            entity.generateCollisionShapes(recursive: true)
            entity.components.set(InputTargetComponent())
        }
        if shape.part != nil {
            entity.components.set(HoverEffectComponent())
        }
        return entity
    }

    // What a shape is made of. Nothing of it is seen through. What glows is its own light.
    private func material(_ shape: Shape) -> RealityKit.Material {
        let color = UIColor(written: shape.color)
        var material = PhysicallyBasedMaterial()
        material.baseColor = .init(tint: color)
        material.metallic = 0.0
        if shape.look == "matte" {
            material.roughness = 0.85
        } else {
            // a soft shine rather than a hard one
            material.roughness = 0.5
            material.clearcoat = .init(floatLiteral: 0.35)
            material.clearcoatRoughness = .init(floatLiteral: 0.4)
        }
        if let glow = shape.glow, glow > 0 {
            material.emissiveColor = .init(color: color)
            material.emissiveIntensity = glow
        }
        // seen from either side, as an outline can go round either way
        material.faceCulling = .none
        return material
    }

    // A picture of a halo: its outline in its color, with an edge that fades outward over this
    // far, in a square of this far to every side of its middle.
    private static func soft(_ outline: [CGPoint], within radius: CGFloat, edge: CGFloat, color: UIColor) -> TextureResource? {
        let size = 768
        guard outline.count >= 3, radius > 0,
              let picture = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                      bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return nil
        }
        let scale = CGFloat(size) / (2 * radius)
        picture.translateBy(x: CGFloat(size) / 2, y: CGFloat(size) / 2)
        picture.scaleBy(x: scale, y: scale)
        // From the widest line around the outline to the narrowest, each less faint than the
        // one before and in its place rather than over it, and then the outline itself.
        var opacity: CGFloat = 0
        color.getWhite(nil, alpha: &opacity)
        picture.setBlendMode(.copy)
        picture.setLineJoin(.round)
        let steps = 40
        for step in stride(from: steps, through: 1, by: -1) {
            let out = CGFloat(step) / CGFloat(steps)
            // eased, so that it leaves no edge where it begins to fade nor where it's gone
            let left = 1 - out * out * (3 - 2 * out)
            picture.setStrokeColor(color.withAlphaComponent(opacity * left).cgColor)
            picture.setLineWidth(2 * edge * out)
            picture.addLines(between: outline)
            picture.closePath()
            picture.strokePath()
        }
        picture.addLines(between: outline)
        picture.closePath()
        picture.setFillColor(color.cgColor)
        picture.fillPath()
        return picture.makeImage().flatMap { try? TextureResource(image: $0, options: .init(semantic: .color)) }
    }

    // An outline as thick as its depth, around where it's at: its two faces and a side for each
    // of its edges.
    private static func slab(_ outline: [SIMD2<Float>], depth: Float) -> MeshResource? {
        guard outline.count >= 3 else {
            return nil
        }
        let corners = Outline.against(outline)
        var positions: [SIMD3<Float>] = []
        var normals: [SIMD3<Float>] = []
        var triangles: [UInt32] = []
        let ears = Outline.triangles(corners)
        for side: Float in [1, -1] {
            let first = UInt32(positions.count)
            positions += corners.map { SIMD3($0.x, $0.y, side * depth / 2) }
            normals += Array(repeating: SIMD3(0, 0, side), count: corners.count)
            for ear in stride(from: 0, to: ears.count, by: 3) {
                let triangle = side > 0 ? [ears[ear], ears[ear + 1], ears[ear + 2]] : [ears[ear], ears[ear + 2], ears[ear + 1]]
                triangles += triangle.map { first + UInt32($0) }
            }
        }
        for index in corners.indices {
            let from = corners[index]
            let to = corners[(index + 1) % corners.count]
            let along = to - from
            guard simd_length(along) > 0 else {
                continue
            }
            let out = simd_normalize(SIMD3(along.y, -along.x, 0))
            let first = UInt32(positions.count)
            positions += [SIMD3(from.x, from.y, depth / 2), SIMD3(from.x, from.y, -depth / 2), SIMD3(to.x, to.y, -depth / 2), SIMD3(to.x, to.y, depth / 2)]
            normals += Array(repeating: out, count: 4)
            triangles += [first, first + 1, first + 2, first, first + 2, first + 3]
        }
        var described = MeshDescriptor()
        described.positions = MeshBuffer(positions)
        described.normals = MeshBuffer(normals)
        described.primitives = .triangles(triangles)
        return try? MeshResource.generate(from: [described])
    }
}

private extension UIColor {
    // A color as the site writes one: #rrggbb.
    convenience init(written: String) {
        let value = UInt32(written.dropFirst(), radix: 16) ?? 0xffffff
        self.init(red: CGFloat((value >> 16) & 0xff) / 255, green: CGFloat((value >> 8) & 0xff) / 255, blue: CGFloat(value & 0xff) / 255, alpha: 1)
    }
}

struct VolumeView: View {
    @ObservedObject var volume: Volume
    // How far the chart was turned round, by a drag across it.
    @State private var turned: Float = 0
    @State private var advancing: EventSubscription?
    @State private var turning: Float = 0

    var body: some View {
        GeometryReader3D { space in
            RealityView { content in
                content.add(volume.root)
                content.add(volume.fixed)
                // what slides to where it is next does so a little with each picture of the room
                advancing = content.subscribe(to: SceneEvents.Update.self) { step in
                    volume.advance(Float(step.deltaTime))
                }
            } update: { content in
                // as large as the room that the volume has for it
                let has = content.convert(space.frame(in: .local), from: .local, to: .scene).extents
                volume.root.scale = SIMD3(repeating: min(has.x / volume.room.x, has.y / volume.room.y, has.z / volume.room.z))
                volume.root.orientation = simd_quatf(angle: turned + turning, axis: [0, 1, 0])
            }
            .gesture(TapGesture().targetedToAnyEntity().onEnded { tap in
                volume.tapped(tap.entity)
            })
            .gesture(DragGesture(minimumDistance: 12).targetedToAnyEntity().onChanged { drag in
                turning = Float(drag.translation.width) * 0.006
            }.onEnded { _ in
                turned += turning
                turning = 0
            })
        }
        // what's behind the chart reaches out of the room that the chart takes
        .preferredWindowClippingMargins(.all, 700)
        .onAppear {
            volume.open = true
            volume.show()
        }
        .onDisappear {
            volume.open = false
        }
    }
}
