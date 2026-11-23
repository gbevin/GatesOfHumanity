// Screenshots a page of the site at desktop and phone width into build/browser, after the given
// wait in milliseconds for the chart and its Explore panel.
//
//   node Tools/browser/shot.mjs /wheel/birth birth [2500]
import { newPage, done } from './lib.mjs';

const [route, name, wait = '2500'] = process.argv.slice(2);
if (!route || !name) {
  console.error('Usage: node Tools/browser/shot.mjs <path> <name> [wait]');
  process.exit(2);
}
for (const [suffix, size] of [['desktop', { width: 1200, height: 1000 }], ['phone', { width: 390, height: 844, mobile: true, touch: true }]]) {
  const page = await newPage(size);
  await page.goto(route, Number(wait));
  console.log(await page.shot(`${name}-${suffix}`));
}
done();
