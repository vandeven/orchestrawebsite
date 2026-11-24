// Keeps the audio players of the member page working (MEM-7, MEM-8). The address of a recording is
// a page of this website that sends the browser on to a short-lived signed address of the file
// storage. When that address has run out, for example after a long pause, the browser's request
// fails. Then the player asks for a new one and carries on from the same moment.
(function () {
  "use strict";

  var MAX_AGE_MS = 10 * 60 * 1000; // a signed address lasts 15 minutes: ask for a new one before
  var MAX_RETRIES = 3;

  function setUp(audio) {
    var source = audio.getAttribute("src");
    var loadedAt = Date.now();
    var retries = 0;
    var resumeAt = null;
    var resumePlaying = false;

    function reload() {
      resumeAt = audio.currentTime || 0;
      resumePlaying = !audio.paused;
      audio.setAttribute("src", source + (source.indexOf("?") < 0 ? "?" : "&") + "t=" + Date.now());
      loadedAt = Date.now();
      audio.load();
    }

    audio.addEventListener("loadedmetadata", function () {
      if (resumeAt !== null) {
        audio.currentTime = resumeAt;
        resumeAt = null;
        if (resumePlaying) {
          var started = audio.play();
          if (started && started.catch) { started.catch(function () {}); }
        }
        retries = 0;
      }
    });

    // A long pause: get a fresh address before playing again, so playback does not stall
    audio.addEventListener("play", function () {
      if (Date.now() - loadedAt > MAX_AGE_MS && audio.readyState < 4) { reload(); }
    });

    audio.addEventListener("error", function () {
      if (retries < MAX_RETRIES) {
        retries += 1;
        reload();
      }
    });

    // Only one recording at a time
    audio.addEventListener("playing", function () {
      Array.prototype.forEach.call(document.querySelectorAll("audio"), function (other) {
        if (other !== audio && !other.paused) { other.pause(); }
      });
    });
  }

  Array.prototype.forEach.call(document.querySelectorAll("audio[src]"), setUp);
})();
