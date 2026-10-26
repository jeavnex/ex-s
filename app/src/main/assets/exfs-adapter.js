(function () {
  'use strict';
  if (window !== window.top || location.origin !== 'https://ex-fs.net') return;
  if (window.EXSApp && window.EXSApp.version === 2) {
    window.EXSBridge.postMessage(JSON.stringify({ type: 'ready' }));
    return;
  }

  var PENDING = 'exs-pending-v2';
  function emit(type, data) {
    try { window.EXSBridge.postMessage(JSON.stringify(Object.assign({ type: type }, data || {}))); } catch (_) {}
  }
  function error(message) { emit('error', { message: message || 'EX-FS не ответил.' }); }
  function text(node) { return node ? String(node.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  function absolute(value, base) { try { return new URL(value || '', base || location.href).href; } catch (_) { return ''; } }
  function cleanTitle(value) {
    return String(value || '').replace(/^\s*Смотреть\s+[«"]?/i, '').replace(/[»"]?\s+онлайн.*$/i, '').replace(/\s+смотреть\s+онлайн.*$/i, '').trim();
  }
  function docFrom(html) { return new DOMParser().parseFromString(html, 'text/html'); }
  async function fetchDoc(path) {
    var response = await fetch(absolute(path, location.origin), { credentials: 'include', redirect: 'follow' });
    if (!response.ok) throw new Error('Сайт вернул ошибку ' + response.status + '.');
    return docFrom(await response.text());
  }
  function cardFrom(anchor, base) {
    var box = anchor.closest('.MiniPostAllForm') || anchor.parentElement;
    var image = anchor.querySelector('img') || (box && box.querySelector('img'));
    var titleNode = box && box.querySelector('.MiniPostName a');
    var rawTitle = text(titleNode) || image && image.getAttribute('title') || '';
    var info = box && box.querySelector('.MiniPostInfo');
    var badge = box && box.querySelector('.customInfo span');
    return {
      url: absolute(anchor.getAttribute('href'), base),
      title: cleanTitle(rawTitle),
      poster: absolute(image && image.getAttribute('src'), base),
      meta: text(info),
      badge: text(badge)
    };
  }
  function cardsFrom(doc, base) {
    var seen = new Set();
    return Array.from(doc.querySelectorAll('a.MiniPostPoster')).map(function (a) { return cardFrom(a, base); })
      .filter(function (item) { if (!item.url || !item.title || seen.has(item.url)) return false; seen.add(item.url); return true; });
  }
  async function catalog(data) {
    try {
      var query = String(data.query || '').trim();
      var path = String(data.path || '/');
      var target = query ? '/index.php?do=search&subaction=search&story=' + encodeURIComponent(query) : path;
      var doc = await fetchDoc(target);
      var items = cardsFrom(doc, absolute(target, location.origin));
      if (query && !items.length) {
        var fallback = cardsFrom(await fetchDoc(path), absolute(path, location.origin));
        var needle = query.toLocaleLowerCase('ru');
        items = fallback.filter(function (item) { return item.title.toLocaleLowerCase('ru').includes(needle); });
      }
      emit('catalog', { items: items });
    } catch (e) { error(e.message); }
  }
  function sliceBetween(value, start, endings) {
    var index = value.indexOf(start); if (index < 0) return '';
    var result = value.slice(index + start.length);
    endings.forEach(function (ending) { var at = result.indexOf(ending); if (at >= 0) result = result.slice(0, at); });
    return result.replace(/^[:\s]+/, '').trim();
  }
  function detailFrom(doc, url) {
    var title = cleanTitle(text(doc.querySelector('h1.view-caption, h1')));
    var original = cleanTitle(text(doc.querySelector('h2')));
    var poster = doc.querySelector('#dle-content img[src*="uploads/posts"], .full-poster img, img[src*="uploads/posts"]');
    var body = text(doc.querySelector('#dle-content'));
    var description = sliceBetween(body, 'Сюжет', ['подробнее', 'Кадры из фильма', 'Информация о фильме']);
    var year = sliceBetween(body, 'Год', ['Страна']);
    var country = sliceBetween(body, 'Страна', ['Жанр']);
    var genre = sliceBetween(body, 'Жанр', ['Перевод']);
    var quality = sliceBetween(body, 'Качество', ['Время']);
    var duration = sliceBetween(body, 'Время', ['Плеер']);
    var ratings = Array.from(doc.querySelectorAll('[class*="rating"]')).map(text).filter(function (v) { return /^\d+[.,]\d+/.test(v); });
    var choices = [];
    Array.from(doc.querySelectorAll('.tab-pane')).forEach(function (panel, panelIndex) {
      var iframe = panel.querySelector('iframe'); if (!iframe) return;
      var trigger = doc.querySelector('a[href="#' + panel.id + '"]');
      var label = text(trigger) || ('Плеер ' + (choices.length + 1));
      if (/торрент/i.test(label)) return;
      choices.push({ index: panelIndex, label: label, ready: !!iframe.getAttribute('src') });
    });
    choices.sort(function (a, b) { return Number(b.ready) - Number(a.ready); });
    return {
      url: url, title: title, originalTitle: original,
      poster: absolute(poster && poster.getAttribute('src'), url),
      meta: [year, country].filter(Boolean).join(' · '),
      facts: [genre, quality, duration].filter(Boolean).join(' · '),
      description: description, rating: ratings[0] || '',
      players: choices.map(function (p) { return { index: p.index, label: p.label }; })
    };
  }
  async function detail(data) {
    try { var url = absolute(data.url); emit('detail', detailFrom(await fetchDoc(url), url)); }
    catch (e) { error(e.message); }
  }
  function removeAdvertising() {
    document.querySelectorAll('script[src*="realanduse"],script[src*="tallertorn"],script[src*="deltarockme"],script[src*="miceonme"],video[src*="realanduse"],a[href*="realanduse"],a[href*="777ua"]').forEach(function (node) { node.remove(); });
    document.querySelectorAll('body > div').forEach(function (node) {
      var value = (node.id + ' ' + node.className).toLowerCase();
      if (/popunder|advert|overlay-ad|vast|preroll/.test(value)) node.remove();
    });
  }
  function activatePlayer(panelIndex) {
    removeAdvertising();
    var panels = Array.from(document.querySelectorAll('.tab-pane'));
    var panel = panels[Number(panelIndex)] || panels.find(function (p) { return p.querySelector('iframe[src]'); }) || panels[0];
    if (!panel) { error('На странице не найден видеоплеер.'); return; }
    panels.forEach(function (p) { p.classList.remove('exs-player-active'); });
    panel.classList.add('exs-player-active');
    var style = document.getElementById('exs-player-style') || document.head.appendChild(document.createElement('style'));
    style.id = 'exs-player-style';
    style.textContent = 'html,body{margin:0!important;background:#000!important;overflow:hidden!important;width:100%!important;height:100%!important}' +
      'body>*{visibility:hidden!important}.exs-player-active,.exs-player-active *{visibility:visible!important}' +
      '.tab-pane.exs-player-active{display:block!important;position:fixed!important;inset:0!important;z-index:2147483000!important;background:#000!important}' +
      '.exs-player-active iframe{display:block!important;border:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important}' +
      '[class*="popup"],[class*="popunder"],[id*="popup"],[id*="advert"]{display:none!important}';
    var iframe = panel.querySelector('iframe');
    if (iframe) { iframe.setAttribute('allow', 'autoplay; fullscreen; encrypted-media'); iframe.setAttribute('allowfullscreen', ''); iframe.focus(); }
  }
  function play(data) {
    var url = absolute(data.url);
    if (location.href.split('#')[0] !== url.split('#')[0]) {
      sessionStorage.setItem(PENDING, JSON.stringify({ url: url, player: Number(data.player) || 0, at: Date.now() }));
      location.assign(url); return;
    }
    activatePlayer(Number(data.player) || 0);
  }
  function login() {
    document.documentElement.classList.add('exs-login');
    var style = document.getElementById('exs-login-style') || document.head.appendChild(document.createElement('style'));
    style.id = 'exs-login-style';
    style.textContent = 'html.exs-login body{background:#07111f!important}.exs-login body>*{visibility:hidden!important}' +
      '.exs-login [class*="modal"],.exs-login [class*="modal"] *,.exs-login #login-box,.exs-login #login-box *{visibility:visible!important}' +
      '.exs-login [class*="modal"]{position:fixed!important;inset:0!important;z-index:2147483000!important;background:#07111f!important;color:white!important}';
    var link = Array.from(document.querySelectorAll('a')).find(function (a) { return /авторизац|войти/i.test(text(a)); });
    if (link) link.click(); else error('Кнопка входа на сайте не найдена.');
  }
  function closeSurface() {
    document.documentElement.classList.remove('exs-login');
    document.querySelectorAll('.exs-player-active').forEach(function (p) { p.classList.remove('exs-player-active'); });
    var video = document.querySelector('video'); if (video) video.pause();
  }
  function playerKey(action) {
    var frame = document.querySelector('.exs-player-active iframe');
    if (frame) { frame.focus(); return; }
    var video = document.querySelector('video'); if (!video) return;
    if (action === 'toggle') video.paused ? video.play() : video.pause();
    if (action === 'left') video.currentTime = Math.max(0, video.currentTime - 10);
    if (action === 'right') video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 10);
  }
  function authState() {
    var authLink = Array.from(document.querySelectorAll('a')).find(function (a) { return /авторизац/i.test(text(a)); });
    var account = Array.from(document.querySelectorAll('a')).find(function (a) { return /профиль|выход|закладки/i.test(text(a)); });
    emit('auth', { loggedIn: !!account && !authLink, displayName: account ? text(account) : '' });
  }
  function command(name, data) {
    if (name === 'catalog') return catalog(data || {});
    if (name === 'detail') return detail(data || {});
    if (name === 'play') return play(data || {});
    if (name === 'login') return login();
    if (name === 'closePlayer' || name === 'closeLogin') return closeSurface();
  }
  window.EXSApp = { version: 2, command: command, playerKey: playerKey };
  removeAdvertising();
  var pending = JSON.parse(sessionStorage.getItem(PENDING) || 'null');
  if (pending && pending.url && Date.now() - pending.at < 60000 && location.href.split('#')[0] === pending.url.split('#')[0]) {
    sessionStorage.removeItem(PENDING); setTimeout(function () { activatePlayer(pending.player); }, 800);
  }
  authState();
  setInterval(removeAdvertising, 1200);
  emit('ready');
})();
