// Checks that a browser can install the site: that Chrome reads the manifest that a page names
// and finds nothing that keeps it from installing, and that the site in a window of its own
// scrolls inside it as an app does and leaves the welcome of a first visit out, where a tab of
// the browser has neither. Needs the site on GATES_URL and Tools/browser/chrome.sh started.
//
//   node Tools/browser/check-install.mjs
import { newPage, done } from './lib.mjs';

const wrong = [];
const seen = async page => JSON.parse(await page.eval(`JSON.stringify({
  marked: document.documentElement.classList.contains('in-app'),
  scrolls: getComputedStyle(document.body).overflowY,
  ground: getComputedStyle(document.body).backgroundImage,
  bar: document.getElementById('title-bar').getBoundingClientRect().width,
  wide: innerWidth,
  // where the bar's first part is, from the left of the column
  held: document.getElementById('menu-toggle').getBoundingClientRect().left - document.getElementById('content').getBoundingClientRect().left,
  welcomed: document.getElementById('welcome').open,
  standalone: matchMedia('(display-mode: standalone)').matches})`));

const tab = await newPage({ width: 1400, welcome: true, log: false });
await tab.goto('/wheel/birth', 1500);
const manifest = await tab.send('Page.getAppManifest');
const read = manifest.data ? JSON.parse(manifest.data) : {};
if (!manifest.url.endsWith('/site.webmanifest') || manifest.errors.length) wrong.push(`the manifest: ${manifest.url} ${JSON.stringify(manifest.errors)}`);
if (read.display !== 'standalone' || !read.icons || read.icons.length !== 3) wrong.push(`what the manifest says: ${manifest.data}`);
// a headless Chrome has no service that installs, which isn't the site's
const against = (await tab.send('Page.getInstallabilityErrors')).installabilityErrors.filter(error => error.errorId !== 'in-incognito');
if (against.length) wrong.push(`Chrome wouldn't install it: ${JSON.stringify(against)}`);
const inTab = await seen(tab);
if (inTab.standalone || inTab.marked || inTab.scrolls === 'auto' || !inTab.welcomed) wrong.push(`a tab of the browser: ${JSON.stringify(inTab)}`);

const installed = await newPage({ width: 1400, welcome: true, log: false });
// Chrome only says that it stands alone once it has installed the site, which a headless one can't: the page is told so
await installed.send('Page.addScriptToEvaluateOnNewDocument', { source: `{
  const asked = window.matchMedia.bind(window);
  window.matchMedia = query => query === '(display-mode: standalone)' ? { matches: true } : asked(query);
}` });
await installed.goto('/wheel/birth', 1500);
const inWindow = await seen(installed);
// beside its column a wide window has the ground of the page in a tab, not the two colors that an app has past its top and bottom
if (!inWindow.standalone || !inWindow.marked || inWindow.scrolls !== 'auto' || inWindow.welcomed || inWindow.ground !== inTab.ground) wrong.push(`a window of its own: ${JSON.stringify(inWindow)}`);
// its bar is as wide as the window, under the bar that the window has, and holds its parts where the column is as in a tab
if (inWindow.bar !== inWindow.wide || Math.abs(inWindow.held - inTab.held) > 0.6 || inTab.bar >= inTab.wide) wrong.push(`the bar: ${JSON.stringify(inWindow)} in a window, ${JSON.stringify(inTab)} in a tab`);
// whoever asks for the welcome still gets it there
await installed.eval(`document.getElementById('welcome-again').click()`);
if (!(await seen(installed)).welcomed) wrong.push('the welcome isn\'t shown again in a window of its own');

console.log(wrong.length ? wrong.join('\n') : `Chrome reads ${manifest.url} with ${read.icons.length} icons and has nothing against installing it; in a window of its own the site scrolls inside it and isn't welcomed, in a tab it's as it was`);
if (wrong.length) process.exit(1);
done();
