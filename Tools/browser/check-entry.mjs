// Checks that the entries of the chart pages, the rows with the dates and the cities, have the
// same sizes on every page and at every width: the first row is the same wherever it has the same
// parts, a field is as high as the button beside it, and the rows of the people below it take
// their sizes from the first one. Needs the site on GATES_URL and Tools/browser/chrome.sh started.
//
//   node Tools/browser/check-entry.mjs [width ...]
import { newPage, done } from './lib.mjs';

const widths = process.argv.slice(2).map(Number);
const PAGES = ['/wheel/birth', '/wheel/connection', '/wheel', '/wheel/birth-transit', '/wheel/connection-transit', '/range', '/range/d'];
const wrong = [];
const near = (one, other) => Math.abs(one - other) < 0.6;

for (const width of widths.length ? widths : [320, 360, 402, 440, 600, 640, 720, 768, 820, 1024, 1280, 1440]) {
  const page = await newPage({ width, height: 800, mobile: width < 700, touch: width < 700, log: false });
  const found = {};
  for (const path of PAGES) {
    await page.goto(path, 700);
    found[path] = JSON.parse(await page.eval(`JSON.stringify((() => {
      const box = element => { const at = element.getBoundingClientRect(); return {x: at.x, y: at.y, w: at.width, h: at.height, right: at.right}; };
      const parts = {};
      for (const element of document.querySelectorAll('#controls form input:not([type=hidden]), #controls form button')) {
        if (element.offsetParent) parts[element.id || element.textContent.trim()] = box(element);
      }
      return {parts, first: box(document.querySelector('.date-place')), rows: [...document.querySelectorAll('.second-date-place')].map(box)};
    })())`));
  }
  await page.close();
  const check = (right, what) => { if (!right) wrong.push(`at ${width}: ${what}`); };
  const same = (one, other, sides, what) => {
    for (const side of sides) check(near(one[side], other[side]), `${what} differs in ${side}, ${one[side]} and ${other[side]}`);
  };
  const birth = found['/wheel/birth'];
  for (const path of PAGES) {
    const here = found[path].parts;
    // the first row with the same parts is the same row
    for (const part of ['datetime', 'location', 'action-show', 'day-min', 'minute-plus']) {
      const whole = path !== '/wheel/birth-transit' && path !== '/wheel/connection-transit';
      // where Load and Save are in other rows, the date is alone on its line on a phone and the city is wider
      const sides = whole ? ['x', 'y', 'w', 'h'] : part === 'location' ? ['x', 'h'] : part === 'action-show' ? ['w', 'h'] : width > 600 || width <= 427 || part !== 'datetime' ? ['x', 'w', 'h'] : ['x', 'h'];
      same(here[part], birth.parts[part], sides, `${part} of ${path} and of the Birth Chart`);
    }
    // and as wide, whatever is in it
    same(found[path].first, birth.first, ['x', 'w'], `the first row of ${path} and of the Birth Chart`);
    // a field is as high as a button
    for (const [name, part] of Object.entries(here)) {
      check(near(part.h, here['action-show'].h), `${name} of ${path} is ${part.h} high, Show is ${here['action-show'].h}`);
    }
    // the rows of the people take their sizes from the first row
    for (const number of ['second', 'third']) {
      if (here[number + '-datetime']) {
        if (width > 600) same(here[number + '-datetime'], here.datetime, ['w'], `the date of the ${number} row and of the first row of ${path}`);
        const load = here['action-load-' + number] || here['action-load'];
        same(load, here['action-show'], ['w'], `Load of the ${number} row and Show of ${path}`);
      }
    }
    if (width > 600) {
      for (const row of found[path].rows) {
        check(near(row.h, found[path].first.h), `a row of a person of ${path} is ${row.h} high, the first row ${found[path].first.h}`);
      }
    }
  }
}

if (wrong.length) {
  console.error(`The entries differ between the pages:\n  ${wrong.join('\n  ')}`);
  process.exit(1);
}
console.log('The entries have the same sizes on every chart page at every width');
done();
