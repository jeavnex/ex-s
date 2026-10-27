const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {parseHTML, DOMParser} = require('linkedom');
const source = fs.readFileSync('app/src/main/assets/exfs-adapter.js', 'utf8');
function fixture(html, options = {}) {
  const {document} = parseHTML(html);
  Object.defineProperty(document, 'activeElement', {value: null, writable: true});
  document.querySelectorAll('input,button,a').forEach(node => {
    node.focus = () => { document.activeElement = node; };
    node.getBoundingClientRect = () => ({height: 44, width: 150});
  });
  const events = [], store = {...options.storage};
  const context = {document, DOMParser, URL, Event, location: new URL(options.url || 'https://ex-fs.net/'), dle_group: options.group || 5,
    sessionStorage: {getItem: k => store[k], setItem: (k,v) => {store[k] = v;}, removeItem: k => {delete store[k];}},
    EXSBridge: {postMessage: value => events.push(JSON.parse(value))},
    addEventListener() {}, removeEventListener() {}, setTimeout() {}, setInterval() {},
    fetch: async url => ({ok: true, text: async () => options.responses[url] || ''})};
  context.window = context; context.top = context;
  vm.runInNewContext(source, context);
  return {document, context, events, store};
}
const guest = '<html><head></head><body><div id="video-error" class="reveal-modal">Описание ошибки в видео</div><section><div id="PopUpLogin" class="reveal-modal"><form action="" method="post"><input name="login_name"><input name="login_password" type="password"><button type="submit">Войти</button></form><a href="/index.php?do=register">Регистрация</a></div></section></body></html>';
const auth = fixture(guest);
auth.context.EXSApp.command('login');
assert.equal(auth.document.querySelector('.exs-auth-root').id, 'PopUpLogin');
assert.equal(auth.document.querySelector('#video-error').classList.contains('exs-auth-root'), false);
assert.equal(auth.document.querySelector('form').getAttribute('method'), 'post');
assert.equal(auth.document.activeElement.name, 'login_name');
auth.context.EXSApp.command('loginKey', {action: 'next'});
assert.equal(auth.document.activeElement.name, 'login_password');
auth.context.EXSApp.command('loginKey', {action: 'activate'});
assert.ok(auth.events.some(e => e.type === 'authInput'));
auth.context.EXSApp.command('closeLogin');
assert.equal(auth.store['exs-auth-mode'], undefined);
assert.equal(auth.document.getElementById('exs-login-style'), null);
const account = fixture('<html><head></head><body><div class="loginbtn"><a>Tester</a></div></body></html>', {group: 4});
assert.equal(account.events.find(e => e.type === 'auth').loggedIn, true);
const registration = fixture('<html><head></head><body><div id="dle-content"><form id="registration"><input name="email"></form></div></body></html>', {url:'https://ex-fs.net/index.php?do=register', storage:{'exs-auth-mode':'1'}});
assert.equal(registration.document.querySelector('.exs-auth-root').id, 'dle-content');
const searchUrl = 'https://ex-fs.net/index.php?do=search&subaction=search&story=' + encodeURIComponent('Мстители');
const responses = {
  [searchUrl]: '<html><body><div class="srx-item"><div class="srx-title"><a href="/film/1-avengers.html">Мстители</a></div><img src="/uploads/poster.jpg"><div class="srx-orig">Avengers</div></div><span class="nav-next"><a href="#" onclick="list_submit(2)">Вперед</a></span></body></html>',
  'https://ex-fs.net/film/': '<html><body><div class="MiniPostAllForm"><a class="MiniPostPoster" href="/film/2.html"><img src="/poster.jpg" title="Фильм"></a></div><div class="nav-next"><a href="/film/page/2/">Вперед</a></div></body></html>',
  'https://ex-fs.net/film/page/2/': '<html><body><div class="MiniPostAllForm"><a class="MiniPostPoster" href="/film/3.html"><img src="/third.jpg" title="Третий"></a></div></body></html>'
};
(async () => {
  const f = fixture(guest, {responses});
  await f.context.EXSApp.command('catalog', {query:'Мстители', path:'/film/'});
  let catalog = f.events.filter(e => e.type === 'catalog').at(-1);
  assert.equal(catalog.items[0].title, 'Мстители');
  assert.equal(catalog.items[0].url, 'https://ex-fs.net/film/1-avengers.html');
  assert.equal(new URL(catalog.nextPage).searchParams.get('search_start'), '2');
  await f.context.EXSApp.command('catalog', {path:'/film/'});
  catalog = f.events.filter(e => e.type === 'catalog').at(-1);
  assert.equal(catalog.nextPage, 'https://ex-fs.net/film/page/2/');
  await f.context.EXSApp.command('catalog', {path:'/film/', pageUrl:catalog.nextPage});
  catalog = f.events.filter(e => e.type === 'catalog').at(-1);
  assert.equal(catalog.append, true);
  assert.equal(catalog.items[0].title, 'Третий');
  assert.equal(catalog.nextPage, '');
  await f.context.EXSApp.command('catalog', {pageUrl:'https://example.com/private'});
  assert.ok(f.events.some(e => e.type === 'error'));
  console.log('Search markup, pagination, login isolation, TV fields and auth restoration: OK');
})().catch(error => {console.error(error); process.exitCode = 1;});
