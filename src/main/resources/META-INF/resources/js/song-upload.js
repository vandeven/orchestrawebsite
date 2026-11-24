// Adding a file to a song (MEM-15, MEM-17, MEM-18). The browser checks the type and the size, asks
// the server for an address signed for this one file, sends the file straight to the storage, and
// tells the server when it is there. The server looks at the file itself before it is used: the
// checks here are only for convenience. Without this script files cannot be added.
(function () {
  "use strict";

  var MAX_BYTES = 15 * 1024 * 1024;
  var form = document.querySelector("[data-song-upload]");
  if (!form || !window.fetch || !window.XMLHttpRequest) {
    return;
  }
  form.hidden = false;
  var base = "/songs/manage/" + form.getAttribute("data-song-id");
  var type = form.querySelector("#upload-type");
  var file = form.querySelector("#upload-file");
  var name = form.querySelector("#upload-name");
  var error = form.querySelector("#upload-error");
  var status = form.querySelector("#upload-status");
  var progress = form.querySelector("#upload-progress");
  var button = form.querySelector("button[type=submit]");

  function extensionOf(fileName) {
    var dot = fileName.lastIndexOf(".");
    return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase();
  }

  function postJson(path, body) {
    return fetch(path, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify(body)
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (data) {
        if (!response.ok) {
          throw new Error(data && data.error ? data.error
            : response.status === 429 ? "You added too many files. Try again later."
            : "Something went wrong. Try again.");
        }
        return data;
      });
    });
  }

  // A PUT with progress, which fetch cannot report
  function put(url, headers, blob) {
    return new Promise(function (resolve, reject) {
      var request = new XMLHttpRequest();
      request.open("PUT", url);
      Object.keys(headers).forEach(function (key) { request.setRequestHeader(key, headers[key]); });
      request.upload.onprogress = function (event) {
        if (event.lengthComputable) { progress.value = Math.round(100 * event.loaded / event.total); }
      };
      request.onload = function () {
        if (request.status >= 200 && request.status < 300) { resolve(); }
        else { reject(new Error("The file could not be sent to the storage. Try again.")); }
      };
      request.onerror = function () { reject(new Error("The file could not be sent to the storage. Try again.")); };
      request.send(blob);
    });
  }

  function fail(message) {
    status.hidden = true;
    progress.hidden = true;
    error.textContent = message;
    error.hidden = false;
    error.setAttribute("tabindex", "-1");
    error.focus();
    button.disabled = false;
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    error.hidden = true;
    var chosen = file.files[0];
    var wanted = type.options[type.selectedIndex].getAttribute("data-extension");
    if (!chosen) { return fail("Choose a file."); }
    if (extensionOf(chosen.name) !== wanted) { return fail("This type needs a file that ends in ." + wanted + "."); }
    if (chosen.size < 1 || chosen.size > MAX_BYTES) { return fail("The file must be from 1 byte to 15 MB."); }
    if (!name.value.trim()) { return fail("Give the file a name that members will see."); }
    button.disabled = true;
    status.hidden = false;
    status.textContent = "Uploading...";
    progress.hidden = false;
    progress.value = 0;
    var ticket;
    postJson(base + "/files/upload-url", { type: type.value, originalName: chosen.name, size: chosen.size })
      .then(function (result) {
        ticket = result;
        return put(ticket.uploadUrl, ticket.headers, chosen);
      })
      .then(function () {
        status.textContent = "Checking the file...";
        return postJson(base + "/files/" + ticket.fileId + "/complete",
          { type: type.value, originalName: chosen.name, displayName: name.value.trim() });
      })
      .then(function () { window.location.assign(base + "?notice=file-added"); })
      .catch(function (problem) { fail(problem.message); });
  });
})();
