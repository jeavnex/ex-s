(function () {
  'use strict';
  if (window !== window.top || location.origin !== 'https://ex-fs.net') return;
  if (window.EXSApp && window.EXSApp.version === 2) {
    window.EXSBridge.postMessage(JSON.stringify({ type: 'ready' }));
    return;
  }

  var PENDING = 'exs-pending-v2';
  var TRANSPARENT_POSTER = 'data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=';
  function emit(type, data) {
    try { window.EXSBridge.postMessage(JSON.stringify(Object.assign({ type: type }, data || {}))); } catch (_) {}
  }
  function error(message) { emit('error', { message: message || 'EX-FS не ответил.' }); }
  function text(node) { return node ? String(node.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
  function absolute(value, base) { if (!value) return ''; try { return new URL(value, base || location.href).href; } catch (_) { return ''; } }
  function posterUrl(value, base) {
    try {
      var url = new URL(value || '', base || location.href);
      if (url.pathname.endsWith('/thumbs.php') && url.searchParams.get('src')) {
        return new URL(url.searchParams.get('src'), location.origin).href;
      }
      return url.href;
    } catch (_) { return ''; }
  }
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
      poster: posterUrl(image && image.getAttribute('src'), base),
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
  function infoValue(doc, label) {
    var title = Array.from(doc.querySelectorAll('.FullstoryInfoTitle')).find(function (node) {
      return text(node).replace(/:$/, '').toLocaleLowerCase('ru') === String(label).toLocaleLowerCase('ru');
    });
    return text(title && title.nextElementSibling);
  }
  function detailFrom(doc, url) {
    var title = cleanTitle(text(doc.querySelector('h1.view-caption, h1')));
    var original = cleanTitle(text(doc.querySelector('h2')));
    var poster = doc.querySelector('#dle-content img[src*="uploads/posts"], .full-poster img, img[src*="uploads/posts"]');
    var description = text(doc.querySelector('.FullstorySubFormText'));
    var year = infoValue(doc, 'Год');
    var country = infoValue(doc, 'Страна');
    var genre = infoValue(doc, 'Жанр');
    var translation = infoValue(doc, 'Перевод');
    var quality = infoValue(doc, 'Качество');
    var duration = infoValue(doc, 'Время');
    var age = infoValue(doc, 'Возраст');
    var ratings = Array.from(doc.querySelectorAll('.in_name_kp, .in_name_imdb')).map(text).filter(function (v) { return /^\d+[.,]\d+/.test(v); });
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
      poster: posterUrl(poster && poster.getAttribute('src'), url),
      meta: [year, country].filter(Boolean).join(' · '),
      facts: [genre, translation, quality, duration, age && ('Возраст ' + age)].filter(Boolean).join(' · '),
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
    var trigger = panel.id && document.querySelector('a[href="#' + panel.id + '"]');
    if (trigger) trigger.click();
    document.querySelectorAll('iframe.exs-frame-active').forEach(function (frame) { frame.classList.remove('exs-frame-active'); });
    panels.forEach(function (p) { p.classList.remove('exs-player-active'); });
    panel.classList.add('exs-player-active');
    var viewport = document.querySelector('meta[name="viewport"]') || document.head.appendChild(document.createElement('meta'));
    viewport.name = 'viewport';
    viewport.content = 'width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover';
    var style = document.getElementById('exs-player-style') || document.head.appendChild(document.createElement('style'));
    style.id = 'exs-player-style';
    style.textContent = 'html,body{margin:0!important;background:#000!important;overflow:hidden!important;width:100%!important;height:100%!important}' +
      'body>*{visibility:hidden!important}iframe.exs-frame-active{visibility:visible!important}' +
      'iframe.exs-frame-active{display:block!important;position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;border:0!important;margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;transform:none!important;z-index:2147483000!important;background:#000!important}' +
      '[class*="popup"],[class*="popunder"],[id*="popup"],[id*="advert"]{display:none!important}';
    var iframe = panel.querySelector('iframe');
    if (!iframe) { error('В выбранной вкладке нет видеоплеера.'); return; }
    var lazySource = iframe.getAttribute('data-src') || iframe.getAttribute('data-lazy-src');
    if (!iframe.getAttribute('src') && lazySource) iframe.setAttribute('src', lazySource);
    iframe.setAttribute('allow', 'autoplay; fullscreen; encrypted-media; picture-in-picture');
    iframe.setAttribute('allowfullscreen', '');
    iframe.setAttribute('data-exs-parent', panel.id || '');
    iframe.classList.add('exs-frame-active');
    document.body.appendChild(iframe);
    iframe.addEventListener('load', function () {
      emit('diagnostic', { message: 'iframe загружен: ' + absolute(iframe.getAttribute('src')) });
    }, { once: true });
    var provider = absolute(iframe.getAttribute('src')) || 'встроенный iframe без src';
    emit('diagnostic', { message: 'Открыт ' + (text(trigger) || ('плеер ' + panelIndex)) + ': ' + provider });
    iframe.focus();
    function inspectInnerVideo() {
      try {
        var inner = iframe.contentDocument;
        var videos = inner ? Array.from(inner.querySelectorAll('video')) : [];
        videos.forEach(function (video) {
          video.poster = TRANSPARENT_POSTER;
          if (video.dataset.exsObserved) return;
          video.dataset.exsObserved = '1';
          ['loadedmetadata', 'canplay', 'playing', 'waiting', 'stalled', 'error'].forEach(function (eventName) {
            video.addEventListener(eventName, function () {
              var mediaError = video.error ? (' code=' + video.error.code + ' ' + (video.error.message || '')) : '';
              emit('diagnostic', { message: 'video ' + eventName + ': ready=' + video.readyState + ', network=' + video.networkState + mediaError });
            });
          });
        });
        emit('diagnostic', { message: 'Внутри iframe: HTML=' + (inner && inner.documentElement ? inner.documentElement.innerHTML.length : 0) + ', video=' + videos.length });
      } catch (_) { emit('diagnostic', { message: 'Содержимое iframe защищено другим доменом' }); }
    }
    setTimeout(function () {
      inspectInnerVideo();
      emit('playerReady', { provider: absolute(iframe.getAttribute('src')) || 'embedded-frame' });
    }, 500);
    setTimeout(inspectInnerVideo, 1600);
    setTimeout(inspectInnerVideo, 3500);
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
    document.querySelectorAll('iframe.exs-frame-active').forEach(function (frame) {
      try { var video = frame.contentDocument && frame.contentDocument.querySelector('video'); if (video) video.pause(); } catch (_) {}
      frame.classList.remove('exs-frame-active');
      var parent = frame.getAttribute('data-exs-parent') && document.getElementById(frame.getAttribute('data-exs-parent'));
      if (parent) parent.appendChild(frame);
    });
    var video = document.querySelector('video'); if (video) video.pause();
  }
  function playerKey(action) {
    var frame = document.querySelector('iframe.exs-frame-active');
    var video = null;
    if (frame) {
      try { video = frame.contentDocument && frame.contentDocument.querySelector('video'); } catch (_) {}
      if (!video) { frame.focus(); return; }
    }
    video = video || document.querySelector('video'); if (!video) return;
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
