(function () {
  'use strict';
  const allowed = raw => {
    try {
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' && (u.hostname === 'javdb.com' || u.hostname.endsWith('.javdb.com')) ? u.href : '';
    } catch (_) { return ''; }
  };
  const links = [], seen = new Set();
  document.querySelectorAll('header a[href], nav a[href], .navbar a[href], .menu a[href], .tabs a[href], .categories a[href], .filters a[href]')
    .forEach(anchor => {
      const url = allowed(anchor.getAttribute('href'));
      const title = (anchor.textContent || anchor.getAttribute('title') || '').replace(/\s+/g, ' ').trim().slice(0, 60);
      if (!url || !title || seen.has(url) || url === location.href || /^javascript:/i.test(url)) return;
      seen.add(url);
      links.push({title, url});
    });
  return JSON.stringify({url: location.href, links: links.slice(0, 40)});
})();
