(function () {
  'use strict';
  const allowed = raw => {
    try {
      const url = new URL(raw, location.href);
      return url.protocol === 'https:' &&
        (url.hostname === 'javdb.com' || url.hostname.endsWith('.javdb.com')) ? url.href : '';
    } catch (_) { return ''; }
  };
  const clean = value => String(value || '').replace(/\s+/g, ' ').trim().slice(0, 60);
  const groups = [], signatures = new Set();
  const active = node => !!(
    node.classList?.contains('active') ||
    node.classList?.contains('is-active') ||
    node.classList?.contains('selected') ||
    node.getAttribute?.('aria-current') === 'page' ||
    node.getAttribute?.('aria-selected') === 'true' ||
    node.closest?.('.is-active, .selected, .active')
  );
  const add = (title, list) => {
    const links = [], seen = new Set();
    list.forEach(item => {
      const url = allowed(item.url);
      const name = clean(item.title);
      if (!url || !name || seen.has(url)) return;
      seen.add(url);
      links.push({title:name, url, selected:!!item.selected});
    });
    if (!links.length || groups.length >= 30) return;
    const signature = links.slice(0,5).map(item => item.url).join('|');
    if (signatures.has(signature)) return;
    signatures.add(signature);
    groups.push({title:clean(title) || '筛选', links:links.slice(0,80)});
  };
  const anchors = root => Array.from(root.querySelectorAll('a[href]')).map(a => ({
    title: a.textContent || a.title || a.getAttribute('aria-label'),
    url: a.getAttribute('href'), selected: active(a)
  }));
  const containers = [
    '.filter-panel', '.filter-group', '.filter-section', '.filters', '.filter',
    '.tag-list', '.tags', '.category-list', '.categories', '.tabs',
    '.pagination', '.sort-options', '.sort', '.ranking-tabs',
    '.movie-list-filter', '.movie-filter', '.filter-buttons', '.sort-list',
    '.movie-list-header', '.filter-box', '.filter-options',
    '.buttons.has-addons', '.dropdown-menu', '.dropdown-content', '.tabs.is-toggle',
    '.level-left', '.level-right'
  ].join(',');
  document.querySelectorAll(containers).forEach(el => {
    if (el.closest('.movie-list .item, .movie-box, .video-item, footer')) return;
    const header = el.querySelector('h2, h3, h4, .title, .label, strong')?.textContent ||
      el.getAttribute('aria-label') || el.className?.toString().split(' ')[0];
    add(header, anchors(el));
  });
  // Desktop JavDB filters may be GET forms with <select>/<option> or radio inputs.
  // Derive URLs from the form's actual action and field names; never guess an API.
  document.querySelectorAll('form').forEach(form => {
    const method = String(form.getAttribute('method') || 'get').toLowerCase();
    if (method !== 'get') return;
    const formUrl = allowed(form.getAttribute('action') || location.href);
    if (!formUrl) return;
    const defaults = new URL(formUrl);
    const fields = Array.from(form.querySelectorAll('select[name], input[name]'));
    fields.forEach(field => {
      if (!field.name || field.disabled) return;
      if (field.tagName === 'SELECT') {
        const select = field;
        const options = Array.from(select.options || []);
        const list = options.map(option => {
          const url = new URL(defaults.href);
          if (option.value) url.searchParams.set(select.name, option.value);
          else url.searchParams.delete(select.name);
          return {
            title: option.textContent,
            url: url.href,
            selected: option.selected
          };
        });
        if (list.length > 1) add(select.getAttribute('aria-label') ||
          select.closest('.field')?.querySelector('label')?.textContent ||
          select.name, list);
      } else if (field.type === 'radio') {
        const radios = fields.filter(candidate => candidate.type === 'radio' && candidate.name === field.name);
        if (radios[0] !== field) return;
        add(field.name, radios.map(radio => {
          const url = new URL(defaults.href);
          url.searchParams.set(radio.name, radio.value);
          return {
            title: radio.closest('label')?.textContent || radio.labels?.[0]?.textContent || radio.value,
            url: url.href,
            selected: radio.checked
          };
        }));
      }
    });
  });
  const nav = document.querySelector('header, nav, .navbar');
  if (nav) add('网站导航', anchors(nav));
  return JSON.stringify({url:location.href, groups:groups.slice(0,30)});
})();