// The pages of the app over the DevTools protocol that a web view answers in an app that was built
// to be looked into, for android/check-app.mjs and android/Store/screenshots.mjs. CDP_PORT is
// where adb forwards it.
import { execFileSync } from 'node:child_process';

export const ROOT = 'https://app.gatesofhumanity.invalid';
export const APP = 'com.uwyn.gatesofhumanity';
export const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

export function device(adb) {
  const shell = (...args) => execFileSync(adb, ['shell', ...args], { encoding: 'utf8' });

  // The page that the web view shows, which is another one after what draws it was stopped.
  async function page(other = '') {
    for (let tries = 0; tries < 60; tries++) {
      try {
        // fails until the app runs
        const pid = shell('pidof', APP).trim();
        execFileSync(adb, ['forward', `tcp:${process.env.CDP_PORT}`, `localabstract:webview_devtools_remote_${pid}`]);
        const pages = await (await fetch(`http://127.0.0.1:${process.env.CDP_PORT}/json`)).json();
        const shown = pages.find(each => each.type === 'page' && each.url.startsWith(ROOT) && each.id !== other);
        if (shown) {
          const socket = new WebSocket(shown.webSocketDebuggerUrl);
          await new Promise((resolve, reject) => { socket.addEventListener('open', resolve); socket.addEventListener('error', reject); });
          let id = 0;
          const pending = new Map();
          socket.addEventListener('message', event => {
            const message = JSON.parse(event.data);
            pending.get(message.id)?.(message);
            pending.delete(message.id);
          });
          const send = (method, params = {}) => new Promise(resolve => { pending.set(++id, resolve); socket.send(JSON.stringify({ id, method, params })); });
          const evaluate = async expression => {
            const answer = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
            if (answer.result?.exceptionDetails) throw new Error(answer.result.exceptionDetails.exception?.description || answer.result.exceptionDetails.text);
            return answer.result?.result?.value;
          };
          // until the page is there, with the scripts of the site
          const settled = async (path, has = 'true') => {
            for (let waits = 0; waits < 100; waits++) {
              try {
                if (await evaluate(`document.readyState === 'complete' && location.pathname === ${JSON.stringify(path)} && (${has})`)) return true;
              } catch (e) {}
              await sleep(200);
            }
            return false;
          };
          // where the web view is on the screen now, in its pixels: it's only laid out a while after it's made
          const placed = async () => {
            const now = (await (await fetch(`http://127.0.0.1:${process.env.CDP_PORT}/json`)).json()).find(each => each.id === shown.id);
            const at = JSON.parse((now || shown).description);
            return [at.screenX, at.screenY];
          };
          return { pid, id: shown.id, placed, send, evaluate, settled, close: () => socket.close(), go: async path => { await evaluate(`location.href = ${JSON.stringify(ROOT + path)}`); return settled(path.split('?')[0]); } };
        }
      } catch (e) {}
      await sleep(500);
    }
    throw new Error('The app shows no page to look into');
  }

  return { shell, page };
}
