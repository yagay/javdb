(function () {
  'use strict';
  const found = new Map();
  function add(uri, label, size) {
    if (!uri || typeof uri !== 'string' || !/^magnet:\?xt=urn:btih:/i.test(uri)) return;
    const normalized = uri.trim();
    if (!found.has(normalized)) found.set(normalized, {
      uri: normalized,
      label: String(label || '磁力链接').trim().slice(0, 240),
      size: String(size || '').trim().slice(0, 80)
    });
  }
  document.querySelectorAll('a[href], [data-magnet], [data-clipboard-text]').forEach(el => {
    const uri = el.getAttribute('href') || el.getAttribute('data-magnet') || el.getAttribute('data-clipboard-text');
    const row = el.closest('tr, .item, .magnet-name, .panel-block, li, article') || el.parentElement;
    const info = (row && row.innerText || el.innerText || '').replace(/\s+/g, ' ').slice(0, 300);
    const size = (info.match(/\b\d+(?:\.\d+)?\s*(?:TiB|GiB|MiB|KiB|TB|GB|MB|KB)\b/i) || [])[0] || '';
    add(uri, info || el.title || '磁力链接', size);
  });
  return JSON.stringify(Array.from(found.values()).slice(0, 300));
})();
