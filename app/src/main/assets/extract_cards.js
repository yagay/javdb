(function () {
  'use strict';
  const sameSite = raw => {
    try {
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' && (u.hostname === 'javdb.com' || u.hostname.endsWith('.javdb.com')) ? u.href : '';
    } catch (_) { return ''; }
  };
  const absoluteImage = raw => {
    try {
      if (!raw || /^(data:|blob:|javascript:|about:)/i.test(raw)) return '';
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' ? u.href : '';
    } catch (_) { return ''; }
  };
  const firstSrcset = raw => (raw || '').split(',').map(s => s.trim().split(/\s+/)[0]).find(Boolean) || '';
  const pickImage = card => {
    const img = card.querySelector('.cover img, .cover-image img, .cover-image, img');
    if (!img) return '';
    const candidates = [
      img.getAttribute('data-original'), img.getAttribute('data-src'),
      img.getAttribute('data-lazy-src'), img.getAttribute('data-url'),
      firstSrcset(img.getAttribute('data-srcset')),
      firstSrcset(img.getAttribute('srcset')),
      img.currentSrc,
      img.getAttribute('src'),
      card.querySelector('source[srcset]')?.getAttribute('srcset') &&
        firstSrcset(card.querySelector('source[srcset]').getAttribute('srcset'))
    ];
    return candidates.map(absoluteImage).find(Boolean) || '';
  };
  const items = [], seen = new Set();
  const cards = document.querySelectorAll(
    '.movie-list a.box.item, .movie-list .item, .movie-list a[href*="/v/"], .is-user-page .movie-list .item, #videos a[href*="/v/"], #videos .item, .movies .item, a.movie-box, .video-item, .grid-item a[href*="/v/"], .box.item[href*="/v/"]'
  );
  cards.forEach(card => {
    // JavDB uses <a class="box item">, not a div containing a nested anchor.
    const anchor = card.matches('a[href]') ? card : card.querySelector('a[href*="/v/"], a[href]');
    const url = anchor ? sameSite(anchor.getAttribute('href')) : '';
    if (!url || seen.has(url) || !/\/(?:v|videos|video)\//i.test(url)) return;
    const img = card.querySelector('.cover img, .cover-image img, img');
    const title = (card.querySelector('.video-title, .title, .movie-title, .uid')?.textContent ||
      anchor.getAttribute('title') || img?.getAttribute('alt') || card.textContent || '')
      .replace(/\s+/g, ' ').trim();
    const code = (card.querySelector('.video-title strong, .uid')?.textContent ||
      title.match(/\b[A-Z]{2,8}[- ]?\d{2,7}\b/i)?.[0] || '').trim();
    seen.add(url);
    items.push({
      url, title: title.slice(0, 220), code: code.slice(0, 32),
      cover: pickImage(card)
    });
  });
  const next = document.querySelector('a[rel="next"], .pagination .next a[href], .pagination-next[href], .pagination-list a[aria-label="Next"], .pagination-list li:last-child a[href]');
  const isMoviePage = /\/(?:v|videos|video)\//i.test(location.pathname || '');
  const blocked = /(?:captcha|cloudflare|verify|just a moment|驗證|验证|存取被拒|access denied)/i.test(
    String(document.title || '') + ' ' + String(document.body?.innerText || '').slice(0, 300)
  );
  return JSON.stringify({
    url: location.href, title: document.title.slice(0, 200),
    items: items.slice(0, 80), nextUrl: next ? sameSite(next.getAttribute('href')) : '',
    scanned: cards.length, missingCovers: items.filter(it => !it.cover).length,
    blocked, isMoviePage
  });
})();