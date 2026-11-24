// Checks of the editor script (src/main/resources/META-INF/resources/js/editor.js) in a simulated
// browser: the HTML view warns about HTML that is not allowed, nothing harmful is ever put into the
// visual editor, and what is submitted is cleaned and uses classes instead of styles.
// Run: NODE_PATH=<folder with jsdom> node scripts/tests/editor-test.js
const { JSDOM } = require("jsdom");
const fs = require("fs");
const path = require("path");
const assert = require("assert");

const script = fs.readFileSync(
  path.join(__dirname, "../../src/main/resources/META-INF/resources/js/editor.js"), "utf8");

const LIBRARY = `<ul hidden data-library>
  <li data-id="11111111-1111-1111-1111-111111111111" data-url="https://images.example.test/one.png" data-alt="The band" data-width="800" data-height="600"></li>
  <li data-id="22222222-2222-2222-2222-222222222222" data-url="https://images.example.test/two.webp" data-alt="" data-width="400" data-height="300"></li></ul>`;

// options.uploader: a stand-in for image-upload.js; options.noLibrary: no image storage address
function page(initialHtml, options) {
  options = options || {};
  const dom = new JSDOM(
    `<body><form data-editor ${options.noImages ? "" : 'data-image-base="https://images.example.test/"'} method="post">
       <div class="field"><label for="html">Text (HTML)</label><p id="html-hint">hint</p>
       <textarea id="html" name="html" data-editor-source></textarea></div>
       <button type="submit">Save</button></form>${LIBRARY}</body>`,
    { runScripts: "outside-only" });
  const { window } = dom;
  // jsdom has no editing commands or modal dialogs; the real browser does. These stand-ins do the
  // one thing the script needs: insertHTML adds the markup, a dialog opens and closes.
  window.document.execCommand = (name, ui, argument) => {
    if (name === "insertHTML") {
      window.document.querySelector(".editor-visual").insertAdjacentHTML("beforeend", argument);
    }
    return true;
  };
  window.HTMLDialogElement.prototype.showModal = function () { this.setAttribute("open", ""); };
  window.HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
    this.dispatchEvent(new window.Event("close"));
  };
  if (options.uploader) { window.OrchestraImages = { upload: options.uploader }; }
  window.document.getElementById("html").value = initialHtml;
  window.eval(script);
  return window;
}

function openDialog(w) {
  click(w, "Image");
  return w.document.querySelector("dialog.editor-dialog");
}

function buttonIn(parent, text) {
  const button = [...parent.querySelectorAll("button")].find((b) => b.textContent === text);
  assert(button, "no button " + text);
  return button;
}

function click(window, text) {
  const button = [...window.document.querySelectorAll("button")].find((b) => b.textContent === text);
  assert(button, "no button " + text);
  button.click();
}

let failures = 0;
function test(name, fn) {
  try { fn(); console.log("ok:   " + name); } catch (e) { failures++; console.log("FAIL: " + name + "\n  " + e.message); }
}

const harmful = [
  '<script>window.hacked=1</script><p>x</p>',
  '<img src="x" onerror="window.hacked=1">',
  '<p onclick="window.hacked=1">x</p>',
  '<a href="javascript:window.hacked=1">x</a>',
  '<iframe src="https://evil.example"></iframe>',
  '<p style="color:red">x</p>',
  '<img src="https://evil.example/a.jpg">',
  '<svg onload="window.hacked=1"></svg>',
  '<form action="https://evil.example"><input name="a"></form>',
];

test("the visual editor starts with the text, as editable content", () => {
  const w = page("<h2>Title</h2><p>Hello <strong>you</strong></p>");
  const visual = w.document.querySelector(".editor-visual");
  assert.strictEqual(visual.getAttribute("contenteditable"), "true");
  assert.strictEqual(visual.querySelector("h2").textContent, "Title");
  assert.strictEqual(visual.querySelector("strong").textContent, "you");
});

test("the toolbar has labelled buttons and one tab stop", () => {
  const w = page("");
  const bar = w.document.querySelector("[role=toolbar]");
  assert(bar.getAttribute("aria-label"));
  const buttons = [...bar.querySelectorAll("button")];
  assert(buttons.length >= 15);
  assert(buttons.every((b) => b.textContent.trim().length > 0));
  assert.strictEqual(buttons.filter((b) => b.tabIndex === 0).length, 1);
});

harmful.forEach((html, i) => test("harmful input " + (i + 1) + " never reaches the visual editor", () => {
  const w = page(html);
  const visual = w.document.querySelector(".editor-visual");
  assert.strictEqual(w.hacked, undefined);
  assert(!visual.querySelector("script, iframe, form, input, svg"), visual.innerHTML);
  assert(!/onerror|onclick|onload|javascript:|style=|evil\.example/i.test(visual.innerHTML), visual.innerHTML);
}));

harmful.forEach((html, i) => test("harmful input " + (i + 1) + " typed in the HTML view shows the warning", () => {
  const w = page("<p>ok</p>");
  click(w, "HTML");
  const source = w.document.getElementById("html");
  const warning = w.document.querySelector(".editor-warning");
  assert.strictEqual(warning.hidden, true);
  source.value = html;
  source.dispatchEvent(new w.Event("input"));
  assert.strictEqual(warning.hidden, false);
}));

test("allowed HTML gives no warning", () => {
  const w = page("<p>ok</p>");
  click(w, "HTML");
  const source = w.document.getElementById("html");
  source.value = '<h2>a</h2><p class="align-center"><strong>b</strong> <a href="https://example.org">c</a></p><table><tr><td>d</td></tr></table>';
  source.dispatchEvent(new w.Event("input"));
  assert.strictEqual(w.document.querySelector(".editor-warning").hidden, true);
});

test("external links open in a new tab without an opener", () => {
  const w = page('<a href="https://example.org/x">x</a><a href="mailto:a@example.org">m</a>');
  const links = w.document.querySelectorAll(".editor-visual a");
  assert.strictEqual(links[0].getAttribute("target"), "_blank");
  assert(links[0].getAttribute("rel").includes("noopener"));
  assert.strictEqual(links[1].getAttribute("target"), null);
});

test("switching between the views keeps the text", () => {
  const w = page("<p>One <em>two</em></p>");
  click(w, "HTML");
  assert.strictEqual(w.document.getElementById("html").hidden, false);
  assert(w.document.getElementById("html").value.includes("<em>two</em>"));
  click(w, "Visual");
  assert.strictEqual(w.document.querySelector(".editor-visual").hidden, false);
  assert.strictEqual(w.document.querySelector(".editor-visual em").textContent, "two");
});

test("what is submitted comes from the visual editor, with classes instead of styles", () => {
  const w = page("<p>x</p>");
  const visual = w.document.querySelector(".editor-visual");
  visual.innerHTML = '<div style="text-align: center">centered</div><p align="right">r</p><p style="color:red">c</p>';
  w.document.querySelector("form").dispatchEvent(new w.Event("submit", { cancelable: true }));
  const value = w.document.getElementById("html").value;
  assert(value.includes('<p class="align-center">centered</p>'), value);
  assert(value.includes('<p class="align-right">r</p>'), value);
  assert(!/style|color|<div/.test(value), value);
});

test("an empty visual editor submits an empty text, also with only line breaks", () => {
  const w = page("<p>x</p>");
  w.document.querySelector(".editor-visual").innerHTML = "<p><br></p><p> </p>";
  w.document.querySelector("form").dispatchEvent(new w.Event("submit", { cancelable: true }));
  assert.strictEqual(w.document.getElementById("html").value, "");
});

const stub = () => Promise.resolve({ id: "33333333-3333-3333-3333-333333333333", url: "https://images.example.test/new.png", alt: "Uploaded", width: 100, height: 50 });

test("there is no Image button without the image storage or without the upload script", () => {
  const without = page("", { noImages: true, uploader: stub });
  assert(![...without.document.querySelectorAll("button")].some((b) => b.textContent === "Image"));
  const noScript = page("");
  assert(![...noScript.document.querySelectorAll("button")].some((b) => b.textContent === "Image"));
});

test("the picture dialog shows the library and starts without a choice", () => {
  const w = page("", { uploader: stub });
  const dialog = openDialog(w);
  assert(dialog.hasAttribute("open"));
  assert(dialog.getAttribute("aria-labelledby"));
  const choices = dialog.querySelectorAll(".image-choice");
  assert.strictEqual(choices.length, 2);
  assert(choices[0].querySelector("img").getAttribute("alt") === "The band");
  assert.strictEqual(choices[1].querySelector("img").getAttribute("alt"), "Decorative image");
  assert(dialog.textContent.includes("Only upload photos of people with their permission"));
});

test("choosing a library image fills in its description and inserts it with size and alignment", () => {
  const w = page("", { uploader: stub });
  const dialog = openDialog(w);
  dialog.querySelectorAll(".image-choice")[0].click();
  const alt = [...dialog.querySelectorAll("input[type=text]")][0];
  assert.strictEqual(alt.value, "The band");
  dialog.querySelectorAll("select")[0].value = "small";
  dialog.querySelectorAll("select")[1].value = "left";
  buttonIn(dialog, "Insert image").click();
  const img = w.document.querySelector(".editor-visual img");
  assert.strictEqual(img.getAttribute("src"), "https://images.example.test/one.png");
  assert.strictEqual(img.getAttribute("alt"), "The band");
  assert.strictEqual(img.getAttribute("width"), "800");
  assert.strictEqual(img.getAttribute("class"), "img-small align-left");
  assert.strictEqual(w.document.querySelector("dialog"), null, "the dialog is removed after closing");
});

test("a description is required unless the image is marked as decoration", () => {
  const w = page("", { uploader: stub });
  const dialog = openDialog(w);
  dialog.querySelectorAll(".image-choice")[0].click();
  const alt = [...dialog.querySelectorAll("input[type=text]")][0];
  alt.value = "   ";
  buttonIn(dialog, "Insert image").click();
  const error = dialog.querySelector(".field-error");
  assert.strictEqual(error.hidden, false);
  assert(error.textContent.includes("Describe the image"));
  assert.strictEqual(w.document.querySelector(".editor-visual img"), null);
  const decorative = dialog.querySelector("input[type=checkbox]");
  decorative.checked = true;
  decorative.dispatchEvent(new w.Event("change"));
  buttonIn(dialog, "Insert image").click();
  assert.strictEqual(w.document.querySelector(".editor-visual img").getAttribute("alt"), "");
});

test("an image of the library that is decoration comes with the decoration box ticked", () => {
  const w = page("", { uploader: stub });
  const dialog = openDialog(w);
  dialog.querySelectorAll(".image-choice")[1].click();
  assert.strictEqual(dialog.querySelector("input[type=checkbox]").checked, true);
});

test("inserting without choosing or uploading anything says so", () => {
  const w = page("", { uploader: stub });
  const dialog = openDialog(w);
  buttonIn(dialog, "Insert image").click();
  assert(dialog.querySelector(".field-error").textContent.includes("Choose an image"));
  buttonIn(dialog, "Cancel").click();
  assert.strictEqual(w.document.querySelector("dialog"), null);
  assert.strictEqual(w.document.querySelector(".editor-visual img"), null);
});

test("a new upload goes through the upload script with the description, then it is inserted", async () => {
  let called = null;
  const w = page("", { uploader: (file, alt, decorative) => { called = { file, alt, decorative }; return stub(); } });
  const dialog = openDialog(w);
  const fileInput = dialog.querySelector("input[type=file]");
  Object.defineProperty(fileInput, "files", { value: [{ name: "photo.jpg" }] });
  fileInput.dispatchEvent(new w.Event("change"));
  [...dialog.querySelectorAll("input[type=text]")][0].value = "A new photo";
  buttonIn(dialog, "Insert image").click();
  await new Promise((resolve) => setTimeout(resolve, 20));
  assert.deepStrictEqual(called, { file: { name: "photo.jpg" }, alt: "A new photo", decorative: false });
  const img = w.document.querySelector(".editor-visual img");
  assert.strictEqual(img.getAttribute("src"), "https://images.example.test/new.png");
  assert.strictEqual(img.getAttribute("alt"), "A new photo");
  assert.strictEqual(w.document.querySelector("dialog"), null);
});

test("a failed upload shows the message and keeps the dialog open", async () => {
  const w = page("", { uploader: () => Promise.reject(new Error("The file is not an acceptable image")) });
  const dialog = openDialog(w);
  const fileInput = dialog.querySelector("input[type=file]");
  Object.defineProperty(fileInput, "files", { value: [{ name: "x.jpg" }] });
  [...dialog.querySelectorAll("input[type=text]")][0].value = "x";
  buttonIn(dialog, "Insert image").click();
  await new Promise((resolve) => setTimeout(resolve, 20));
  assert(dialog.hasAttribute("open"));
  assert(dialog.querySelector(".field-error").textContent.includes("not an acceptable image"));
  assert.strictEqual(buttonIn(dialog, "Insert image").disabled, false);
});

test("images of the website's own storage are kept when saving, images from elsewhere are not", () => {
  const w = page('<p><img src="https://images.example.test/ok.png" alt="a" class="img-full align-center" width="10" height="5"></p>' +
                 '<p><img src="https://evil.example/x.png" alt="b"></p>', { uploader: stub });
  w.document.querySelector("form").dispatchEvent(new w.Event("submit", { cancelable: true }));
  const value = w.document.getElementById("html").value;
  assert(value.includes('src="https://images.example.test/ok.png"'), value);
  assert(value.includes('class="img-full align-center"'), value);
  assert(!value.includes("evil.example"), value);
});

test("without the browser feature the plain text area stays", () => {
  const dom = new JSDOM(`<form data-editor><textarea id="html" data-editor-source>x</textarea></form>`, { runScripts: "outside-only" });
  dom.window.eval(script); // no execCommand
  assert.strictEqual(dom.window.document.querySelector(".editor"), null);
});

if (failures) { console.log(failures + " check(s) failed"); process.exit(1); }
console.log("All editor checks passed");
