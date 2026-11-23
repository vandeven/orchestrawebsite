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

function page(initialHtml) {
  const dom = new JSDOM(
    `<form data-editor data-image-base="https://images.example.test/" method="post">
       <div class="field"><label for="html">Text (HTML)</label><p id="html-hint">hint</p>
       <textarea id="html" name="html" data-editor-source></textarea></div>
       <button type="submit">Save</button></form>`,
    { runScripts: "outside-only" });
  const { window } = dom;
  window.document.execCommand = () => true; // jsdom has none; the real browser does
  window.document.getElementById("html").value = initialHtml;
  window.eval(script);
  return window;
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

test("without the browser feature the plain text area stays", () => {
  const dom = new JSDOM(`<form data-editor><textarea id="html" data-editor-source>x</textarea></form>`, { runScripts: "outside-only" });
  dom.window.eval(script); // no execCommand
  assert.strictEqual(dom.window.document.querySelector(".editor"), null);
});

if (failures) { console.log(failures + " check(s) failed"); process.exit(1); }
console.log("All editor checks passed");
