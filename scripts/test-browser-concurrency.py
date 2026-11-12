#!/usr/bin/env python3
"""Exercise the production Web build in two same-origin Chromium contexts.

Run after `npm run build:gh`. Uses only the Python standard library and an
installed Chromium/Chrome binary; no user profile or playlist is accessed.
"""
import functools
import http.server
import os
import re
import shutil
import subprocess
import tempfile
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'web/vizhi-tivi/dist/vizhi-tivi/browser'
FINISHED = threading.Event()
HTML = r'''<!doctype html><html><body><pre id="result">RUNNING</pre><img src="/hold" hidden><script>
const result = document.querySelector('#result');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(predicate, message) {
  for (let i = 0; i < 150; i++) { if (predicate()) return; await sleep(100); }
  throw Error(message);
}
function button(doc, label) {
  return [...doc.querySelectorAll('nav button')].find(b => b.textContent.trim() === label);
}
async function context() {
  const frame = document.createElement('iframe');
  frame.src = '/vizhi-tivi/'; document.body.append(frame);
  await until(() => frame.contentDocument && button(frame.contentDocument, 'Settings'), 'application did not start');
  button(frame.contentDocument, 'Settings').click();
  await until(() => frame.contentDocument.querySelectorAll('.settings select').length === 2, 'settings unavailable');
  return frame;
}
function state() { const raw = localStorage.getItem('vizhi-tivi.device.v1'); return raw && JSON.parse(raw); }
function theme(frame) {
  const select = frame.contentDocument.querySelectorAll('.settings select')[1];
  select.value = 'dark'; select.dispatchEvent(new Event('change', { bubbles: true }));
}
function numbering(frame) { frame.contentDocument.querySelector('.settings input[type=checkbox]').click(); }
async function main() {
  localStorage.clear();
  if (!navigator.locks) throw Error('Web Locks unavailable');
  const a = await context(), b = await context();
  theme(a); numbering(b);
  try { await until(() => state() && (state().theme === 'dark' || state().localNumbers), 'first write did not complete'); }
  catch (e) {
    const alerts = [a, b].map(f => [...f.contentDocument.querySelectorAll('[role=alert]')].map(x => x.textContent.trim()).join('|'));
    throw Error(e.message + '; alerts=' + JSON.stringify(alerts) + '; frameLocks=' + !!a.contentWindow.navigator.locks);
  }
  await sleep(500);
  let saved = state();
  if (!(saved.theme === 'dark' && saved.localNumbers)) {
    const notices = [a, b].map(f => f.contentDocument.body.textContent);
    if (!notices.some(s => s.includes('Private data changed in another tab'))) throw Error('stale operation had no visible conflict');
    if (saved.theme !== 'dark') theme(a);
    if (!saved.localNumbers) numbering(b);
    await until(() => state()?.theme === 'dark' && state()?.localNumbers, 'retry did not retain both updates');
  }
  // Hold the same lock in one context, queue a real app write in the other,
  // then destroy the holder: Web Locks must release it on context termination.
  let held = false;
  void a.contentWindow.navigator.locks.request('vizhi-tivi.private-storage.v1', () => {
    held = true; return new Promise(() => {});
  });
  await until(() => held, 'lock was not acquired');
  const select = b.contentDocument.querySelectorAll('.settings select')[1];
  select.value = 'light'; select.dispatchEvent(new Event('change', { bubbles: true }));
  a.remove();
  await until(() => state()?.theme === 'light', 'lock not released after context termination');
  const c = await context();
  if (c.contentDocument.querySelectorAll('.settings select')[1].value !== 'light' ||
      !c.contentDocument.querySelector('.settings input[type=checkbox]').checked) throw Error('new context restored stale state');
  result.textContent = 'PASS: concurrent writes, conflict/retry, lock-holder termination and new-context restore';
}
main().catch(e => { result.textContent = 'FAIL: ' + e.message; }).finally(() => fetch('/done'));
</script></body></html>'''


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/hold':
            FINISHED.wait(30)
            self.send_response(204); self.end_headers()
            return
        if self.path == '/done':
            FINISHED.set()
            self.send_response(204); self.end_headers()
            return
        if self.path == '/concurrency-test':
            body = HTML.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if self.path.startswith('/vizhi-tivi/'):
            self.path = self.path[len('/vizhi-tivi'):]
        return super().do_GET()

    def log_message(self, *_args):
        pass


def main():
    if not (BUILD / 'index.html').is_file():
        raise SystemExit('Build the Angular production app first (npm run build:gh).')
    candidates = [os.environ.get('CHROME'), shutil.which('chromium'), shutil.which('google-chrome'),
                  r'C:\Program Files\Google\Chrome\Application\chrome.exe']
    chrome = next((str(p) for p in candidates if p and Path(p).is_file()), None)
    if not chrome:
        raise SystemExit('Chromium/Chrome not found; set CHROME to its executable.')
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Handler, directory=str(BUILD)))
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    try:
        with tempfile.TemporaryDirectory(prefix='vizhi-browser-') as profile:
            command = [chrome, '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
                       '--disable-background-networking', '--dump-dom',
                       '--user-data-dir=' + profile, f'http://127.0.0.1:{server.server_port}/concurrency-test']
            completed = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=90)
            # The page source itself contains the success text; inspect the
            # rendered result element rather than matching script contents.
            if completed.returncode or '<pre id="result">PASS: concurrent writes' not in completed.stdout:
                match = re.search(r'<pre id="result">([^<]*)</pre>', completed.stdout)
                print('Rendered result:', match.group(1) if match else 'missing')
                print(completed.stdout[-3000:]); print(completed.stderr[-2000:])
                raise SystemExit('Browser concurrency regression failed.')
            print('PASS: actual Chromium same-origin concurrent writes, conflict/retry, lock-holder termination and restart')
    finally:
        server.shutdown(); server.server_close(); worker.join()


if __name__ == '__main__':
    main()
