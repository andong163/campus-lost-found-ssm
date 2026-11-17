/* Public page layout only: measured sticky offsets and the advanced-filter disclosure. */
(function (window, document) {
  'use strict';
  if (!document.body.classList.contains('public-items-page')) return;
  const root = document.documentElement;
  const header = document.querySelector('.workspace > .topbar');
  const toolbar = document.querySelector('.filter-primary');
  const details = document.getElementById('advanced-filters');
  const toggle = document.getElementById('advanced-toggle');
  const indicator = document.getElementById('advanced-indicator');
  const badge = document.getElementById('advanced-badge');
  if (!header || !toolbar || !details || !toggle) return;
  const heading = header.querySelector('.page-heading');
  if (heading && heading.tagName !== 'H1') {
    const title = document.createElement('h1');
    title.className = heading.className;
    title.textContent = heading.textContent;
    heading.replaceWith(title);
  }
  function syncDisclosure() {
    toggle.setAttribute('aria-expanded', String(details.open));
    badge.hidden = indicator.hidden;
  }
  toggle.addEventListener('click', () => {
    details.open = !details.open;
    syncDisclosure();
    if (details.open) {
      const top = details.getBoundingClientRect().top;
      const offset = parseFloat(getComputedStyle(root).getPropertyValue('--public-focus-offset')) || 0;
      if (top < offset || top > window.innerHeight - 60) details.scrollIntoView({ block: 'start', behavior: 'auto' });
    }
  });
  details.addEventListener('toggle', syncDisclosure);
  new MutationObserver(syncDisclosure).observe(indicator, { attributes: true, attributeFilter: ['hidden'], childList: true });
  syncDisclosure();
  function measure() {
    const headerHeight = header.getBoundingClientRect().height;
    const toolbarHeight = toolbar.getBoundingClientRect().height;
    const available = window.visualViewport?.height || window.innerHeight;
    // Wrapping or a short viewport must never turn the controls into a large fixed obstruction.
    document.body.classList.toggle('public-static-toolbar', headerHeight + toolbarHeight > Math.min(240, available * 0.35));
    const headerOffset = getComputedStyle(header).position === 'sticky' ? Math.ceil(headerHeight) : 0;
    const toolbarOffset = getComputedStyle(toolbar).position === 'sticky' ? Math.ceil(toolbarHeight) : 0;
    root.style.setProperty('--public-header-height', headerOffset + 'px');
    root.style.setProperty('--public-focus-offset', (headerOffset + toolbarOffset + 12) + 'px');
  }
  const observer = new ResizeObserver(measure);
  observer.observe(header); observer.observe(toolbar);
  window.addEventListener('resize', measure);
  window.visualViewport?.addEventListener('resize', measure);
  measure();
})(window, document);
