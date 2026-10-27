const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const handlers = [], calls = [], parent = {}, timers = [];
const video = {paused:true,currentSrc:'https://media.example/test.m3u8',readyState:4,currentTime:20,duration:120};
const context = {top:{},parent,document:{referrer:'https://ex-fs.net/film/example.html',
  querySelectorAll: s => s === 'video' ? [video] : [], querySelector: () => null}, URL,
  addEventListener: (type, fn) => {if(type === 'message') handlers.push(fn);}, setTimeout: fn => timers.push(fn),
  player:{api(action, value) {calls.push(action); if(action === 'play') video.paused=false; if(action === 'pause') video.paused=true; if(action === 'time') return video.currentTime; if(action === 'seek') video.currentTime=value;}}};
context.window = context;
vm.runInNewContext(fs.readFileSync('app/src/main/assets/player-frame.js','utf8'),context);
function send(command, origin='https://ex-fs.net', source=parent) {handlers.forEach(fn => fn({source,origin,data:{exsCommand:command}}));}
assert.equal(calls.length,0,'Inactive/hidden providers must not autoplay');
send('play','https://attacker.example'); send('play','https://ex-fs.net',{});
assert.equal(calls.length,0,'Reject commands from a wrong origin or frame');
send('autostart'); assert.equal(video.paused,false);
send('toggle'); assert.equal(video.paused,true);
timers.splice(0).forEach(fn => fn());
assert.equal(video.paused,true,'Autostart cannot undo remote pause');
send('right'); assert.equal(video.currentTime,30);
send('play'); assert.equal(video.paused,false);
send('pause'); assert.equal(video.paused,true);
send('eval'); assert.equal(calls.at(-1),'pause');
console.log('Cross-origin media protocol, autoplay cancellation and command isolation: OK');
