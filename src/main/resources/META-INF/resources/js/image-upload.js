// Adding an image (EDT-9 to EDT-12, HOS-14). The browser makes the image smaller and removes its
// metadata (location, camera) by drawing it again, then sends it straight to the image storage with
// an address the server signed, and tells the server when it is there. The server looks at the file
// itself before it is used: nothing here is trusted. Without this script images cannot be added.
(function () {
  "use strict";

  var MAX_WIDTH = 2000;
  var MAX_BYTES = 10 * 1024 * 1024;

  // The file is read with createImageBitmap, which needs no address. Giving an <img> a blob: address
  // would be blocked by the Content-Security-Policy, which only allows pictures from this website
  // and the images bucket (HRD-6).
  function resize(file) {
    if (typeof window.createImageBitmap !== "function") {
      return Promise.reject(new Error(
        "This browser cannot prepare images. Use a recent version of Chrome, Firefox, Edge or Safari."));
    }
    return window.createImageBitmap(file).then(function (bitmap) {
      var scale = Math.min(1, MAX_WIDTH / bitmap.width);
      var width = Math.max(1, Math.round(bitmap.width * scale));
      var height = Math.max(1, Math.round(bitmap.height * scale));
      var canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      canvas.getContext("2d").drawImage(bitmap, 0, 0, width, height);
      if (bitmap.close) { bitmap.close(); }
      return new Promise(function (resolve, reject) {
        // WebP where the browser can make it; otherwise keep PNG (transparency) or JPEG
        canvas.toBlob(function (webp) {
          if (webp && webp.type === "image/webp") {
            resolve({ blob: webp, width: width, height: height });
            return;
          }
          var type = file.type === "image/png" ? "image/png" : "image/jpeg";
          canvas.toBlob(function (blob) {
            if (!blob) {
              reject(new Error("This image could not be prepared."));
            } else {
              resolve({ blob: blob, width: width, height: height });
            }
          }, type, 0.85);
        }, "image/webp", 0.85);
      });
    }, function () {
      throw new Error("This file is not an image the browser can read. Use a JPEG, PNG or WebP photo "
        + "(a photo in another format, such as HEIC, has to be saved as JPEG first).");
    });
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
          var message = data && data.error;
          if (!message) {
            message = response.status === 429
              ? "You added too many images. Try again later."
              : response.status === 403 ? "You are not allowed to add images." : "Something went wrong. Try again.";
          }
          throw new Error(message);
        }
        return data;
      });
    });
  }

  // Resolves with {id, url, alt, width, height} of the new image of the library
  function upload(file, alt, decorative) {
    if (!file) { return Promise.reject(new Error("Choose an image.")); }
    if (!decorative && (!alt || !alt.trim())) {
      return Promise.reject(new Error("Describe the image, or mark it as decoration."));
    }
    var prepared;
    return resize(file).then(function (result) {
      prepared = result;
      if (prepared.blob.size > MAX_BYTES) { throw new Error("The image is still over 10 MB after making it smaller."); }
      return postJson("/edit/images/upload-url", { contentType: prepared.blob.type, size: prepared.blob.size });
    }).then(function (ticket) {
      return fetch(ticket.uploadUrl, { method: "PUT", headers: ticket.headers, body: prepared.blob })
        .then(function (response) {
          if (!response.ok) { throw new Error("The image could not be sent to the storage. Try again."); }
          return postJson("/edit/images/" + ticket.id + "/complete", {
            contentType: prepared.blob.type,
            alt: decorative ? "" : alt.trim(),
            decorative: !!decorative
          });
        });
    });
  }

  window.OrchestraImages = { upload: upload };

  var form = document.querySelector("[data-image-upload]");
  if (!form || !window.fetch || !window.URL || !document.createElement("canvas").getContext) {
    return;
  }
  form.hidden = false;
  var file = form.querySelector("#image-file");
  var alt = form.querySelector("#image-alt");
  var decorative = form.querySelector("#image-decorative");
  var error = form.querySelector("#image-error");
  var status = form.querySelector("#image-status");

  decorative.addEventListener("change", function () {
    alt.disabled = decorative.checked;
    if (decorative.checked) { alt.value = ""; }
  });

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    error.hidden = true;
    status.hidden = false;
    status.textContent = "Uploading...";
    var button = form.querySelector("button[type=submit]");
    button.disabled = true;
    upload(file.files[0], alt.value, decorative.checked).then(function () {
      window.location.assign("/edit/images?notice=added");
    }).catch(function (problem) {
      status.hidden = true;
      error.textContent = problem.message;
      error.hidden = false;
      button.disabled = false;
      error.focus && error.setAttribute("tabindex", "-1");
      error.focus();
    });
  });
})();
