import RIFE2NativeApp
import SwiftUI

@main
struct Gates: App {
    @StateObject private var pages = Pages()

    init() {
        Watch.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            NativePagesView(pages: pages).ignoresSafeArea(.container, edges: .bottom)
                .background(Color(uiColor: .edge).ignoresSafeArea())
                .task {
                    if let url = await Probe.tapped() {
                        pages.show(url)
                    }
                }
                // A widget opens the page of what it shows, and no other address gets in this way.
                .onOpenURL(perform: pages.show)
        }
        .commands {
            Menus(pages: pages)
        }
    }
}
