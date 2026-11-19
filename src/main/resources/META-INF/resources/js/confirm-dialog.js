// The shared confirmation dialog for actions that delete or remove something (DES-13). Every such
// link also works without this script: it opens a confirmation page. With the script the same
// question is asked in a native <dialog>. Cancel has the focus when it opens, Escape and Cancel
// close it without doing anything, and the focus goes back to the link that opened it.
(function () {
  "use strict";
  var dialog = document.getElementById("confirm-dialog");
  if (!dialog || typeof dialog.showModal !== "function") {
    return;
  }
  var form = dialog.querySelector("form");
  var title = document.getElementById("confirm-title");
  var text = document.getElementById("confirm-text");
  var submit = dialog.querySelector("[data-confirm-submit]");
  var cancel = dialog.querySelector("[data-confirm-cancel]");
  var opener = null;

  // The form is only ever sent to a path on this website, never to another site or to a
  // javascript: address, whatever the attribute says.
  function safeAction(value) {
    if (typeof value !== "string" || value.charAt(0) !== "/" || value.charAt(1) === "/") {
      return null;
    }
    var url = new URL(value, window.location.origin);
    return url.origin === window.location.origin ? url.pathname + url.search : null;
  }

  document.addEventListener("click", function (event) {
    var link = event.target.closest("[data-confirm]");
    if (!link) {
      return;
    }
    var action = safeAction(link.getAttribute("data-confirm-action"));
    if (!action) {
      return; // not a path on this website: the link works as a plain link, with the confirm page
    }
    event.preventDefault();
    opener = link;
    title.textContent = link.getAttribute("data-confirm-title");
    text.textContent = link.getAttribute("data-confirm-text");
    submit.textContent = link.getAttribute("data-confirm-button");
    form.setAttribute("action", action);
    dialog.showModal();
    cancel.focus();
  });

  cancel.addEventListener("click", function () {
    dialog.close();
  });

  dialog.addEventListener("close", function () {
    if (opener) {
      opener.focus();
    }
  });
})();
