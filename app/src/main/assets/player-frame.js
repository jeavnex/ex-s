(function () {
  'use strict';
  // Installed by WebView in subframes. No native bridge, credentials or DOM
  // content are exposed: only a small media command protocol is accepted.
  if (window === window.top || window.__exsFrameAgent) return;
  window.__exsFrameAgent = true;
  var parentOrigin;
  try { parentOrigin = new URL(document.referrer).origin; } catch (_) { parentOrigin = 'https://ex-fs.net'; }
  var stopped = false, attempts = 0;
  function media() {
    var all = Array.from(document.querySelectorAll('video'));
    return all.find(function (v) { return v.currentSrc && !v.error; }) || all[0];
  }
  function run(action) {
    var video = media();
    var command = action === 'toggle' ? (!video || video.paused ? 'play' : 'pause') : action;
    var names = ['player', 'playerjs', 'pjs', 'app'].concat(Object.keys(window));
    var api;
    for (var i = 0; i < names.length && !api; i++) {
      var d = Object.getOwnPropertyDescriptor(window, names[i]);
      if (d && d.value && typeof d.value.api === 'function') api = d.value;
    }
    if (api) {
      if (command === 'left' || command === 'right') {
        var time = Number(api.api('time')) || video && video.currentTime || 0;
        api.api('seek', Math.max(0, Math.min(video && video.duration || Infinity, time + (command === 'left' ? -10 : 10))));
      } else api.api(command);
    } else if (video) {
      if (command === 'play') { var promise = video.play(); if (promise && promise.catch) promise.catch(function () {}); }
      if (command === 'pause') video.pause();
      if (command === 'left' || command === 'right') video.currentTime = Math.max(0, Math.min(video.duration || Infinity, video.currentTime + (command === 'left' ? -10 : 10)));
    }
    // Also reach nested frames, such as a trailer hosted inside a provider.
    document.querySelectorAll('iframe').forEach(function (frame) {
      frame.contentWindow.postMessage({ exsCommand: action }, '*');
      if (/youtube(?:-nocookie)?\.com/.test(frame.src)) {
        frame.contentWindow.postMessage(JSON.stringify({ event: 'command', func: command === 'pause' ? 'pauseVideo' : 'playVideo', args: [] }), '*');
      }
    });
    if (command === 'play' && !api && (!video || !video.currentSrc)) {
      var button = document.querySelector('.vjs-big-play-button,.jw-icon-playback,.plyr__control--overlaid,button[aria-label="Play"],button[aria-label="Воспроизвести"]');
      if (button) button.click();
    }
  }
  function start() {
    if (stopped) return;
    var video = media();
    if (video && !video.paused && video.readyState >= 2) { stopped = true; return; }
    try { run('play'); } catch (_) {}
    if (++attempts < 15) setTimeout(start, 1500);
  }
  window.addEventListener('message', function (event) {
    if (event.source !== window.parent || event.origin !== parentOrigin || !event.data) return;
    var action = event.data.exsCommand;
    if (!['play', 'pause', 'toggle', 'left', 'right'].includes(action)) return;
    stopped = true;
    try { run(action); } catch (_) {}
  });
  // Only the selected provider is started: a parent message arms autoplay.
  window.addEventListener('message', function (event) {
    if (event.source !== window.parent || event.origin !== parentOrigin || !event.data || event.data.exsCommand !== 'autostart') return;
    if (attempts) return;
    stopped = false; start();
  });
})();
