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

  document.addEventListener("click", function (event) {
    var link = event.target.closest("[data-confirm]");
    if (!link) {
      return;
    }
    event.preventDefault();
    opener = link;
    title.textContent = link.getAttribute("data-confirm-title");
    text.textContent = link.getAttribute("data-confirm-text");
    submit.textContent = link.getAttribute("data-confirm-button");
    form.setAttribute("action", link.getAttribute("data-confirm-action"));
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
