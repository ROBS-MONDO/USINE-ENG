(function () {
  var $ = function (id) { return document.getElementById(id); };
  var login = $("login"), scanner = $("scanner"), search = $("search");
  var video = $("video"), canvas = $("canvas"), ctx = canvas.getContext("2d", { willReadFrequently: true });
  var stream = null, scanning = false, busy = false, detector = null, lastCode = "", lastAt = 0;

  // ---- lingue: italiano, inglese, russo ----
  var STR = {
    it: { title: "Controllo ingressi", password: "Password staff", enter: "Entra", search: "Cerca", logout: "Esci", hint: "Inquadra il QR del biglietto", next: "Prossimo", back: "← Scanner", export: "Scarica elenco CSV", searchph: "Nome, email o codice (min. 2 lettere)",
      entered: "Entrati", valid: "✔ VALIDO", used: "GIÀ USATO", cancelled: "ANNULLATO", invalid: "NON VALIDO", error: "ERRORE", nonet: "NESSUNA RETE", ticketOf: "biglietto {0} di {1}",
      usedAt: "Ingresso già registrato: {0}", cancelledD: "Biglietto rimborsato/annullato", invalidD: "Codice non riconosciuto", retry: "Riprova", checkNet: "Controlla la connessione e riprova",
      nocam: "Fotocamera non disponibile: consenti l'accesso alla fotocamera oppure usa «Cerca».", offline: "Connessione assente",
      tagValid: "valido", tagCancelled: "annullato", tagIn: "entrato", entry: "Ingresso", resend: "Reinvia", confirmResend: "Reinviare i biglietti a {0}?", sent: "Email inviata",
      e_bad_password: "Password errata", e_too_many: "Troppi tentativi, riprova tra qualche minuto", e_login_required: "Accesso richiesto", e_not_found: "Biglietto non trovato", e_server_error: "Errore del server", e_bad_action: "Azione sconosciuta", e_forbidden: "Non consentito" },
    en: { title: "Entrance check", password: "Staff password", enter: "Sign in", search: "Search", logout: "Sign out", hint: "Point the camera at the ticket QR code", next: "Next", back: "← Scanner", export: "Download CSV list", searchph: "Name, email or code (min. 2 letters)",
      entered: "Checked in", valid: "✔ VALID", used: "ALREADY USED", cancelled: "CANCELLED", invalid: "NOT VALID", error: "ERROR", nonet: "NO NETWORK", ticketOf: "ticket {0} of {1}",
      usedAt: "Already checked in: {0}", cancelledD: "Ticket refunded/cancelled", invalidD: "Code not recognised", retry: "Try again", checkNet: "Check your connection and try again",
      nocam: "Camera not available: allow camera access or use “Search”.", offline: "No connection",
      tagValid: "valid", tagCancelled: "cancelled", tagIn: "in", entry: "Check in", resend: "Resend", confirmResend: "Resend the tickets to {0}?", sent: "Email sent",
      e_bad_password: "Wrong password", e_too_many: "Too many attempts, try again in a few minutes", e_login_required: "Sign-in required", e_not_found: "Ticket not found", e_server_error: "Server error", e_bad_action: "Unknown action", e_forbidden: "Not allowed" },
    ru: { title: "Контроль входа", password: "Пароль сотрудника", enter: "Войти", search: "Поиск", logout: "Выйти", hint: "Наведите камеру на QR-код билета", next: "Далее", back: "← Сканер", export: "Скачать список CSV", searchph: "Имя, email или код (от 2 букв)",
      entered: "Вошли", valid: "✔ ДЕЙСТВИТЕЛЕН", used: "УЖЕ ИСПОЛЬЗОВАН", cancelled: "АННУЛИРОВАН", invalid: "НЕДЕЙСТВИТЕЛЕН", error: "ОШИБКА", nonet: "НЕТ СЕТИ", ticketOf: "билет {0} из {1}",
      usedAt: "Вход уже отмечен: {0}", cancelledD: "Билет возвращён/аннулирован", invalidD: "Код не распознан", retry: "Повторите", checkNet: "Проверьте соединение и повторите",
      nocam: "Камера недоступна: разрешите доступ к камере или используйте «Поиск».", offline: "Нет соединения",
      tagValid: "действителен", tagCancelled: "аннулирован", tagIn: "вошёл", entry: "Вход", resend: "Отправить снова", confirmResend: "Отправить билеты снова на {0}?", sent: "Письмо отправлено",
      e_bad_password: "Неверный пароль", e_too_many: "Слишком много попыток, повторите через несколько минут", e_login_required: "Требуется вход", e_not_found: "Билет не найден", e_server_error: "Ошибка сервера", e_bad_action: "Неизвестное действие", e_forbidden: "Запрещено" }
  };
  var lang = "it";
  try { lang = localStorage.getItem("lf-scan-lang") || ""; } catch (e) {}
  if (!STR[lang]) { var nl = (navigator.language || "it").slice(0, 2).toLowerCase(); lang = STR[nl] ? nl : "it"; }
  function t(k) {
    var s = (STR[lang] && STR[lang][k]) || STR.it[k] || k, a = arguments;
    return s.replace(/\{(\d)\}/g, function (m, i) { return a[+i + 1]; });
  }
  function err(j) { return j && j.error ? t("e_" + j.error) : t("error"); }
  function applyLang() {
    document.documentElement.lang = lang;
    Array.prototype.forEach.call(document.querySelectorAll("[data-i18n]"), function (el) { el.textContent = t(el.getAttribute("data-i18n")); });
    Array.prototype.forEach.call(document.querySelectorAll("[data-i18n-ph]"), function (el) { el.placeholder = t(el.getAttribute("data-i18n-ph")); });
    $("lang").value = lang; $("lang2").value = lang; $("lang3").value = lang;
    document.title = t("title");
    refreshStats();
  }
  function setLang(l) { lang = l; try { localStorage.setItem("lf-scan-lang", l); } catch (e) {} applyLang(); if (!search.hidden) { $("q").dispatchEvent(new Event("input")); } }
  $("lang").addEventListener("change", function () { setLang(this.value); });
  $("lang2").addEventListener("change", function () { setLang(this.value); });
  $("lang3").addEventListener("change", function () { setLang(this.value); });

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
      if (j.ok) { $("pw").value = ""; show("scanner"); } else { $("login-msg").textContent = err(j); }
    }).catch(function () { $("login-msg").textContent = t("offline"); });
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
      $("hint").textContent = t("nocam");
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
      var multi = j.of > 1 ? " · " + t("ticketOf", j.seq, j.of) : "";
      if (j.result === "ok") { showResult("ok", t("valid"), j.name, j.code + multi); }
      else if (j.result === "used") { showResult("used", t("used"), j.name, t("usedAt", j.at) + " · " + j.code); }
      else if (j.result === "cancelled") { showResult("bad", t("cancelled"), j.name, t("cancelledD") + " · " + j.code); }
      else if (j.result === "invalid") { showResult("bad", t("invalid"), "", t("invalidD")); }
      else if (j._status === 401) { busy = false; }
      else { showResult("bad", t("error"), "", err(j)); }
      refreshStats();
    }).catch(function () { showResult("bad", t("nonet"), "", t("checkNet")); });
  }
  $("btn-next").addEventListener("click", function () { $("result").hidden = true; busy = false; lastCode = ""; if (!search.hidden) { return; } });

  function refreshStats() {
    api("stats").then(function (j) { if (j.total !== undefined) { $("stats").textContent = t("entered") + " " + j.inside + " / " + j.total; } }).catch(function () {});
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
      if (it.status !== "valid") { tag.className += " x"; tag.textContent = t("tagCancelled"); }
      else if (it.checked_in_at) { tag.className += " in"; tag.textContent = t("tagIn") + " " + it.checked_in_at.slice(11, 16); }
      else { tag.textContent = t("tagValid"); }
      li.appendChild(who); li.appendChild(tag);
      if (it.status === "valid" && !it.checked_in_at) {
        var bi = document.createElement("button"); bi.type = "button"; bi.textContent = t("entry");
        bi.addEventListener("click", function () { show("scanner"); busy = true; lastCode = it.code; lastAt = Date.now(); checkin(it.code); });
        li.appendChild(bi);
      }
      var br = document.createElement("button"); br.type = "button"; br.className = "ghost"; br.textContent = t("resend");
      br.addEventListener("click", function () {
        if (!confirm(t("confirmResend", it.email))) { return; }
        api("resend", { code: it.code }).then(function (j) { alert(j.ok ? t("sent") : err(j)); });
      });
      li.appendChild(br);
      ul.appendChild(li);
    });
  }

  // ---- avvio ----
  applyLang();
  api("me").then(function (j) { show(j.staff ? "scanner" : "login"); }).catch(function () { show("login"); });
})();
