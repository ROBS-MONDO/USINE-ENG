(function () {
  var $ = function (id) { return document.getElementById(id); };
  var login = $("login"), scanner = $("scanner"), search = $("search");
  var video = $("video"), canvas = $("canvas"), ctx = canvas.getContext("2d", { willReadFrequently: true });
  var stream = null, scanning = false, busy = false, detector = null, lastCode = "", lastAt = 0;

  function api(action, data, method) {
    var opt = { method: method || (data ? "POST" : "GET"), credentials: "same-origin", headers: { "X-Requested-With": "lf-scan" } };
    var url = "api.php?action=" + encodeURIComponent(action);
    if (data && opt.method === "POST") {
      var f = new FormData(); Object.keys(data).forEach(function (k) { f.append(k, data[k]); }); opt.body = f;
    } else if (data) {
      url += "&" + Object.keys(data).map(function (k) { return k + "=" + encodeURIComponent(data[k]); }).join("&");
    }
    return fetch(url, opt).then(function (r) {
      return r.json().then(function (j) { if (r.status === 401 && action !== "login") { show("login"); } j._status = r.status; return j; });
    });
  }

  function show(which) {
    login.hidden = which !== "login"; scanner.hidden = which !== "scanner"; search.hidden = which !== "search";
    if (which === "scanner") { startCamera(); refreshStats(); } else { stopCamera(); }
  }

  // ---- login ----
  $("login-form").addEventListener("submit", function (e) {
    e.preventDefault();
    $("login-msg").textContent = "";
    api("login", { password: $("pw").value }).then(function (j) {
      if (j.ok) { $("pw").value = ""; show("scanner"); } else { $("login-msg").textContent = j.error || "Errore"; }
    }).catch(function () { $("login-msg").textContent = "Connessione assente"; });
  });
  $("btn-logout").addEventListener("click", function () { api("logout", {}).then(function () { show("login"); }); });

  // ---- camera e lettura QR ----
  function startCamera() {
    if (stream) { scanning = true; return; }
    navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 } }, audio: false }).then(function (s) {
      stream = s; video.srcObject = s; return video.play();
    }).then(function () {
      scanning = true;
      if ("BarcodeDetector" in window) { try { detector = new BarcodeDetector({ formats: ["qr_code"] }); } catch (e) { detector = null; } }
      requestAnimationFrame(tick);
    }).catch(function () {
      $("hint").textContent = "Fotocamera non disponibile: consenti l'accesso alla fotocamera oppure usa «Cerca».";
    });
  }
  function stopCamera() {
    scanning = false;
    if (stream) { stream.getTracks().forEach(function (t) { t.stop(); }); stream = null; video.srcObject = null; }
  }
  var lastTick = 0;
  function tick(ts) {
    if (!stream) { return; }
    requestAnimationFrame(tick);
    if (!scanning || busy || video.readyState < 2 || ts - lastTick < 120) { return; }
    lastTick = ts;
    if (detector) {
      detector.detect(video).then(function (r) { if (r && r.length) { onCode(r[0].rawValue); } }).catch(function () { detector = null; });
      return;
    }
    var w = video.videoWidth, h = video.videoHeight; if (!w) { return; }
    var scale = Math.min(1, 640 / w);
    canvas.width = Math.round(w * scale); canvas.height = Math.round(h * scale);
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    var img = ctx.getImageData(0, 0, canvas.width, canvas.height);
    var res = window.jsQR && jsQR(img.data, img.width, img.height, { inversionAttempts: "dontInvert" });
    if (res && res.data) { onCode(res.data); }
  }

  function beep(ok) {
    try {
      var A = window.AudioContext || window.webkitAudioContext; if (!A) { return; }
      var a = new A(), o = a.createOscillator(), g = a.createGain();
      o.frequency.value = ok ? 880 : 220; o.connect(g); g.connect(a.destination); g.gain.value = .15;
      o.start(); o.stop(a.currentTime + (ok ? .15 : .45));
    } catch (e) {}
    if (navigator.vibrate) { navigator.vibrate(ok ? 80 : [200, 80, 200]); }
  }

  function onCode(code) {
    code = String(code || "").trim();
    var now = Date.now();
    if (!code || (code === lastCode && now - lastAt < 4000)) { return; }
    lastCode = code; lastAt = now; busy = true;
    checkin(code);
  }

  function showResult(cls, title, name, detail) {
    var r = $("result"); r.className = cls; r.hidden = false;
    $("result-title").textContent = title; $("result-name").textContent = name || ""; $("result-detail").textContent = detail || "";
    beep(cls === "ok");
  }

  function checkin(code) {
    api("checkin", { code: code }).then(function (j) {
      var multi = j.of > 1 ? " · biglietto " + j.seq + " di " + j.of : "";
      if (j.result === "ok") { showResult("ok", "✔ VALIDO", j.name, j.code + multi); }
      else if (j.result === "used") { showResult("used", "GIÀ USATO", j.name, "Ingresso già registrato: " + j.at + " · " + j.code); }
      else if (j.result === "cancelled") { showResult("bad", "ANNULLATO", j.name, "Biglietto rimborsato/annullato · " + j.code); }
      else if (j.result === "invalid") { showResult("bad", "NON VALIDO", "", "Codice non riconosciuto"); }
      else if (j._status === 401) { busy = false; }
      else { showResult("bad", "ERRORE", "", j.error || "Riprova"); }
      refreshStats();
    }).catch(function () { showResult("bad", "NESSUNA RETE", "", "Controlla la connessione e riprova"); });
  }
  $("btn-next").addEventListener("click", function () { $("result").hidden = true; busy = false; lastCode = ""; if (!search.hidden) { return; } });

  function refreshStats() {
    api("stats").then(function (j) { if (j.total !== undefined) { $("stats").textContent = "Entrati " + j.inside + " / " + j.total; } }).catch(function () {});
  }

  // ---- ricerca manuale ----
  $("btn-list").addEventListener("click", function () { show("search"); $("q").focus(); });
  $("btn-back").addEventListener("click", function () { show("scanner"); });
  var timer = null;
  $("q").addEventListener("input", function () {
    clearTimeout(timer);
    var q = this.value;
    timer = setTimeout(function () {
      api("search", { q: q }, "GET").then(function (j) { renderItems(j.items || []); });
    }, 250);
  });
  function renderItems(items) {
    var ul = $("items"); ul.textContent = "";
    items.forEach(function (it) {
      var li = document.createElement("li");
      var who = document.createElement("div"); who.className = "who";
      var b = document.createElement("b"); b.textContent = it.holder_name;
      var s = document.createElement("small"); s.textContent = it.email + " · " + it.code;
      who.appendChild(b); who.appendChild(s);
      var tag = document.createElement("span"); tag.className = "tag";
      if (it.status !== "valid") { tag.className += " x"; tag.textContent = "annullato"; }
      else if (it.checked_in_at) { tag.className += " in"; tag.textContent = "entrato " + it.checked_in_at.slice(11, 16); }
      else { tag.textContent = "valido"; }
      li.appendChild(who); li.appendChild(tag);
      if (it.status === "valid" && !it.checked_in_at) {
        var bi = document.createElement("button"); bi.type = "button"; bi.textContent = "Ingresso";
        bi.addEventListener("click", function () { show("scanner"); busy = true; lastCode = it.code; lastAt = Date.now(); checkin(it.code); });
        li.appendChild(bi);
      }
      var br = document.createElement("button"); br.type = "button"; br.className = "ghost"; br.textContent = "Reinvia";
      br.addEventListener("click", function () {
        if (!confirm("Reinviare i biglietti a " + it.email + "?")) { return; }
        api("resend", { code: it.code }).then(function (j) { alert(j.ok ? "Email inviata" : (j.error || "Errore")); });
      });
      li.appendChild(br);
      ul.appendChild(li);
    });
  }

  // ---- avvio ----
  api("me").then(function (j) { show(j.staff ? "scanner" : "login"); }).catch(function () { show("login"); });
})();
