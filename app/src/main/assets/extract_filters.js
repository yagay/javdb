(function () {
  'use strict';
  const allowed = raw => {
    try {
      const url = new URL(raw, location.href);
      return url.protocol === 'https:' &&
        (url.hostname === 'javdb.com' || url.hostname.endsWith('.javdb.com')) ? url.href : '';
    } catch (_) { return ''; }
  };
  const clean = s => String(s || '').replace(/\s+/g, ' ').trim().slice(0, 50);
  const groups = [];
  const used = new Set();
  function collect(root, heading, selector, max) {
    if (!root || groups.length >= 20) return;
    const links = [];
    root.querySelectorAll(selector).forEach(a => {
      const url = allowed(a.getAttribute('href'));
      const title = clean(a.textContent || a.title || a.getAttribute('aria-label'));
      if (!title || !url || links.some(x => x.url === url)) return;
      links.push({title, url, selected: a.classList.contains('active') ||
        a.getAttribute('aria-current') === 'page' ||
        !!a.closest('.is-active, .selected, .active')});
    });
    if (links.length === 0) return;
    const signature = links.map(x => x.url).slice(0, 3).join('|');
    if (used.has(signature)) return;
    used.add(signature);
    groups.push({title: clean(heading) || '筛选', links: links.slice(0, max)});
  }
  const selectors = [
    '.filter-panel', '.filter-group', '.filter-section', '.filters', '.filter',
    '.tag-list', '.tags', '.category-list', '.categories',
    '.tabs', '.navbar-menu', '.pagination', '.sort-options', '.sort',
    '.actors', '.actor-list', '.makers', '.series', '.ranking-tabs',
    '.movie-list-filter', '.movie-filter', '.filter-buttons', '.sort-list',
    '.movie-list-header', '.filter-box', '.filter-options', '.filter-item',
    '.buttons.has-addons', '.dropdown-menu', '.dropdown-content', '.tabs.is-toggle'
  ].join(',');
  document.querySelectorAll(selectors).forEach(el => {
    if (el.closest('.movie-list .item, .movie-box, .video-item, footer')) return;
    const heading = el.querySelector('h2, h3, h4, .title, .label, strong')?.textContent ||
      el.getAttribute('aria-label') || el.className.toString().split(' ')[0] || '分类';
    collect(el, heading, 'a[href]', 50);
  });
  // Dedicated navigation categories, including rankings, actors, studios, and tags.
  const nav = document.querySelector('header, nav, .navbar');
  collect(nav, '网站导航', 'a[href]', 35);
  return JSON.stringify({url: location.href, groups: groups.slice(0, 20)});
})();