# Gates of Humanity on a Mac

This is Gates of Humanity as an app for a Mac, and it's on the Mac App Store. It's
the same App Store app as the one for iPhone and iPad, so whoever has one has
this one too. It shows the charts in a dedicated window, with a menu bar for what the pages do,
and it has widgets. It's for Apple silicon, and a few things are only checked by hand, see
[What isn't there yet](#what-isnt-there-yet).

It's built the way the iOS and the Android apps are. The site is compiled to a
native library with GraalVM, and a web view asks that library for every page
instead of a server. There's no Java in the app to run it with, only what
`native-image` made of the site beforehand. The library answers on a Mac byte
for byte what the same classes answer on the JVM.

* [Building and running](#building-and-running)
* [What's in this folder](#whats-in-this-folder)
* [The window](#the-window)
* [The menu bar](#the-menu-bar)
* [The welcome](#the-welcome)
* [The widgets](#the-widgets)
* [How it's signed](#how-its-signed)
* [What isn't there yet](#what-isnt-there-yet)

## Building and running

These are the commands you'll use most, from the top of the repository:

```console
mac/build.sh                 # builds the library and the app
mac/build.sh app             # the app alone, around the library that was built last
mac/check-icloud.sh          # checks that the app is signed for iCloud and that the profile in it agrees
./bld app-check mac          # compares what the library answers on a Mac with the JVM
open mac/build/Gates.app     # starts the app
```

### The library

`./bld app-library mac` builds the site into
`native/mac/gatesofhumanity.framework`, with the bld extension of RIFE2 for
native apps, see `AppBuild.java`. It takes a minute.

A Mac is what GraalVM builds for by itself, with the libraries of the JDK that
it ships, so there's nothing to prepare as there is for iOS and Android.

`./bld app-check mac` asks the library for the addresses that `AppBuild.java`
lists, and compares the answers with the JVM's.

### The app

`build.sh` builds `build/Gates.app` around the library with `swiftc`, as
`ios/build.sh` does for the simulator. The widgets are an extension inside it,
and it uses RIFE2's Swift package for native apps, which starts the site and
answers the web view with it. The first time, it fetches the sources of SwiftDraw into
`ios/build`, as the iOS build does, with `ios/swiftdraw.sh`.

### An app that checks can look into

With `GATES_PROBED` set, `build.sh` makes an app that a check can look into.
Started from a terminal, it does what these variables say, and then it ends:

| Variable | What the app does |
| --- | --- |
| `GATES_START` | opens another page than the Birth Chart |
| `GATES_EVAL` | runs a script in the page and prints what it returns |
| `GATES_SHOT` | writes a picture of what its window shows and prints where |
| `GATES_SIZE` | gives its window that size, as 900x1150 |
| `GATES_FILM` | writes such pictures one after the other while the script runs |
| `GATES_WELCOME` | doesn't keep its pages past their welcome |

To the Mac, it's another app than the one that someone uses,
`com.uwyn.gatesofhumanity.probed`. So what a check or a capture saves in it
leaves the people of that one alone. The first start after a build can take
most of a minute.

### Releases

With `GATES_RELEASED` set, `build.sh` signs the app with the Developer ID
certificate of the team, for other Macs than this one. `push_build.py` builds it
that way, has Apple check it, keeps Apple's answer inside it, and publishes it
with the apps for the other systems.

The target `Mac` of `ios/project.yml` is the same app as Xcode builds it, with
its widgets, for the Mac App Store, since only Xcode signs for the store.
`testflight.py mac` archives and uploads it.

`app/Info.plist` and `widgets/Extension.plist` are what that target adds to the
Info.plist that Xcode makes, and `app/Assets.xcassets` has the icon in the sizes
of a Mac.

## What's in this folder

`app/` has what a Mac does differently from iOS:

| File | What it is |
| --- | --- |
| `Gates.swift` | the app: one window around the pages |
| `Platform.swift` | what the pages get of a Mac in its own way: the size of a page, and copying a link |
| `Probe.swift` | what a check asks of an app that's built to be looked into |

The rest is the Swift of the iOS app as it is, from `ios/app`:

* `Site.swift`,
* `Pages.swift`, the web view,
* `People.swift`, `Saved.swift` and `Elsewhere.swift`,
* `Menus.swift`, the menu bar.

The widgets are the ones of `ios/widgets`.

## The window

A page scrolls inside the window, as in the iOS app. A page in an app marks
itself with the class `in-app`, and on a Mac with `on-mac`, which has the window
itself not give. That way what's pulled past its ends is the page, under a title
bar that stays.

A page has its own title bar. The one of the window goes on in its color above
it, without a title. That's where you move the window, and where its three
buttons are.

The window opens where you left it and as large, and a page as enlarged as you
left it. Two fingers enlarge a page, and a swipe goes back and forward, as in a
browser.

The app is done when its window is closed, and it has nothing to do with tabs.

A link out of the site opens in the browser. What a page saves goes where the
save dialog of a Mac says, and printing opens its print dialog.

## The menu bar

What the pages have buttons for is also in a menu, with a key for it. It's the
menu of the iOS app, `ios/app/Menus.swift`, with the size of the page and the
About page added.

| Menu | What it has |
| --- | --- |
| **File** | Load Person and Save Person, Command-O and Command-S, Save Chart, Copy Link and Print |
| **View** | Back, Forward and Reload, the size of the page with Command-plus, Command-minus and Command-0, and Enlarge Chart |
| **Chart** | the seven charts and the People page by their numbers, Command-1 to Command-8 |
| **Time** | steps through time, see below |
| **Help** | opens the Learn pages and the About page, and shows the welcome again |

In the Time menu, Command-T is now. An arrow steps a day with Shift and Command,
an hour with Option and Command, and a minute with all three. An arrow has two
keys with it, since a web view scrolls with an arrow that has fewer, without the
app seeing it.

A menu that's about a chart is off on a page that has none.

## The welcome

The pages welcome the first start as they welcome a first visit in a browser,
and say "Click" for how you choose a part of a chart.

## The widgets

These are the three widgets of the iOS app in the sizes of a desktop. The ones
of the Lock Screen aren't there, since `ios/widgets/Entries.swift` leaves them to
iOS.

They're an extension inside the app, with SwiftDraw as it's built for a Mac,
and they use the library of the app. An extension of a Mac is closed in once it
runs and may not open a file of its app anymore, so it's linked to the library,
which is loaded with it at its start.

A click on a widget opens its page in the app.

The app and its widgets share a folder for the people that the pages saved. A
Mac names such a folder after the team that signs the app, so both say which in
their Info.plist, as `AppGroup`, where `Saved.swift` reads it.

A Mac also lists the widgets of an iPhone that's signed in to the same account,
under "From iPhone". The ones of this app are under its own name.

## How it's signed

### Closed in, as the Mac App Store asks

The app is closed in, and follows the rules of the Mac App Store for what a
program may run. The library needs no leave for code that isn't signed, since
all of it was compiled beforehand.

A web view only starts in an app that's closed in when the app may make outgoing
connections. So the app asks for those, though it makes none.

Under those rules an app only loads a library that the same team signed, and
only the team's own apps share its folder. So `build.sh` signs the library, the
widgets and the app with the development certificate of the team that
`ios/project.yml` names.

What the app keeps is in its own folder under `~/Library/Containers`.

### iCloud

iCloud keeps the saved people the same on someone's devices. It only lets an app
in that says so in what it's signed with, and that carries a provisioning
profile of the team which agrees.

* The app of the Mac App Store gets its profile from Xcode.
* `build.sh` looks for one on this Mac with `profile.py`. A released app has to
  have the Developer ID one.
* An app for this Mac alone that finds none is signed without iCloud, since a
  Mac refuses to start an app that claims it without a profile. That app keeps
  its people on this Mac and says so on its pages, as the app for the checks
  always does: `Site.swift` asks what the app was signed with.

`check-icloud.sh` compares what an app is signed for with the profile in it.

## What isn't there yet

A check of the menus, of printing, of saving a file and of the widgets on a
desktop, which take someone at the Mac.
