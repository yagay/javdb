(function () {
  'use strict';
  const clean = s => String(s || '').replace(/\s+/g, ' ').trim();
  const get = sel => document.querySelector(sel);
  const all = sel => Array.from(document.querySelectorAll(sel));
  const metadata = sel => clean(get(sel)?.getAttribute('content'));
  const firstText = (...selectors) => {
    for (const selector of selectors) {
      const value = clean(get(selector)?.textContent);
      if (value) return value;
    }
    return '';
  };
  const absoluteImage = raw => {
    try {
      if (!raw || /^(data:|blob:|javascript:)/i.test(raw)) return '';
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' ? u.href : '';
    } catch (_) { return ''; }
  };
  const coverNode = get('.video-meta-panel .column-video-cover img, .column-video-cover img, .video-cover img, .cover img, img.video-cover');
  const coverCandidates = [
    coverNode?.getAttribute('data-original'),
    coverNode?.getAttribute('data-src'),
    coverNode?.getAttribute('data-lazy-src'),
    coverNode?.getAttribute('srcset')?.split(',')[0]?.trim().split(/\s+/)[0],
    coverNode?.currentSrc,
    coverNode?.getAttribute('src'),
    metadata('meta[property="og:image"]')
  ];
  const cover = coverCandidates.map(absoluteImage).find(Boolean) || '';
  const fields = {};
  // Desktop JavDB uses ".movie-panel-info .panel-block" with <strong> labels
  // and a separate .value element (often without a colon in the text).
  const containers = all('.movie-panel-info .panel-block, .video-meta-panel .panel-block, .movie-panel-info > div, .video-meta-panel .panel-block');
  containers.forEach(node => {
    const strong = node.querySelector('strong, .label, .panel-label');
    const label = clean(strong?.textContent).replace(/[：:]\s*$/, '');
    const valueNode = node.querySelector('.value, .panel-value');
    let value = clean(valueNode?.textContent);
    if (!value && label) {
      const clone = node.cloneNode(true);
      clone.querySelectorAll('strong, .label, .panel-label, script, style').forEach(n => n.remove());
      value = clean(clone.textContent);
    }
    if (label && value && !fields[label]) fields[label] = value.slice(0, 320);
    const raw = clean(node.textContent);
    const colon = raw.search(/[:：]/);
    if (colon > 0 && colon < 28) {
      const key = clean(raw.slice(0, colon));
      const rest = clean(raw.slice(colon + 1));
      if (key && rest && !fields[key]) fields[key] = rest.slice(0, 320);
    }
  });
  const field = (...aliases) => {
    const wanted = new Set(aliases.map(alias => alias.toLowerCase().replace(/[\s：:]+/g, '')));
    for (const [key, value] of Object.entries(fields)) {
      const normalized = key.toLowerCase().replace(/[\s：:]+/g, '');
      if (wanted.has(normalized)) return value;
    }
    return '';
  };
  const title = firstText('.video-meta-panel h2.title', 'h2.title', '.video-detail h2', 'h1') ||
    metadata('meta[property="og:title"]') || clean(document.title);
  // Site-wide SEO meta descriptions are not movie synopses; never display them.
  const description = firstText('.movie-synopsis', '.video-description .content',
    '.movie-description .content', '[itemprop="description"]', '.video-introduction');
  const excerpt = '';
  const metadataText = clean(get('.movie-panel-info')?.textContent ||
    get('.video-meta-panel')?.textContent || '');
  const codeFromClipboard = get('.movie-panel-info [data-clipboard-text], .video-meta-panel [data-clipboard-text]')
    ?.getAttribute('data-clipboard-text');
  const codeMatch = (title + ' ' + metadataText).match(/\b[A-Z]{2,8}[- ]?\d{2,7}\b/i);
  const code = field('番號', '番号', '識別碼', '识别码', 'ID') ||
    clean(codeFromClipboard) || (codeMatch ? codeMatch[0].toUpperCase() : '');
  const tags = all('.movie-panel-info a[href*="/tags/"], .video-meta-panel a[href*="/tags/"]')
    .map(node => clean(node.textContent)).filter(Boolean).slice(0, 40);
  return JSON.stringify({
    url: location.href,
    title: title.slice(0, 300),
    code: code.slice(0, 40),
    description: description.slice(0, 1200),
    cover, excerpt,
    releaseDate: field('日期', '發行日期', '发行日期', 'date'),
    studio: field('片商', '發行商', '发行商', '製作商', '制作商', 'studio', 'publisher'),
    actors: field('演員', '演员', 'actor', 'actors', 'star'),
    duration: field('時長', '时长', '長度', '长度', 'duration'),
    director: field('導演', '导演', 'director'),
    series: field('系列', 'series'),
    tags,
    fieldsFound: Object.keys(fields).length
  });
})();