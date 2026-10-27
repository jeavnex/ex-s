// Regression checks for the original iframe lifecycle; no network/media required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/exfs-adapter.js', 'utf8');
const events = [], timers = [], messages = [];
class Element extends EventTarget {
  constructor(tag) {
    super(); this.tagName = tag; this.attrs = {}; this.children = []; this.style = {};
    this.dataset = {}; this.classes = new Set();
    this.classList = {add: c => this.classes.add(c), remove: c => this.classes.delete(c)};
  }
  setAttribute(k, v) { this.attrs[k] = v; }
  getAttribute(k) { return this.attrs[k] || null; }
  appendChild(node) { assert.ok(!node.parentElement, 'Never relocate the original iframe'); this.children.push(node); node.parentElement = this; return node; }
  remove() { if (this.parentElement) this.parentElement.children = this.parentElement.children.filter(n => n !== this); this.parentElement = null; }
  focus() {}
}
function documentFixture() {
  const doc = new EventTarget(); doc.head = new Element('head'); doc.body = new Element('body');
  doc.documentElement = new Element('html'); doc.createElement = tag => new Element(tag);
  doc.getElementById = id => [...doc.head.children, ...doc.body.children].find(n => n.id === id) || null;
  doc.querySelector = () => null; doc.querySelectorAll = () => [];
  return doc;
}
const document = documentFixture(), inner = documentFixture();
const root = inner.body.appendChild(new Element('div'));
const video = root.appendChild(new Element('video')); video.pause = () => {};
inner.querySelectorAll = selector => selector === 'video' ? [video] : [];
inner.querySelector = selector => selector === 'video' ? video : null;
inner.documentElement.innerHTML = '<video></video>';
const panel = document.body.appendChild(new Element('section'));
const frame = panel.appendChild(new Element('iframe'));
frame.contentDocument = inner;
frame.contentWindow = {postMessage: m => messages.push(m), dispatchEvent() {}};
let requests = 0;
frame.requestFullscreen = function () { assert.equal(this, frame); requests++; document.fullscreenElement = frame; document.dispatchEvent(new Event('fullscreenchange')); return Promise.resolve(); };
panel.querySelector = () => frame;
document.querySelectorAll = s => s === '.tab-pane' ? [panel] : s === 'iframe.exs-frame-active' ? [frame] : [];
const context = {document, location: new URL('https://ex-fs.net/film/test.html'), URL, Event,
  innerWidth: 900, innerHeight: 450, addEventListener() {}, removeEventListener() {},
  sessionStorage: {getItem() { return null; }, removeItem() {}, setItem() {}},
  EXSBridge: {postMessage: m => events.push(JSON.parse(m))},
  setTimeout: fn => timers.push(fn), setInterval() {}};
context.window = context; context.top = context;
vm.runInNewContext(source, context);
context.EXSApp.command('play', {url: context.location.href, player: 0});
timers.splice(0).forEach(fn => fn());
assert.equal(frame.parentElement, panel, 'src-less iframe stays alive');
assert.equal(frame.contentDocument, inner);
assert.equal(events.filter(e => e.type === 'fullscreenRequest').length, 1);
document.getElementById('exs-enter-fullscreen').onclick();
assert.equal(requests, 1, 'Fullscreen uses iframe, not a replacement video element');
assert.equal(document.getElementById('exs-enter-fullscreen').hidden, true);
assert.equal(messages.at(-1).enter, true);
assert.ok(root.classes.has('exs-video-parent'), 'Video ancestors follow viewport height');
assert.match(inner.getElementById('exs-original-fit').textContent, /object-fit:contain/);
context.EXSApp.command('resizePlayer');
context.EXSApp.command('closePlayer');
assert.equal(document.getElementById('exs-enter-fullscreen'), null);
assert.equal(frame.parentElement, panel);
assert.equal(context.EXSFitPlayer, null);
console.log('Original player fullscreen, resize and iframe preservation: OK');
