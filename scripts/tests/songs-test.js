// Checks of the two scripts of the songs pages in a simulated browser: the audio player that asks
// for a fresh address when the signed one has expired, and the form that adds a file to a song.
// Run: NODE_PATH=<folder with jsdom> node scripts/tests/songs-test.js
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const assert = require("assert");

const resources = path.join(__dirname, "../../src/main/resources/META-INF/resources/js/");
const playerScript = fs.readFileSync(resources + "audio-player.js", "utf8");
const uploadScript = fs.readFileSync(resources + "song-upload.js", "utf8");

let failures = 0;
function test(name, fn) {
  Promise.resolve().then(fn).then(
    () => console.log("ok:   " + name),
    (e) => { failures++; console.log("FAIL: " + name + "\n  " + e.message); });
}

// ---- the audio player ----

function player() {
  const dom = new JSDOM(`<audio controls preload="none" src="/members/files/abc/play"></audio><audio src="/members/files/def/play"></audio>`,
    { runScripts: "outside-only" });
  const { window } = dom;
  const audios = [...window.document.querySelectorAll("audio")];
  audios.forEach((audio) => {
    audio.loads = 0;
    audio.plays = 0;
    audio.load = function () { this.loads++; };
    audio.play = function () { this.plays++; this._paused = false; return Promise.resolve(); };
    audio.pause = function () { this._paused = true; };
    Object.defineProperty(audio, "paused", { get() { return this._paused !== false; } });
    let time = 0;
    Object.defineProperty(audio, "currentTime", { get() { return time; }, set(v) { time = v; } });
  });
  window.eval(playerScript);
  return { window, audio: audios[0], other: audios[1] };
}

test("an expired address is replaced and playback continues at the same moment", () => {
  const { window, audio } = player();
  audio.currentTime = 95.5;
  audio._paused = false; // it was playing
  audio.dispatchEvent(new window.Event("error"));
  assert.strictEqual(audio.loads, 1);
  assert(/^\/members\/files\/abc\/play\?t=\d+$/.test(audio.getAttribute("src")), audio.getAttribute("src"));
  audio.currentTime = 0; // the new address starts at the beginning
  audio.dispatchEvent(new window.Event("loadedmetadata"));
  assert.strictEqual(audio.currentTime, 95.5);
  assert.strictEqual(audio.plays, 1, "it plays on");
});

test("a paused player does not start by itself after a new address", () => {
  const { window, audio } = player();
  audio.currentTime = 12;
  audio.dispatchEvent(new window.Event("error"));
  audio.currentTime = 0;
  audio.dispatchEvent(new window.Event("loadedmetadata"));
  assert.strictEqual(audio.currentTime, 12);
  assert.strictEqual(audio.plays, 0);
});

test("it gives up after three failed tries instead of asking for ever", () => {
  const { window, audio } = player();
  for (let i = 0; i < 6; i++) { audio.dispatchEvent(new window.Event("error")); }
  assert.strictEqual(audio.loads, 3);
});

test("after a success the count of tries starts again", () => {
  const { window, audio } = player();
  for (let round = 0; round < 3; round++) {
    audio.dispatchEvent(new window.Event("error"));
    audio.dispatchEvent(new window.Event("loadedmetadata"));
  }
  audio.dispatchEvent(new window.Event("error"));
  assert.strictEqual(audio.loads, 4);
});

test("only one recording plays at a time", () => {
  const { window, audio, other } = player();
  other._paused = false;
  audio._paused = false;
  audio.dispatchEvent(new window.Event("playing"));
  assert.strictEqual(other.paused, true);
  assert.strictEqual(audio.paused, false);
});

// ---- the form that adds a file ----

function uploader() {
  const dom = new JSDOM(`<form data-song-upload data-song-id="11111111-1111-1111-1111-111111111111" hidden>
      <select id="upload-type"><option value="sheet-music" data-extension="pdf">Sheet</option><option value="audio" data-extension="mp3">Audio</option></select>
      <input type="file" id="upload-file"><input type="text" id="upload-name">
      <p id="upload-error" hidden></p><p id="upload-status" hidden></p><progress id="upload-progress" hidden></progress>
      <button type="submit">Upload</button></form>`, { runScripts: "outside-only", url: "https://example.test/songs/manage/11111111-1111-1111-1111-111111111111" });
  const { window } = dom;
  const calls = [];
  window.fetch = (url, options) => {
    calls.push({ url, options });
    const body = url.endsWith("upload-url")
      ? { fileId: "22222222-2222-2222-2222-222222222222", uploadUrl: "https://files.example.test/put", headers: { "Content-Type": "application/pdf", "x-goog-content-length-range": "1,15728640" } }
      : { id: "22222222-2222-2222-2222-222222222222" };
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
  };
  const puts = [];
  window.XMLHttpRequest = class {
    constructor() { this.upload = {}; this.headers = {}; }
    open(method, url) { this.method = method; this.url = url; }
    setRequestHeader(k, v) { this.headers[k] = v; }
    send(blob) { puts.push({ method: this.method, url: this.url, headers: this.headers, blob }); this.status = 200; setTimeout(() => this.onload(), 0); }
  };
  window.eval(uploadScript);
  return { window, calls, puts, doc: window.document };
}

function choose(doc, window, name, size) {
  const input = doc.getElementById("upload-file");
  Object.defineProperty(input, "files", { value: [{ name, size }], configurable: true });
}

function submit(window) {
  window.document.querySelector("form").dispatchEvent(new window.Event("submit", { cancelable: true }));
}

test("a song ID that is not a UUID keeps the form hidden and sends nothing", () => {
  for (const bad of ["", "../../x", "javascript:alert(1)", "11111111-1111-1111-1111-11111111111", "11111111-1111-1111-1111-1111111111111", "//evil.example/"]) {
    const dom = new JSDOM(`<form data-song-upload data-song-id="${bad}" hidden></form>`, { runScripts: "outside-only" });
    dom.window.fetch = () => { throw new Error("must not be called"); };
    dom.window.eval(uploadScript);
    assert.strictEqual(dom.window.document.querySelector("form").hidden, true, bad);
  }
});

test("the form is shown by the script", () => {
  const { doc } = uploader();
  assert.strictEqual(doc.querySelector("form").hidden, false);
});

test("the browser refuses a wrong extension, a missing file, a big file and a missing name", () => {
  const cases = [
    [null, 0, "x", "Choose a file"],
    ["part.mp3", 1000, "x", "ends in .pdf"],
    ["part.pdf", 15 * 1024 * 1024 + 1, "x", "1 byte to 15 MB"],
    ["part.pdf", 0, "x", "1 byte to 15 MB"],
    ["part.pdf", 1000, "   ", "Give the file a name"],
  ];
  for (const [file, size, name, message] of cases) {
    const { window, doc, calls } = uploader();
    if (file) { choose(doc, window, file, size); }
    doc.getElementById("upload-name").value = name;
    submit(window);
    const error = doc.getElementById("upload-error");
    assert.strictEqual(error.hidden, false, message);
    assert(error.textContent.includes(message), error.textContent + " / " + message);
    assert.strictEqual(calls.length, 0, "nothing is sent");
    assert.strictEqual(doc.querySelector("button").disabled, false);
  }
});

test("the extension check is not case sensitive and 15 MB exactly is allowed", async () => {
  const { window, doc, calls, puts } = uploader();
  choose(doc, window, "PART.PDF", 15 * 1024 * 1024);
  doc.getElementById("upload-name").value = "Soprano";
  submit(window);
  await new Promise((resolve) => setTimeout(resolve, 30));
  assert.strictEqual(doc.getElementById("upload-error").hidden, true, doc.getElementById("upload-error").textContent);
  assert.strictEqual(calls.length, 2);
  assert.strictEqual(puts.length, 1);
});

test("a file goes in three steps: an address, the upload to the storage, and the report", async () => {
  const { window, doc, calls, puts } = uploader();
  choose(doc, window, "Score.pdf", 4321);
  doc.getElementById("upload-name").value = "  Soprano 1  ";
  submit(window);
  await new Promise((resolve) => setTimeout(resolve, 30));
  const base = "/songs/manage/11111111-1111-1111-1111-111111111111";
  assert.strictEqual(calls[0].url, base + "/files/upload-url");
  assert.deepStrictEqual(JSON.parse(calls[0].options.body), { type: "sheet-music", originalName: "Score.pdf", size: 4321 });
  assert.strictEqual(puts[0].method, "PUT");
  assert.strictEqual(puts[0].url, "https://files.example.test/put");
  assert.strictEqual(puts[0].headers["Content-Type"], "application/pdf");
  assert.strictEqual(puts[0].headers["x-goog-content-length-range"], "1,15728640");
  assert.strictEqual(calls[1].url, base + "/files/22222222-2222-2222-2222-222222222222/complete");
  assert.deepStrictEqual(JSON.parse(calls[1].options.body), { type: "sheet-music", originalName: "Score.pdf", displayName: "Soprano 1" });
});

test("a message from the server is shown and the form can be used again", async () => {
  const { window, doc } = uploader();
  window.fetch = () => Promise.resolve({ ok: false, status: 400, json: () => Promise.resolve({ error: "The file is not a valid sheet music file" }) });
  choose(doc, window, "fake.pdf", 100);
  doc.getElementById("upload-name").value = "Fake";
  submit(window);
  await new Promise((resolve) => setTimeout(resolve, 30));
  assert(doc.getElementById("upload-error").textContent.includes("not a valid sheet music file"));
  assert.strictEqual(doc.querySelector("button").disabled, false);
});

setTimeout(() => {
  if (failures) { console.log(failures + " check(s) failed"); process.exit(1); }
  console.log("All songs script checks passed");
}, 600);
