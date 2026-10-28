// The site's motion: a night sky like the app's home screen, the before/after comparator, reveals on scroll,
// counters and a light 3D tilt. Everything rests still when the visitor asks for reduced motion.
(function () {
  var still = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var root = document.documentElement;
  root.classList.add("js");

  // Night sky: twinkling stars and, now and then, a shooting star.
  var sky = document.getElementById("sky");
  if (sky) {
    var ctx = sky.getContext("2d"), stars = [], meteor = null, w = 0, h = 0, dpr = Math.min(window.devicePixelRatio || 1, 2);
    var size = function () {
      w = sky.clientWidth; h = sky.clientHeight;
      sky.width = w * dpr; sky.height = h * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      stars = [];
      for (var i = 0, n = Math.round(w * h / 5200); i < n; i++)
        stars.push({ x: Math.random() * w, y: Math.random() * h, r: Math.random() * 1.2 + 0.25, p: Math.random() * 6.28, s: 0.4 + Math.random() * 1.4 });
    };
    var draw = function (t) {
      ctx.clearRect(0, 0, w, h);
      for (var i = 0; i < stars.length; i++) {
        var st = stars[i], a = still ? 0.6 : 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(st.p + t / 1000 * st.s));
        ctx.globalAlpha = a * (1 - st.y / h * 0.55);
        ctx.fillStyle = "#f4f4f0";
        ctx.beginPath(); ctx.arc(st.x, st.y, st.r, 0, 6.283); ctx.fill();
      }
      if (!still) {
        if (!meteor && Math.random() < 0.004) meteor = { x: Math.random() * w * 0.7 + w * 0.2, y: Math.random() * h * 0.35, life: 0 };
        if (meteor) {
          meteor.life += 1;
          var k = meteor.life / 55, x = meteor.x - k * 260, y = meteor.y + k * 120;
          var g = ctx.createLinearGradient(x, y, x + 90, y - 42);
          g.addColorStop(0, "rgba(244,244,240,0.9)"); g.addColorStop(1, "rgba(244,244,240,0)");
          ctx.globalAlpha = Math.sin(Math.min(k, 1) * 3.1416);
          ctx.strokeStyle = g; ctx.lineWidth = 1.6;
          ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x + 90, y - 42); ctx.stroke();
          if (k >= 1) meteor = null;
        }
      }
      ctx.globalAlpha = 1;
      if (!still && visible) requestAnimationFrame(draw);
    };
    var visible = true;
    size(); draw(0);
    window.addEventListener("resize", function () { size(); if (still) draw(0); });
    // The sky only animates while the hero is on screen.
    new IntersectionObserver(function (e) {
      var was = visible; visible = e[0].isIntersecting;
      if (visible && !was && !still) requestAnimationFrame(draw);
    }).observe(sky);
  }

  // Before/after: the range input does the dragging (mouse, touch, keyboard); a sweep shows how it works.
  var cmp = document.getElementById("compare"), range = document.getElementById("compare-range");
  if (cmp && range) {
    var touched = false;
    var set = function (v) { cmp.style.setProperty("--pos", v + "%"); range.value = v; cmp.classList.toggle("hide-before", v < 16); cmp.classList.toggle("hide-after", v > 84); };
    // Keyboard: the range input. Mouse and finger: anywhere on the picture, while vertical swipes still scroll the page.
    range.addEventListener("input", function () { touched = true; set(+range.value); });
    var at = function (e) { var r = cmp.getBoundingClientRect(); return Math.min(100, Math.max(0, (e.clientX - r.left) / r.width * 100)); };
    cmp.addEventListener("pointerdown", function (e) {
      touched = true; cmp.classList.add("dragging"); set(at(e));
      cmp.setPointerCapture(e.pointerId);
    });
    cmp.addEventListener("pointermove", function (e) { if (cmp.classList.contains("dragging")) set(at(e)); });
    ["pointerup", "pointercancel"].forEach(function (n) { cmp.addEventListener(n, function () { cmp.classList.remove("dragging"); }); });
    var sweep = function () {
      if (still || touched) return;
      var keys = [[0, 50], [700, 88], [1700, 12], [2500, 50]], t0 = null;
      var ease = function (x) { return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2; };
      var step = function (t) {
        if (touched) return;
        if (t0 === null) t0 = t;
        var e = t - t0, i = 1;
        while (i < keys.length - 1 && e > keys[i][0]) i++;
        var a = keys[i - 1], b = keys[i], p = Math.min(1, Math.max(0, (e - a[0]) / (b[0] - a[0])));
        set(a[1] + (b[1] - a[1]) * ease(p));
        if (e < keys[keys.length - 1][0]) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    };
    new IntersectionObserver(function (e, o) {
      if (e[0].isIntersecting) { o.disconnect(); setTimeout(sweep, 500); }
    }, { threshold: 0.5 }).observe(cmp);
  }

  // Reveals on scroll, a little staggered inside each group.
  var groups = [".hero [data-reveal]", "section .eyebrow, section h2, section .sub", ".steps li", ".safe > div", ".split > *", ".tv-stats > div", ".faq details"];
  groups.forEach(function (sel) {
    document.querySelectorAll(sel).forEach(function (el, i) {
      if (!el.hasAttribute("data-reveal")) { el.setAttribute("data-reveal", ""); el.style.setProperty("--d", (i % 6) * 90 + "ms"); }
    });
  });
  var counters = document.querySelectorAll(".count, .tv-stats strong:not(#n-reports)");
  var countUp = function (el) {
    var to = parseInt(el.getAttribute("data-to") || el.textContent, 10);
    if (isNaN(to) || still) return;
    var t0 = null, run = function (t) {
      if (t0 === null) t0 = t;
      var p = Math.min(1, (t - t0) / 1200);
      el.textContent = Math.round(to * (1 - Math.pow(1 - p, 3)));
      if (p < 1) requestAnimationFrame(run);
    };
    requestAnimationFrame(run);
  };
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (!e.isIntersecting) return;
      e.target.classList.add("in");
      e.target.querySelectorAll && Array.prototype.forEach.call(e.target.querySelectorAll(".count, strong"), function (c) {
        if (Array.prototype.indexOf.call(counters, c) >= 0 && !c.dataset.done) { c.dataset.done = 1; countUp(c); }
      });
      io.unobserve(e.target);
    });
  }, { threshold: 0.15, rootMargin: "0px 0px -40px 0px" });
  document.querySelectorAll("[data-reveal]").forEach(function (el) { io.observe(el); });

  // Screens lean towards the pointer.
  if (!still && window.matchMedia("(hover: hover)").matches) {
    document.querySelectorAll(".split .shot").forEach(function (el) {
      el.classList.add("tilt");
      el.addEventListener("pointermove", function (e) {
        var r = el.getBoundingClientRect(), x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
        el.style.transform = "perspective(900px) rotateY(" + x * 7 + "deg) rotateX(" + -y * 7 + "deg) translateY(-4px)";
        el.style.setProperty("--gx", (x + 0.5) * 100 + "%"); el.style.setProperty("--gy", (y + 0.5) * 100 + "%");
      });
      el.addEventListener("pointerleave", function () { el.style.transform = ""; });
    });
  }

  // Pick an app: the aurora takes its colour, as on the home screen.
  document.querySelectorAll(".app").forEach(function (b) {
    var pick = function () {
      root.style.setProperty("--aurora", b.getAttribute("data-color"));
      document.querySelectorAll(".app").forEach(function (o) { o.setAttribute("aria-pressed", o === b ? "true" : "false"); });
    };
    b.addEventListener("click", pick);
    b.addEventListener("mouseenter", pick);
    b.addEventListener("focus", pick);
  });
})();
