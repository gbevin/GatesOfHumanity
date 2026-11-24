// The screens of the store, for android/Store/screenshots.py, which sends what to show on stdin:
// what the pages keep, and for each screen its file, its page and the script that's run there.
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { device, sleep } from '../cdp.mjs';

const [adb, raw] = process.argv.slice(2);
const { stored, shots } = JSON.parse(fs.readFileSync(0, 'utf8'));
const { page } = device(adb);

const shown = await page();
if (!await shown.settled('/wheel/birth')) throw new Error('the app did not start at the Birth Chart');
await shown.evaluate(`localStorage.setItem('hdapp', ${JSON.stringify(stored)}); localStorage.setItem('hdapp-welcomed', '1')`);
for (const [file, start, script] of shots) {
  // a chart is drawn after the page is there
  const drawn = start.startsWith('/wheel/') ? "!!document.querySelector('#svg-image svg [data-explore]')" : 'true';
  if (!await shown.go(start) || !await shown.settled(start, drawn)) throw new Error(`${start} did not show`);
  await sleep(1000);
  const said = await shown.evaluate(`(async () => { ${script} })()`);
  // what the script started, a list that opens or a page that scrolls, has to come to rest
  await sleep(2000);
  fs.writeFileSync(path.join(raw, file + '.png'), execFileSync(adb, ['exec-out', 'screencap', '-p'], { maxBuffer: 64 * 1024 * 1024 }));
  console.log(`${file} (${said})`);
}
shown.close();
