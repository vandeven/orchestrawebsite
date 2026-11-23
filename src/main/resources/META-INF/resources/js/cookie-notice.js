// The short notice about cookies (LEG-6). The website only sets cookies it needs to work, so no
// choice is asked: the notice is shown until someone presses OK. That is remembered in the
// browser's local storage, which is not a cookie and is never sent to the server. If storage is
// not available the notice simply shows on every page.
(function () {
  "use strict";
  var notice = document.querySelector("[data-cookie-notice]");
  var ok = document.querySelector("[data-cookie-notice-ok]");
  if (!notice || !ok) {
    return;
  }
  var KEY = "cookie-notice-seen";
  var seen = false;
  try {
    seen = window.localStorage.getItem(KEY) === "1";
  } catch (e) {
    seen = false;
  }
  if (!seen) {
    notice.hidden = false;
  }
  ok.addEventListener("click", function () {
    notice.hidden = true;
    try {
      window.localStorage.setItem(KEY, "1");
    } catch (e) {
      // not remembered: the notice shows again on the next page
    }
  });
})();
