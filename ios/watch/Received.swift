import os
import WatchConnectivity
import WatchKit
import WidgetKit

let log = Logger(subsystem: "com.uwyn.gatesofhumanity", category: "watch")

// The watch's side of the iPhone: asks the app there for drawings when it has none or when the
// ones it has say it's time, and keeps what arrives for the screens of the app and for what a
// watch face shows. The iPhone needn't be near for any of it to wait: a question is kept until
// it's in reach, and so is what it sends.
final class Received: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = Received()

    @Published private(set) var drawings = Drawings.kept()
    // Whether the iPhone was asked and didn't answer yet: one question is enough.
    private var asking = false
    // The moments that the watch gave the app to take what the iPhone sent.
    private var waiting: [WKWatchConnectivityRefreshBackgroundTask] = []

    func start() {
        #if targetEnvironment(simulator)
        // What check-watch.sh hands over as if the iPhone had sent it: the simulators of an
        // iPhone and a watch take minutes to pass something on, when they do.
        if let file = ProcessInfo.processInfo.environment["GATES_DRAWINGS"] {
            took(try? Data(contentsOf: URL(fileURLWithPath: file)))
            return
        }
        #endif
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    // The app is on the screen, or the watch gave it the moment that it asked for: asks the
    // iPhone when it's time, and tells when that's done.
    func active(_ done: @escaping () -> Void = {}) {
        guard WCSession.default.delegate != nil, drawings.map({ $0.reload <= .now }) ?? true else {
            again()
            done()
            return
        }
        ask(done)
    }

    private func ask(_ done: @escaping () -> Void = {}) {
        let session = WCSession.default
        // it asks once it is
        guard session.activationState == .activated, !asking else {
            done()
            return
        }
        asking = true
        let asked = {
            DispatchQueue.main.async {
                self.asking = false
                self.again()
                done()
            }
        }
        // kept for when the iPhone is in reach
        let later = {
            if session.outstandingUserInfoTransfers.isEmpty {
                session.transferUserInfo(["ask": Drawings.sent])
            }
            asked()
        }
        if session.isReachable {
            session.sendMessage(["ask": Drawings.sent], replyHandler: { answer in
                // the drawings themselves, or nothing when they come another way
                if let packed = answer[Drawings.sent] as? Data {
                    DispatchQueue.main.async {
                        self.took(packed)
                    }
                }
                asked()
            }, errorHandler: { _ in later() })
        } else {
            later()
        }
    }

    // Has the watch give the app a moment when the drawings say to ask again, or in a while
    // when that already was.
    private func again() {
        let next = max(drawings?.reload ?? .now, Date.now.addingTimeInterval(1800))
        WKApplication.shared().scheduleBackgroundRefresh(withPreferredDate: next, userInfo: nil) { _ in }
    }

    func wait(for task: WKWatchConnectivityRefreshBackgroundTask) {
        DispatchQueue.main.async {
            self.waiting.append(task)
            self.settled()
        }
    }

    // The moments that the watch gave end when the iPhone has nothing more on its way.
    private func settled() {
        if WCSession.default.delegate != nil, WCSession.default.activationState == .activated, !WCSession.default.hasContentPending {
            waiting.forEach { $0.setTaskCompletedWithSnapshot(false) }
            waiting = []
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith state: WCSessionActivationState, error: Error?) {
        // what the iPhone sent while the app wasn't there to hear of it
        let packed = session.receivedApplicationContext[Drawings.sent] as? Data
        DispatchQueue.main.async {
            if state == .activated {
                if let packed, let sent = try? Drawings(packed: packed), sent.reload > self.drawings?.reload ?? .distantPast {
                    self.took(packed)
                }
                self.active()
            }
            self.settled()
        }
    }

    func sessionReachabilityDidChange(_ session: WCSession) {
        if session.isReachable {
            DispatchQueue.main.async {
                self.active()
            }
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext context: [String: Any]) {
        let packed = context[Drawings.sent] as? Data
        DispatchQueue.main.async {
            self.took(packed)
        }
    }

    func session(_ session: WCSession, didReceive file: WCSessionFile) {
        // the file is only there until this returns
        let packed = try? Data(contentsOf: file.fileURL)
        DispatchQueue.main.async {
            self.took(packed)
        }
    }

    // Keeps what the iPhone sent, for the screens and for the watch face.
    private func took(_ packed: Data?) {
        if let kept = packed.flatMap(Drawings.keep) {
            drawings = kept
            WidgetCenter.shared.reloadAllTimelines()
            again()
            log.info("\(kept.screens.count) screens kept")
            #if targetEnvironment(simulator)
            // for ios/check-watch.sh
            let opened = kept.screens.first?.details.flatMap { Drawings.current(of: $0, at: .now)?.told }
            print("kept \(kept.screens.map { "\($0.title) \($0.entries.count)+\($0.details?.count ?? 0)" }.joined(separator: ", ")); \(kept.complications.keys.sorted().joined(separator: " ")); "
                  + "\(opened?.rows.count ?? 0) rows, \(opened?.rows.first.map { "\($0.label ?? "") \(kept.explained($0.about).map(\.title).joined(separator: ", ")): \(kept.explained($0.about).first?.texts.first ?? "")" } ?? ""); "
                  + "\(kept.screens.compactMap { Drawings.current(of: $0.entries, at: .now) }.map { Drawn(svg: $0.svg).words }.joined(separator: " | "))")
            #endif
        } else {
            log.error("The iPhone sent something that isn't drawings")
        }
        settled()
    }

    func session(_ session: WCSession, didFinish asked: WCSessionUserInfoTransfer, error: Error?) {
    }
}
