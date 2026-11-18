/* Shared mockup interactions. No build step, no dependencies. */
(function () {
  const root = document.documentElement;
  const body = document.body;

  const savedLight = localStorage.getItem("duo-mock-light") === "1";
  if (savedLight) body.classList.add("light");
  const savedZoom = localStorage.getItem("duo-mock-zoom") || "1";
  root.style.setProperty("--zoom", savedZoom);

  function syncButtons() {
    document.querySelectorAll("[data-theme-toggle]").forEach((b) => {
      b.textContent = body.classList.contains("light") ? "暗色" : "亮色";
    });
    document.querySelectorAll("[data-zoom]").forEach((b) => {
      b.classList.toggle("active", b.dataset.zoom === root.style.getPropertyValue("--zoom"));
    });
  }

  let zhMode = true;

  function applyLang() {
    document.querySelectorAll("[data-lang-toggle]").forEach((k) => {
      k.classList.toggle("on", zhMode);
    });
    document.querySelectorAll("[data-cand-bar]").forEach((el) => {
      el.hidden = !zhMode;
    });
    document.querySelectorAll("[data-kb-hint]").forEach((el) => {
      el.textContent = zhMode ? el.dataset.zh : el.dataset.en;
    });
    const builtin = document.querySelector('[data-ime-panel="builtin"]');
    if (builtin && !builtin.hidden) {
      document.querySelectorAll("[data-ime-text]").forEach((el) => {
        el.textContent = zhMode ? "ni'hao" : "abc";
      });
      document.querySelectorAll("[data-ime-tag]").forEach((el) => {
        el.textContent = zhMode ? "内置拼音" : "English";
      });
      builtin.querySelectorAll("[data-ime-preview-text]").forEach((el) => {
        el.textContent = zhMode ? "你好，世界" : "Hello, world";
      });
      builtin.querySelectorAll("[data-ime-preview-d]").forEach((el) => {
        el.textContent = zhMode ? el.dataset.zh : el.dataset.en;
      });
      builtin.querySelectorAll("[data-ime-kb-label]").forEach((el) => {
        el.textContent = zhMode ? el.dataset.zh : el.dataset.en;
      });
    }
  }

  function setImePanel(mode) {
    document.querySelectorAll("[data-ime-panel]").forEach((p) => {
      p.hidden = p.dataset.imePanel !== mode;
    });
    if (mode === "system") {
      document.querySelectorAll("[data-ime-text]").forEach((el) => {
        el.textContent = "nihao";
      });
      document.querySelectorAll("[data-ime-tag]").forEach((el) => {
        el.textContent = "系统输入法";
      });
    } else {
      applyLang();
    }
  }

  function openIme(mode) {
    const overlay = document.querySelector("[data-ime-overlay]");
    if (!overlay) return;
    overlay.hidden = false;
    setImePanel(mode);
  }

  document.addEventListener("click", (e) => {
    const t = e.target.closest(
      "[data-theme-toggle],[data-zoom],[data-tab],[data-ime],[data-ime-close],[data-sysime],[data-ime-back],[data-lang-toggle],[data-menu],[data-menu-close],[data-gamepad]"
    );
    if (!t) return;

    if (t.matches("[data-theme-toggle]")) {
      body.classList.toggle("light");
      localStorage.setItem("duo-mock-light", body.classList.contains("light") ? "1" : "0");
      syncButtons();
      return;
    }

    if (t.matches("[data-zoom]")) {
      const z = t.dataset.zoom;
      root.style.setProperty("--zoom", z);
      localStorage.setItem("duo-mock-zoom", z);
      syncButtons();
      return;
    }

    if (t.matches("[data-tab]")) {
      const group = t.closest("[data-tab-group]");
      if (!group) return;
      group.querySelectorAll("[data-tab]").forEach((b) => b.classList.toggle("active", b === t));
      // Panels are siblings of the tab row inside the same screen/sheet.
      const scope = group.parentElement || document;
      scope.querySelectorAll("[data-panel]").forEach((p) => {
        p.hidden = p.dataset.panel !== t.dataset.tab;
      });
      return;
    }

    if (t.matches("[data-ime]")) {
      const overlay = document.querySelector("[data-ime-overlay]");
      if (!overlay) return;
      if (overlay.hidden) {
        openIme("builtin");
      } else {
        overlay.hidden = true;
      }
      return;
    }

    if (t.matches("[data-ime-close]")) {
      const overlay = document.querySelector("[data-ime-overlay]");
      if (overlay) overlay.hidden = true;
      return;
    }

    if (t.matches("[data-sysime]")) {
      openIme("system");
      return;
    }

    if (t.matches("[data-ime-back]")) {
      setImePanel("builtin");
      return;
    }

    if (t.matches("[data-lang-toggle]")) {
      zhMode = !zhMode;
      applyLang();
      return;
    }

    if (t.matches("[data-menu]")) {
      const overlay = document.querySelector("[data-menu-overlay]");
      if (overlay) overlay.hidden = !overlay.hidden;
      return;
    }

    if (t.matches("[data-menu-close]")) {
      const overlay = document.querySelector("[data-menu-overlay]");
      if (overlay) overlay.hidden = true;
      return;
    }

    if (t.matches("[data-gamepad]")) {
      const buttons = document.querySelectorAll("[data-gamepad]");
      const local = !buttons[0].classList.contains("local");
      buttons.forEach((b) => b.classList.toggle("local", local));
      document.querySelectorAll("[data-gamepad-label]").forEach((el) => {
        el.textContent = local ? "手柄 本地操作" : "手柄 直通电脑";
      });
      document.querySelectorAll("[data-gamepad-short]").forEach((el) => {
        el.textContent = local ? "本地操作" : "直通电脑";
      });
      document.body.dataset.gpMode = local ? "local" : "passthrough";
      window.dispatchEvent(new CustomEvent("gp-mode-change", { detail: { mode: local ? "local" : "passthrough" } }));
      return;
    }
  });

  // Press visual for keys/buttons (mockup only).
  document.addEventListener("pointerdown", (e) => {
    const k = e.target.closest(".key,.pad-btn,.shortcut,.icon-btn,.tab");
    if (!k) return;
    k.classList.add("pressed");
  });
  document.addEventListener("pointerup", () => {
    document.querySelectorAll(".pressed").forEach((k) => k.classList.remove("pressed"));
  });
  document.addEventListener("pointerleave", () => {
    document.querySelectorAll(".pressed").forEach((k) => k.classList.remove("pressed"));
  });

  // Deep links for screenshot/review: ?tab=keys&ime=1
  const params = new URLSearchParams(location.search);
  if (params.get("ime") === "1") {
    openIme("builtin");
  }
  if (params.get("sysime") === "1") {
    openIme("system");
  }
  if (params.get("lang") === "en") {
    zhMode = false;
  }
  if (params.get("menu") === "1") {
    const overlay = document.querySelector("[data-menu-overlay]");
    if (overlay) overlay.hidden = false;
  }
  const tab = params.get("tab");
  if (tab) {
    const button = document.querySelector('[data-tab="' + CSS.escape(tab) + '"]');
    if (button) button.click();
  }

  applyLang();
  syncButtons();
})();

/* ==========================================================================
   Gamepad focus runtime (mockup review aid).
   One input owner and one focus tree: the bottom screen, or the single frame.
   The top screen is read-only and is never part of the focus tree.
   Keyboard mirrors the gamepad so the design can be reviewed without hardware:
   arrows = D-pad, Enter = B (confirm), Esc = A (back), Q/E = L1/R1,
   [ ] = L2/R2, X/Y = actions, F1/? = START help.
   ========================================================================== */
(function () {
  const screens = [...document.querySelectorAll(".screen-top, .screen-bottom, .single-frame, .single-app-frame")];
  if (!screens.length) return;

  const body = document.body;
  const AUTO = [
    ".device-card", ".file-row", ".display-card", ".tool-card", ".setting-row",
    ".cc-row", ".queue-item", ".cc-nav-item", ".km-plan", ".km-prop", ".port-row",
    ".key", ".pad-btn", ".shortcut", ".km-key", ".cat",
    ".sf-row[data-kind]", ".sd-card", ".sl-field",
  ].join(",");

  screens.forEach((s) => {
    if (s.classList.contains("screen-top")) s.dataset.gpRole = "preview";
    else s.dataset.gpRole = "control";
    s.dataset.gpScreen = s.classList.contains("screen-top") ? "top"
      : s.classList.contains("screen-bottom") ? "bottom" : "single";
  });

  const control = screens.find((s) => s.dataset.gpRole === "control");
  if (!control) return;

  const single = control.dataset.gpScreen === "single";
  let current = null;
  let helpOpen = false;
  let lastMoveAt = 0;
  let toastTimer = 0;
  let lastOutsideFocus = null;

  /* ---------- chrome: state pill, HUD, toast, help ---------- */
  function screenLabel(screen) {
    let label = screen.previousElementSibling;
    if (!label || !label.classList.contains("screen-label")) {
      const wrap = screen.closest(".screen-wrap");
      label = wrap ? [...wrap.children].find((c) => c.classList.contains("screen-label")) : null;
    }
    return label;
  }

  screens.forEach((s) => {
    const label = screenLabel(s);
    if (!label || label.querySelector(".gp-state")) return;
    const pill = document.createElement("span");
    pill.className = "gp-state " + (s.dataset.gpRole === "control" ? "active" : "passive");
    pill.dataset.gpState = s.dataset.gpRole;
    label.appendChild(pill);
  });

  const hud = document.createElement("div");
  hud.className = "gp-hud";
  hud.innerHTML =
    '<span class="gp-mode">● 下屏操作</span>' +
    '<span class="gp-sep"></span>' +
    '<span><kbd>↑↓←→</kbd> 移动</span>' +
    '<span><kbd>B</kbd> 确认</span>' +
    '<span><kbd>A</kbd> 返回</span>' +
    '<span><kbd>L1</kbd>/<kbd>R1</kbd> 页签</span>' +
    '<span><kbd>L2</kbd>/<kbd>R2</kbd> 二级</span>' +
    '<span><kbd>X</kbd>/<kbd>Y</kbd> 动作</span>' +
    '<span><kbd>START</kbd> 说明</span>' +
    '<span class="gp-sep"></span>' +
    '<span class="gp-now">焦点：—</span>';
  document.body.appendChild(hud);

  const toastEl = document.createElement("div");
  toastEl.className = "gp-toast";
  document.body.appendChild(toastEl);

  function toast(text) {
    toastEl.textContent = text;
    toastEl.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastEl.classList.remove("show"), 1500);
  }

  const help = document.createElement("div");
  help.className = "gp-help";
  help.hidden = true;
  help.innerHTML =
    '<div class="gp-help-card">' +
      '<h2>手柄操作</h2>' +
      '<p>单一输入所有者：所有手柄按键进入<b>下屏操作台</b>（单屏时进入唯一的操作区）。' +
      '上屏只显示视频/状态，不参与焦点，因此不需要在上下屏之间切换焦点。</p>' +
      '<div class="gp-help-grid">' +
        '<div class="gp-help-row"><span class="k"><kbd>↑↓←→</kbd></span><span class="d">移动焦点 / 左摇杆</span></div>' +
        '<div class="gp-help-row"><span class="k"><kbd>B</kbd></span><span class="d">确认 / 打开</span></div>' +
        '<div class="gp-help-row"><span class="k"><kbd>A</kbd></span><span class="d">返回 / 关闭浮层</span></div>' +
        '<div class="gp-help-row"><span class="k"><kbd>X</kbd><kbd>Y</kbd></span><span class="d">当前页面动作</span></div>' +
        '<div class="gp-help-row"><span class="k"><kbd>L1</kbd><kbd>R1</kbd></span><span class="d">一级页签 / 分类</span></div>' +
        '<div class="gp-help-row"><span class="k"><kbd>L2</kbd><kbd>R2</kbd></span><span class="d">二级页签</span></div>' +
        '<div class="gp-help-row"><span class="k"><kbd>START</kbd></span><span class="d">打开本说明</span></div>' +
        '<div class="gp-help-row"><span class="k">触摸</span><span class="d">点按即聚焦并执行</span></div>' +
      '</div>' +
      '<p style="margin-top:12px">串流页默认把手柄直通电脑；只有切到“本地操作”，焦点环和方向键才会用于下屏工具箱。' +
      '键盘预览：方向键、Enter=B、Esc=A、Q/E=L1/R1、[ ]=L2/R2、F1 或 ?=START。</p>' +
      '<div class="gp-help-foot"><span>焦点环：外圈深色间隙 + 2dp 高亮环，不覆盖内容</span><span class="grow"></span>' +
      '<button class="cc-btn primary" data-gp-help-close>关闭</button></div>' +
    '</div>';
  document.body.appendChild(help);

  help.addEventListener("click", (e) => {
    if (e.target === help || e.target.closest("[data-gp-help-close]")) closeHelp();
  });

  function openHelp() {
    helpOpen = true;
    help.hidden = false;
  }
  function closeHelp() {
    helpOpen = false;
    help.hidden = true;
  }

  /* ---------- mode: local focus vs gamepad passthrough ---------- */
  function mode() {
    return body.dataset.gpMode === "passthrough" ? "passthrough" : "local";
  }

  function syncChrome() {
    const pass = mode() === "passthrough";
    hud.classList.toggle("passthrough", pass);
    hud.querySelector(".gp-mode").textContent = pass
      ? "● 手柄直通电脑"
      : single ? "● 单屏操作" : "● 下屏操作";
    screens.forEach((s) => {
      const pill = screenLabel(s)?.querySelector(".gp-state");
      if (!pill) return;
      if (s.dataset.gpRole === "preview") {
        pill.className = "gp-state passive";
        pill.innerHTML = '<span class="dot"></span>状态预览 · 不参与焦点';
        return;
      }
      if (pass) {
        pill.className = "gp-state passthrough";
        pill.innerHTML = '<span class="dot"></span>手柄直通 · 本地焦点未激活';
      } else {
        pill.className = "gp-state active";
        pill.innerHTML = '<span class="dot"></span>' + (single ? "单屏操作 · 焦点在此" : "下屏操作 · 焦点在此");
      }
    });
    if (pass) clearFocus();
    else if (!current) focusInitial();
    updateHud();
  }

  window.addEventListener("gp-mode-change", syncChrome);

  /* ---------- focus tree ---------- */
  function isVisible(el) {
    if (!el || el.disabled) return false;
    if (el.closest("[hidden]")) return false;
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) return false;
    return getComputedStyle(el).visibility !== "hidden";
  }

  function prepare() {
    control.querySelectorAll(".cc-switch").forEach((el) => {
      if (!el.hasAttribute("tabindex")) el.tabIndex = 0;
      el.setAttribute("role", "switch");
      el.setAttribute("aria-checked", el.classList.contains("on") ? "true" : "false");
    });
    control.querySelectorAll(AUTO).forEach((el) => {
      if (el.hasAttribute("tabindex")) return;
      if (el.querySelector('button, a[href], [tabindex="0"]')) return;
      el.tabIndex = 0;
    });
  }

  function candidates() {
    const modal = modalScope();
    const scope = modal || control;
    return [...scope.querySelectorAll('button, a[href], [tabindex="0"], .cc-switch[tabindex]')]
      .filter(isVisible)
      .filter((el) => el.tabIndex >= 0)
      .filter((el, i, arr) => arr.indexOf(el) === i);
  }

  function modalScope() {
    return [...control.querySelectorAll("[data-menu-overlay], [data-ime-overlay], [data-more-overlay]")]
      .find((o) => !o.hidden) || null;
  }

  function labelOf(el) {
    if (!el) return "—";
    const explicit = el.dataset.gpLabel || el.getAttribute("aria-label") || el.getAttribute("title");
    const text = (explicit || el.textContent || "").replace(/\s+/g, " ").trim();
    return text.slice(0, 26) || "控件";
  }

  function updateHud() {
    hud.querySelector(".gp-now").textContent = "焦点：" + (current ? labelOf(current) : "—");
  }

  function clearFocus() {
    control.querySelectorAll(".gp-focus").forEach((el) => el.classList.remove("gp-focus"));
    current = null;
    updateHud();
  }

  function setFocus(el, scroll) {
    if (!el || mode() === "passthrough") return;
    control.querySelectorAll(".gp-focus").forEach((x) => x.classList.remove("gp-focus"));
    current = el;
    el.classList.add("gp-focus");
    if (scroll) {
      try { el.scrollIntoView({ block: "nearest", inline: "nearest" }); } catch (e) { /* noop */ }
    }
    updateHud();
  }

  function focusInitial() {
    if (mode() === "passthrough") return;
    const list = candidates();
    if (!list.length) return;
    const preferred = [...control.querySelectorAll("[data-gp-initial]")]
      .find((el) => isVisible(el) && list.includes(el));
    setFocus(preferred || list[0], false);
  }

  function refresh() {
    if (!current || !isVisible(current) || !candidates().includes(current)) {
      const list = candidates();
      if (list.length) setFocus(list[0], false);
      else clearFocus();
    }
  }

  function move(dir) {
    if (mode() === "passthrough") {
      toast("手柄直通电脑 · 切到本地操作后可用方向键");
      return;
    }
    const now = performance.now();
    if (now - lastMoveAt < 80) return;
    lastMoveAt = now;
    const list = candidates();
    if (!list.length) return;
    if (!current || !list.includes(current)) {
      setFocus(list[0], true);
      return;
    }
    const r = current.getBoundingClientRect();
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;
    let best = null;
    let bestScore = Infinity;
    list.forEach((el) => {
      if (el === current) return;
      const b = el.getBoundingClientRect();
      const dx = b.left + b.width / 2 - cx;
      const dy = b.top + b.height / 2 - cy;
      let primary;
      let secondary;
      if (dir === "up") { if (dy > -4) return; primary = -dy; secondary = Math.abs(dx); }
      else if (dir === "down") { if (dy < 4) return; primary = dy; secondary = Math.abs(dx); }
      else if (dir === "left") { if (dx > -4) return; primary = -dx; secondary = Math.abs(dy); }
      else { if (dx < 4) return; primary = dx; secondary = Math.abs(dy); }
      if (secondary > primary * 1.7 + 48) return;
      const score = primary + secondary * 1.8;
      if (score < bestScore) { bestScore = score; best = el; }
    });
    if (best) setFocus(best, true);
  }

  function toggleSwitch(el) {
    el.classList.toggle("on");
    el.setAttribute("aria-checked", el.classList.contains("on") ? "true" : "false");
    toast((el.classList.contains("on") ? "已开启 · " : "已关闭 · ") + labelOf(el));
  }

  function activate(el) {
    if (!el || mode() === "passthrough") return;
    if (el.classList.contains("cc-switch")) { toggleSwitch(el); return; }
    if (el.matches("button, a[href]")) {
      el.click();
      setTimeout(refresh, 20);
      return;
    }
    if (el.dataset.gpHref) {
      toast("打开 · " + labelOf(el));
      setTimeout(() => { location.href = el.dataset.gpHref; }, 180);
      return;
    }
    if (el.matches(".file-row, .sf-row")) {
      if (el.dataset.kind === "folder") {
        const crumb = control.querySelector("[data-file-crumb]");
        if (crumb) crumb.textContent = el.dataset.name || labelOf(el);
        toast("打开文件夹 · " + labelOf(el));
      } else {
        el.classList.toggle("sel");
        const check = el.querySelector(".file-check, .check");
        if (check) check.classList.toggle("on", el.classList.contains("sel"));
        toast((el.classList.contains("sel") ? "已选择 · " : "已取消 · ") + labelOf(el));
      }
      return;
    }
    if (el.matches(".device-card, .display-card, .tool-card, .km-plan, .sd-card")) {
      const group = el.parentElement;
      group.querySelectorAll(".selected, .active").forEach((x) => x.classList.remove("selected", "active"));
      el.classList.add(el.matches(".tool-card, .display-card, .km-plan") ? "active" : "selected");
      if (el.dataset.gpHref) { location.href = el.dataset.gpHref; return; }
      el.click();
      toast("已选择 · " + labelOf(el));
      return;
    }
    if (el.matches(".cc-row, .setting-row, .cc-nav-item, .queue-item, .port-row")) {
      el.click();
      toast(labelOf(el));
      return;
    }
    if (el.matches(".key")) {
      toast("发送按键 · " + labelOf(el));
      return;
    }
    el.click();
    toast(labelOf(el));
  }

  function tabGroups() {
    const selector = "[data-tab], [data-cc-tab], [data-settings-tab], [data-tool], [data-gp-tab], [data-sa-tab], .km-plan";
    const groups = new Map();
    control.querySelectorAll(selector).forEach((el) => {
      if (!isVisible(el)) return;
      const key = el.parentElement;
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(el);
    });
    return [...groups.values()].filter((g) => g.length > 1);
  }

  function cyclePrimary(dir) {
    if (mode() === "passthrough") { toast("手柄直通电脑 · 本地页签不可用"); return; }
    const groups = tabGroups();
    if (!groups.length) { toast("本页没有页签"); return; }
    const group = groups.find((g) => g.includes(current)) || groups[0];
    let idx = group.findIndex((el) => el === current || el.classList.contains("active") || el.classList.contains("on"));
    if (idx < 0) idx = 0;
    const next = group[(idx + dir + group.length) % group.length];
    next.click();
    setTimeout(() => { setFocus(next, true); refresh(); }, 20);
  }

  function cycleSecondary(dir) {
    if (mode() === "passthrough") { toast("手柄直通电脑 · 本地页签不可用"); return; }
    const group = [...control.querySelectorAll("[data-sub-btn]")].filter(isVisible);
    if (!group.length) { toast("本页没有二级页签"); return; }
    let idx = group.findIndex((el) => el === current || el.classList.contains("on"));
    if (idx < 0) idx = 0;
    const next = group[(idx + dir + group.length) % group.length];
    next.click();
    setTimeout(() => { setFocus(next, true); refresh(); }, 20);
  }

  function pageAction(key) {
    const label = body.dataset["gp" + key.toUpperCase()] || control.dataset["gp" + key.toUpperCase()] || "";
    toast(key.toUpperCase() + " · " + (label || "本页无绑定"));
    document.dispatchEvent(new CustomEvent("gp-action", { detail: { key } }));
  }

  function back() {
    if (helpOpen) { closeHelp(); return; }
    const overlay = [...document.querySelectorAll("[data-ime-overlay], [data-menu-overlay], [data-more-overlay]")]
      .find((o) => !o.hidden);
    if (overlay) { overlay.hidden = true; return; }
    const url = control.dataset.gpBack || body.dataset.gpBack;
    if (url) {
      toast("返回…");
      setTimeout(() => { location.href = url; }, 180);
    } else {
      toast("A · 返回");
    }
  }

  function typing(target) {
    return target && target.matches && target.matches("input, textarea, select, [contenteditable='true']");
  }

  document.addEventListener("keydown", (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (typing(e.target)) return;
    const k = e.key;
    if (helpOpen) {
      if (k === "Escape" || k === "Enter" || k === " " || k === "Backspace") {
        e.preventDefault();
        closeHelp();
      }
      return;
    }
    if (k === "ArrowUp" || k === "ArrowDown" || k === "ArrowLeft" || k === "ArrowRight") {
      e.preventDefault();
      move(k.slice(5).toLowerCase());
      return;
    }
    if (e.repeat) return;
    if (k === "Enter" || k === " ") { e.preventDefault(); activate(current); return; }
    if (k === "Escape" || k === "Backspace") { e.preventDefault(); back(); return; }
    if (k === "q" || k === "Q") { e.preventDefault(); cyclePrimary(-1); return; }
    if (k === "e" || k === "E") { e.preventDefault(); cyclePrimary(1); return; }
    if (k === "[") { e.preventDefault(); cycleSecondary(-1); return; }
    if (k === "]") { e.preventDefault(); cycleSecondary(1); return; }
    if (k === "x" || k === "X") { e.preventDefault(); pageAction("x"); return; }
    if (k === "y" || k === "Y") { e.preventDefault(); pageAction("y"); return; }
    if (k === "?" || k === "F1") { e.preventDefault(); openHelp(); return; }
  });

  control.addEventListener("click", (e) => {
    const el = e.target.closest('button, a[href], [tabindex="0"], .cc-switch');
    if (el && control.contains(el) && isVisible(el)) {
      setFocus(el, false);
      setTimeout(refresh, 30);
    }
  });

  /* ---------- gamepad polling (Xbox standard mapping) ---------- */
  let prevButtons = [];
  let axisAt = 0;
  function pollGamepad() {
    const pads = navigator.getGamepads ? navigator.getGamepads() : [];
    const gp = pads && [...pads].find(Boolean);
    if (gp) {
      const pressed = gp.buttons.map((b) => b.pressed || b.value > 0.55);
      const just = (i) => pressed[i] && !prevButtons[i];
      if (just(12)) move("up");
      if (just(13)) move("down");
      if (just(14)) move("left");
      if (just(15)) move("right");
      if (just(0)) back();
      if (just(1)) activate(current);
      if (just(2)) pageAction("x");
      if (just(3)) pageAction("y");
      if (just(4)) cyclePrimary(-1);
      if (just(5)) cyclePrimary(1);
      if (just(6)) cycleSecondary(-1);
      if (just(7)) cycleSecondary(1);
      if (just(9)) { if (helpOpen) closeHelp(); else openHelp(); }
      const ax = gp.axes[0] || 0;
      const ay = gp.axes[1] || 0;
      const now = performance.now();
      if (now - axisAt > 260) {
        if (ax < -0.62) { move("left"); axisAt = now; }
        else if (ax > 0.62) { move("right"); axisAt = now; }
        else if (ay < -0.62) { move("up"); axisAt = now; }
        else if (ay > 0.62) { move("down"); axisAt = now; }
      }
      prevButtons = pressed;
    }
    requestAnimationFrame(pollGamepad);
  }

  window.gpFocus = { refresh, focusInitial, setMode: (m) => { body.dataset.gpMode = m; syncChrome(); } };
  window.addEventListener("resize", () => setTimeout(refresh, 60));

  prepare();
  syncChrome();

  // Modal ownership: while a menu/IME overlay is open, only its controls are
  // in the focus tree; on close, return to the control that opened it.
  const observer = new MutationObserver(() => {
    setTimeout(() => {
      const modal = modalScope();
      if (modal) {
        if (!current || !modal.contains(current)) {
          if (current && !modal.contains(current)) lastOutsideFocus = current;
          setFocus(candidates()[0], false);
        }
      } else if (current && !isVisible(current)) {
        const backTo = lastOutsideFocus && isVisible(lastOutsideFocus) ? lastOutsideFocus : candidates()[0];
        setFocus(backTo, false);
      }
      refresh();
    }, 20);
  });
  observer.observe(control, { attributes: true, attributeFilter: ["hidden"], subtree: true });

  setTimeout(() => {
    if (mode() !== "passthrough") focusInitial();
  }, 60);
  if (navigator.getGamepads) requestAnimationFrame(pollGamepad);
})();
