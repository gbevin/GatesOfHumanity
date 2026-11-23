// Drives the headless Chrome of Tools/browser/chrome.sh over the DevTools protocol: each page in a
// fresh browser context, with real mouse and keyboard input, answered dialogs, and the console, failed
// requests and downloads logged. GATES_URL is the site, 8080 on localhost by default, CDP_PORT the
// port of Chrome, 9588 by default. Screenshots go to build/browser.
import fs from 'node:fs';
import path from 'node:path';

export const BASE = process.env.GATES_URL || 'http://localhost:8080';
const SHOTS = path.join(import.meta.dirname, '..', '..', 'build', 'browser');
export const sleep = ms => new Promise(r => setTimeout(r, ms));
const ver = await (await fetch(`http://127.0.0.1:${process.env.CDP_PORT || 9588}/json/version`).catch(() => {
  console.error('No Chrome answers, start it with Tools/browser/chrome.sh start');
  process.exit(2);
})).json();
const ws = new WebSocket(ver.webSocketDebuggerUrl);
await new Promise(r => ws.addEventListener('open', r));
let id = 0; const pending = new Map(); const listeners = [];
ws.addEventListener('message', e => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); }
  else for (const l of listeners) l(m);
});
const rawSend = (method, params = {}, sessionId) => new Promise(r => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params, sessionId })); });

export async function newPage({ width = 1200, height = 900, mobile = false, touch = false, context = null, log = true, welcome = false } = {}) {
  const ctx = context || (await rawSend('Target.createBrowserContext', { disposeOnDetach: false })).result.browserContextId;
  const t = (await rawSend('Target.createTarget', { url: 'about:blank', browserContextId: ctx })).result.targetId;
  const sid = (await rawSend('Target.attachToTarget', { targetId: t, flatten: true })).result.sessionId;
  const send = async (method, params) => { const r = await rawSend(method, params, sid); if (r.error) console.log('CDP ERR', method, JSON.stringify(r.error)); return r.result; };
  const p = { ctx, sid, send, dialogs: [], dialogAnswers: [], requests: [], console: [], fileChoosers: [], downloads: [] };
  const requested = new Map();
  listeners.push(m => {
    if (m.sessionId !== sid && !(m.method?.startsWith('Browser.'))) return;
    const pr = m.params;
    switch (m.method) {
      case 'Runtime.consoleAPICalled': { const s = `console.${pr.type}: ${pr.args.map(a => a.value ?? a.description).join(' ')}`; p.console.push(s); if (log) console.log('  [' + s + ']'); break; }
      case 'Runtime.exceptionThrown': { const s = 'EXCEPTION ' + (pr.exceptionDetails.exception?.description || pr.exceptionDetails.text); p.console.push(s); console.log('  [' + s + ']'); break; }
      case 'Log.entryAdded': { const s = `log.${pr.entry.level}: ${pr.entry.text} ${pr.entry.url || ''}`; p.console.push(s); if (log) console.log('  [' + s + ']'); break; }
      case 'Network.requestWillBeSent': p.requests.push(pr.request.url); requested.set(pr.requestId, pr.request.url); break;
      case 'Network.responseReceived': if (pr.response.status >= 400) { console.log('  [HTTP ' + pr.response.status + ' ' + pr.response.url + ']'); } break;
      case 'Network.loadingFailed': if (!pr.canceled) console.log('  [FAILED ' + pr.errorText + ' ' + (requested.get(pr.requestId) || pr.requestId) + ']'); break;
      case 'Page.javascriptDialogOpening': {
        const ans = p.dialogAnswers.shift();
        p.dialogs.push({ type: pr.type, message: pr.message });
        if (log) console.log(`  [dialog ${pr.type}: ${pr.message}] -> ${JSON.stringify(ans)}`);
        const accept = ans === undefined ? true : ans !== false;
        send('Page.handleJavaScriptDialog', { accept, promptText: typeof ans === 'string' ? ans : '' });
        break;
      }
      case 'Page.fileChooserOpened': p.fileChoosers.push(pr); if (log) console.log('  [file chooser opened ' + JSON.stringify(pr) + ']'); break;
      case 'Browser.downloadWillBegin': p.downloads.push(pr); if (log) console.log('  [download ' + pr.suggestedFilename + ']'); break;
      case 'Browser.downloadProgress': if (pr.state === 'completed') { p.downloadDone = pr; if (log) console.log('  [download completed]'); } break;
    }
  });
  await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable'); await send('Log.enable');
  await send('Page.setInterceptFileChooserDialog', { enabled: true }); await send('Page.bringToFront'); await send('Emulation.setFocusEmulationEnabled', { enabled: true });
  await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile });
  if (touch) await send('Emulation.setTouchEmulationEnabled', { enabled: true });
  // every page here is a first visit, whose welcome would cover what's looked at
  if (!welcome) await send('Page.addScriptToEvaluateOnNewDocument', { source: "try { localStorage.setItem('hdapp-welcomed', '1') } catch (e) {}" });
  p.eval = async expr => { const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true }); if (r.exceptionDetails) return 'EVAL-ERR ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text); return r.result.value; };
  p.goto = async (url, wait = 2500) => { await send('Page.navigate', { url: url.startsWith('http') ? url : BASE + url }); await sleep(wait); };
  p.reload = async (wait = 2500) => { await send('Page.reload', {}); await sleep(wait); };
  p.click = async (sel, wait = 400) => {
    const q = sel.startsWith('js:') ? sel.slice(3) : `document.querySelector(${JSON.stringify(sel)})`;
    const where = await p.eval(`(() => { const e = ${q}; if (!e) return null; e.scrollIntoView({block: 'center'}); const r = e.getBoundingClientRect(); return [r.x + r.width / 2, r.y + r.height / 2]; })()`);
    if (!where) { console.log('  !! no element ' + sel); return false; }
    const hit = await p.eval(`(() => { const e = document.elementFromPoint(${where[0]}, ${where[1]}); const t = ${q}; return e === t || t.contains(e) ? 'ok' : 'HIT OTHER ' + (e ? e.tagName + '#' + e.id + '.' + e.className : 'nothing'); })()`);
    if (hit !== 'ok') console.log('  !! click ' + sel + ' ' + hit);
    await p.mouse(where[0], where[1]);
    await sleep(wait);
    return true;
  };
  p.mouse = async (x, y) => {
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y });
    for (const type of ['mousePressed', 'mouseReleased']) await send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 });
  };
  p.key = async (key, code, keyCode, text) => {
    await send('Input.dispatchKeyEvent', { type: text ? 'keyDown' : 'rawKeyDown', key, code, windowsVirtualKeyCode: keyCode, text });
    await send('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode: keyCode });
  };
  p.type = async (text, delay = 60) => { for (const ch of text) { await send('Input.insertText', { text: ch }); await sleep(delay); } };
  p.typeKeys = async (text, delay = 80) => {
    for (const ch of text) {
      const code = /\d/.test(ch) ? 'Digit' + ch : /[a-z]/i.test(ch) ? 'Key' + ch.toUpperCase() : '';
      await send('Input.dispatchKeyEvent', { type: 'keyDown', key: ch, code, text: ch, windowsVirtualKeyCode: ch.toUpperCase().charCodeAt(0) });
      await send('Input.dispatchKeyEvent', { type: 'keyUp', key: ch, code, windowsVirtualKeyCode: ch.toUpperCase().charCodeAt(0) });
      await sleep(delay);
    }
  };
  p.store = () => p.eval(`localStorage.getItem('hdapp')`);
  p.state = () => p.eval(`JSON.stringify({dt: document.getElementById('datetime')?.value, loc: document.getElementById('location')?.value, id: document.getElementById('location-id')?.value, dt2: document.getElementById('second-datetime')?.value, loc2: document.getElementById('second-location')?.value, id2: document.getElementById('second-location-id')?.value, src: document.getElementById('svg-image')?.dataset.src, svg: (document.querySelector('#svg-image svg') ? 'svg' : 'none')})`);
  p.shot = async name => {
    const s = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.mkdirSync(SHOTS, { recursive: true });
    const file = path.join(SHOTS, name + '.png');
    fs.writeFileSync(file, Buffer.from(s.data, 'base64'));
    return file;
  };
  p.chartReqs = () => p.requests.filter(u => /\/(wheel|range)\/(image|explore)/.test(u));
  p.close = () => rawSend('Target.closeTarget', { targetId: t });
  p.target = t;
  return p;
}
export const browserSend = rawSend;
export function done() { ws.close(); process.exit(0); }
