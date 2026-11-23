import Foundation
import os
import RIFE2NativeApp
import Security

let log = Logger(subsystem: "com.uwyn.gatesofhumanity", category: "site")

struct Refusal: Error {
    let why: String

    init(_ why: String) {
        self.why = why
    }

    init(_ error: Error) {
        why = (error as? Refusal)?.why ?? String(describing: error)
    }
}

// The site of this app: the one that RIFE2 runs inside it, natively compiled and without a
// server, see NativeSite, told what's this app's own before it starts.
enum Site {
    static let shared: NativeSite = {
        // Where the web view has the site, which is also where the pages keep what they store:
        // at another address they'd start without the people that someone saved.
        NativeSite.scheme = "hdapp"
        NativeSite.host = "app"
        // The pages say where the people that someone saved stay. A widget shows no page.
        if !NativeSite.isExtension && inICloud {
            NativeSite.properties["gatesofhumanity.keptIn"] = "this app and your own iCloud"
        }
        return NativeSite.shared
    }()

    // iCloud only lets an app in that's signed for it. An iPhone doesn't install one that
    // isn't. A Mac runs an app that was built for it alone without that, which then keeps its
    // people on that Mac and says so.
    static let inICloud: Bool = {
        #if os(macOS)
        guard let app = SecTaskCreateFromSelf(nil) else {
            return false
        }
        return SecTaskCopyValueForEntitlement(app, "com.apple.developer.ubiquity-kvstore-identifier" as CFString, nil) != nil
        #else
        return true
        #endif
    }()

    static var root: String {
        shared.root
    }
}
