// Shows or hides a password (LOG-2) on the login page and the set new password page. Without this
// script the buttons stay hidden and the forms work as they are.
(function () {
  "use strict";
  var buttons = document.querySelectorAll("[data-toggle-password]");
  Array.prototype.forEach.call(buttons, function (button) {
    var field = document.getElementById(button.getAttribute("aria-controls"));
    if (!field) {
      return;
    }
    button.hidden = false;
    button.addEventListener("click", function () {
      var show = field.type === "password";
      field.type = show ? "text" : "password";
      button.textContent = show ? "Hide password" : "Show password";
      button.setAttribute("aria-pressed", show ? "true" : "false");
    });
  });
})();
