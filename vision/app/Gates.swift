import RIFE2NativeApp
import SwiftUI

// The app on visionOS: the pages of the site in a window, with what a window in a room has
// around it. The charts and the other pages are tabs at its side, and what steps through time
// and what a chart is saved and shared with are below it. The pages leave those out
// themselves there, see app.js of the site.
@main
struct Gates: App {
    @StateObject private var pages: Pages
    @StateObject private var explained: Explained
    @StateObject private var volume: Volume
    private static let window = "pages"

    init() {
        let explained = Explained()
        let volume = Volume()
        let pages = Pages()
        pages.listen("explained", explained.heard)
        pages.listen("chart", volume.heard)
        explained.pages = pages
        explained.changed = volume.explained
        volume.pages = pages
        _pages = StateObject(wrappedValue: pages)
        _explained = StateObject(wrappedValue: explained)
        _volume = StateObject(wrappedValue: volume)
    }

    var body: some Scene {
        WindowGroup(id: Self.window) {
            Shown(pages: pages, explained: explained, volume: volume)
                // the narrowest bar below it fits between its corners, and its tabs beside it
                .frame(minWidth: 460, minHeight: 520)
                .task {
                    if let url = await Probe.tapped() {
                        pages.show(url)
                    }
                }
                .onOpenURL(perform: pages.show)
        }
        .windowResizability(.contentMinSize)
        // a chart is as wide as it's high, with its dates above it
        .defaultSize(Probe.size ?? CGSize(width: 1000, height: 1120))
        .commands {
            Menus(pages: pages)
        }

        // The chart in the room, in a room as large as the chart, so that what it's moved and sized by is right below it, from
        // the size of a globe on a desk to the height of a room.
        WindowGroup(id: Volume.window) {
            VolumeView(volume: volume)
                .frame(minWidth: 500, maxWidth: 3600, minHeight: 450, maxHeight: 3240)
                .frame(minDepth: 500, maxDepth: 3600)
        }
        .windowStyle(.volumetric)
        .windowResizability(.contentSize)
        // It's the chart of a page, so it only comes when it's asked for from the window of the
        // pages: by itself, at a start of the app, it would stay empty.
        .defaultLaunchBehavior(.suppressed)
        .restorationBehavior(.disabled)
        .defaultSize(width: 0.9, height: 0.81, depth: 0.9, in: .meters)
        // beside the window, at the side that the panel isn't at
        .defaultWindowPlacement { _, context in
            // shapes that are being designed are looked at from up close
            Probe.shapes == nil ? context.windows.first { $0.id == Self.window }.map { WindowPlacement(.leading($0)) } ?? WindowPlacement() : WindowPlacement()
        }
    }
}

// A page that has a tab. visionOS shows eight tabs at most, so the transits by month and by
// day share one, and which of the two it is is chosen below the window.
private struct Place: Identifiable {
    let name: String
    let symbol: String
    let path: String

    var id: String {
        path
    }

    static let all = [
        Place(name: "Birth Chart", symbol: "person", path: "/wheel/birth"),
        Place(name: "Connection Chart", symbol: "person.2", path: "/wheel/connection"),
        Place(name: "Transit Chart", symbol: "moon.stars", path: "/wheel"),
        Place(name: "Birth Transit Chart", symbol: "person.badge.clock", path: "/wheel/birth-transit"),
        Place(name: "Connection Transit Chart", symbol: "person.2.badge.gearshape", path: "/wheel/connection-transit"),
        Place(name: "Transits by Month or Day", symbol: "calendar", path: "/range"),
        Place(name: "People", symbol: "person.crop.rectangle.stack", path: "/people"),
        Place(name: "Learn", symbol: "book", path: "/learn"),
    ]

    // The tab of a page: its own, or the one that it's a page under, as a page to learn from.
    static func of(_ path: String) -> Place? {
        all.first { $0.path == path } ?? all.filter { path.hasPrefix($0.path + "/") }.max { $0.path.count < $1.path.count }
    }
}

private struct Shown: View {
    @Environment(\.openWindow) private var openWindow
    @ObservedObject var pages: Pages
    let explained: Explained
    let volume: Volume
    // The tab that was chosen last, which stays so on a page that has none.
    @State private var last = Place.all[0].path

    // What a bottom corner of the window takes of its width: it's what the window is sized
    // by, so nothing below the window is over it.
    private static let corner: CGFloat = 44

    private var chosen: Binding<String> {
        Binding {
            Place.of(pages.path)?.path ?? last
        } set: { path in
            last = path
            pages.go(path)
        }
    }

    var body: some View {
        GeometryReader { window in
            // The tabs are those of visionOS, which it puts beside the window. They all show the
            // one web view, which is over what they'd show themselves.
            TabView(selection: chosen) {
                ForEach(Place.all) { place in
                    Color.clear
                        .tabItem {
                            Label(place.name, systemImage: place.symbol)
                        }
                        .tag(place.path)
                }
            }
            .overlay {
                NativePagesView(pages: pages)
            }
            .onChange(of: pages.path) { _, path in
                if let place = Place.of(path) {
                    last = place.path
                }
            }
            .ornament(visibility: pages.charted || pages.canGoBack ? .visible : .hidden, attachmentAnchor: .scene(.bottom)) {
                if pages.charted {
                    Below(pages: pages, volume: volume, room: window.size.width - 2 * Self.corner)
                } else {
                    // a page that was come to from another one, as a topic to learn about
                    Button {
                        pages.back()
                    } label: {
                        Label("Back", systemImage: "chevron.backward")
                    }
                    .padding(12)
                    .glassBackgroundEffect()
                }
            }
            .task {
                if Probe.volume != nil {
                    openWindow(id: Volume.window)
                }
            }
            .ornament(attachmentAnchor: .scene(.trailing), contentAlignment: .leading) {
                Beside(explained: explained, height: window.size.height * 0.8)
            }
        }
    }
}

// What's beside the window once a part of a chart was tapped: the panel that explains it,
// which slides out from behind the window and back.
private struct Beside: View {
    @ObservedObject var explained: Explained
    let height: CGFloat

    var body: some View {
        ZStack {
            if explained.shown {
                VStack(spacing: 0) {
                    HStack {
                        Text("Explore").font(.title3.weight(.semibold))
                        Spacer()
                        Button {
                            explained.close()
                        } label: {
                            Label("Close", systemImage: "xmark")
                        }
                        .labelStyle(.iconOnly)
                        .buttonBorderShape(.circle)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                    NativePagesView(pages: explained)
                }
                .frame(width: 420, height: height)
                .glassBackgroundEffect()
                .padding(.leading, 28)
                .transition(.move(edge: .leading).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.45), value: explained.shown)
    }
}

// What's below the window of a chart: the steps through time, and what the chart is kept and
// passed on with. Each presses the button that the page has for it. The bar stays between the
// bottom corners of the window, which are what the window is sized by: in a window that's too
// narrow for all of it, first what comes after the steps is in a menu, then the steps are
// one pair of buttons for the step that's chosen between them, and then the choice between
// the transits by month and by day is in the menu too.
private struct Below: View {
    @Environment(\.openWindow) private var openWindow
    @Environment(\.dismissWindow) private var dismissWindow
    @ObservedObject var pages: Pages
    @ObservedObject var volume: Volume
    let room: CGFloat
    // Whether the link was copied a moment ago, which its button shows.
    @State private var copied = false
    // The step of the one pair of buttons.
    @State private var unit = Below.steps[0].1

    private static let steps = [("Day", "day"), ("Hour", "hour"), ("Minute", "minute")]

    var body: some View {
        ViewThatFits(in: .horizontal) {
            bar(every: true, rest: true, months: true)
            bar(every: true, rest: false, months: true)
            bar(every: false, rest: false, months: true)
            bar(every: false, rest: false, months: false)
        }
        .padding(12)
        .glassBackgroundEffect()
        .onGeometryChange(for: [CGFloat].self) { bar in
            [bar.size.width, room]
        } action: { sides in
            Probe.below(sides[0], of: sides[1])
        }
        .frame(maxWidth: room)
    }

    // A bar with a pair of buttons for every step or for one, with the rest of it in the bar
    // or in a menu, and what chooses between the transits by month and by day so as well.
    private func bar(every: Bool, rest inBar: Bool, months chosen: Bool) -> some View {
        HStack(spacing: every ? 20 : 12) {
            if ranged && chosen {
                months
                    .pickerStyle(.segmented)
                    .frame(width: 180)
            }
            Button("Now") {
                pages.press("#change-time button[name=now]")
            }
            if every {
                ForEach(Self.steps, id: \.1) { step in
                    stepped(step.0, by: step.1) {
                        Text(step.0).frame(minWidth: 64)
                    }
                }
            } else {
                stepped(Self.steps.first { $0.1 == unit }?.0 ?? "", by: unit) {
                    Picker("Step", selection: $unit) {
                        ForEach(Self.steps, id: \.1) { step in
                            Text(step.0).tag(step.1)
                        }
                    }
                    .pickerStyle(.menu)
                    // or it's squeezed between its buttons
                    .fixedSize()
                }
            }
            if !inBar {
                Menu {
                    rest.labelStyle(.titleAndIcon)
                    if ranged && !chosen {
                        months
                    }
                } label: {
                    Label("More", systemImage: "ellipsis")
                }
                .labelStyle(.iconOnly)
                .buttonBorderShape(.circle)
            } else {
                Divider().frame(height: 32)
                rest
                    .toggleStyle(.button)
                    .labelStyle(.iconOnly)
                    // Without it a button that isn't shown yet is wider than it is once it's shown,
                    // and a bar that fits is taken for one that doesn't.
                    .buttonBorderShape(.circle)
            }
        }
    }

    private var ranged: Bool {
        pages.path.hasPrefix("/range")
    }

    private var months: some View {
        Picker("Transits by", selection: Binding {
            pages.path
        } set: { path in
            pages.go(path)
        }) {
            Text("Month").tag("/range")
            Text("Day").tag("/range/d")
        }
    }

    private func stepped(_ name: String, by unit: String, @ViewBuilder between: () -> some View) -> some View {
        HStack(spacing: 4) {
            Button {
                pages.step(unit, later: false)
            } label: {
                Image(systemName: "chevron.left")
            }
            .accessibilityLabel("Previous \(name)")
            between()
            Button {
                pages.step(unit, later: true)
            } label: {
                Image(systemName: "chevron.right")
            }
            .accessibilityLabel("Next \(name)")
        }
        // held, a step goes on
        .buttonRepeatBehavior(.enabled)
        .buttonBorderShape(.circle)
    }

    @ViewBuilder private var rest: some View {
        if pages.path.hasPrefix("/wheel") {
            Toggle(isOn: Binding {
                volume.open
            } set: { open in
                if open {
                    openWindow(id: Volume.window)
                } else {
                    dismissWindow(id: Volume.window)
                }
            }) {
                Label("Chart in the Room", systemImage: "rotate.3d")
            }
            .help("Chart in the Room")
            Button {
                pages.press(Pages.enlarge)
            } label: {
                Label("Enlarge Chart", systemImage: "arrow.up.left.and.arrow.down.right")
            }
            .help("Enlarge Chart")
        }
        Button {
            pages.press("#chart-download")
        } label: {
            Label("Save Chart", systemImage: "square.and.arrow.down")
        }
        .help("Save Chart")
        Button {
            pages.copyLink()
            copied = true
            Task {
                try? await Task.sleep(for: .seconds(1.5))
                copied = false
            }
        } label: {
            Label("Copy Link", systemImage: copied ? "checkmark" : "link")
        }
        .help("Copy Link")
    }
}
