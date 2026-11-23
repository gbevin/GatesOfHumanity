# Gates of Humanity on iOS

This is Gates of Humanity as an app for iPhone and iPad. It shows the same pages as the site,
with the site's Java code and RIFE2 inside the app and no web server, in the simulator and on a
device.

The app is called Gates of Humanity, with "Gates" below its icon, and its bundle identifier is
`com.uwyn.gatesofhumanity`.

* [Building and running](#building-and-running)
* [How it's made](#how-its-made)
* [The pages in the app](#the-pages-in-the-app)
* [The keys of a keyboard](#the-keys-of-a-keyboard)
* [Scrolling](#scrolling)
* [The saved people on someone's devices](#the-saved-people-on-someones-devices)
* [Widgets](#widgets)
* [The watch](#the-watch)
* [Checks](#checks)
* [How the library is built for iOS](#how-the-library-is-built-for-ios)
* [Releasing](#releasing)
* [Screenshots and previews](#screenshots-and-previews)
* [The icon](#the-icon)
* [What it shows](#what-it-shows)
* [What it doesn't show yet](#what-it-doesnt-show-yet)

## Building and running

Once, before the first build, you build the libraries that the app links with. A simulator
build can also do without them, see [Without the built libraries](#without-the-built-libraries):

```console
./bld app-jdk-libs ios-sim    # once: the JDK's and GraalVM's libraries for the simulator, from their sources
./bld app-jdk-libs ios        # once: the same for devices
```

Then you build the app and run it in a simulator:

```console
ios/build.sh                  # builds, installs and launches on the iPhone 17 simulator
ios/build.sh "iPad Pro 11-inch (M5)"   # the same on the simulator of this name
ios/build-watch.sh            # builds the watch app and launches it on the watch of the iPhone 17 simulator
```

To install it on your own devices:

```console
ios/install.sh                # builds the app as it's released and installs it on the iPhone 17 when it's in reach
ios/install.sh "iPad Pro" "Apple Vision Pro"   # on the devices of these names, the app for visionOS on the last
```

Each check is described with what it checks, the launch settings and the library in
[Checks](#checks), and releasing in [Releasing](#releasing):

```console
./bld app-check ios-sim       # compares what the library answers in the simulator with the JVM
ios/check-burst.sh            # checks that the app keeps up with 150 requests asked at once
ios/check-feel.sh             # checks with a finger how a page gives, glides and goes back to its top
ios/check-keys.sh             # checks that the keys of a connected keyboard do what the pages have buttons for
ios/check-swift.sh            # checks on this Mac what the Swift works out by itself
ios/check-sync.sh             # checks that the saved people go from the pages to iCloud and back
ios/check-watch.sh            # checks what the app makes for the watch app, and what the watch app does with it
push_build.py                 # publishes a build for every system to uwyn.com/deploy/hd: iOS, visionOS, Mac and Android
push_build.py --local         # builds and exports them without uploading
testflight.py                 # uploads the build last published to App Store Connect: iOS, visionOS and Mac
testflight.py --export        # signs them for the store without uploading
testflight.py --status        # lists the builds that App Store Connect holds and their state
```

### What the build uses

* `./bld app-library` builds the library for the simulator or for devices, and for visionOS
  too, see [vision/README.md](../vision/README.md). It goes into `native` at the top of the
  project, which a clean build leaves alone.
* `project.yml` is the Xcode project of the device build, which xcodegen generates. Every
  publish raises its build number.
* The Swift package of RIFE2 comes from a checkout of `rife2-native-app` beside the folder of
  this project, in `../rife2/rife2-native-app`, or where `RIFE2_NATIVE_APP` says for the
  scripts here.
* The scripts ask `./bld app-simulator` for the simulator of a name.

When the library's Java doesn't start, a page says why, instead of the app not launching.

## How it's made

### The site inside the app

RIFE2 has the site answer a request of the app with its `SiteRunner`, which runs a site in the
process that asks, so there's no Jetty, no socket and no port. It hands the response over while
it's written: the status and headers, the content, and one last call when it completed, failed
or was cancelled. `AppSiteProvider.java` of the site says which site that is, and what its
pages say in an app.

### The library

`./bld app-library ios-sim` and `ios` compile the site and RIFE2 into a native library for iOS,
with GraalVM's `native-image` and the bld extension of RIFE2 for native apps, see
`AppBuild.java`. The precompiled templates and the scripts, styles and images of the site are
resources inside it.

### The Swift app

`app/` is a SwiftUI app with one web view, a file for each thing it does, around RIFE2's Swift
package for native apps. That package starts the library's Java once, has it answer every
request of the web view for `hdapp://app` on the thread that asks, several at a time, and tells
it when the web view stopped waiting for one. The package also does what a web view doesn't do
by itself: links out of the site, files that a page saves, printing.

| File | What it does |
| --- | --- |
| `Gates.swift` | The app, with the pages in its window, which starts the link with the watch. |
| `Site.swift` | Says which site that is for the app and its widgets, which are linked to the library. |
| `Pages.swift` | The web view of that package with what's this app's own: the people that the pages saved, the buttons of a page that the app presses, a tap on a widget. It's the same for every system of Apple. |
| `Platform.swift` | What iOS does in its own way. |
| `People.swift`, `Saved.swift` | Keep who the pages saved, for the widgets. |
| `Elsewhere.swift` | The store of iCloud that the pages keep those people the same on someone's devices with. |
| `Menus.swift` | A key of a keyboard for what the pages have buttons for. |
| `Watch.swift` | The watch as the iPhone app sees it, see [The watch](#the-watch). |
| `Probe.swift` | What the checks and the store scripts of this folder ask of the app from outside, through its environment. |

The pages know that they're in an app, through the script that RIFE2 includes in their head,
and ask it for what's this app's own in `js/app.js` of the site.

Only a simulator build has any of `app/Probe.swift`. Only there, or in a debug build, can Safari
on a Mac look into the pages. What the app notes of its requests goes to the log of iOS, which
Console shows, with the addresses kept private.

### The widgets and the watch

* `widgets/` is the extension with the widgets of the app, which asks the same library what
  they show, see [Widgets](#widgets).
* `watch/` is the watch app and what it has on a watch face, which show drawings that the app
  on the iPhone sends, see [The watch](#the-watch).

### Cookies and redirects

A web view keeps no cookies for an address like `hdapp://app`, so the site keeps them itself,
with RIFE2's cookie jar and a file in the app's Application Support directory. The city that
the pages start at is remembered in one.

A web view can't be answered with a redirect either. For a page, the app answers with a page of
one line that sends the web view on, so that its address is the one of what it shows. For
anything else it follows the redirect itself.

## The pages in the app

### What the pages say

`AppSiteProvider.java` sets the properties of the site that change what the pages say in the
app. These are the ones that change most on iOS:

* `gatesofhumanity.keptIn`: the pages say that the saved people stay in "this app" where the
  site says "this browser". The app says "this app and your own iCloud" there when it's signed
  for iCloud, see [The saved people on someone's devices](#the-saved-people-on-someones-devices).
* `gatesofhumanity.columnWidth`: "none", so that the pages are as wide as the screen of an iPad
  where the site has a column between two edges.
* `gatesofhumanity.credit`: the credits of the About page name GraalVM and OpenJDK in the app,
  with the license of the GraalVM that built the library, Oracle's or the Community Edition's.
* `gatesofhumanity.shareRootUrl`: the address of the chart on the web, which the share button
  shares. The drawings have that address below the chart as well, rather than the one inside
  the app.

It also sets `gatesofhumanity.app`, `gatesofhumanity.tap` ("Tap" on iOS) and
`gatesofhumanity.widgetsCredit`, which credits SwiftDraw. The main README lists them all.

Below the credits, the About page says which version and build the app is. RIFE2's package
tells the library what the bundle of the app says, before the first request.

### The welcome

The first time the app starts, the pages welcome as they welcome a first visit in a browser:
the app has no separate welcome. It says where the app keeps the saved people, and "Tap" for
how a part of a chart is chosen.

### Saving, printing and sharing

* What a page saves, a chart as an image or a drawing and the saved people as a file, goes to
  the sheet of iOS that saves and sends files. The web view downloads what the page made into a
  temporary file first.
* Print in the pages opens the print dialog of iOS.
* The share button shares the address of the chart on the web.
* Importing people opens the file browser of iOS, which a web view does by itself.

### The color behind the status bar

The app has the color of the top of the title bar and of the bottom of the footer behind the
status bar, `--edge-color` in `main.css` and `UIColor.edge` in the app, so the bar goes on
rather than ends. A browser that lets a page be pulled past its top or its bottom shows that
same color there.

### Zooming

A page keeps its size under two fingers and a double tap: a page in an app of Apple says so in
its viewport, the site leaves enlarging to the browser. The full screen view of a chart zooms
by itself and still does.

## The keys of a keyboard

With a keyboard connected to an iPhone or an iPad, what the pages have buttons for has a key.
`Menus.swift` is the menu bar of an iPad and of the Mac app, and its keys work on an iPhone too.

* Command with 1 to 7 opens a chart, with 8 the People page, and with a question mark the
  Learn pages.
* Command-T shows now, and an arrow steps through time: a day with Shift and Command, an hour
  with Option and Command, a minute with all three.
* Command-O and Command-S load and save a person, Shift-Command-S saves the chart,
  Shift-Command-C copies its link, and Command-P prints.
* Command with a bracket goes back and forward, Command-R loads the page again, and Command-E
  enlarges the chart.

A page that was touched has the keys first, and its web view scrolls with an arrow that has
one key with it, Command alone too, without the app seeing it. So the steps have two.
`check-keys.sh` presses keys in a simulator after touching the page, with a UI test.

## Scrolling

In the app a page gives when it's pulled past its top or its bottom and comes back, as a screen
of an app does, while the title bar stays where it is.

### How the page scrolls

A web view that's pulled takes the title bar along, as a web page before it reloads, so the web
view itself doesn't give. A page in an app marks itself instead, with the class `in-app` on its
`html`. When it's marked, `main.css` has the page scroll inside the window: the `body` is what
scrolls, which iOS gives its own pull.

Three things follow from that, and the app sees to each:

* The window doesn't go on below its safe area by itself anymore, so the viewport of a page
  covers the screen, and the footer and the card of a tapped part leave room for the bar at the
  bottom of a phone.
* A tap on the status bar does nothing by itself, since iOS only asks a view that isn't at its
  top already and the window always is. A view of one point behind the page stays scrolled
  down to be asked in its place, and the app has the page go to its top. A tap on the title of
  a page does the same, which is the site's own and so also goes for a browser and for Android.
* Past its ends a page shows the color that the controls begin with and the one that the footer
  ends with, halves of the background of the `body`.

A marked page doesn't ask what to do with a link that's pressed and held either.

### How it feels

What a page does under a finger is iOS's own doing then, not something the pages work out: the
`body` is a scroll view of UIKit inside the web view. `check-feel.sh` measures it, since it's
the feel of it that matters and nothing shows that but a finger. A UI test, `feel/Feel.swift`,
drags over the app in the simulator while a script in the page keeps where the page was each
frame.

* A finger that moves 260 points past the top takes the page 117 along, which is the curve that
  UIKit has for it, and the page is back in six tenths of a second, the same at the bottom.
* A flick that lets go at 1400 points a second glides 670 points on, the half second of its
  speed that a scroll view of an app has, where a web page once had a tenth.
* The title bar doesn't move in any of it.
* A page that fits on the screen gives too, as a screen of an app with nothing to scroll does.
  iOS only pulls what has something to scroll, so the content of a marked page is a point
  higher than the window.

The check fails without the mark and without that point.

## The saved people on someone's devices

The people that someone saves are the same on their iPhone, their iPad, their Apple Vision Pro
and their Mac, through the key-value store of iCloud. It needs no server and no account of the
app's, it's the person's own, and the app can't be looked into from outside it. iOS has a
switch for it per app, in the settings of iCloud.

### Who does what

The pages keep the people, in the web view, and they're also what works out who goes where.
`appSynced` in `js/app.js` of the site compares who the pages have with who iCloud has and with
who both had when they were last the same, which the pages keep too.

The app is only the store. `Elsewhere.swift` keeps and removes in iCloud who the pages say, and
`People.swift` tells the pages who's there, when a page asks and when iCloud says that it
changed. What the pages have to save or to remove goes to `syncPeople` in `main.js`, which does
it as if it was done on the page, until both sides are the same. So an app on another system
only needs its own store for the same rules.

### The rules

* iCloud has a thing for each person, under a key made of their name, so that two devices that
  each save someone while they're apart both keep theirs.
* Someone who's only on one side is saved on the other. Someone who's another on each side is
  the one of this device when only this device changed them, and the one of iCloud otherwise.
* Someone is only removed on the other side when they were seen to be removed: by the pages,
  one at a time as they do it, or by iCloud telling that it changed. Missing alone removes
  nobody. A device that lost what its pages keep, or that was set up again from a backup
  before iCloud reached it, gets its people back rather than taking them from the other
  devices, and an account that's another one adds its people to the ones here.
* Someone who was removed on another device is only seen to be when the pages hear that
  iCloud changed. When no page is there to hear it, the app tells the next one that asks.
  The pages are also told who's there each time the app comes to the front.
* iCloud keeps a thousand and twenty-four things for an app. The app sends a thousand people
  at most, the others stay on their device.

### Checking it

The tests of the site check all of that, with devices and what stands in for iCloud between
them, in `MainTest`. `check-sync.sh` checks the way from the pages to the app and back in a
simulator, which has no account of iCloud: `SIMCTL_CHILD_GATES_ICLOUD`, with people written as
the pages keep them, stands in for it there. Two real devices are the only check of iCloud
itself.

The app needs iCloud with its key-value storage turned on for its identifier, at
developer.apple.com, as `App.entitlements` says. The widgets have their own entitlements,
without it.

## Widgets

A widget of iOS can't show a page: it's a view of SwiftUI that iOS draws by itself, in a
process apart from the app that gets little memory and runs for a moment. What it shows still
comes from the site, as a drawing in SVG, which is how the site draws its charts.

The extension loads the library of the app, so there's one copy of it, and asks the site for an
address as the web view does. `AppSiteEmbedded` is the site with the routes for widgets, which
the site on the web doesn't have.

### The routes

* `/widget/transits/systemSmall` answers the moments of the three days ahead at which the Sun,
  the Earth or the Moon moves to another line, which `TransitTimeline` calculates. Each comes
  with the address of its drawing, when to ask again, a day later, and until when these are all
  the changes, three days later. It's a list in JSON, of `date` and `drawing`, with `reload`
  and `until`.
* `/widget/transits/systemSmall/202611151700` answers the drawing for a moment in UTC, from
  `widget_transits_systemSmall.svg` in `src/main/resources/templates`, one template for each
  size. It's a whole SVG document that a browser shows as the widget does. iOS shows each
  drawing at its moment without running anything.
* `TransitsWidget` answers both, as every widget does that extends `Widget`, which leaves it to
  say when its drawing changes and to make the drawing.
* `/widget/bodygraph` is the body graph of a moment in three sizes, which `BodygraphWidget`
  answers: the gates that the planets are in, lit as the charts light them, the channels that
  those define by name with until when each one lasts, and how many centers are defined. Its
  drawing changes when a planet moves to another gate. A template has a block for each channel
  that it has a place for, `channel_1` and on, and one that counts the rest.
* `/widget/person/198803140625` is the same body graph for a person, with what the planets add
  to it, which `PersonWidget` answers from the templates of the body graph: the gates of the
  person in one color and those of the planets in another, the channels that the planets
  complete by name, and a line of their color around the centers that those define. The person
  is the moment of their birth in UTC, with a name to show after the address, `?name=Nora`.

### The widget of a person

A widget of a person is one of the people that the pages saved, who are in the storage of the
web view, where a widget can't read. So the pages tell the app of them when a page loads and
each time they change. The app keeps the names and the births in a file of the folder that it
shares with its extension, `group.com.uwyn.gatesofhumanity`, which is what
`app/App.entitlements` is for.

A birth is a time on the clock of a place there, and looking a place up takes the memory that
a widget doesn't have. So a page asks the site once for each birth, at
`/widget/born/198803140725/2800866`, for the moment in UTC, and tells the app that.

iOS offers those people to choose from when a widget is edited, and a widget that nobody was
chosen for shows the one whose chart the app showed last. A tap on it opens that person's chart
with the transits. The address says who, `/wheel/birth-transit?person=Nora`, and the app has
the page that's shown remember them, as the People page does for one of its charts.

### Shared drawings and code

What the widgets have in common is written once:

* The body graph is `bodygraph.svg`, which the chart of the pages includes too, with other
  colors, and `WheelImage.lightBodygraph` lights it for both.
* `Hexagram` draws the lines of a hexagram for the pages and the widgets.
* The templates include their letters and colors from `widget_style.svg`, their sky from
  `widget_sky.svg`, the two shapes of a hexagram's line from `widget_hexagram.svg`, and the
  colors of a body graph and the shape behind it from `widget_bodygraph_style.svg` and
  `widget_bodygraph_cone.svg`.

In the extension, `Widgets.swift` declares each widget and `DrawnTimeline.asked` in
`Drawn.swift` asks the site for its drawings. `DrawnView` in `Entries.swift` draws one with
[SwiftDraw](https://github.com/swhitty/SwiftDraw), since iOS has nothing that draws SVG in a
widget.

So another widget is a route, an element and templates in Java, and a few lines in
`Widgets.swift` that tell iOS its name, its description and its sizes, which iOS wants to know
from the app itself.

### What a drawing says beyond the picture

A drawing says three things that are more than a picture, in ways that SVG has, and
`Drawn.swift` takes them out of its text:

* The link around what it shows, `<a href>`, is the address that a tap on the widget opens in
  the app.
* The one element with the id `background`, which closes itself, is what's behind the widget.
  iOS draws that apart and leaves it out where it has its own: on a tinted home screen, and on
  the Lock Screen, where the backdrop of iOS takes its place.
* A `<text>` with a `data-time` is a moment that the device writes in its own language and time
  zone, as its `data-text` says with a date skeleton of Unicode between braces,
  `until {EEEjmm}`. What the text holds is the same in UTC, for a browser. A moment that's more
  than six days ahead is written as a date, since the day of the week would name the wrong one,
  and as a month and a year when it's most of a year away.

A drawing has more than text: beside the gate of a planet it has the hexagram of that gate,
with the line that the planet is in lit, from the same two shapes that the pages draw a
hexagram with.

### Fitting the text

SVG places text and doesn't fit it, so the site does. A template says on its first element how
many letters of a name fit a line, at which size, down to which size and on how many lines,
and `Fitted` breaks a long name over the lines and makes its letters smaller. It counts letters
since it knows no font, so a new layout is worth a look with its longest names: the gate of the
Sun on the eighth of August has 35 letters.

The widgets are in Helvetica Neue, as the title of the site is, which iOS has by that name.
SwiftDraw looks a font up by its name and knows no `system-ui`, and it leaves out
`letter-spacing` and a `<tspan>` inside a text. `TransitsWidgetTest` checks the drawings for
all of this.

### Memory

What a widget asks must stay small. Started and asked for a widget, the Java side takes 11 MB on
a Mac, where a chart takes 106 MB, nearly all of it the cities. So a route for a widget mustn't
look up a city, and gets a moment in UTC where a page gets a place.

Left to itself the Java side only clears up after it used far more than a widget may, 49 MB
after thirty requests. So RIFE2's package starts it in an extension with a heap of 16 MB at
most, which keeps those thirty under 19 MB. A simulator has no limit on memory, where an
iPhone 17 Pro lets the extension use 60 MB.

### Building and seeing them

`build.sh` builds the extension into the app for the simulator, where iOS lists the widget,
with the SwiftDraw that `project.yml` names for the device build, whose sources it fetches into
`build`.

`widgets/sheet.py OUT.png` draws every size on one sheet without building the app. It asks the
site on the JVM for the drawings of two moments, one with short names and one with the longest,
and draws them with SwiftDraw's command line tool, which it builds into `build`.

The site on the web has the small and the medium drawing too, as images that a page elsewhere
can show, see the README of the site. There nothing writes the moments again, so those drawings
say that they're in UTC, and where they're from.

## The watch

A watch has no web view for the pages, and the library isn't built for one. So the watch app,
`watch/`, shows drawings that the app on the iPhone makes for it: the ones of the small widgets,
which fit a watch. It's inside the app of the iPhone, as iOS wants a watch app that belongs to
one, and gets to a watch along with it.

### What it's made of

* `watch/Drawings.swift` is what's sent, and what both sides do with it. A screen is a title
  with the drawings of a timeline of the site, each from its moment on, for the three days that
  the site looks ahead. There's one for the transits, one for the body graph of this moment, and
  one for each of the first eight people that the widgets offer, the one whose chart the app
  showed last first. What a watch face shows of the transits comes along in its three sizes.
* A tap on a screen opens rows to read, which the site says at the address of the size `watch`,
  as JSON rather than a drawing and with their own moments. That's a row for each of the
  thirteen planets with its gate and line, the name of the gate and until when it stays, or
  every channel by name with until when it lasts. A tap on a row opens what explains it, in the
  texts of the Explore panel of the pages: the planet, the gate and the line, or the channel,
  its circuit and its two gates. A last row explains the screen as a whole. What explains is the
  same from one moment to the next, so it's sent once, and the rows say where theirs is. Packed,
  four screens with all of that are 28 KB, since the drawings and the rows of a timeline are
  much alike.
* `app/Watch.swift` is the watch as the iPhone app sees it. The watch asks, and the answer is
  the drawings themselves. iOS starts the app for that when it isn't running, so the iPhone can
  stay in a pocket. The app also sends them by itself when the saved people change and when it
  comes to the front after six hours, as what the watch should have of it, of which only the
  last counts.
* `watch/Received.swift` asks when the watch app has nothing or when what it has says to ask
  again, a day after it was made, also while the app isn't on the screen. A question waits until
  the iPhone is in reach. What arrives is kept in the folder that the watch app shares with its
  extension.
* `watch/Gates.swift` is the app: a screen for each, one below the other, which shows the
  drawing of this moment and the next one when its moment comes, without the iPhone. A tap opens
  the list, which the watch writes itself in the colors of the drawings, with the page of what
  explains a row. After the last moment that the drawings are good for, it says that the
  transits show when the iPhone is near, as it does before anything arrived.
* `watch/Complications.swift` is the extension for a watch face: the transits in a circle, in a
  rectangle and as a line of words, from the same kept drawings, with the view that the widgets
  of the iPhone have for the Lock Screen. A tap on it opens the app on the screen of the
  transits, wherever the app was left.

### Building and checking it

`build-watch.sh` builds the watch app for the simulator and starts it on the watch that's paired
with the simulator of an iPhone. `build.sh` puts it inside the iPhone app.

The link between two simulators takes a minute or two to pass a question on and often doesn't,
so `check-watch.sh` hands over what the iPhone app makes itself. The iPhone app writes it to a
file, the watch app takes the file as if it was sent, and says what it kept.

These settings say where the watch app opens, or which watch face it taps:

| Setting | What it does |
| --- | --- |
| `SIMCTL_CHILD_GATES_SCREEN` | Opens on a later screen, the second one for `1`. |
| `SIMCTL_CHILD_GATES_DETAIL` | Opens on what a tap on that screen opens. |
| `SIMCTL_CHILD_GATES_ROW` | Opens on what explains a row of it, the first one for `1` and the screen as a whole for `0`. |
| `SIMCTL_CHILD_GATES_FACE` | Taps a watch face that many seconds after the start. The watch app says which screen that opened, which the check looks at too. |

For a device, the watch app and its extension each have their own identifier with the shared
folder of the app, `group.com.uwyn.gatesofhumanity`, which Xcode registers the first time that
the project is built in it.

## Checks

### Launch settings

These settings change how the app starts, when launching with
`xcrun simctl launch --console-pty`:

| Setting | What it does |
| --- | --- |
| `SIMCTL_CHILD_GATES_START` | Opens another page than the Birth Chart, a share link for instance. |
| `SIMCTL_CHILD_GATES_EVAL` | A script that runs in the page after it loaded, whose result the app prints. |
| `SIMCTL_CHILD_GATES_OPEN` | An address that the app opens after a few seconds, as a tap on a widget would. |
| `SIMCTL_CHILD_GATES_WELCOME` | Shows the welcome. Without it, in a simulator the pages are past their welcome, which would cover what a check looks at and taps. |

### The library against the JVM

`./bld app-check ios-sim` asks the library in the simulator for charts, descriptions, tables,
cities, widgets and files whose content doesn't depend on the moment, the addresses that
`AppBuild.java` lists, and asks the same classes on the JVM. It passes when every status, size
and checksum is the same, so it shows whether a build with another GraalVM, JDK or RIFE2 still
calculates what the site does.

The pages have something new in them each time, so the build goes through them instead, with
`AppWalk.java`.

### Many requests at once

Each piece of a response that the app hands to the web view is a message to the process of the
web view, and while there are many of them the app answers nothing else. RIFE2 collects what a
site writes, 32 KB at a time, so a chart is eight pieces, where a piece for each part of its
template would be about seven thousand.

`check-burst.sh` asks the app in the simulator for 150 charts, descriptions and times at once,
as a few quick taps on a step of a chart do. It passes when they're all there within ten
seconds, and it takes a fifth of a second.

## How the library is built for iOS

GraalVM 25 knows iOS as a target, and only lacks what it links into every image: the static
libraries of the JDK and three of its own small C libraries. `./bld app-jdk-libs` builds both
sets from their sources, once for every app on this Mac, and RIFE2's README for native apps says
how. `./bld app-library` then links an iOS library from the start, against the iOS SDK, and
nothing in it was compiled for macOS.

### Which GraalVM

It builds with GraalVM Community Edition 25.5 as sdkman installs it,
`sdk install java 25.5.5.0+3-graalce`, whichever JDK `java` is. It's under the GPL with the
Classpath Exception like the JDK, so an app made with it can also be sold. The Community
Edition's library is 35 MB.

Set `GRAALVM_HOME` to build with another one. Oracle GraalVM 25.0.3 builds the app too, and its
license lets an app be given away but not sold. Its library is 40 MB:

```console
GRAALVM_HOME=~/.sdkman/candidates/java/25.0.3-graal ./bld app-library ios-sim
```

### Without the built libraries

Without the built libraries, a library for the simulator is built with stand-ins: the macOS
libraries of GraalVM, under the platform of iOS. That works because an iPhone and a Mac have the
same processor and the functions these libraries call exist on both.

## Releasing

### The library in the app

The library is in the app as a framework, `gatesofhumanity.framework`, since the App Store takes
no library that's a separate file. `./bld app-library` also puts the frameworks that it built
for devices and for simulators together as `native/gatesofhumanity.xcframework`, which is what
the project of Xcode links, so that it builds for either. It has a scheme for the app, one for
the app for visionOS and one for the watch app.

### Privacy manifests

Apple asks for a reason for some of what an app calls. `./bld app-check-privacy APP` compares
what the app and the framework call with what their privacy manifests declare,
`app/PrivacyInfo.xcprivacy` and `app/Library-PrivacyInfo.xcprivacy`, both ways.
`push_build.py` and `testflight.py` run it on every archive.

### Build numbers

`testflight.py` leaves the build number as it is, and App Store Connect refuses a number it
already holds. So publish a build first, commit the number it raised, and then upload it. A tree
with uncommitted changes is refused.

### The release tools

`push_build.py`, `testflight.py`, `store_listing.py` and `store_media.py` aren't of this project
and aren't in its repository. They're Uwyn's own tools for releasing its apps, the same for
every app that's published this way, and read what's this app's own from `Store/store.json`,
from the folder of the project. The app itself builds, runs and installs without any of them,
with the scripts of this folder.

`Store/store.json` names:

* the systems with their schemes and what's built before each,
* the file with the version and the build number,
* the address where a build is published,
* which app of App Store Connect this is, with its listing and how the listing names each
  system.

A `Store/store.local.json` beside it, which isn't tracked, is laid over it. It has the server
that builds are uploaded to, and which screenshots and previews, in folders outside the project,
go to which kind of display.

`store_listing.py` puts the promotional text, the description and the notes for App Review of
each system on its version, with the build that was uploaded last. `store_media.py` adds the
screenshots and the previews where a version has none of a kind yet, the watch's to the version
for iOS.

### The listing

`Store/AppStoreListing.md` has everything that App Store Connect asks for: the texts of the
product page, the answers to its questions and the notes for the reviewer, every value in its
own block. `Store/listing_page.py OUT.html` checks their limits and makes a page of them, from
`Store/listing_page.html`, on which each value has a button that copies it.

### Signing for another team

The apps are signed for Uwyn's team, which `project.yml`, `Store/store.json`, and the
entitlements and property lists of the Mac app in `mac` name by its ID. To build them for
another team, put that team's ID in its place, and a bundle identifier of your own where the
project files, sources and scripts say `com.uwyn.gatesofhumanity`.

## Screenshots and previews

### Screenshots

`Store/screenshots.py` makes the screenshots of the store after `build.sh` built the app, into a
folder outside the project, `SHOTS` here:

* `capture "<simulator name>" RAW` fills the app in a simulator with people who don't exist and
  captures eight screens.
* `home "<simulator name> Capture" RAW` captures the Home Screen with the widgets.
* `frame RAW OUT` sets each one under its headline.

The 6.9-inch iPhone and the 13-inch iPad are the sizes that the store asks for. The 6.3-inch
iPhone set is the 6.9-inch one scaled down:

    ios/Store/screenshots.py capture "iPhone 18 Pro Max" ios/build/shots/iPhone
    ios/Store/screenshots.py home "iPhone 18 Pro Max Capture" ios/build/shots/iPhone
    ios/Store/screenshots.py frame ios/build/shots/iPhone SHOTS/iPhone
    ios/Store/screenshots.py capture "iPad Pro 13-inch (M5)" ios/build/shots/iPad
    ios/Store/screenshots.py home "iPad Pro 13-inch (M5) Capture" ios/build/shots/iPad
    ios/Store/screenshots.py frame ios/build/shots/iPad SHOTS/iPad

The same script makes the screenshots of the other apps, each as its store asks for them:

    ios/Store/screenshots.py watch "iPhone 17" SHOTS/Watch
    ios/Store/screenshots.py vision "Apple Vision Pro Capture" SHOTS/Vision
    GATES_PROBED=1 mac/build.sh app
    ios/Store/screenshots.py mac ios/build/shots/Mac SHOTS/Mac

* `watch` has the iPhone app make the drawings of those people and opens the watch app, in the
  simulator of the watch that's paired with the iPhone's, at each of its screens.
* `vision` captures the app for visionOS that `vision/build.sh` built, under a headline over the
  room.
* `mac` captures the Mac app that's built to be looked into, which is another app to the Mac
  than the one that someone uses and keeps its own people, and sets what its window shows in a
  window beside its headline. Put the app that's used back afterwards with `mac/build.sh app`.

### The simulator for Vision Pro screenshots

The `vision` simulator is one made for captures, since it's put in the room of the simulator that takes
the least attention away from the window and is looked at straight on, the living room in the
evening, which a simulator keeps. Nothing moves the eye of a simulator from a command line, so a
picture with something beside the window is taken out of a wider one, around the middle of
both:

    xcrun simctl create "Apple Vision Pro Capture" com.apple.CoreSimulator.SimDeviceType.Apple-Vision-Pro-4K com.apple.CoreSimulator.SimRuntime.xrOS-27-0

### Widgets on the Home Screen

Nothing puts a widget on a Home Screen from a command line but a finger, so a UI test does it,
`feel/HomeScreen.swift`, which `feel/run.sh UDID Class/test` builds and runs. It holds the Home
Screen, taps Edit and Add Widget, searches for the app and adds the widgets whose places among
those of the app it's given, and another test takes them off again.

So that the Home Screen is quiet behind them, `home` first changes it, for good. That's why it
only does so in a simulator whose name ends with Capture, one made for this:

* a wallpaper of one color, a night that's deeper than the widgets' own (the Settings of a
  simulator have no wallpaper, the Home Screen's Edit has, and a photo chosen there comes out
  black),
* the icons large, which is without their names,
* off the first page what iOS put there, widgets with the news of the day among it.

The iPhone's is the one that the previews are recorded in. The iPad's was made once with
`xcrun simctl create "iPad Pro 13-inch (M5) Capture"` and the device type and runtime of the
iPad Pro.

The widget of a person only gets a person in a simulator because `build.sh` makes the App
Intents metadata of the extension as Xcode does, with `appintentsmetadataprocessor`. Without it
iOS gives that widget no question to answer and it stays empty.

### Previews for iPhone and iPad

`Store/previews.py` makes the app preview of the store in the same two steps. `capture` records
the app while a script taps, flicks and steps through its pages, a scene at a time, and `cut`
joins the scenes, each above its two lines of words, at the size and in the format that the
store asks for:

    python3 ios/Store/previews.py capture "iPhone 18 Pro Max Capture" ios/build/previews/iPhone
    python3 ios/Store/previews.py cut ios/build/previews/iPhone PREVIEWS 886x1920
    python3 ios/Store/previews.py capture "iPad Pro 13-inch (M5) Capture" ios/build/previews/iPad
    python3 ios/Store/previews.py cut ios/build/previews/iPad PREVIEWS 1200x1600

How the scenes are laid out:

* The words have the bottom of the picture, so a scene says how much of the top of the screen
  is left out instead, and moves its page up before a tap opens the card, so that the chart,
  what changes and what's tapped stay in view.
* An iPad lays the pages out in another way, so a scene has its own frame for it, and the page
  isn't moved up there: the card leaves the chart in view.
* A script also says where the camera comes closer, which the cut does.

How one scene follows another:

* A scene fades into the next one, and two charts that are nearly at the same place fading into
  each other look like a fault. Where one scene ends on a chart and the next begins on one,
  they're of quite another size.
* What happens in one page is one scene, whose words change under it.
* The transits and the connection, whose pages are nearly the same, have Learn between them.
* Words leave to the left and the next ones come from the right, since words that only fade
  into others are easy to miss.

How it begins and ends:

* It begins with the chart rather than with a name, since the store plays it without being
  asked. It opens close to the birth chart while that runs through the three weeks before the
  birth, a day at a time, which is the only place where a chart runs through time.
* It ends on the icon and the name on their sky. The last scene isn't a page of the app: it's
  the Home Screen with the widgets, as the screenshots have it, standing still under its words,
  so both simulators are ones made for captures.

The simulator is one without a Dynamic Island, which iOS would otherwise draw into the
recording: a copy of a device type with `DeviceSupportsDynamicIsland` set to false.

### Previews for Vision Pro and Mac

`Store/wide_previews.py` makes the previews of the app for visionOS and of the Mac app, whose
picture is wider than high, from the scenes and with the words of the other two:

* The one for visionOS is the room of the simulator with the words over its top, and has the
  chart in the room in it.
* The one for a Mac is its window on the sky of the icon with the words beside it. The Mac app
  films what its own window shows, so nothing records the screen.

The watch has no preview, its store takes none.

    python3 ios/Store/wide_previews.py vision "Apple Vision Pro Capture" ios/build/previews/Vision
    python3 ios/Store/wide_previews.py cut vision ios/build/previews/Vision PREVIEWS
    python3 ios/Store/wide_previews.py mac ios/build/previews/Mac
    python3 ios/Store/wide_previews.py cut mac ios/build/previews/Mac PREVIEWS

## The icon

`icon.py` draws the icon of the app into the asset catalog: the wheel of the 64 gates with a few
of them lit, around the outline of the chart's body graph, whose corners and curved edge are as
far from the ring.

The site has the same icon, so it also writes `favicon.svg` and `apple-touch-icon.png` in
`src/main/webapp/images`, and the emblem without the sky around it as `emblem.svg` there, which
the welcome of the site shows.

## What it shows

In the iPhone 17 simulator and on an iPhone, with the libraries built from the sources:

* The pages, the charts, the Explore panel and the city search, with the web pages unchanged.
  Tapping a part of a chart, a share link with three rows, rendering a chart as PNG and local
  storage that survives a relaunch were checked with the stand-ins and happen in the web view.
  The first chart takes between 0.3 and 1.5 seconds, the next ones a few milliseconds.
* The system properties come from the iOS port: `os.name` is `iOS`.

The library is 35 MB with the Community Edition and 40 MB with Oracle GraalVM.

## What it doesn't show yet

* **Forms that post.** The app passes the method and the body on, and a `POST` from a script
  arrived whole in the simulator, but the site has no form that posts. Uploads and answers that
  stream, such as server-sent events, are untried too.
* **The workflow engine.** The same instrumentation covers it, but nothing here runs it, and
  iOS stops the threads of an app that isn't in front.
