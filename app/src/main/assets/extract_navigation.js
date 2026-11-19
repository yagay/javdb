(function () {
  'use strict';
  const allowed = raw => {
    try {
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' && (u.hostname === 'javdb.com' || u.hostname.endsWith('.javdb.com')) ? u.href : '';
    } catch (_) { return ''; }
  };
  const links = [], seen = new Set();
  const categoryNames = new Set([
    'censored', 'uncensored', 'western', '有码', '有碼', '无码', '無碼',
    '欧美', '歐美', 'fc2', 'anime', '最新', 'latest', 'newest'
  ]);
  const routeNameMismatch = (title, url) => {
    const path = new URL(url).pathname;
    const name = title.toLowerCase().trim();
    // Content/filter categories are not site sections and must never be
    // displayed under "网站其他栏目", especially when links resolve to actors.
    if (categoryNames.has(name)) return true;
    if (/^\/(?:actors|makers|directors|series|publishers)(?:\/|$)/.test(path)) {
      if (/^(?:censored|uncensored|western|latest|newest|有码|有碼|无码|無碼|欧美|歐美)/i.test(name)) return true;
    }
    return false;
  };
  document.querySelectorAll('header a[href], nav a[href], .navbar a[href], .navbar-menu a[href]')
    .forEach(anchor => {
      const url = allowed(anchor.getAttribute('href'));
      const title = (anchor.textContent || anchor.getAttribute('title') || '').replace(/\s+/g, ' ').trim().slice(0, 60);
      if (!url || !title || seen.has(url) || url === location.href || routeNameMismatch(title, url)) return;
      seen.add(url);
      links.push({title, url});
    });
  return JSON.stringify({url: location.href, links: links.slice(0, 40)});
})();
