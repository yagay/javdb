(function () {
  'use strict';
  const clean = s => (s || '').replace(/\s+/g, ' ').trim();
  const pick = (...selectors) => {
    for (const selector of selectors) {
      const node = document.querySelector(selector);
      if (node) { const value = clean(node.textContent); if (value) return value; }
    }
    return '';
  };
  const metadata = selector => clean(document.querySelector(selector)?.getAttribute('content'));
  const title = pick('h2.title', '.video-detail h2', 'h1') || metadata('meta[property="og:title"]') || document.title;
  const description = metadata('meta[name="description"]') || metadata('meta[property="og:description"]');
  const image = document.querySelector('.video-cover img, .column-video-cover img, .cover img, meta[property="og:image"]');
  const rawCover = image?.getAttribute('content') || image?.getAttribute('data-src') || image?.getAttribute('src') || '';
  let cover = '';
  try { const u = new URL(rawCover, location.href); if (u.protocol === 'https:') cover = u.href; } catch (_) {}
  const panel = document.querySelector('.movie-panel-info, .video-meta-panel, .panel-block');
  const excerpt = clean((panel || document.body).innerText).slice(0, 1400);
  const fields = {};
  if (panel) {
    panel.querySelectorAll('.panel-block, .movie-panel-info > div, .video-meta-panel > div, p').forEach(node => {
      const txt = clean(node.innerText || node.textContent);
      if (!txt || txt.length > 600) return;
      const colon = txt.search(/[:：]/);
      if (colon < 1 || colon > 25) return;
      const key = clean(txt.slice(0, colon));
      const value = clean(txt.slice(colon + 1));
      if (key && value && !fields[key]) fields[key] = value.slice(0, 260);
    });
  }
  const field = (...keys) => {
    for (const [key, value] of Object.entries(fields)) {
      if (keys.some(s => key.toLowerCase().includes(s))) return value;
    }
    return '';
  };
  const codeMatch = (title + ' ' + excerpt).match(/\b[A-Z]{2,8}[- ]?\d{2,7}\b/i);
  const tags = [...document.querySelectorAll('.tags a, .video-meta-panel .tag, .movie-panel-info .tag')]
    .map(n => clean(n.textContent)).filter(Boolean).slice(0, 24);
  const result = {url: location.href, title: title.slice(0, 300), code: (field('番號', '番号', '識別', 'id') || (codeMatch ? codeMatch[0].toUpperCase() : '')).slice(0, 40),
    description: description.slice(0, 1200), cover, excerpt,
    releaseDate: field('日期', '發行', '发行', 'date'), studio: field('片商', '製作', '制作', 'studio', 'publisher'),
    actors: field('演員', '演员', 'actor', 'star'), duration: field('時長', '时长', '長度', '长度', 'duration'),
    director: field('導演', '导演', 'director'), series: field('系列', 'series'), tags};
  return JSON.stringify(result);
})();
