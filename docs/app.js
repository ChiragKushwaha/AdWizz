// AdWizz site: theme toggle (sun / moon) and the live version label from update.json.
(function () {
  var root = document.documentElement;
  var btn = document.getElementById("theme");

  function isDark() {
    var t = root.dataset.theme;
    return t ? t === "dark" : window.matchMedia("(prefers-color-scheme: dark)").matches;
  }
  function paint() {
    btn.classList.toggle("night", isDark());
    btn.setAttribute("aria-label", isDark() ? "Switch to light mode" : "Switch to dark mode");
  }
  btn.addEventListener("click", function () {
    var next = isDark() ? "light" : "dark";
    root.dataset.theme = next;
    try { localStorage.setItem("theme", next); } catch (e) {}
    paint();
  });
  paint();

  // The release script publishes update.json; show the real version and size next to the button.
  fetch("update.json", { cache: "no-store" })
    .then(function (r) { return r.ok ? r.json() : null; })
    .then(function (u) {
      if (!u) return;
      var mb = u.sizeBytes ? " · " + (u.sizeBytes / 1048576).toFixed(1) + " MB" : "";
      document.getElementById("version").textContent = "v" + u.versionName + mb + " · Android 6.0+ · free & open source";
      document.getElementById("download").href = u.apkUrl;
    })
    .catch(function () {});
})();
