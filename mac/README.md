# Gates of Humanity on a Mac

This is Gates of Humanity as an app for a Mac, built the way the iOS and the Android apps are: the site is
compiled to a native library with GraalVM, and a web view asks that library for every page
instead of a server. There's no Java in the app to run it with, only what `native-image` made
of the site beforehand.

The app shows the charts in a window of its own, with a menu bar for what the pages do,
and widgets, signed and closed in as the Mac App Store asks. The library answers on a
Mac byte for byte what the same classes answer on the JVM. It's for Apple silicon, and it
isn't all there yet, see the end.

## What's there

* `./bld app-library mac` builds the site into `native/mac/gatesofhumanity.framework`, with the bld
  extension of RIFE2 for native apps, see `AppBuild.java`. A Mac is what GraalVM builds for by
  itself, with the libraries of the JDK that it ships, so there's nothing to prepare as for
  iOS and Android, and it takes a minute.
* `build.sh` builds `build/Gates.app` around it with `swiftc`, as `ios/build.sh` does for the
  simulator, with the widgets as an extension inside it, and with RIFE2's Swift package for
  native apps, which starts the site and answers the web view with it.
* `./bld app-check mac` asks the library for the addresses that `AppBuild.java` lists and
  compares the answers with the JVM's.
* `app/` is what a Mac does otherwise than iOS:

  | | |
  |---|---|
  | `Gates.swift` | the app: one window around the pages |
  | `Platform.swift` | what the pages get of a Mac in its own way: the size of a page, and copying a link |
  | `Probe.swift` | what a check asks of an app that's built to be looked into |

  The rest is the Swift of the iOS app as it is: `Site.swift`, `Pages.swift`, the web view,
  `People.swift`, `Saved.swift`, `Elsewhere.swift` and `Menus.swift`, the menu bar, of
  `ios/app`, and the widgets of `ios/widgets`.

```console
mac/build.sh                 # builds the library and the app
mac/build.sh app             # the app alone, around the library that was built last
mac/check-icloud.sh          # checks that the app is signed for iCloud and that the profile in it agrees
./bld app-check mac          # compares what the library answers on a Mac with the JVM
open mac/build/Gates.app
```

With `GATES_PROBED` set, `build.sh` makes an app that a check can look into: started from a
terminal with `GATES_START` it opens another page than the Birth Chart, with `GATES_EVAL` it
runs a script in the page and prints what it returns, with `GATES_SHOT` it writes a picture of
what its window shows and prints where, with `GATES_SIZE` its window has that size for it, as
900x1150, with `GATES_FILM` it writes such pictures one after the other while the script
runs, with `GATES_WELCOME` its pages aren't kept past their welcome, and then it ends. It's
another app to the Mac than the one that someone uses, `com.uwyn.gatesofhumanity.probed`, so
what a check or a capture saves in it leaves the people of that one alone. The first start
after a build can take most of a minute.

With `GATES_RELEASED` set, `build.sh` signs the app with the Developer ID certificate of the
team, for other Macs than this one. `push_build.py` builds it so, has Apple check it,
keeps Apple's answer inside it and publishes it with the apps for the other systems.

The target `Mac` of `ios/project.yml` is the same app as Xcode builds it, with its widgets,
for the Mac App Store: only Xcode signs for the store. `testflight.py mac` archives and
uploads it. `app/Info.plist` and `widgets/Extension.plist` are what that target adds to the
Info.plist that Xcode makes, and `app/Assets.xcassets` has the icon in the sizes of a Mac.

## The window

* A page scrolls inside the window, as in the iOS app: a page in an app marks itself with the
  class `in-app`, and on a Mac with `on-mac`, which has the window itself not give, so that
  what's pulled past its ends is the page under a title bar that stays.
* A page has a title bar of its own. The one of the window goes on in its color above it,
  without a title: it's where the window is moved by and where its three buttons are.
* The window is where it was left and as large, and a page as enlarged as it was left. Two
  fingers enlarge a page and a swipe goes back and forward, as in a browser.
* The app is done when its window is closed, and it has nothing to do with tabs.
* A link out of the site opens in the browser. What a page saves goes where the save dialog
  of a Mac says, and printing opens its print dialog.

## The menu bar

What the pages have buttons for is also in a menu, with a key for it. It's the one of the iOS
app, `ios/app/Menus.swift`, with the size of the page and the About page besides.

* **File** has Load Person and Save Person, Command-O and Command-S, Save Chart, Copy Link and
  Print.
* **View** has Back, Forward and Reload, the size of the page with Command-plus,
  Command-minus and Command-0, and Enlarge Chart.
* **Chart** has the seven charts and the People page by their numbers, Command-1 to
  Command-8.
* **Time** steps through time: Command-T is now, and an arrow steps a day with Shift and
  Command, an hour with Option and Command, and a minute with all three. An arrow has two
  keys with it since a web view scrolls with one that has less, without the app seeing it.
* **Help** opens the Learn pages and the About page, and shows the welcome again.

A menu that's about a chart is off on a page that has none.

## The welcome

The pages welcome the first start as they welcome a first visit in a browser, and say "Click"
for how a part of a chart is chosen.

## The widgets

The three widgets of the iOS app in the sizes of a desktop, without the ones of the Lock
Screen, which `ios/widgets/Entries.swift` leaves to iOS. They're an extension inside the app,
with SwiftDraw as it's built for a Mac, and they use the library of the app. An extension of
a Mac is closed in once it runs and may not open a file of its app anymore, so it's linked to
the library, which is loaded with it at its start. A click on a widget opens its page in the
app.

The app and its widgets share a folder for the people that the pages saved. A Mac names such
a folder after the team that signs the app, so both say which in their Info.plist, as
`AppGroup`, where `Saved.swift` reads it.

A Mac also lists the widgets of an iPhone that's signed in to the same account, under "From
iPhone". The ones of this app are under its own name.

## How it's signed

* The app is closed in and follows the rules of the Mac App Store for what a program may run.
  The library needs no leave for code that isn't signed, since all of it was compiled
  beforehand.
* A web view only starts in an app that's closed in when the app may make outgoing
  connections, so the app asks for those, though it makes none.
* Under those rules an app only loads a library that the same team signed, and only the
  team's own apps share its folder. So `build.sh` signs the library, the widgets and the app
  with the development certificate of the team that `ios/project.yml` names.
* What the app keeps is in its own folder under `~/Library/Containers`.
* iCloud, which keeps the saved people the same on someone's devices, only lets an app in
  that says so in what it's signed with and that carries a provisioning profile of the team
  which agrees. The app of the Mac App Store gets one from Xcode. `build.sh` looks for one
  on this Mac with `profile.py`: a released app has to have the Developer ID one, and an app
  for this Mac alone that finds none is signed without iCloud, since a Mac refuses to start
  an app that claims it without a profile. That app keeps its people on this Mac and says
  so on its pages, as the app for the checks always does: `Site.swift` asks what the app was
  signed with. `check-icloud.sh` compares what an app is signed for with the profile in it.

## What isn't there yet

* A check of the menus, of printing, of saving a file and of the widgets on a desktop, which
  take someone at the Mac.
* What the Mac App Store asks beyond the signing: a distribution certificate and profile, the
  Mac added to the app's record, which lets someone who has the iOS app have this one too, and
  screenshots.
