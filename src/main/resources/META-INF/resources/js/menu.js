// The phone menu button (DES-7). Without this script the menu is a plain list that is always
// visible, so nothing depends on it. It touches only the elements with the data attributes below.
(function () {
  "use strict";
  document.documentElement.classList.add("js");

  document.addEventListener("DOMContentLoaded", function () {
    var nav = document.querySelector("[data-site-nav]");
    var toggle = document.querySelector("[data-site-nav-toggle]");
    if (!nav || !toggle) {
      return;
    }
    toggle.addEventListener("click", function () {
      var open = nav.classList.toggle("is-open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && nav.classList.contains("is-open")) {
        nav.classList.remove("is-open");
        toggle.setAttribute("aria-expanded", "false");
        toggle.focus();
      }
    });
  });
})();
