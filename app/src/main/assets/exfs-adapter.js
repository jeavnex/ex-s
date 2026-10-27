(function () {
  'use strict';
  if (window !== window.top || location.origin !== 'https://ex-fs.net') return;
  if (window.EXSApp && window.EXSApp.version === 2) {
    window.EXSBridge.postMessage(JSON.stringify({ type: 'ready' }));
    return;
  }

  var PENDING = 'exs-pending-v2';
  var TRANSPARENT_POSTER = 'data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs=';
  var cancelAutostart = null;
  var remoteControl = null;
  var catalogRequest = 0;
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
    var searchCards = Array.from(doc.querySelectorAll('.srx-item')).map(function (box) {
      var anchor = box.querySelector('.srx-title a');
      var image = box.querySelector('img');
      return { url: absolute(anchor && anchor.getAttribute('href'), base), title: cleanTitle(text(anchor)),
        poster: posterUrl(image && image.getAttribute('src'), base), meta: text(box.querySelector('.srx-orig, .srx-meta')), badge: '' };
    });
    return searchCards.concat(Array.from(doc.querySelectorAll('a.MiniPostPoster')).map(function (a) { return cardFrom(a, base); }))
      .filter(function (item) { if (!item.url || !item.title || seen.has(item.url)) return false; seen.add(item.url); return true; });
  }
  async function catalog(data) {
    var request = ++catalogRequest;
    try {
      var query = String(data.query || '').trim();
      var path = String(data.path || '/');
      var target = data.pageUrl || (query ? '/index.php?do=search&subaction=search&story=' + encodeURIComponent(query) : path);
      if (new URL(target, location.origin).origin !== location.origin) throw new Error('Недопустимый адрес страницы каталога');
      var doc = await fetchDoc(target);
      var items = cardsFrom(doc, absolute(target, location.origin));
      var next = doc.querySelector('.nav-next a, a[rel="next"]');
      var nextUrl = next ? absolute(next.getAttribute('href'), absolute(target, location.origin)) : '';
      if (query && next) {
        var searchPage = String(next.getAttribute('onclick') || '').match(/list_submit\((\d+)\)/);
        if (searchPage) {
          var searchNext = new URL(target, location.origin); searchNext.searchParams.set('search_start', searchPage[1]);
          nextUrl = searchNext.href;
        }
      }
      if (nextUrl && nextUrl.split('#')[0] === absolute(target, location.origin).split('#')[0]) nextUrl = '';
      if (nextUrl && new URL(nextUrl).origin !== location.origin) nextUrl = '';
      if (request === catalogRequest) emit('catalog', { items: items, append: !!data.pageUrl, nextPage: nextUrl, query: query, path: path });
    } catch (e) { if (request === catalogRequest) error(e.message); }
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
    if (cancelAutostart) cancelAutostart();
    if (window.EXSClosePlayer) window.EXSClosePlayer();
    remoteControl = null;
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
      'html,body{background:#000!important}' +
      '.exs-frame-ancestor{transform:none!important;perspective:none!important;filter:none!important;contain:none!important;overflow:visible!important}' +
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
    var ancestor = iframe.parentElement;
    while (ancestor && ancestor !== document.body) {
      ancestor.classList.add('exs-frame-ancestor');
      ancestor = ancestor.parentElement;
    }
    iframe.addEventListener('load', function () {
      emit('diagnostic', { message: 'iframe загружен: ' + absolute(iframe.getAttribute('src')) });
    }, { once: true });
    var provider = absolute(iframe.getAttribute('src')) || 'встроенный iframe без src';
    emit('diagnostic', { message: 'Открыт ' + (text(trigger) || ('плеер ' + panelIndex)) + ': ' + provider });
    iframe.focus();
    iframe.addEventListener('load', function () { iframe.contentWindow.postMessage({ exsCommand: 'autostart' }, '*'); }, { once: true });
    iframe.contentWindow.postMessage({ exsCommand: 'autostart' }, '*');
    var startCancelled = false;
    var startAttempts = 0;
    cancelAutostart = function () { startCancelled = true; };
    function autoplay() {
      if (startCancelled || !iframe.classList.contains('exs-frame-active')) return;
      startAttempts++;
      var video = originalVideo(iframe);
      if (video && !video.paused && video.readyState >= 2) {
        startCancelled = true;
        emit('diagnostic', { message: 'Автозапуск: воспроизведение подтверждено' });
        return;
      }
      originalAction(iframe, 'play');
      if (startAttempts < 15) setTimeout(autoplay, 1500);
      else emit('diagnostic', { message: 'Автозапуск: таймаут; нажмите OK, проверьте журнал ошибок видео' });
    }
    setTimeout(autoplay, 700);
    // The iframe must stay in its original DOM position: moving a src-less
    // actualize iframe destroys its dynamically written player document.
    function fitOriginalPlayer() {
      try {
        var inner = iframe.contentDocument;
        if (!inner || !inner.body) return;
        var fitStyle = inner.getElementById('exs-original-fit');
        if (!fitStyle) {
          fitStyle = inner.createElement('style'); fitStyle.id = 'exs-original-fit';
          inner.head.appendChild(fitStyle);
        }
        fitStyle.textContent = 'html,body{margin:0!important;width:100%!important;height:100%!important;overflow:hidden!important;background:#000!important}' +
          '.exs-video-parent{box-sizing:border-box!important;width:100%!important;height:100%!important;min-height:0!important;max-height:100%!important;max-width:100%!important;aspect-ratio:auto!important;padding-top:0!important;padding-bottom:0!important}' +
          'video{object-fit:contain!important;max-width:100%!important;max-height:100%!important}';
        inner.querySelectorAll('video').forEach(function (video) {
          var parent = video.parentElement;
          while (parent && parent !== inner.body) {
            parent.classList.add('exs-video-parent'); parent = parent.parentElement;
          }
        });
        iframe.contentWindow.dispatchEvent(new Event('resize'));
      } catch (_) {} // Cross-origin players keep their own layout and controls.
    }
    window.EXSFitPlayer = fitOriginalPlayer;
    var fullscreenButton = document.getElementById('exs-enter-fullscreen') || document.createElement('button');
    fullscreenButton.id = 'exs-enter-fullscreen';
    fullscreenButton.textContent = '⛶';
    fullscreenButton.setAttribute('aria-label', 'Открыть оригинальный плеер на весь экран');
    fullscreenButton.style.cssText = 'position:fixed;left:0;top:0;width:48px;height:48px;z-index:2147483647;background:#152638;color:white;border:0;font-size:24px';
    fullscreenButton.onclick = function () {
      originalAction(iframe, 'play');
      var request = iframe.requestFullscreen || iframe.webkitRequestFullscreen;
      if (!request) { emit('diagnostic', { message: 'Fullscreen API недоступен; оставлена кнопка оригинального плеера' }); return; }
      try {
        var result = request.call(iframe);
        if (result && result.catch) result.catch(function (e) {
          emit('diagnostic', { message: 'Fullscreen отклонён: ' + e.message });
          fullscreenButton.hidden = false;
        });
      } catch (e) { emit('diagnostic', { message: 'Fullscreen: ' + e.message }); }
    };
    document.body.appendChild(fullscreenButton);
    function fullscreenChanged() {
      var entered = !!(document.fullscreenElement || document.webkitFullscreenElement);
      fullscreenButton.hidden = entered;
      fitOriginalPlayer();
      // Use the player's external fullscreen protocol too, so its controls,
      // quality/voice selectors and timeline know they are on a full surface.
      try { iframe.contentWindow.postMessage({ event: 'fullScreen', enter: entered }, '*'); } catch (_) {}
      emit('diagnostic', { message: 'Оригинальный fullscreen=' + entered + '; viewport=' + innerWidth + 'x' + innerHeight });
    }
    document.addEventListener('fullscreenchange', fullscreenChanged);
    document.addEventListener('webkitfullscreenchange', fullscreenChanged);
    window.addEventListener('resize', fitOriginalPlayer);
    window.EXSClosePlayer = function () {
      document.removeEventListener('fullscreenchange', fullscreenChanged);
      document.removeEventListener('webkitfullscreenchange', fullscreenChanged);
      window.removeEventListener('resize', fitOriginalPlayer);
      fullscreenButton.remove(); window.EXSFitPlayer = null;
    };
    function inspectInnerVideo() {
      try {
        var inner = iframe.contentDocument;
        var videos = inner ? Array.from(inner.querySelectorAll('video')) : [];
        fitOriginalPlayer();
        videos.forEach(function (video) {
          video.poster = TRANSPARENT_POSTER;
          if (video.dataset.exsObserved) return;
          video.dataset.exsObserved = '1';
          ['loadedmetadata', 'canplay', 'playing', 'waiting', 'stalled', 'error'].forEach(function (eventName) {
            video.addEventListener(eventName, function () {
              if (eventName === 'playing') startCancelled = true;
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
      emit('fullscreenRequest');
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
    if (authState()) return;
    sessionStorage.setItem('exs-auth-mode', '1');
    var registration = /do=(register|lostpassword)/.test(location.search);
    var root = document.querySelector(registration ? '#dle-content' : '#PopUpLogin');
    if (!root) { error('Форма входа не найдена. Обновите страницу.'); return; }
    document.documentElement.classList.add('exs-login');
    var style = document.getElementById('exs-login-style') || document.head.appendChild(document.createElement('style'));
    style.id = 'exs-login-style';
    root.classList.add('exs-auth-root');
    document.body.appendChild(root);
    style.textContent = 'html.exs-login,html.exs-login body{margin:0!important;background:#07111f!important;height:auto!important;overflow:auto!important}' +
      '.exs-login body>*:not(.exs-auth-root){display:none!important}' +
      '.exs-login .exs-auth-root{display:block!important;visibility:visible!important;opacity:1!important;position:relative!important;inset:auto!important;top:auto!important;left:auto!important;transform:none!important;margin:0 auto!important;padding:78px 24px 24px!important;box-sizing:border-box!important;width:100%!important;max-width:650px!important;height:auto!important;background:#07111f!important;color:white!important;z-index:1!important}' +
      '.exs-login .exs-auth-root *{visibility:visible!important}.exs-login .exs-auth-root input,.exs-login .exs-auth-root button{font-size:18px!important;min-height:44px!important}' +
      '.exs-login .exs-auth-root :focus{outline:3px solid #35d4ff!important}.exs-login .close-reveal-modal{display:none!important}';
    root.querySelectorAll('input:not([type="hidden"]),button,a,textarea,select').forEach(function (node) { node.tabIndex = 0; });
    var first = root.querySelector('input:not([type="hidden"])');
    if (first) first.focus();
  }
  function loginKey(action) {
    var root = document.querySelector('.exs-auth-root'); if (!root) return;
    var controls = Array.from(root.querySelectorAll('input:not([type="hidden"]),button:not(.close-reveal-modal),a,textarea,select')).filter(function (node) { return !node.disabled && node.getBoundingClientRect().height > 0; });
    if (!controls.length) return;
    var index = Math.max(0, controls.indexOf(document.activeElement));
    if (action === 'next' || action === 'previous') controls[(index + (action === 'next' ? 1 : -1) + controls.length) % controls.length].focus();
    else {
      var current = controls[index]; current.focus();
      if (/INPUT|TEXTAREA/.test(current.tagName)) emit('authInput'); else current.click();
    }
  }
  function closeSurface() {
    sessionStorage.removeItem('exs-auth-mode');
    if (cancelAutostart) { cancelAutostart(); cancelAutostart = null; }
    remoteControl = null;
    if (window.EXSClosePlayer) { window.EXSClosePlayer(); window.EXSClosePlayer = null; }
    document.documentElement.classList.remove('exs-login');
    var loginStyle = document.getElementById('exs-login-style'); if (loginStyle) loginStyle.remove();
    document.querySelectorAll('.exs-player-active').forEach(function (p) { p.classList.remove('exs-player-active'); });
    document.querySelectorAll('iframe.exs-frame-active').forEach(function (frame) {
      try { var video = frame.contentDocument && frame.contentDocument.querySelector('video'); if (video) video.pause(); } catch (_) {}
      frame.classList.remove('exs-frame-active');
      frame.classList.remove('act-fullscreen');
      try { frame.contentWindow.postMessage({ event: 'fullScreen', enter: false }, '*'); } catch (_) {}
    });
    document.querySelectorAll('.exs-frame-ancestor').forEach(function (node) { node.classList.remove('exs-frame-ancestor'); });
    var video = document.querySelector('video'); if (video) video.pause();
  }
  function originalVideo(frame) {
    try {
      var inner = frame && frame.contentDocument;
      if (!inner) return null;
      var videos = Array.from(inner.querySelectorAll('video'));
      return videos.find(function (v) { return v.currentSrc && !v.error; }) || videos[0] || null;
    } catch (_) { return null; }
  }
  function originalAction(frame, action) {
    var video = originalVideo(frame);
    var actual = action === 'toggle' ? (!video || video.paused ? 'play' : 'pause') : action;
    try {
      var win = frame.contentWindow;
      // Playerjs's own API starts its HLS loader and clears the initial Play
      // overlay; video.play() alone may leave the media element without a src.
      var names = ['player', 'playerjs', 'pjs', 'app'].concat(Object.keys(win));
      var api = null;
      for (var i = 0; i < names.length && !api; i++) {
        var descriptor = Object.getOwnPropertyDescriptor(win, names[i]);
        var value = descriptor && descriptor.value;
        if (value && typeof value.api === 'function') api = value;
      }
      if (api) {
        if (actual === 'left' || actual === 'right') {
          var time = Number(api.api('time')) || (video && video.currentTime) || 0;
          api.api('seek', Math.max(0, Math.min(video && video.duration || Infinity, time + (actual === 'left' ? -10 : 10))));
        } else api.api(actual);
        return true;
      }
    } catch (_) {}
    if (!video) {
      frame.contentWindow.postMessage({ exsCommand: action }, '*');
      emit('diagnostic', { message: 'Команда ' + action + ' передана медиаплееру другого домена' });
      return false;
    }
    if (actual === 'play') {
      try {
        var promise = video.play();
        if (promise && promise.catch) promise.catch(function (e) { emit('diagnostic', { message: 'play(): ' + e.name + ' ' + e.message }); });
      } catch (_) {}
    }
    if (actual === 'pause') video.pause();
    if (actual === 'left' || actual === 'right') video.currentTime = Math.max(0, Math.min(video.duration || Infinity, video.currentTime + (actual === 'left' ? -10 : 10)));
    return true;
  }
  function playerKey(action) {
    var frame = document.querySelector('iframe.exs-frame-active');
    if (!frame) return;
    if (cancelAutostart) cancelAutostart();
    try {
      var inner = frame.contentDocument;
      if (inner && action === 'controls') {
        var controls = Array.from(inner.querySelectorAll('button,select,[role="button"],[tabindex],[title]')).filter(function (node) {
          var rect = node.getBoundingClientRect(); return rect.width > 8 && rect.height > 8 && !node.disabled;
        });
        remoteControl = controls.length ? { items: controls, index: 0 } : null;
      } else if (action === 'video') {
        if (remoteControl) remoteControl.items.forEach(function (node) { node.style.outline = ''; });
        remoteControl = null;
      }
      else if (remoteControl) {
        if (action === 'left' || action === 'right') remoteControl.index = (remoteControl.index + (action === 'left' ? -1 : 1) + remoteControl.items.length) % remoteControl.items.length;
        if (action === 'toggle') {
          var control = remoteControl.items[remoteControl.index];
          if (control.tagName === 'SELECT' && control.options.length) {
            control.selectedIndex = (control.selectedIndex + 1) % control.options.length;
            control.dispatchEvent(new Event('change', { bubbles: true }));
          } else control.click();
          return;
        }
      } else { originalAction(frame, action); return; }
      if (remoteControl) {
        remoteControl.items.forEach(function (node, index) { node.style.outline = index === remoteControl.index ? '3px solid #35d4ff' : ''; });
        var selected = remoteControl.items[remoteControl.index]; selected.tabIndex = 0; selected.focus();
      }
    } catch (_) { originalAction(frame, action); }
  }
  function authState() {
    var account = document.querySelector('a[href*="action=logout"],a[href*="/user/"]');
    var loggedIn = typeof window.dle_group !== 'undefined' ? Number(window.dle_group) > 0 && Number(window.dle_group) !== 5 : !!document.querySelector('a[href*="action=logout"]');
    emit('auth', { loggedIn: loggedIn, displayName: loggedIn ? text(document.querySelector('.loginbtn a')) || text(account) : '' });
    return loggedIn;
  }
  function command(name, data) {
    if (name === 'catalog') return catalog(data || {});
    if (name === 'detail') return detail(data || {});
    if (name === 'play') return play(data || {});
    if (name === 'resizePlayer') { if (window.EXSFitPlayer) window.EXSFitPlayer(); return; }
    if (name === 'login') return login();
    if (name === 'loginKey') return loginKey(data.action);
    if (name === 'pausePlayer') {
      var frame = document.querySelector('iframe.exs-frame-active');
      if (cancelAutostart) cancelAutostart();
      if (frame) originalAction(frame, 'pause');
      return;
    }
    if (name === 'closePlayer' || name === 'closeLogin') return closeSurface();
  }
  window.EXSApp = { version: 2, command: command, playerKey: playerKey };
  removeAdvertising();
  var pending = JSON.parse(sessionStorage.getItem(PENDING) || 'null');
  if (pending && pending.url && Date.now() - pending.at < 60000 && location.href.split('#')[0] === pending.url.split('#')[0]) {
    sessionStorage.removeItem(PENDING); setTimeout(function () { activatePlayer(pending.player); }, 800);
  }
  var loggedIn = authState();
  if (sessionStorage.getItem('exs-auth-mode') && !loggedIn) login();
  setInterval(removeAdvertising, 1200);
  emit('ready');
})();
