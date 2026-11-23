// The editor for the editable texts (EDT-3 to EDT-7). Without this script the page is a plain text
// area for HTML that works on its own. With it, admins get a visual editor with a toolbar built on
// the browser's contenteditable, and a switch to the HTML view.
//
// Nothing typed here is trusted: HTML from the HTML view is cleaned with the same allowlist as the
// server (src/main/java/.../content/internal/html/HtmlCleaner.java) in an inert document before it
// is shown in the visual editor, and the server cleans it again when it is saved. The warning
// (EDT-6) is only for convenience.
(function () {
  "use strict";

  var ALLOWED = {
    h2: 1, h3: 1, h4: 1, p: 1, br: 1, strong: 1, b: 1, em: 1, i: 1, u: 1, s: 1, strike: 1,
    ul: 1, ol: 1, li: 1, blockquote: 1, hr: 1, table: 1, thead: 1, tbody: 1, tr: 1, th: 1, td: 1,
    a: 1, img: 1
  };
  // Removed with everything inside them
  var DROPPED = {
    script: 1, style: 1, iframe: 1, object: 1, embed: 1, form: 1, input: 1, textarea: 1,
    select: 1, button: 1, svg: 1, math: 1, link: 1, meta: 1, base: 1, noscript: 1, template: 1,
    title: 1, head: 1, frame: 1, frameset: 1, applet: 1
  };
  var CLASS_TOKEN = /^(align-(left|center|right)|img-(small|medium|full))$/;

  // Returns {html, removed}: the cleaned HTML and whether anything had to be removed.
  function cleanHtml(html, imageBase) {
    var doc = new DOMParser().parseFromString(html || "", "text/html"); // inert: nothing runs
    var removed = false;
    var out = document.createDocumentFragment();
    function copyAttributes(from, to) {
      var tag = from.nodeName.toLowerCase();
      for (var i = 0; i < from.attributes.length; i++) {
        var name = from.attributes[i].name.toLowerCase();
        var value = from.attributes[i].value;
        var ok = false;
        if (tag === "a" && name === "href") {
          ok = /^(https?:|mailto:|\/(?!\/)|#)/i.test(value.trim());
        } else if (name === "class") {
          var tokens = value.split(/\s+/).filter(Boolean);
          var good = tokens.filter(function (t) { return CLASS_TOKEN.test(t); });
          if (good.length) { to.setAttribute("class", good.join(" ")); }
          ok = good.length === tokens.length;
          if (!ok) { removed = true; }
          continue;
        } else if (tag === "img" && name === "src") {
          ok = !!imageBase && value.indexOf(imageBase) === 0;
        } else if (tag === "img" && name === "alt") {
          ok = value.length <= 250;
        } else if (tag === "img" && (name === "width" || name === "height")) {
          ok = /^[0-9]{1,5}$/.test(value);
        } else if ((tag === "td" || tag === "th") && (name === "colspan" || name === "rowspan")) {
          ok = /^[1-9][0-9]?$/.test(value);
        } else if (tag === "th" && name === "scope") {
          ok = value === "col" || value === "row";
        }
        if (ok) { to.setAttribute(name, value); } else { removed = true; }
      }
    }
    function walk(node, target) {
      for (var child = node.firstChild; child; child = child.nextSibling) {
        if (child.nodeType === 3) {
          target.appendChild(document.createTextNode(child.nodeValue));
        } else if (child.nodeType === 1) {
          var tag = child.nodeName.toLowerCase();
          if (DROPPED[tag]) {
            removed = true;
          } else if (ALLOWED[tag]) {
            if (tag === "img" && !(child.getAttribute("src") || "").length) { removed = true; continue; }
            var copy = document.createElement(tag);
            copyAttributes(child, copy);
            if (tag === "img" && !copy.getAttribute("src")) { removed = true; continue; }
            if (tag === "a" && /^https?:/i.test(copy.getAttribute("href") || "")) {
              copy.setAttribute("target", "_blank");
              copy.setAttribute("rel", "noopener noreferrer");
            }
            walk(child, copy);
            target.appendChild(copy);
          } else {
            removed = true; // not allowed, but what is inside it may be
            walk(child, target);
          }
        }
      }
    }
    // The parser puts a leading <script>, <style>, <meta> or <link> into the head: not allowed either
    if (doc.head && doc.head.children.length) { removed = true; }
    walk(doc.body, out);
    var holder = document.createElement("div");
    holder.appendChild(out);
    return { html: holder.innerHTML, fragment: holder, removed: removed };
  }

  var form = document.querySelector("[data-editor]");
  if (!form || !window.DOMParser || typeof document.execCommand !== "function") {
    return; // the plain text area keeps working
  }
  var source = form.querySelector("[data-editor-source]");
  var label = form.querySelector('label[for="' + source.id + '"]');
  var hint = form.querySelector("#" + source.id + "-hint");
  var imageBase = form.getAttribute("data-image-base") || "";

  var wrapper = document.createElement("div");
  wrapper.className = "editor";
  var modes = document.createElement("div");
  modes.className = "editor-modes";
  var visualButton = makeButton("Visual", "editor-mode");
  var htmlButton = makeButton("HTML", "editor-mode");
  modes.appendChild(visualButton);
  modes.appendChild(htmlButton);
  var toolbar = document.createElement("div");
  toolbar.className = "editor-toolbar";
  toolbar.setAttribute("role", "toolbar");
  toolbar.setAttribute("aria-label", "Formatting");
  var visual = document.createElement("div");
  visual.className = "editor-visual page-text";
  visual.setAttribute("contenteditable", "true");
  visual.setAttribute("role", "textbox");
  visual.setAttribute("aria-multiline", "true");
  visual.setAttribute("aria-label", "Text");
  visual.setAttribute("spellcheck", "true");
  var warning = document.createElement("p");
  warning.className = "alert alert-warning editor-warning";
  warning.setAttribute("role", "status");
  warning.hidden = true;
  warning.textContent =
    "This HTML contains parts that are not allowed on the website. They are removed when you save.";

  source.parentNode.insertBefore(wrapper, source);
  wrapper.appendChild(modes);
  wrapper.appendChild(toolbar);
  wrapper.appendChild(visual);
  wrapper.appendChild(warning);
  wrapper.appendChild(source);

  function makeButton(text, className) {
    var button = document.createElement("button");
    button.type = "button";
    button.className = className;
    button.textContent = text;
    return button;
  }

  function command(name, argument) {
    return function () {
      visual.focus();
      document.execCommand(name, false, argument);
    };
  }

  function askForLink() {
    var address = window.prompt("Address of the link (https://..., or mailto:name@example.org)", "https://");
    if (!address) { return; }
    if (!/^(https?:\/\/|mailto:)\S+$/i.test(address.trim())) {
      window.alert("Only addresses that start with http://, https:// or mailto: are allowed.");
      return;
    }
    visual.focus();
    document.execCommand("createLink", false, address.trim());
  }

  function insertTable() {
    visual.focus();
    document.execCommand("insertHTML", false,
      "<table><tbody><tr><th scope=\"col\">Heading</th><th scope=\"col\">Heading</th></tr>" +
      "<tr><td>Text</td><td>Text</td></tr></tbody></table><p></p>");
  }

  var tools = [
    ["Bold", command("bold")], ["Italic", command("italic")], ["Underline", command("underline")],
    ["Strikethrough", command("strikeThrough")],
    ["Heading 2", command("formatBlock", "<h2>")], ["Heading 3", command("formatBlock", "<h3>")],
    ["Heading 4", command("formatBlock", "<h4>")], ["Paragraph", command("formatBlock", "<p>")],
    ["Bulleted list", command("insertUnorderedList")], ["Numbered list", command("insertOrderedList")],
    ["Quote", command("formatBlock", "<blockquote>")], ["Line", command("insertHorizontalRule")],
    ["Link", askForLink], ["Remove link", command("unlink")], ["Table", insertTable],
    ["Align left", command("justifyLeft")], ["Align center", command("justifyCenter")],
    ["Align right", command("justifyRight")], ["Clear formatting", command("removeFormat")]
  ];
  var toolButtons = tools.map(function (tool, index) {
    var button = makeButton(tool[0], "editor-button");
    button.tabIndex = index === 0 ? 0 : -1; // one tab stop, arrow keys move inside the toolbar
    button.addEventListener("mousedown", function (event) { event.preventDefault(); }); // keep the selection
    button.addEventListener("click", tool[1]);
    toolbar.appendChild(button);
    return button;
  });
  toolbar.addEventListener("keydown", function (event) {
    var index = toolButtons.indexOf(document.activeElement);
    if (index < 0) { return; }
    var next = null;
    if (event.key === "ArrowRight") { next = (index + 1) % toolButtons.length; }
    else if (event.key === "ArrowLeft") { next = (index + toolButtons.length - 1) % toolButtons.length; }
    else if (event.key === "Home") { next = 0; }
    else if (event.key === "End") { next = toolButtons.length - 1; }
    if (next === null) { return; }
    event.preventDefault();
    toolButtons[index].tabIndex = -1;
    toolButtons[next].tabIndex = 0;
    toolButtons[next].focus();
  });

  // From the visual editor to HTML: alignment becomes a class, divs become paragraphs
  function serialize() {
    var copy = visual.cloneNode(true);
    var all = copy.querySelectorAll("*");
    for (var i = 0; i < all.length; i++) {
      var element = all[i];
      var align = (element.style && element.style.textAlign) || element.getAttribute("align") || "";
      if (/^(left|center|right)$/.test(align)) { element.classList.add("align-" + align); }
      element.removeAttribute("style");
      element.removeAttribute("align");
    }
    var divs = copy.querySelectorAll("div");
    for (var j = 0; j < divs.length; j++) {
      var paragraph = document.createElement("p");
      while (divs[j].firstChild) { paragraph.appendChild(divs[j].firstChild); }
      if (divs[j].getAttribute("class")) { paragraph.setAttribute("class", divs[j].getAttribute("class")); }
      divs[j].parentNode.replaceChild(paragraph, divs[j]);
    }
    var cleaned = cleanHtml(copy.innerHTML, imageBase).html;
    var hasContent = /\S/.test(copy.textContent) || copy.querySelector("img, hr, table");
    return hasContent ? cleaned : "";
  }

  var mode = "visual";

  function showVisual() {
    var result = cleanHtml(source.value, imageBase);
    visual.replaceChildren.apply(visual, Array.prototype.slice.call(result.fragment.childNodes));
    mode = "visual";
    source.hidden = true;
    visual.hidden = false;
    toolbar.hidden = false;
    warning.hidden = true;
    visualButton.setAttribute("aria-pressed", "true");
    htmlButton.setAttribute("aria-pressed", "false");
    if (label) { label.textContent = "Text"; }
    if (hint) { hint.hidden = true; }
  }

  function showHtml() {
    if (mode === "visual") { source.value = serialize(); }
    mode = "html";
    visual.hidden = true;
    toolbar.hidden = true;
    source.hidden = false;
    visualButton.setAttribute("aria-pressed", "false");
    htmlButton.setAttribute("aria-pressed", "true");
    if (label) { label.textContent = "Text (HTML)"; }
    if (hint) { hint.hidden = false; }
    checkHtml();
  }

  function checkHtml() {
    warning.hidden = !cleanHtml(source.value, imageBase).removed; // EDT-6
  }

  visualButton.addEventListener("click", function () { if (mode !== "visual") { showVisual(); visual.focus(); } });
  htmlButton.addEventListener("click", function () { if (mode !== "html") { showHtml(); source.focus(); } });
  source.addEventListener("input", checkHtml);

  // Both Save and Preview submit the form: the visual text goes into the field first
  form.addEventListener("submit", function () {
    if (mode === "visual") { source.value = serialize(); }
  });

  document.execCommand("styleWithCSS", false, false);
  document.execCommand("defaultParagraphSeparator", false, "p");
  showVisual();

})();
