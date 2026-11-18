(function () {
  'use strict';
  const hostAllowed = h => h === 'javdb.com' || h.endsWith('.javdb.com');
  const sameSite = raw => {
    try { const u = new URL(raw, location.href); return u.protocol === 'https:' && hostAllowed(u.hostname) ? u.href : ''; }
    catch (_) { return ''; }
  };
  const imageUrl = raw => {
    try { const u = new URL(raw, location.href); return u.protocol === 'https:' ? u.href : ''; }
    catch (_) { return ''; }
  };
  const items = [], seen = new Set();
  document.querySelectorAll('.movie-list .item, .movie-list .movie-box, .movies .item, .movie-box, .video-item').forEach(card => {
    const anchor = card.querySelector('a[href]');
    const url = anchor && sameSite(anchor.getAttribute('href'));
    if (!url || seen.has(url) || !/\/(?:v|videos|video)\//i.test(url)) return;
    const image = card.querySelector('img');
    const title = (card.querySelector('.video-title, .title, .movie-title, .uid')?.textContent || image?.alt || anchor?.title || card.textContent || '').replace(/\s+/g, ' ').trim();
    const code = (card.querySelector('.video-title strong, .uid')?.textContent || title.match(/\b[A-Z]{2,8}[- ]?\d{2,7}\b/i)?.[0] || '').trim();
    const cover = imageUrl(image?.getAttribute('data-src') || image?.getAttribute('data-original') || image?.getAttribute('src') || '');
    seen.add(url);
    items.push({url, title: title.slice(0, 220), code: code.slice(0, 32), cover});
  });
  const next = document.querySelector('a[rel="next"], .pagination .next a[href], a.pagination-next[href]');
  return JSON.stringify({url: location.href, title: document.title.slice(0, 200), items: items.slice(0, 80), nextUrl: next ? sameSite(next.getAttribute('href')) : ''});
})();
