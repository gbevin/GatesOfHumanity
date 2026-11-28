# Gates of Humanity

Gates of Humanity draws Human Design charts and transits. You can use it on the
web at [hd.uwyn.com](https://hd.uwyn.com), as an app for iPhone, iPad, Apple
Watch, Mac and Apple Vision Pro from the
[App Store](https://apps.apple.com/app/id6834835560), or as an app for Android
from [Google Play](https://play.google.com/store/apps/details?id=com.uwyn.gatesofhumanity).
It's free, without ads or tracking.

Human Design reads a chart from the positions of the Sun, the Moon and the
planets at two moments: the birth, and the moment the Sun was 88 degrees
earlier, about three months before. What I care about here is calculating
those positions properly and drawing them clearly. I'm not a Human Design
practitioner, and there are no readings or advice. The descriptions introduce
what each part of a chart means within Human Design, following its own
literature.

This repository is the source of the site and of the apps. I'm Geert Bevin of
[Uwyn](https://uwyn.com), and I wrote it in Java with [RIFE2](https://rife2.com), my web framework, and built
it with [bld](https://rife2.com/bld). If you only want to see a chart, the site
or the app is all you need. The source is here in case you want to run it
yourself, check how the charts are calculated, or see what RIFE2 and bld look
like in a complete application.

<img src="docs/images/birth-chart.png" alt="A birth chart" width="480"> <img src="docs/images/month-transits.png" alt="The transits of a month" width="480">

**For everyone**

* [What it does](#what-it-does)
* [Where your data stays](#where-your-data-stays)
* [How the charts are calculated](#how-the-charts-are-calculated)

**For developers**

* [Running it yourself](#running-it-yourself)
* [Deploying as a war](#deploying-as-a-war)
* [Native executable](#native-executable)
* [The apps](#the-apps)
* [Tests](#tests)
* [A tour of the source](#a-tour-of-the-source)
* [Data](#data)
* [Tools](#tools)
* [License](#license)

## What it does

There are five kinds of charts and two tables:

* a birth chart with its Type, Strategy, Authority, Profile, Definition,
  Incarnation Cross, channels and Variables,
* the transits of any moment, on their own or on top of a birth chart,
* a connection chart of two people, on its own or with the transits,
* the gate and line changes of every planet over a month or a day.

Below every chart, an Explore panel introduces each part of it, and tapping a
part of the chart opens its description. For a birth chart it also shows how
much earlier or later the birth could have been with each part staying the
same. The Learn pages have the same descriptions, one topic at a time.

The button in the corner of a chart shows it full screen. There you can make it
larger and move it around with your fingers, the mouse or the keys to read its
details, and tapping a part describes it as on the page.

On a phone the wheel takes the full width, and what the drawing lists below it
is shown as text instead. The two tables are HTML, and scroll sideways on a
phone with their dates staying in sight.

You can save a chart as a PNG image or an SVG drawing, or print it. A share link
rebuilds the charts of a page from what follows its `#`, which browsers never
send to the server.

The first time you open a chart page, it welcomes you with the name of the site
and what it's for. The foot of every page has a link to see that welcome again:
a chart page shows its own, and any other page leads to the Birth Chart, which
shows it for the `#welcome` of its address.

## Where your data stays

The people you save, with their birth date, time and place, stay in your
browser's local storage, as does the last birth data you used. You can load
anyone you saved into any chart, and give people a group, such as Family or
Friends.

The Saved people window of a chart lists them under their group and can show
one group at a time. The People page of the menu lists them too. That's where
you add someone with their birth date, time and city, rename them, put them in
another group, delete them or open them in a chart. The browser fills in that
page, the server has nobody to list.

To draw a chart, the browser sends its date, place and name to the server, and
the application uses them for that answer without keeping them. The server
only remembers the id of the location charts are seen from, in a cookie, to
start pages at the local time there.

Until you choose a city, a page starts at the most populated city of the time
zone your browser is set to, New York for America/New_York, and at London when
the zone has no city. The browser asks the server for that city by the name of
the zone and keeps the answer. The time there is your own, the city often
isn't, and it's never remembered as one you chose.

Browsers can clear what a site stores. Safari does after 7 days of browsing
without a visit, unless the site was added to the home screen. Export on the
People page keeps a copy as a file, which is the only sure backup, and Import
brings it back or onto another browser.

## How the charts are calculated

Everything is calculated in pure Java.

### Positions

* Positions are geocentric apparent ecliptic longitudes of date, the same as
  the defaults of the Swiss Ephemeris. The lunar node is the true (osculating)
  node.
* The Design is the moment the Sun was 88 degrees of longitude before its
  position at birth.
* Earth is opposite the Sun and the South Node is opposite the North Node.
  Chiron is shown but doesn't activate gates.

### Gates, lines, colors and tones

* Gate 41 starts at 302 degrees (2 degrees Aquarius).
* Each gate covers 5.625 degrees and each line 0.9375 degrees, including its
  start and excluding its end.
* Each line divides into six colors and each color into six tones, the same
  way.

### What a chart says

* Type, Authority, Profile, Definition and the angle of the Incarnation Cross
  follow The Definitive Book of Human Design. The tests check them against the
  book's sample charts, Jovian Archive's published charts of Steve Jobs and
  Stephen King, and the published charts of a dozen public figures.
* The Variables come from the color and tone of the Design Sun
  (Determination), the Design Nodes (Environment), the Personality Sun
  (Motivation) and the Personality Nodes (Perspective, also called View).
  Tones 1 to 3 face left, 4 to 6 right. A tone is 93.75 arcseconds of
  longitude, about 38 minutes of the Sun's motion, so the Variables need a
  birth time accurate to a few minutes.

### Line fixing

A line is fixed in exaltation or detriment by its planet anywhere in the same
gate, or in the harmonic gate at the other end of a channel, from the
Personality as well as the Design.

In a birth transit chart the transits fix lines of the birth chart, and in a
connection chart each person fixes lines of the other, temporarily. A line that
only another chart fixes is drawn with an outlined triangle, its own fixings
with a filled one.

This follows
[Line Fixing in Human Design](https://jovianarchive.com/blogs/chart-interpretations-components/line-fixing-in-human-design-exaltation-detriment-and-juxtaposition),
and its examples are among the tests.

### Accuracy and range

The ephemeris covers 1800-01-03 to 2199-12-17, so birth charts can be made from
1800-04-08.

The tests require the positions to agree with the Swiss Ephemeris within 6
arcseconds for every body up to 2025, and the design moments within 3 seconds.
Most bodies agree within 2 arcseconds, Chiron within 5. For comparison, a line
is 3375 arcseconds wide.

Further in the future, the two predict the rotation of the Earth differently,
which moves the Moon by up to a minute of arc by 2200.

## Running it yourself

Everything is pure Java, with no database and no native library, and the site
runs as a single jar, as a war or as a native executable. You need Java 25 or
later. To start it from the source:

```console
# compile and start the embedded server
./bld compile run
```

bld downloads the dependencies the first time. Then open http://localhost:8080.

You can also build a standalone jar and run that:

```console
# build the jar
./bld uberjar
# run it
java -jar build/dist/gatesofhumanity-2.0.0-uber.jar
```

The port of the embedded server is set with `gatesofhumanity.port` or the
`GATES_PORT` environment variable.

Until the site knows its public root URL, it runs in development mode and
disables caching.

### Behind a reverse proxy

The site needs to know its public root URL, for instance
`https://charts.example.com`. You can give it in any of these ways:

* as `hdapp.proxyRootUrl`, an init parameter of the RIFE2 filter or of the
  servlet context in a war's `web.xml`,
* as `hdapp.proxyRootUrl`, a Java system property,
* in the `HDAPP_PROXY_ROOT_URL` environment variable.

### Names that stay hdapp

A few names start with `hdapp`, the name the project began under. They stay,
since servers that run the site set the first one, and under another name the
people someone saved would be gone and the files they exported refused:

* the proxy root URL, which servers that run the site set,
* what the pages store in a browser or an app,
* the address `hdapp://app` that an app has its pages at, which is where they
  store it,
* the format `hdapp-charts` of a file of exported people.

## Deploying as a war

This builds `build/dist/gatesofhumanity-2.0.0.war`:

```console
./bld war
```

It runs in a Jakarta Servlet 6 container such as Jetty 12 with its `ee10`
environment. Its `web.xml` runs the site without development error pages.

Copy it into Jetty's `webapps` as `gatesofhumanity.war` and set the proxy root
URL as an init parameter, for instance in a context file next to it:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE Configure PUBLIC "-//Jetty//Configure//EN" "https://jetty.org/configure_10_0.dtd">
<Configure class="org.eclipse.jetty.ee10.webapp.WebAppContext">
  <Set name="contextPath">/</Set>
  <Set name="war"><Property name="jetty.webapps"/>/gatesofhumanity.war</Set>
  <Call name="setInitParameter">
    <Arg>hdapp.proxyRootUrl</Arg>
    <Arg>https://charts.example.com</Arg>
  </Call>
</Configure>
```

You can tell the setting was applied when the image links on a chart point to
the public URL and the Learn pages don't carry no-cache headers. The chart pages
always carry them, since they start at the current time.

## Native executable

With a GraalVM JDK 25 or later, you can build the site as a native executable
that starts its server in milliseconds:

```console
# build the executable
./bld native
# run it
GATES_PORT=8080 build/dist/gatesofhumanity
```

`./bld native-check` starts the executable and checks that every route answers
as expected.

The native image configuration is in
`src/main/resources/META-INF/native-image/gatesofhumanity`. `./bld native-config`
records it by running the application under the GraalVM tracing agent and
visiting every route listed in `src/bld/java/gatesofhumanity/NativeImage.java`.
Whenever you add a route, template or resource, add a visit there and run it
again.

The agent can't see the static files, so they're included through
`META-INF/native-image/gatesofhumanity-webapp`.

## The apps

The apps show the same pages as the site, with the site compiled to native code
inside them and no server. Each has its own README:

* iPhone, iPad and Apple Watch: [ios/README.md](ios/README.md)
* Mac: [mac/README.md](mac/README.md)
* Apple Vision Pro: [vision/README.md](vision/README.md)
* Android: [android/README.md](android/README.md)

### What an app tells its pages

These properties change what the pages say and how they look in an app:

| Property | What it does |
| --- | --- |
| `gatesofhumanity.app` | Which app the pages are in: "ios", "mac", "vision" or "android". The site has it from RIFE2 there. |
| `gatesofhumanity.keptIn` | Where the pages say the saved people stay, "this app" for instance, instead of "this browser". |
| `gatesofhumanity.shareRootUrl` | The address of the site on the web. The links of the share button start with it, and the drawings show it. |
| `gatesofhumanity.columnWidth` | The width of the page column on a wide screen, 900 pixels by default, or "none" for pages as wide as the screen. |
| `gatesofhumanity.tap` | The word the welcome uses for choosing a part of a chart: "Tap", or "Click" on a Mac. |
| `gatesofhumanity.credit`, `gatesofhumanity.widgetsCredit` | Sentences added to the credits of the About page, for what the app has inside it that the site alone doesn't. The site's own credits are the same as [NOTICE.txt](NOTICE.txt). |
| `gatesofhumanity.version`, `gatesofhumanity.build` | The version and build number of the app. When the version is set, the About page shows it below the credits. |

The welcome greets the first start of an app the same way it greets a first
visit to the site.

### What the pages and the app say to each other

An app adds no script to a page. RIFE2's own script, which the head of a page
includes in an app, marks the `html` of the page with the classes `in-app` and
`on-` with the app's name, and `main.css` has rules for those. It also gives the
page what a web view doesn't do by itself: printing, and on Android sharing,
saving a file and what a page posts.

What these pages ask of their app beyond that is in `js/app.js`:

* The pages tell the app who was saved, when a page loads and each time that
  changes. That's each name with the moment of the birth in UTC, in the order
  of the names with the one whose chart is shown first, which is what a widget
  of the app needs. They tell it with `rife2NativeApp.tell` of RIFE2's script,
  as `people`, and only if the app says it hears that.
* An app can also keep the saved people somewhere else for the other devices
  of whoever uses it, as the apps for Apple devices do in iCloud. The pages work out who
  goes where, in `appSynced`. The app only keeps and removes who they say,
  which it hears as `elsewhere`, and tells them who's there and how that
  changed by calling `appElsewhere`. Who both sides had when they were last
  the same stays with what the pages keep, as `hdapp-same`.

The pages remember that they showed the welcome as `hdapp-welcomed`, in the
storage of the browser or the app.

## Tests

To run the suite:

```console
./bld compile test
```

It compares the calculated positions, design moments and activations against
2000 reference charts computed with the Swiss Ephemeris, and checks the gate
and line boundaries against The Complete Rave I'Ching. It runs in a few
seconds.

The browser scripts are tested too, in GraalJS, a JavaScript engine for the JVM
that bld downloads with the other test dependencies.

## A tour of the source

If you're here for RIFE2 and bld, these are the places to look.

### The site

* `src/main/java/gatesofhumanity/AppSite.java` declares every route as a field.
  It maps the path of a URL to parameters, has lambda routes for `robots.txt`
  and the sitemap, a fallback route for the addresses without a page, and the
  settings of the engine.
* `src/main/java/gatesofhumanity/elements` holds the elements, with
  `@Parameter`, `@Cookie` and `@ActiveSite` fields, and URLs built from the
  routes with `urlFor`.
* `/embed/transits/small` and `/embed/transits/medium` are the transits of this
  moment as an SVG image for a page elsewhere, the same drawings as the
  widgets. `TransitsEmbed` answers them. On the web, the About page shows both
  with what to copy into a page.

### The templates

`src/main/resources/templates` holds HTML, SVG, XML and text templates.

* `wheel.svg` is the whole chart, drawn by the template engine from blocks and
  values, without an image library.
* `wheel_summary.html` has the values and blocks of what the chart says below
  its wheel, under the same names. The same code fills both, so the text that
  phones show can't differ from the drawing.
* `range_table.html` is the table of a month or a day, which the page shows
  where a chart has its drawing.

### Deployment and apps

* `AppSiteDeployed.java` and `src/main/webapp/WEB-INF/web.xml` run the same
  site from an uber jar, as a war or as a native executable, with a dedicated
  page for errors.
* `AppSiteEmbedded.java` is the site inside an app. It adds what the widgets of
  the app show as dedicated routes, see `ios/README.md`, and a chart as shapes
  for the room around the app for visionOS, see `vision/README.md`.
* `AppSiteProvider.java` hands that site to RIFE2's `rife.nativeapp`, which
  runs a site inside an app, with what the pages say there.
* `AppWalk.java` goes through its pages before a native library is built.

### The build and the tests

* `src/bld/java/gatesofhumanity/AppBuild.java` is the whole build: the
  dependencies of each scope, the precompiled templates, and dedicated commands
  for the data generators and the native image. It extends `NativeAppProject`
  of RIFE2's bld extension for native apps, whose `app-library` builds the site
  as a native library for each kind of app, into `native`.
* `src/test/java/gatesofhumanity/AppTest.java` tests the site through RIFE2's
  `MockConversation`, without starting a server.
* `src/test/java/gatesofhumanity/js` runs the browser scripts in GraalJS.

## Data

The data the application needs is in `src/main/resources`. You only regenerate
it when it has to change, and downloads are cached in `.cache`.

### The sky

`ephemeris/*.bin` are Chebyshev series fitted to the
[JPL Horizons](https://ssd.jpl.nasa.gov/horizons/) ephemerides. To regenerate
them all, or only the bodies you name:

```console
./bld generate-ephemeris
./bld generate-ephemeris moon true_node
```

### Cities

`cities.tsv.gz` holds the cities of 5000 people or more from
[GeoNames](https://www.geonames.org), with the other names of the cities of
15,000 people or more, such as Cologne for Köln. It's regenerated with
`./bld generate-cities`.

### Names and dignities

These are extracted from The Definitive Book of Human Design and The Complete
Rave I'Ching by scripts in `Tools`:

* `crosses.tsv` holds the names of the 192 Incarnation Crosses, from the Global
  Incarnation Cross Index of The Definitive Book of Human Design, extracted
  with `Tools/generate-crosses.py`, which checks every cross against the
  geometry of the wheel.
* `channels.tsv` holds the names of the 36 channels from the same book,
  extracted with `Tools/generate-channels.py`.
* `gates.tsv` holds the names of the 64 gates and of their I Ching hexagrams
  from the same book, extracted with `Tools/generate-gates.py`.
* `src/test/resources/dignities.tsv` holds the planets that exalt each of the
  384 lines and the planet in detriment, from The Complete Rave I'Ching,
  extracted with `Tools/generate-dignities.py`. The tests compare it with the
  dignities in the code, and the hexagrams with the rule that opposite gates on
  the wheel have complementary hexagrams.

### Descriptions

`explore.tsv` holds the descriptions of the Explore panels and the Learn pages,
written for this site. The tests check that every part of a chart has one, and
that every part that can be tapped opens one.

### Images

`images/og-image.png` in `src/main/webapp` is the link preview of every page.
`Tools/generate-images.sh` renders it with headless Chrome from a server
started with `./bld compile run`. After rendering a new one, raise the `v`
parameter of its `og:image` in `templates/head.html`, since Facebook keeps the
image of a URL it has seen.

The icon of the site is the one of the app. `ios/icon.py` draws
`images/favicon.svg`, `images/apple-touch-icon.png` and the emblem of the
welcome, `images/emblem.svg`, along with it.

### Reference charts for the tests

`Tools/generate-oracle.py` regenerates the Swiss Ephemeris reference charts in
`src/test/resources/oracle`. It needs a local Swiss Ephemeris build with
`swetest`, which nothing else in the project depends on:

```console
Tools/generate-oracle.py /path/to/swisseph > src/test/resources/oracle/swisseph.csv
```

## Tools

The scripts in `Tools` need Node 22 or later, Python 3, Perl and Chrome. The
ones that read a PDF also need `pdftotext`.

### Checking that a test can fail

`Tools/mutation-check.sh` applies a Perl substitution to a source file, runs
the given test classes (or all of them) against a clean build, lists the tests
that caught it and restores the file, also when interrupted:

```console
Tools/mutation-check.sh src/main/java/gatesofhumanity/models/Variables.java 's/tone <= 3/tone < 3/' gatesofhumanity.models.VariablesTest
```

It exits with 0 when a test caught the mutation, 1 when it survived and 2 when
the mutation didn't apply or compile.

### Checking pages in a real browser

`Tools/browser` drives a headless Chrome from Node over the DevTools protocol,
against a server started with `./bld compile run`:

* `lib.mjs` opens pages in fresh browser contexts with real clicks and keys,
  logs the console and failed requests, and saves screenshots in
  `build/browser`. Its pages are past the welcome of a first visit, unless
  `newPage` is given `welcome: true`.
* `shot.mjs` screenshots a page at desktop and phone width.
* `check-entry.mjs` checks that the rows with the dates and the cities have the
  same sizes on every chart page, at twelve widths from a small phone to a wide
  screen, and that the cities a field suggests open below it.
* `check-install.mjs` checks that Chrome can install the site from
  `site.webmanifest`, and that the installed site scrolls inside its window and
  leaves the welcome out.

Here's a typical session:

```console
Tools/browser/chrome.sh start
node Tools/browser/shot.mjs /wheel/birth birth
node Tools/browser/check-entry.mjs
node Tools/browser/check-install.mjs
Tools/browser/chrome.sh stop
```

`chrome.sh` looks for Chrome where macOS has it. Elsewhere, set `CHROME` to
another Chrome or Chromium binary.

### Comparing with other calculators

`Tools/compare` compares the site's charts with other open source calculators.
You run it locally, it's never part of the build. The other calculators are
[pyhd](https://github.com/ppo/pyhd) (Swiss Ephemeris) and
[natalengine](https://github.com/Unforced-Dev/natalengine) (astronomy-engine).

1. `compare.py moments 10000` writes random moments from 1900 to 2100.
2. `ExportCharts.java`, `pyhd_charts.py` and `natalengine.mjs` turn them into
   charts.
3. `compare.py diff` compares every activation and everything derived from
   them.

For instance, the site's own charts come from:

```console
java -cp "build/main:src/main/resources:lib/compile/*" Tools/compare/ExportCharts.java moments.txt > site.jsonl
```

## License

Gates of Humanity is licensed under the Apache License, Version 2.0, the same
as RIFE2. See [LICENSE.txt](LICENSE.txt), and [NOTICE.txt](NOTICE.txt) for the
data and libraries it includes, among which the GeoNames city data under
CC BY 4.0.
