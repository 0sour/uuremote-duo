/* Shared single-screen mockup behavior: size toggle + 640 tab panels. */
(function () {
  const frame = document.querySelector("[data-single-frame]");
  if (!frame) return;

  const label = document.querySelector("[data-single-label]");
  const tabs = [...document.querySelectorAll("[data-sa-tab]")];
  const panels = [...document.querySelectorAll("[data-sa-panel]")];

  function writeLabel(text) {
    if (!label) return;
    // 保留焦点运行时刻注入的状态 pill，只改前面的文本节点。
    if (label.firstChild && label.firstChild.nodeType === 3) label.firstChild.nodeValue = text;
    else label.insertBefore(document.createTextNode(text), label.firstChild);
  }

  function openPanel(name) {
    tabs.forEach((t) => {
      const on = t.dataset.saTab === name;
      t.classList.toggle("active", on);
      t.setAttribute("aria-selected", on ? "true" : "false");
    });
    panels.forEach((p) => {
      p.classList.toggle("is-active", p.dataset.saPanel === name);
    });
  }

  tabs.forEach((t) => t.addEventListener("click", () => openPanel(t.dataset.saTab)));

  function setSize(size) {
    const bottom = size === "bottom";
    frame.classList.toggle("size-bottom", bottom);
    frame.classList.remove("size-top");
    document.body.dataset.singleSize = size;
    writeLabel(bottom
      ? "单屏 · 下屏 Presentation · 640×480dp"
      : "单屏 · 上屏 Activity · 832×468dp");
    document.querySelectorAll("[data-single-size]").forEach((b) => {
      b.classList.toggle("active", b.dataset.singleSize === size);
    });
    // 640 下默认回到第一个页签，避免当前页签被隐藏。
    if (bottom && tabs.length) {
      const active = tabs.find((t) => t.classList.contains("active")) || tabs[0];
      openPanel(active.dataset.saTab);
    }
    if (window.gpFocus) window.gpFocus.refresh();
  }

  document.querySelectorAll("[data-single-size]").forEach((b) => {
    b.addEventListener("click", () => setSize(b.dataset.singleSize));
  });

  const params = new URLSearchParams(location.search);
  const initial = params.get("size") || "top";
  setSize(initial === "bottom" ? "bottom" : "top");
  if (panels.length) {
    const requested = params.get("panel");
    const target = requested && panels.some((p) => p.dataset.saPanel === requested)
      ? requested : panels[0].dataset.saPanel;
    openPanel(target);
  }
})();
