(function () {
  'use strict';
  const sameSite = raw => {
    try {
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' &&
        (u.hostname === 'javdb.com' || u.hostname.endsWith('.javdb.com')) ? u.href : '';
    } catch (_) { return ''; }
  };
  const picture = raw => {
    try {
      if (!raw || /^(data:|blob:|javascript:)/i.test(raw)) return '';
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' ? u.href : '';
    } catch (_) { return ''; }
  };
  const clean = text => String(text || '').replace(/\s+/g, ' ').trim();
  const found = [], seen = new Set();
  const selector = [
    '.actor-list a[href]', '.actors-list a[href]', '.actor-item a[href]',
    '.actor-box a[href]', '.actress-list a[href]', '.person-list a[href]',
    '.maker-list a[href]', '.series-list a[href]', '.director-list a[href]',
    '.category-list a[href]', '.categories a[href]',
    '.grid a[href*="/actors/"]', '.grid a[href*="/makers/"]',
    'a[href*="/actors/"]', 'a[href*="/makers/"]',
    'a[href*="/series/"]', 'a[href*="/directors/"]',
    'a[href*="/publishers/"]'
  ].join(',');
  document.querySelectorAll(selector).forEach(anchor => {
    if (anchor.closest('header, nav, footer, .navbar, .tabs, .filters, .filter-panel, .movie-list')) return;
    const url = sameSite(anchor.getAttribute('href'));
    if (!url || seen.has(url) || !/\/(?:actors|makers|series|directors|publishers)\//.test(url)) return;
    const card = anchor.closest('.box, .item, .actor-item, .grid-item') || anchor;
    const img = card.querySelector('img') || anchor.querySelector('img');
    const label = clean(card.querySelector('.name, .title, .actor-name, .label')?.textContent ||
      anchor.getAttribute('title') || img?.getAttribute('alt') || anchor.textContent);
    if (!label) return;
    const cover = [
      img?.getAttribute('data-original'), img?.getAttribute('data-src'),
      img?.getAttribute('data-lazy-src'), img?.getAttribute('src')
    ].map(picture).find(Boolean) || '';
    seen.add(url);
    found.push({title: label.slice(0, 100), url, cover});
  });
  return JSON.stringify({url: location.href, items: found.slice(0, 100)});
})();