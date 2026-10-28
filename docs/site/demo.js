// A web replica of the OwnYourTV home screen: the dock moves with the arrow keys, a swipe or a click, and the
// aurora takes the colour of the app in front, as on the TV. Brand names are written out, never their logos.
(function () {
  var el = document.getElementById("demo");
  if (!el) return;
  var still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var apps = [
    { n: "Netflix", c: "#e50914", bg: "#141414", fg: "#e50914", f: "condensed" },
    { n: "YouTube", c: "#ff2b2b", bg: "#f4f4f0", fg: "#1a1a1a" },
    { n: "Prime Video", c: "#1fb8ff", bg: "#0f2b46", fg: "#ffffff" },
    { n: "Disney+", c: "#3d6bff", bg: "#0d1b4d", fg: "#ffffff" },
    { n: "Spotify", c: "#1ed760", bg: "#121212", fg: "#1ed760" },
    { n: "Plex", c: "#e5a00d", bg: "#1f1f1f", fg: "#e5a00d" },
    { all: true, c: "#7b6cff", bg: "#2a2464", fg: "#ffffff" }
  ];
  var deck = document.getElementById("d-deck"), name = document.getElementById("d-name"), toast = document.getElementById("d-toast");
  var focus = 0, touched = false, toastTimer = null;

  apps.forEach(function (a, i) {
    var card = document.createElement("div");
    card.className = "d-card" + (a.f ? " " + a.f : "") + (a.all ? " all" : "");
    card.style.setProperty("--bg", a.bg); card.style.setProperty("--fg", a.fg); card.style.setProperty("--c", a.c);
    card.innerHTML = a.all ? '<span class="grid" aria-hidden="true"><i></i><i></i><i></i><i></i></span><span data-i18n="d_all">' + t("d_all") + "</span>" : "<span>" + a.n + "</span>";
    card.addEventListener("click", function (e) { e.stopPropagation(); touched = true; if (i === focus) open(); else go(i); });
    deck.appendChild(card);
    a.el = card;
  });

  function label(a) { return a.all ? t("d_all") : a.n; }

  function layout() {
    apps.forEach(function (a, i) {
      var d = i - focus, s = a.el.style, tx, tz, ry, op;
      if (d === 0) { tx = 0; tz = 0; ry = 0; op = 1; }
      else if (d > 0) { tx = 15 + d * 9.5; tz = -d * 9; ry = -26; op = Math.max(0, 1 - (d - 1) * 0.22); }
      else { tx = d * 9 - 13; tz = d * 9; ry = 26; op = d === -1 ? 0.3 : 0; }
      s.transform = "translateX(" + tx + "cqw) translateZ(" + tz + "cqw) rotateY(" + ry + "deg)";
      s.opacity = op;
      s.zIndex = 50 - Math.abs(d);
      a.el.classList.toggle("on", d === 0);
      a.el.setAttribute("aria-hidden", d === 0 ? "false" : "true");
    });
    var a = apps[focus];
    name.textContent = label(a);
    el.style.setProperty("--d-aurora", a.c);
    document.documentElement.style.setProperty("--aurora", a.c);
  }

  function go(i) { focus = (i + apps.length) % apps.length; layout(); }

  function open() {
    var a = apps[focus];
    toast.textContent = a.all ? t("d_open_all") : t("d_open").replace("%s", a.n);
    toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.classList.remove("show"); }, 1800);
  }

  el.addEventListener("keydown", function (e) {
    if (e.key === "ArrowRight") { touched = true; go(focus + 1); e.preventDefault(); }
    else if (e.key === "ArrowLeft") { touched = true; go(focus - 1); e.preventDefault(); }
    else if (e.key === "Enter" || e.key === " ") { touched = true; open(); e.preventDefault(); }
  });
  el.querySelector(".d-prev").addEventListener("click", function (e) { e.stopPropagation(); touched = true; go(focus - 1); });
  el.querySelector(".d-next").addEventListener("click", function (e) { e.stopPropagation(); touched = true; go(focus + 1); });

  // Swipe on a phone or a trackpad drag: horizontal moves the dock, vertical still scrolls the page.
  var x0 = null;
  el.addEventListener("pointerdown", function (e) { x0 = e.clientX; });
  el.addEventListener("pointerup", function (e) {
    if (x0 === null) return;
    var dx = e.clientX - x0; x0 = null;
    if (Math.abs(dx) > 40) { touched = true; go(focus + (dx < 0 ? 1 : -1)); }
  });

  // Clock, greeting and date, in the page's language.
  function clock() {
    var now = new Date(), h = now.getHours(), fr = document.documentElement.lang === "fr";
    document.getElementById("d-clock").textContent = String(h).padStart(2, "0") + ":" + String(now.getMinutes()).padStart(2, "0");
    document.getElementById("d-greet").textContent = t(h < 5 ? "g_night" : h < 12 ? "g_morning" : h < 18 ? "g_afternoon" : h < 22 ? "g_evening" : "g_night");
    var d = now.toLocaleDateString(fr ? "fr-FR" : "en-GB", { weekday: "long", day: "numeric", month: "long" });
    document.getElementById("d-date").textContent = d.charAt(0).toUpperCase() + d.slice(1);
  }
  clock();
  setInterval(clock, 20000);
  new MutationObserver(function () { clock(); layout(); }).observe(document.documentElement, { attributes: true, attributeFilter: ["lang"] });

  // Stars, drawn once per size.
  var sky = el.querySelector(".d-sky");
  function stars() {
    var w = sky.clientWidth, h = sky.clientHeight, r = Math.min(window.devicePixelRatio || 1, 2), c = sky.getContext("2d");
    sky.width = w * r; sky.height = h * r; c.setTransform(r, 0, 0, r, 0, 0);
    for (var i = 0, n = Math.round(w * h / 2600); i < n; i++) {
      c.globalAlpha = Math.random() * 0.7 + 0.15;
      c.fillStyle = "#f4f4f0";
      c.beginPath(); c.arc(Math.random() * w, Math.random() * h * 0.75, Math.random() * 1.1 + 0.2, 0, 6.283); c.fill();
    }
  }
  stars();
  window.addEventListener("resize", stars);

  layout();

  // A short tour of the dock when it first comes into view, until the visitor takes over.
  if (!still) {
    new IntersectionObserver(function (e, o) {
      if (!e[0].isIntersecting) return;
      o.disconnect();
      var steps = 0, tour = setInterval(function () {
        if (touched || ++steps > 4) { clearInterval(tour); if (!touched) go(0); return; }
        go(focus + 1);
      }, 1600);
    }, { threshold: 0.6 }).observe(el);
  }
})();
