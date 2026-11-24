# Google Play listing

For the Play Console, from what the Android app does in version 1.0.1, build 56. Everything it says was checked against the app. The text is the one of the App Store with what Android does in its own way: the widgets on the home screen, no Lock Screen, no watch, and the people kept on the phone alone.

Every value to paste is in a block of its own, under the name of its field in the Play Console, so that it's copied whole. `ios/Store/listing_page.py OUT.html android/Store/PlayStoreListing.md "Play Console"` checks the limits and makes a page of this file with a button that copies each value.

## Creating the app

In the Play Console, Home, Create app.

### App name

Limit: 30

```
Gates of Humanity
```

### Default language

```
English (United States) - en-US
```

### App or game

```
App
```

### Free or paid

```
Free
```

An app that's free can't be made paid later.

The two declarations below it are ticked: that the app meets the Developer Program Policies, and that it's subject to US export laws.

## Main store listing

Under Grow users, Store presence, Store listings.

### App name

Limit: 30

```
Gates of Humanity
```

### Short description

Limit: 80

```
Human Design charts and transits, with widgets. Free, no ads and no account.
```

Google Play has no subtitle, so this says what the App Store's subtitle and promotional text say.

### Full description

Limit: 4000

```
Gates of Humanity draws Human Design charts and transits, and explains every part of them. Everything is calculated inside the app, so it works without a connection, and it's free, without ads, subscriptions or an account.

YOUR BIRTH CHART
Enter a birth date, time and city, and you get the wheel of the 64 gates with the body graph inside it, together with the Type, Strategy, Authority, Profile, Definition, Incarnation Cross, channels and Variables. The city search knows 70,000 cities and takes care of their time zones.

CONNECTIONS AND TRANSITS
Add a second person to see the connection chart of two people, with their electromagnetic, companionship, dominance and compromise channels. Look at the transits of this moment or of any other, on their own or on top of a birth chart or a connection chart, and move through time a minute, an hour or a day at a time. When you hold one of those buttons down, the chart keeps stepping and you can watch it change. Two tables list every gate and line change of the Sun, the Moon and the planets over a day or a month.

TAP TO EXPLORE
Tap any part of a chart to read what it is, whether that's a gate, a channel, a center, a planet or a Variable. Below every chart the same descriptions are listed in order, and for a birth chart they also tell you how much earlier or later the birth could have been with that part staying the same, which helps when a birth time isn't exact. The Learn pages introduce Human Design in 13 topics, from the chart and its centers to the Types, Authorities, Profiles and Variables.

A CLOSER LOOK
Show a chart full screen, make it larger and move it around, so that you can read its smallest details. Save a chart as an image or as a drawing that stays sharp at any size, print it, or share a link that opens the same chart on the web.

WIDGETS
The widgets put what's happening now on your home screen. One shows the gate and line that the Sun, the Earth and the Moon are in, with their hexagrams, and tells you when each of them moves on. Another one shows the body graph of this moment, with the channels that the planets define and when each of them ends. A third one is for someone you saved, and it shows their body graph with the channels and centers that the transits add to it right now. Tapping a widget opens its chart in the app.

KEPT IN THE APP
Save the people whose charts you look at, put them in groups, and export them to a file as a backup. They stay in the app on your phone, and they're never stored anywhere else.

CALCULATED FROM SCRATCH
The positions of the Sun, the Moon and the planets come from tables fitted to the JPL ephemerides, for any date from 1800 to 2199, and they're checked against two thousand reference charts made with the Swiss Ephemeris.

- Five kinds of charts and two transit tables
- Widgets for the home screen
- Works without a connection
- No ads, no tracking, no subscription, no account
- For Android 10 and later

I'm not a Human Design practitioner, and there are no readings or advice here. The descriptions introduce what each part of a chart means within Human Design, following its own literature, so that you can explore it and decide for yourself what it means to you.
```

The last paragraph is the one from the About page. Like the App Store listing, it says "body graph" in two words and leaves out "Rave Mandala" and "BodyGraph", which Jovian Archive claims as its own.

The graphics go on the same page:

- App icon: `icon-512.png`, 512 x 512.
- Feature graphic: `feature-graphic.png`, 1024 x 500.
- Phone screenshots: the eight frames, 1080 x 1920, in the order of their numbers.
- The tablet screenshots and the video are left empty. They're optional.

`android/Store/graphics.py` makes the first two and `android/Store/screenshots.py` the frames.

## Store settings

Under Grow users, Store presence, Store settings.

The tags are up to five from the list that Google Play offers, the ones closest to astrology and to reference. The email address is your support address, which the listing shows. The phone number is optional and stays empty, so that no number is shown.

### App category

```
Lifestyle
```

The category of the App Store listing, which is where astrology apps are on Google Play too.

### Website

```
https://hd.uwyn.com
```

## App content

Under Policy and programs, App content. Each of these is a declaration that the Play Console asks before a first release.

### Privacy policy

```
https://uwyn.com/privacy.html
```

The paragraph about the app there says that the people stay "in the app and in your own iCloud", which is only so on Apple's devices. It wants a sentence for Android before the app is sent for review.

### The paragraph of the privacy policy

```
Gates of Humanity, our app for Human Design charts, does not collect anything. The charts are calculated inside the app. The people you save stay in the app, and on Apple's devices also in your own iCloud, which keeps them the same on your devices and which we can't look into. The app makes no connection to us or to anyone else.
```

For the page on uwyn.com, in place of the one that's there now.

### App access

```
All functionality in my app is available without any access restrictions
```

There's no account or login.

### Ads

```
No, my app does not contain ads
```

### Content rating

```
All Other App Types
```

The category of the questionnaire, then No to every question: no violence, no sexual content, no bad language, no controlled substances, no crude humor, no gambling, no interaction between people, no sharing of where someone is, no digital purchases and no web browser. The email address of the questionnaire is your support address.

### Target audience

```
16-17, 18 and over
```

The ages that the app is made for. A choice that includes ages under 13 puts the app under the Families policy. The App Store rates it 4+, which is about its content, not about who it's for.

### Data safety

```
No
```

The answer to "Does your app collect or share any of the required user data types?" The app makes no network connection: it asks for no permission, so not even for the network. The people that someone saves stay in the app on the phone, and Android's backup leaves them out, since the app turns backups off. A saved image, an exported file and a shared link only go where the person sends them, through Android's own file picker or share sheet. With No, the form asks nothing further.

### Advertising ID

```
No
```

The app doesn't use the advertising ID: it has no ads and no analytics.

### Government apps

```
No
```

### Financial features

```
My app doesn't provide any financial features
```

### Health apps

```
My app does not have any health features
```

The descriptions say what the parts of a chart mean within Human Design, without advice.

## Release

Under Test and release. The bundle is `android/build/Gates.aab`, which `android/build.sh bundle` makes, version 1.0.1, build 56.

### Play App Signing

```
Export and upload a key from Java keystore
```

Under "Use a different app signing key". Google Play signs the apps that it makes from the bundle with the key it's given here, `~/.android/gatesofhumanity.jks` (alias `gates`), which also signs the APK that uwyn.com publishes. Android only installs an app over one that the same key signed, so someone who installed that APK can update it from Google Play and keep the people saved in it.

The Play Console offers its public encryption key and PEPK, the tool that encrypts the key for it, and shows the command with the encryption key filled in. For this keystore it's `java -jar pepk.jar --keystore=$HOME/.android/gatesofhumanity.jks --alias=gates --output=gates-signing-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem`, run in the folder with the two files that the Play Console offers. The password it asks for is in `~/.android/gatesofhumanity.jks.pass`.

The zip it writes is uploaded on the same page. The bundle is signed with that key too, which makes it the upload key as well.

### Release name

Limit: 50

```
1.0.1 (56)
```

### Release notes

Limit: 500

```
<en-US>
The first version of Gates of Humanity on Google Play.
</en-US>
```

### Countries and regions

```
All countries and regions
```

## Testing before production

The account is an organization's, so the first release goes to production directly. A personal account made after November 13, 2023 has to run a closed test first: at least 12 testers who stay opted in for 14 days in a row. After that, the Play Console lets you apply for production access.

A closed test takes the same bundle. Its testers join with a link that the Play Console makes, from an email list of their Google accounts.
