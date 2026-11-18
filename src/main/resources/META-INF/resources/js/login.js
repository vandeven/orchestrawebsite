// Shows or hides the password on the login page (LOG-2). Without this script the button stays
// hidden and the form works as it is.
(function () {
  "use strict";
  var button = document.querySelector("[data-toggle-password]");
  if (!button) {
    return;
  }
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
})();
