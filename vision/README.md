# Gates of Humanity on visionOS

This is Gates of Humanity as an app for Apple Vision Pro, and it's on the App
Store as part of the same app as the one for iPhone and iPad. It's built the way the
iOS, the Mac and the Android apps are: the site is compiled to a native library
with GraalVM, and a web view asks that library for every page instead of a
server.

The app shows the charts in a window, with what a window in a room has around
it: tabs at its side for the charts, the steps through time below it, and a
panel that slides out beside it to explain what was tapped. A chart is also in
the room itself, as shapes that the site describes and the app only builds.

## Building and running

These are the commands you'll use most:

```console
vision/build.sh                       # builds the library and the app, and starts it in the simulator
vision/build.sh app                   # the app alone, around the library that was built last
vision/check.sh                       # builds, and checks the app in the simulator
vision/shapes.sh OUT.png              # shows the shapes that the site describes on the JVM, without building the library
ios/install.sh "Apple Vision Pro"     # builds the app for a device and installs it
```

### The library

`./bld app-library vision-sim` and `vision` build the site into
`native/vision-sim` and `native/vision`, and into
`native/gatesofhumanity.xcframework` with the frameworks for iOS, with the bld
extension of RIFE2 for native apps.

GraalVM doesn't know visionOS as a target, so the library is built as the one
for iOS is: with the libraries of the JDK for iOS under the platform of
visionOS, and with a linker that's told to link for visionOS against its SDK.
An Apple Vision Pro and an iPhone have the same processor, and the functions
that these libraries call exist on both.

### The app

* `build.sh` builds `build/sim/Gates.app` around the library with `swiftc`, as
  `ios/build.sh` does for the simulator of an iPhone, and starts it in the
  simulator.
* The target `Vision` of `ios/project.yml` is the app for a device.
  `ios/install.sh` builds it and installs it on an Apple Vision Pro that's in
  reach, and `push_build.py` publishes it for a registered one to install from
  anywhere. It's the same app as the one on an iPhone, under the same
  identifier.
* `app/Assets.xcassets` has the icon in the three layers that visionOS wants:
  the sky, the wheel, and the outline of the body graph in its middle.
  `ios/icon.py` draws them with the icon of the iOS app.

### The files

`app/` is what visionOS has otherwise than iOS:

| File | What it is |
|---|---|
| `Gates.swift` | the app: the window of the pages, its tabs, and what's below and beside it |
| `Explained.swift` | the panel beside the window that explains what was tapped |
| `Volume.swift` | the chart in the room, built out of the shapes that the site describes |
| `Shape.swift` | one of those shapes: what it's made of, where it is, and what of it a tap lands on |
| `Outline.swift` | an outline of such a shape as triangles |
| `Moves.swift` | which shapes slide to where they are next and which are there at once |

The rest is the Swift of the iOS app as it is: `Site.swift`, `Pages.swift`,
`Platform.swift`, `People.swift`, `Saved.swift`, `Elsewhere.swift`,
`Menus.swift` and `Probe.swift` of `ios/app`, around RIFE2's Swift package for
native apps.

## Checking the app

`check.sh` checks the app in the simulator: what a page leaves out there, the
chart in the room, that it follows the page through time, and that a tap on it
is explained in the panel.

The app is looked into as the iOS app is, through its environment in a
simulator, see `ios/app/Probe.swift`. These are what visionOS adds:

| Variable | What it does |
|---|---|
| `SIMCTL_CHILD_GATES_VOLUME` | Has the chart in the room from the start, and taps the shape that it names there once it's built, `center.SACRAL` for instance. |
| `SIMCTL_CHILD_GATES_SIZE` | The size that the window opens with, as `600x800`. |
| `SIMCTL_CHILD_GATES_SHAPES` | A file of shapes to show from up close, in the place of what the site describes. |

`shapes.sh` uses `SIMCTL_CHILD_GATES_SHAPES`. It has the site on the JVM write
the shapes of a chart, which takes a second where the native library takes a
minute to build. Then it starts the app in the simulator with them, and leaves
a picture of the simulator.

`ios/check-swift.sh` checks on this Mac how an outline becomes triangles, which
shapes slide to where they are next, and that a tap lands on a shape and not
beside it.

## The window

### Pages on glass

A page in this app marks itself with the class `on-vision`, and leaves out its
menu and its buttons that step through time, since the app has those around
the window. The name of the site and of the page are at the top of the page,
on a card in the color of the site's own bar.

The window is the glass of visionOS:

* What's entered for a chart is on the glass itself, in fields that are light
  on it.
* The chart and the texts are cards on it, in the colors that they have
  everywhere.
* What a page opens over itself, as the people to load, what to save or the
  welcome, is a sheet of dark glass in the middle of the window. So is the list
  of cities that a field suggests.
* What's read is a little larger than in a browser.

`main.css` has the styles for all of this under `html.on-vision`.

### Tabs

The tabs are those of visionOS, at the side of the window: the five charts, the
transits by month or by day, People and Learn. visionOS shows eight tabs at
most, so the two tables of transits share one, and you choose which of the two
it is below the window.

### Below the window

Below the window of a chart are Now and the steps of a day, an hour and a
minute, which go on while they're held. Then come the chart in the room, the
chart as large as the window and back, saving it and copying its link. Each
presses the button that the page has for it.

Below a page that was come to from another one, as a topic of Learn, is the way
back.

### Size

The window is sized by its bottom corners, so the bar below it stays between
them. When the window gets narrower, the bar gives up room in three steps:

1. What comes after the steps moves into a menu at its end.
2. The steps become one pair of buttons, with the step that they take chosen
   between them.
3. The choice between the transits by month and by day moves into the menu
   too.

The window is never narrower than that bar and its corners, nor lower than its
tabs.

### Keyboard

A keyboard has the keys that it has in the iOS app, from `ios/app/Menus.swift`.

## The panel

A tap on a part of a chart explains it in a panel that slides out from behind
the window.

The page fills the card that it has for that as ever, without showing it, and
tells the app what the card holds whenever that changes, and nothing once it's
closed.

The panel is a page of the site too, `/panel`. Its template includes the head
and the card of a chart's page and nothing else, and it shows what it's given
on the glass of the app. A button that's pressed there is pressed in the card,
by its place among the buttons, so the card of the page does what it does
everywhere else.

A link in the panel opens in the window. The panel closes with the page that it
explained: a page that loads tells the app that nothing is explained yet.

Nothing of this is about visionOS on the side of the site. A page tells any app
that listens for it, and `/panel` is there for any app that has room beside its
window.

## The chart in the room

The button with the turning cube puts the chart of the page in the room, beside
the window. It's in a room of its own that's as large as the chart, with the
bar that visionOS moves it by right below it. It's 90 centimeters wide at
first, and the corner of its room makes it smaller or larger, from a globe on a
desk to the height of a room.

The chart in the room only opens from the window of the pages, whose chart it
is, and not by itself when the app starts.

### The body graph

* The body graph stands in the middle, on a stand, lit as the drawing lights
  it.
* A center that's defined is in its color and glows, one that isn't is white,
  and each stands out of a dark slab that's a little larger, its edge.
* The corners of the centers are rounded off a little, as are those of the
  gates on the wheel, so the shapes are the ones of the drawing without their
  points.
* Every gate of the body graph is the box that the drawing has for it, with its
  number on both of its faces. That way the number is on its gate however the
  chart is turned, and you can read it from behind too.
* A gate that's lit is a black box with a white number that goes through its
  center and stands out of it. The others have a dark number, on a white box
  where their center has a color. The boxes are on the faces of their centers
  and clear of each other.

### The channels

* The channels run between the gates, as wide as the drawing has them. Each
  half is in the color of what has its gate, striped as finely as the drawing
  stripes it when two have it, and white when nothing does.
* Where the drawing paints a channel over another one, or right beside it, the
  two are at different depths, the one that's painted later before the other.
  So no channel is in another one, and two depths do for all of the body graph.
* A center is deep enough, edge included, for a channel at either depth to go
  into it whole.

### The wheel

* The wheel is around the body graph, tipped towards whoever looks, and built
  as the drawing has it: the signs outside, the 64 gates inside, and between
  them the dark band that the planets are over.
* The gates that the drawing lights, those of the planets on the wheel, stand
  out of the others in the wash of their color that the drawing gives them.
  Each has a segment of the circle inside it, towards the body graph, as the
  drawing has: thin, in the same wash, and ending before anything of the body
  graph.
* Where the drawing marks the gates of two people inside the gates, they're
  marked on the edge of the band there.
* The numbers of the gates and the signs lie on the wheel, all with their top
  away from whoever looks, so each is on its own gate from wherever it's looked
  at.
* The planets that the drawing has on the wheel are each a ball on a stem over
  where it is, with its sign, which turns to whoever looks. Planets that are
  close together stand apart, each on a stem that leans from where it is, so
  that no ball or sign is in another one.

### The halo and the lights

* Behind it all is the pale color that a page has behind its drawing, in the
  shape that the drawing has around the body graph, pointed at its top and
  round below. It keeps turning to be behind the chart, from wherever the chart
  is looked at.
* It lets a little of the room through, and its edge fades into the room. It
  reaches out of the room of the chart.
* Three lights are on the chart, which stay where they are when the chart is
  turned: one from above at the front, a weaker one from the side and one from
  behind.

### Lines to the gates

A planet has a line to its gate in the body graph, from where the segment of
its gate ends, so it goes through nothing of the wheel.

All of them at once are a tangle over the body graph, so a line is only there
while the panel explains what was tapped for it: its planet, the gate of the
wheel that the planet is in, or its gate in the body graph. It grows from a
thread into a cord that glows.

### How it's made

The chart in the room is made from the drawing. At the address of the chart's
image with `volume` in the place of `image`, the site fills the template of the
drawing with the code that draws the image, and reads the filled drawing back,
see `WheelVolume.Drawing`. From it, the site reads:

* which gates and centers are lit,
* who lights each half of a channel,
* which gates of the wheel are lit and in which wash,
* where the planets are,
* every color, from the drawing's own styles.

The places of the body graph are its shapes in `bodygraph.svg`. So the chart in
the room can't show anything that the drawing doesn't, and what it adds is what
a drawing doesn't have: how thick each shape is and where it stands.

The site answers that as JSON, a list of shapes that are slabs, rods, balls,
texts, the halo behind them and the lights on them. Each has where it is, how
it's turned, how large, in which color and whether it's matte or glowing.
Nothing of the chart is seen through, only what's behind it is.

`Volume.swift` builds the shapes as they come, and nothing in it knows what a
chart is. A slab can have any outline, which `Outline.swift` cuts into
triangles.

### Moving through time

The page says which chart it shows, so the chart in the room follows it, and a
page without a chart leaves the last one there.

After a step through time, a planet slides to where it is next with its stem
and its sign. With each picture of the room, a shape is where its description
is on its way, so a stem is always the stem between where its ball and its
foot are then.

In another chart it's there at once, in either of two cases:

* when it has far to go, further than the Moon does in a day, since what slid
  that far would cross the chart on its way,
* when its ball isn't the same one, since its stem would slide to a ball that's
  already there.

### Tapping and looking

A shape names the part of the Explore panel that explains it, and a tap on it
opens that in the panel. It lights up when it's looked at.

What a tap or a look lands on is the shape itself, not the box around it, which
for a gate that lies along a diagonal of the wheel would reach over its
neighbors.

What was tapped stays chosen while the page explains it, through a step in time
too, and isn't once the page explains something else or nothing.

A drag that starts on anything solid of the chart turns it round.

## What isn't there yet

* The widgets, which visionOS has on a wall or a table.
* The checks are of the simulator. On an Apple Vision Pro itself the app is
  looked at by whoever wears it.
