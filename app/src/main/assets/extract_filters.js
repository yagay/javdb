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
  const currentPath = new URL(location.href).pathname;
  const isTagPage = /^\/tags\/?$/.test(currentPath);
  const isEntityPath = path => /^\/(?:actors|directors|makers|publishers|series|video_codes)(?:\/|$)/.test(path);
  const isFilterTarget = raw => {
    const url = allowed(raw);
    if (!url) return false;
    const dest = new URL(url);
    // A tag or category control must never navigate to an actor or maker.
    if (isEntityPath(dest.pathname) || /\/(?:login|sign_in|logout)(?:\/|$)/.test(dest.pathname)) return false;
    return !isTagPage || /^\/tags\/?$/.test(dest.pathname);
  };
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
      if (!url || !name || seen.has(url) || !isFilterTarget(url)) return;
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
    '.tag-list', '.sort-options', '.sort', '.ranking-tabs',
    '.movie-list-filter', '.movie-filter', '.filter-buttons', '.sort-list',
    '.movie-list-header', '.filter-box', '.filter-options',
    '.buttons.has-addons', '.dropdown-content', '.tabs.is-toggle'
  ].join(',');
  document.querySelectorAll(containers).forEach(el => {
    if (el.closest('.movie-list .item, .movie-box, .video-item, header, nav, .navbar, footer')) return;
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
    const here = new URL(location.href);
    if (defaults.pathname === here.pathname && defaults.hostname === here.hostname) {
      here.searchParams.forEach((value, key) => {
        if (!defaults.searchParams.has(key)) defaults.searchParams.append(key, value);
      });
    }
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
  // JavDB's /tags page is built from dt.tag-category groups. Items may be
  // clickable labels rather than <a>, and selected items expose a delete
  // button whose onclick URL removes exactly that selection.
  if (/^\/tags\/?$/.test(new URL(location.href).pathname)) {
    const tagGroups = [];
    const current = new URL(location.href);
    const onclickUrl = node => {
      const js = node?.getAttribute?.('onclick') || '';
      const match = js.match(/(?:window\.)?location(?:\.href)?\s*=\s*['"]([^'"]+)['"]/);
      return match ? allowed(match[1]) : '';
    };
    const allGroups = Array.from(document.querySelectorAll('dt.tag-category'));
    allGroups.forEach(group => {
      const title = clean(group.querySelector('strong')?.textContent || group.getAttribute('data-cid') || '标签')
        .replace(/[:：]$/, '');
      if (title === '收藏' || group.getAttribute('data-cid') === 'custom') return;
      const options = [], seen = new Set();
      const addOption = (label, targetUrl, selected) => {
        label = clean(label);
        if (!label || !targetUrl) return;
        const key = label.toLowerCase();
        if (seen.has(key)) return;
        seen.add(key);
        options.push({title:label, url:targetUrl, selected});
      };
      // Use the site's own URLs for toggling; do not infer category numbers.
      group.querySelectorAll('a[href], a[onclick], div.tag, span.tag').forEach(node => {
        const label = clean(node.textContent || node.getAttribute('title'))
          .replace(/[×✕✖]\s*$/, '');
        const deleteButton = node.querySelector('button.delete');
        const selected = !!deleteButton || node.classList?.contains('is-info');
        const target = selected
          ? onclickUrl(deleteButton) || onclickUrl(node)
          : allowed(node.getAttribute('href')) || onclickUrl(node);
        if (target) addOption(label, target, selected);
      });
      if (options.length) tagGroups.push({title, links:options.slice(0,80)});
    });
    // On /tags, never fall back to generic site navigation: generic links may
    // point to /actors despite being presented alongside Censored filters.
    return JSON.stringify({url:location.href, groups:tagGroups.slice(0,30)});
  }
  // Site navigation belongs to extract_navigation.js, not filter controls.
  return JSON.stringify({url:location.href, groups:groups.slice(0,30)});
})();