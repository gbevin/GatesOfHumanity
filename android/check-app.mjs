// What android/check-app.sh asks of the pages of the app, over the DevTools protocol that a web
// view answers in an app that was built to be looked into. CDP_PORT is where adb forwards it.
import fs from 'node:fs';
import { APP, ROOT, device, sleep } from './cdp.mjs';

const [adb, out] = process.argv.slice(2);
const { shell, page } = device(adb);
const wrong = [];
const check = (right, what) => { if (!right) wrong.push(what); };

// what goes wrong in the checks themselves is told as one more thing that's wrong
let version = [];
try {
let shown = await page();
check(await shown.settled('/wheel/birth', "typeof showPersonChart === 'function'"), 'the Birth Chart is not what the app starts with');

// what the app adds to a page
check(await shown.evaluate("document.documentElement.classList.contains('in-app')"), 'a page is not marked as one of the app');
// the first start is welcomed by the page, which says where the app keeps people and how a part is chosen
check(await shown.evaluate("in_app === 'android' && !!document.querySelector('#welcome[open]')"), 'the first start is not welcomed');
check((await shown.evaluate("document.querySelector('#welcome').textContent.replace(/\\s+/g, ' ')")).includes('Tap to explore Tap any part of a chart to read what it is.'), 'the welcome does not say to tap');
await shown.evaluate("document.querySelector('#welcome button').click()");
check(await shown.evaluate("!document.querySelector('#welcome[open]') && localStorage.getItem('hdapp-welcomed') === '1'"), 'the welcome does not close for good');
check(await shown.evaluate("getComputedStyle(document.body).overflowY === 'auto' && getComputedStyle(document.documentElement).overflowY === 'hidden'"), 'a page does not scroll inside the window');
check(await shown.evaluate("getComputedStyle(document.querySelector('#title-bar')).userSelect === 'none'"), 'the text of the title bar can be selected');

// a tap on the title of a page takes it back to its top: a finger on the screen, not a script,
// once the chart is there and the page has something to scroll
for (let waits = 0; waits < 50 && !await shown.evaluate('document.body.scrollHeight - document.body.clientHeight > 900'); waits++) {
  await sleep(200);
}
const title = await shown.evaluate(`(() => {
  document.body.scrollTop = 900;
  const at = document.getElementById('page-name').getBoundingClientRect();
  return [(at.left + at.width / 2) * devicePixelRatio, (at.top + at.height / 2) * devicePixelRatio, document.body.scrollTop];
})()`);
// a notice that Android shows over the top of the screen, as it does after it started, would take the tap
shell('cmd', 'statusbar', 'collapse');
await sleep(500);
const [across, down] = await shown.placed();
shell('input', 'tap', String(Math.round(across + title[0])), String(Math.round(down + title[1])));
let left = title[2];
for (let waits = 0; waits < 25 && left > 0; waits++) {
  await sleep(200);
  left = await shown.evaluate('document.body.scrollTop');
}
check(title[2] > 800 && left === 0, `scrolled down to ${title[2]}, a tap on the title of the page left it at ${left}`);

// the About page says which version and build of the app this is, and what draws its widgets
version = shell('dumpsys', 'package', APP).match(/versionCode=(\d+)[\s\S]*?versionName=(\S+)/);
await shown.go('/about');
const about = await shown.evaluate('document.body.innerText');
check(about.includes(`Version ${version[2]}, build ${version[1]}`), `the About page does not say version ${version[2]}, build ${version[1]}`);
check(about.includes('AndroidSVG') && !about.includes('SwiftDraw'), 'the About page does not credit what draws the widgets of this app');

// who the pages save is kept for the widgets, with the moment of their birth in UTC
await shown.go('/wheel/birth');
await shown.evaluate(`HdStore.update(browserStorage(), data => {
  data.charts['Nora'] = {datetime: '1988-03-14T07:25', locationId: '2800866', locationCity: 'Brussels, Brussels Capital, Belgium'};
  data.charts['Zoë + Co'] = {datetime: '1975-06-21T14:30', locationId: '2643743', locationCity: 'London, England, United Kingdom'}; })`);
let kept = '';
for (let waits = 0; waits < 50 && !kept.includes('Nora'); waits++) {
  await sleep(200);
  try { kept = shell('run-as', APP, 'cat', 'shared_prefs/people.xml'); } catch (e) {}
}
check(kept.includes('198803140625') && kept.includes('197506211330'), `the people kept for the widgets are ${kept}`);

// what a widget shows of a drawing of the site, in each size
for (const [path, width, height] of [
  ['/widget/transits/systemSmall/202611151700', 480, 480], ['/widget/transits/systemMedium/202611151700', 1000, 440],
  ['/widget/bodygraph/systemSmall/202611151700', 480, 480], ['/widget/bodygraph/systemMedium/202611151700', 1000, 440], ['/widget/bodygraph/systemLarge/202611151700', 1000, 1040],
  ['/widget/person/198803140625/systemSmall/202611151700?name=Nora', 480, 520], ['/widget/person/198803140625/systemMedium/202611151700?name=Nora', 1000, 440],
  ['/widget/person/198803140625/systemLarge/202611151700?name=Zo%C3%AB%20%2B%20Co', 1000, 1040]]) {
  const picture = await shown.evaluate(`HdProbe.widget(${JSON.stringify(path)}, ${width}, ${height})`);
  const png = Buffer.from(picture.startsWith('failed') ? '' : picture, 'base64');
  // a picture with a drawing in it doesn't pack as small as an empty one
  check(png.length > 8000 && png.readUInt32BE(16) === width && png.readUInt32BE(20) === height, `the picture of ${path} is ${picture.startsWith('failed') ? picture : png.length + ' bytes'}`);
  fs.writeFileSync(`${out}/${path.slice(8).replace(/[/?=%]/g, '_')}.png`, png);
}

// A tap on a widget of a person opens their chart with the transits. It says what only the
// app knows, so that no other app can have an address opened, and no address gets in that
// isn't of the site.
const tap = (address, said) => shell('am', 'start', '-n', `${APP}/.MainActivity`, '-a', 'android.intent.action.VIEW', '-d', `'${address}'`, '--es', 'token', `'${said}'`);
tap(`${ROOT}/about`, 'guessed');
await sleep(1500);
check(await shown.evaluate('location.pathname') === '/wheel/birth', 'an address was opened for something that is not a widget of the app');
const token = shell('run-as', APP, 'cat', 'shared_prefs/widgets.xml').match(/name="token">([^<]+)</)?.[1];
tap(`${ROOT}/wheel/birth-transit?person=Nora`, token);
check(await shown.settled('/wheel/birth-transit', "HdStore.load(browserStorage()).named?.name === 'Nora'"), 'a tap on the widget of a person does not open their chart with the transits');
tap(`${ROOT}/range`, token);
check(await shown.settled('/range'), 'a tap on a widget does not open its page');
tap('https://example.invalid/elsewhere', token);
tap(`http://${ROOT.slice(8)}/about`, token);
await sleep(1500);
check(await shown.evaluate('location.href') === `${ROOT}/range`, 'an address that is not of the site was opened');

// the same tap when the app doesn't run, for someone whose chart wasn't the last that it showed
shown.close();
shell('am', 'force-stop', APP);
tap(`${ROOT}/wheel/birth-transit?person=Zo%C3%AB%20%2B%20Co`, token);
shown = await page();
check(await shown.settled('/wheel/birth-transit', "HdStore.load(browserStorage()).named?.name === 'Zoë + Co'"), 'a tap on a widget while the app does not run opens the chart of someone else');
await shown.go('/range');

// when Android stops what draws the pages, the app goes on with the page that it showed
await shown.evaluate("history.pushState(null, '', '/range?stepped=1')");
shown.send('Page.crash');
const before = shown;
shown = await page(before.id);
before.close();
check(shown.pid === before.pid, 'the app was stopped along with what draws its pages');
check(await shown.settled('/range', "document.documentElement.classList.contains('in-app')"), 'the page is not shown again after what draws it was stopped');
shown.close();
} catch (e) {
  wrong.push(`the checks stopped: ${e.message.split('\n')[0]}`);
}

if (wrong.length) {
  console.error('The app does not do what it should around its pages: ' + wrong.join('; '));
  process.exit(1);
}
console.log(`The pages of the app are marked, go back up from their title, say version ${version[2]} build ${version[1]}, keep people for the widgets, draw 8 widgets, open what a widget is tapped for, and come back after what draws them stops`);
