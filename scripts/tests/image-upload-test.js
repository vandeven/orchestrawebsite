// Checks of the browser side of adding an image (src/main/resources/META-INF/resources/js/image-upload.js)
// in a simulated browser: the image is read without any blob: address (the Content-Security-Policy
// forbids those pictures), made smaller, and sent in three steps.
// Run: NODE_PATH=<folder with jsdom> node scripts/tests/image-upload-test.js
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const assert = require("assert");

const script = fs.readFileSync(
  path.join(__dirname, "../../src/main/resources/META-INF/resources/js/image-upload.js"), "utf8");

let failures = 0;
function test(name, fn) {
  Promise.resolve().then(fn).then(
    () => console.log("ok:   " + name),
    (e) => { failures++; console.log("FAIL: " + name + "\n  " + e.message); });
}

function browser(options) {
  options = options || {};
  const dom = new JSDOM("<body></body>", { runScripts: "outside-only", url: "https://example.test/edit/images" });
  const { window } = dom;
  const log = { bitmaps: [], drawn: [], canvases: [], blobUrls: 0, images: 0, fetches: [], puts: [] };
  if (!options.noBitmap) {
    window.createImageBitmap = (file) => {
      log.bitmaps.push(file);
      if (options.unreadable) { return Promise.reject(new Error("not decodable")); }
      return Promise.resolve({ width: options.width || 4000, height: options.height || 3000, close() { log.closed = true; } });
    };
  }
  // The things a picture from a blob: address would need must never be used
  window.URL.createObjectURL = () => { log.blobUrls++; return "blob:x"; };
  const RealImage = window.Image;
  window.Image = function () { log.images++; return new RealImage(); };
  window.HTMLCanvasElement.prototype.getContext = function () {
    log.canvases.push(this);
    return { drawImage: (bitmap, x, y, w, h) => log.drawn.push({ w, h }) };
  };
  window.HTMLCanvasElement.prototype.toBlob = function (callback, type, quality) {
    const supported = options.webp !== false;
    const blobType = type === "image/webp" && !supported ? "image/png" : type;
    callback({ type: blobType, size: 1234, quality });
  };
  window.fetch = (url, init) => {
    log.fetches.push({ url, init });
    const body = url.endsWith("upload-url")
      ? { id: "33333333-3333-3333-3333-333333333333", uploadUrl: "https://images.example.test/put", headers: { "Content-Type": "image/webp" } }
      : { id: "33333333-3333-3333-3333-333333333333", url: "https://images.example.test/x.webp", alt: "x", width: 2000, height: 1500 };
    if (url.startsWith("https://images.example.test/put")) {
      log.puts.push({ url, init });
      return Promise.resolve({ ok: true, status: 200 });
    }
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
  };
  window.eval(script);
  return { window, log };
}

test("a photo is read without any blob address or image element", async () => {
  const { window, log } = browser();
  const file = { name: "photo.jpg", type: "image/jpeg" };
  await window.OrchestraImages.upload(file, "A photo", false);
  assert.strictEqual(log.bitmaps[0], file);
  assert.strictEqual(log.blobUrls, 0, "no blob: address");
  assert.strictEqual(log.images, 0, "no image element");
  assert.strictEqual(log.closed, true, "the bitmap is released");
});

test("a large photo is made at most 2000 pixels wide, a small one keeps its size", async () => {
  const big = browser({ width: 4000, height: 3000 });
  await big.window.OrchestraImages.upload({ name: "a.jpg", type: "image/jpeg" }, "x", false);
  assert.deepStrictEqual(big.log.drawn[0], { w: 2000, h: 1500 });
  const small = browser({ width: 800, height: 600 });
  await small.window.OrchestraImages.upload({ name: "a.jpg", type: "image/jpeg" }, "x", false);
  assert.deepStrictEqual(small.log.drawn[0], { w: 800, h: 600 });
});

test("the image goes in three steps with the type that was really made", async () => {
  const { window, log } = browser();
  await window.OrchestraImages.upload({ name: "a.png", type: "image/png" }, "  A description ", false);
  assert.strictEqual(log.fetches[0].url, "/edit/images/upload-url");
  assert.deepStrictEqual(JSON.parse(log.fetches[0].init.body), { contentType: "image/webp", size: 1234 });
  assert.strictEqual(log.puts[0].init.method, "PUT");
  assert.strictEqual(log.puts[0].init.headers["Content-Type"], "image/webp");
  assert.strictEqual(log.fetches[2].url, "/edit/images/33333333-3333-3333-3333-333333333333/complete");
  assert.deepStrictEqual(JSON.parse(log.fetches[2].init.body), { contentType: "image/webp", alt: "A description", decorative: false });
});

test("without WebP support a PNG stays PNG and a photo becomes JPEG", async () => {
  const png = browser({ webp: false });
  await png.window.OrchestraImages.upload({ name: "a.png", type: "image/png" }, "x", false);
  assert.strictEqual(JSON.parse(png.log.fetches[0].init.body).contentType, "image/png");
  const jpg = browser({ webp: false });
  await jpg.window.OrchestraImages.upload({ name: "a.jpg", type: "image/jpeg" }, "x", false);
  assert.strictEqual(JSON.parse(jpg.log.fetches[0].init.body).contentType, "image/jpeg");
});

test("a file the browser cannot read gives a clear message and sends nothing", async () => {
  const { window, log } = browser({ unreadable: true });
  await assert.rejects(window.OrchestraImages.upload({ name: "a.heic", type: "image/heic" }, "x", false),
    /not an image the browser can read.*HEIC/);
  assert.strictEqual(log.fetches.length, 0);
});

test("an old browser without createImageBitmap says so", async () => {
  const { window } = browser({ noBitmap: true });
  await assert.rejects(window.OrchestraImages.upload({ name: "a.jpg", type: "image/jpeg" }, "x", false), /recent version/);
});

test("a description is required unless the image is decoration, and a file is required", async () => {
  const { window, log } = browser();
  await assert.rejects(window.OrchestraImages.upload({ name: "a.jpg", type: "image/jpeg" }, "  ", false), /Describe the image/);
  await assert.rejects(window.OrchestraImages.upload(null, "x", false), /Choose an image/);
  assert.strictEqual(log.bitmaps.length, 0);
  await window.OrchestraImages.upload({ name: "a.jpg", type: "image/jpeg" }, "", true);
  assert.strictEqual(JSON.parse(log.fetches[2].init.body).alt, "");
});

setTimeout(() => {
  if (failures) { console.log(failures + " check(s) failed"); process.exit(1); }
  console.log("All image upload checks passed");
}, 600);
