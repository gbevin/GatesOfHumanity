# Gates of Humanity on Android

This is Gates of Humanity as an Android app, built the way the iOS app is: the site is compiled to a native
library with GraalVM, and a web view asks that library for every page instead of a server.
The library answers on Android byte for byte what the same classes answer on the JVM, and the
app has what the iOS app has around its pages, its widgets included, but for what's listed
at the end.

It's built on an Apple silicon Mac, for arm64 devices with Android 10 or later.

## What's there

* `./bld app-library android` builds the site into `native/android/libgatesofhumanity.so`, with the bld
  extension of RIFE2 for native apps, see `AppBuild.java`.
* `./bld app-android` builds the app around it, `build/Gates.apk`, and installs and starts
  it, as `AppBuild.java` describes the app. `build.sh` does both, after it fetched AndroidSVG.
* `./bld app-check android` asks the library on an Android for the addresses that
  `AppBuild.java` lists and compares the answers with the JVM's.
* `check-app.sh` checks what the app does around its pages, see below, by looking into the
  pages of an app that it builds for that.
* `app/` is the app, around the classes of RIFE2 that start the site and show its pages in a
  web view, `rife.nativeapp.android`:

  | | |
  |---|---|
  | `App.java` | says which library the site is, where its pages are, and what the pages say of the widgets |
  | `Pages.java` | the web view of RIFE2 with what's this app's own: who the pages saved, and a tap on a widget |
  | `MainActivity.java` | the app itself: the pages, and the way in for a tap on a widget |
  | `Widgets.java`, `Drawn.java` | the widgets, and the drawing of the site that one shows |
  | `People.java`, `PersonChoice.java` | who the pages saved, for a widget to offer and to show |
  | `Probe.java` | what `check-app.sh` asks of an app that's built to be looked into |

* `icon.py` draws the icon of the app from the drawing of `ios/icon.py`, and `previews.py` the pictures of the widgets where one is chosen, from the
  drawings that the site makes of them. The icon is Android's kind, two layers that it cuts to
  the shape of its choosing: the sky behind and the emblem in front, small enough to stay
  inside any shape.

```console
./bld app-jdk-libs android   # once, with an emulator or a device connected
android/build.sh             # builds, installs and starts the app
android/build.sh app         # the app alone, around the library that was built last
./bld app-check android      # compares what the library answers on Android with the JVM
android/check-app.sh         # checks what the app does around its pages and what its widgets draw
```

## What it needs

* What the iOS build needs, see `ios/README.md`, and that `./bld app-jdk-libs ios-sim` ran
  once: it fetches the sources of the JDK that `./bld app-jdk-libs android` builds from. The
  GraalVM is Community Edition in the version that RIFE2's extension names: the container has
  that same one.
* The Android SDK with its command line tools, an NDK, a platform, build tools and an emulator
  with an arm64 system image, and the NDK, in the versions that RIFE2's extension builds
  with. With Homebrew's
  `android-commandlinetools`:

  ```console
  sdkmanager --sdk_root=$HOME/Library/Android/sdk "cmdline-tools;latest" "ndk;28.2.13676358" \
      "platforms;android-35" "build-tools;35.0.0" "platform-tools" "emulator" \
      "system-images;android-35;google_apis;arm64-v8a"
  $HOME/Library/Android/sdk/cmdline-tools/latest/bin/avdmanager create avd -n gatesofhumanity \
      -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_8
  $HOME/Library/Android/sdk/emulator/emulator -avd gatesofhumanity -no-window -no-audio
  ```

  The oldest Android that the app is for is worth an emulator of its own, with
  `system-images;android-29;google_apis;arm64-v8a`: the checks pass there as on the newest.
* Docker, for the one step that can't run on a Mac.

## How the library is built

RIFE2's extension builds it, and its README says how: the libraries of the JDK for Android,
which GraalVM doesn't ship, the sizes of Android's C types, which an emulator or a device
answers, and `native-image` in a Linux container, since it compiles the classes of the JDK
that it runs on and Android has those of Linux. On Apple silicon the container runs at full
speed, and the step takes about a minute.

The app itself is put together by RIFE2's extension too, with the tools of the SDK and
without Gradle: it compiles RIFE2's Android classes with the app's own and takes the library
of Android that they need from Google's repository once.

## How the app asks the site

A web view of Android lets an app answer its requests itself, which RIFE2's classes do for
one address, `https://app.gatesofhumanity.invalid`. It's an address of the web rather than
one of its own kind as on iOS, since a web view of Android only keeps what a page stores, the
saved people here, for an address of the web. Nothing is ever asked of that address: its
ending is one that no site can have, and the app asks for no access to the network, so
nothing but the site inside it loads in the web view at all. `App.java` names the address
before anything asks the site, a widget included.

The library is loaded and its Java started with the first request, and a page says why when
that fails instead of the app stopping. The site answers on the thread that asks, so several
requests are answered at a time. A web view of Android doesn't say when it stops waiting for
an answer, so a request runs to its end.

## What the app adds around the pages

It's what the iOS app adds, with what Android has for each.

The pages know which app they're in, since the app says so to the site. RIFE2's script, which
the head of a page includes in an app, marks the page with the class `in-app` that `main.css`
has rules for, and gives it what a web view of Android lacks: `navigator.share`,
`window.print` and saving a file, which a page does with a link that names the file. Each of
them asks the app, through an object that only the pages of the site get. `js/app.js` of the
site tells the app who the pages saved through that script too, which `Pages.java` listens
for.

* The first time, the pages welcome as they welcome a first visit in a browser, and say "Tap"
  for how a part of a chart is chosen.
* Sharing a chart opens Android's share sheet with the link.
* Printing opens Android's print dialog with the chart on one page.
* What a page saves, the image or the drawing of a chart or the saved people, can be kept as a
  file where the person chooses, through Android's own file picker, or shared with another
  app. It's a file of the app first, which a provider of RIFE2 hands to that other app, since
  no app can read a file of another.
* A page that asks for a file, to bring the saved people in, gets Android's file picker.
* A link out of the site opens in the browser.
* The About page says which version and build of the app this is, the ones of the iOS app,
  which `AppBuild.java` reads from `ios/project.yml`.
* When Android stops what draws the pages, to have its memory or because it failed, a web
  view is of no use anymore and an app that keeps it is stopped too. The app puts another web
  view in its place, at the page that was shown, though without the pages before it to go
  back to.
* What the app keeps isn't copied to a backup elsewhere: the people that someone saved stay
  in the app, as its welcome says.

Android has no tap that takes a page back to its top, as iOS has on the status bar. A tap on
the title of a page does that, which is the site's own.

A page that's pulled past its top or its bottom stretches, as Android has it. It stretches all
of a web view, so pulled at the bottom the title bar stretches along: only a title bar that
the app draws itself would stay.

## The widgets

The three widgets of the iOS app, which the site draws in SVG: Transits Now, Body Graph Now
and Transits of a Person. `Widgets.java` asks the site for the moments at which a widget
changes and shows a picture of the drawing of this moment, which `Drawn.java` makes with
AndroidSVG, a library that `build.sh` takes from Maven Central once, as the iOS app has
SwiftDraw. A drawing says the same three things as on iOS: the address that a tap opens, what's
behind it, which is stretched over the widget while the rest keeps its shape, and the moments
that the device writes in its own language and time zone.

* A widget is a square, twice as wide, or large, as the site draws it, whichever is closest to
  the room that someone gave it. A launcher says how much room that is when the screen is
  upright and when it's on its side, and the widget has a picture for each.
* iOS is given a drawing for every moment ahead and changes the widget by itself. Android has
  nothing like that, so the widget asks to be woken at the moment that it changes. Android
  does that within ten minutes, the least that it takes without a permission that's for
  alarm clocks, and later while the phone sleeps. It also draws each widget again every half
  hour, and when the time zone, the language or the clock of the device changes.
* A widget of a person shows who was chosen for it, in what Android shows to set a widget up,
  `PersonChoice.java`, or else the person whose chart the app showed last. Android 12 and
  later place the widget first and let it be changed afterwards, before that it asks when the
  widget is placed. The pages keep who was saved in the web view, which a widget can't read,
  so the pages tell the app when that changes, each name with the moment of the birth, and
  `People.java` keeps them.
* A tap opens the page of what the widget shows. Any app can start another one with an
  address, so a widget also hands over what only this app knows, and the app opens nothing
  for a tap without it or with an address that isn't of the site.
* Someone who has the screen read out hears what the drawing says.

## Checking the app

The app is compiled against the newest Android, which accepts what an older one doesn't
have, and the app then stops on an older phone the first time that it gets there. Gradle has
lint for that. Here `./bld app-android` looks up everything that the classes of the app and
of RIFE2 refer to in the list that the SDK has of when each class, method and field came, and
the build fails on what came after Android 10.

`check-app.sh` builds the app so that a program on this Mac can look into its pages, over the
protocol of Chrome's developer tools, and with a `Probe.java` that gives the picture of a
widget. An app that's built the usual way has neither. It checks the mark on the pages, the
version on the About page, the people kept for the widgets, the picture of every widget in
every size, a tap on a widget while the app runs and while it doesn't, a tap without what a
widget hands over, an address that isn't of the site, and that the app goes on at its page
after what draws it is stopped. The pictures are left in `build/checks`. It removes what the
app keeps first, so it only runs in an emulator.

Placing a widget on a home screen, choosing its person and tapping it take a hand: no check
does those.

## Installing it on a phone

`./bld app-android` signs `build/Gates.apk` with a key that it makes once and keeps outside the
project, `~/.android/gatesofhumanity.jks`, with its password in the file beside it. Android
only installs a newer build over an older one when the same key signed both, so that key is
worth a backup: without it the app has to be removed from a phone first, with the people
saved in it.

The file is all that a phone needs. Sent to it in any way, a message, a mail, a shared
folder, it installs when it's opened there, once Android was told that the app that opened it
may install apps, which it asks by itself. `push_build.py` publishes it with the apps for
the other systems, for a phone to download. With a cable and USB debugging turned on,
`adb install -r android/build/Gates.apk` does the same.

## What isn't there yet

* The saved people on the other devices of whoever uses the app, which the apps of Apple
  keep in iCloud.
* A release for the Play Store: its own format and requirements, and other processors than
  arm64.
