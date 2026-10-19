// Shows "Add to Lax" instead of "Add to Firefox" on addons.mozilla.org. Only the text of links and
// buttons changes; installing still goes through Lax's own install prompt.
"use strict";

(() => {
  const replacements = [
    [/\bAdd to Firefox\b/g, "Add to Lax"],
    [/\bAñadir a Firefox\b/g, "Añadir a Lax"],
    [/\bAgregar a Firefox\b/g, "Agregar a Lax"],
  ];
  const clickable = "a, button, [role='button']";
  const labels = ["aria-label", "title"];

  function rename(text) {
    if (!text || !text.includes("Firefox")) return text;
    for (const [pattern, value] of replacements) text = text.replace(pattern, value);
    return text;
  }

  function fixElement(element) {
    for (const name of labels) {
      const value = element.getAttribute(name);
      const renamed = rename(value);
      if (renamed !== value) element.setAttribute(name, renamed);
    }
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      const renamed = rename(node.nodeValue);
      if (renamed !== node.nodeValue) node.nodeValue = renamed;
    }
  }

  function fix(node) {
    const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
    if (!element) return;
    const owner = element.closest(clickable);
    if (owner) fixElement(owner);
    element.querySelectorAll(clickable).forEach(fixElement);
  }

  // AMO is a single-page app: buttons appear and change as the user navigates.
  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === "childList") mutation.addedNodes.forEach(fix);
      else fix(mutation.target);
    }
  });

  fix(document.body || document.documentElement);
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
    characterData: true,
    attributes: true,
    attributeFilter: labels,
  });
})();
