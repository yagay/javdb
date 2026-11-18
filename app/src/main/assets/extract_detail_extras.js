(function () {
  'use strict';
  const tidy = s => String(s || '').replace(/\s+/g,' ').trim();
  const href = raw => {
    try {
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' && (u.hostname === 'javdb.com' || u.hostname.endsWith('.javdb.com')) ? u.href : '';
    } catch (_) { return ''; }
  };
  const image = raw => {
    try {
      if (!raw || /^(data:|blob:|javascript:)/i.test(raw)) return '';
      const u = new URL(raw, location.href);
      return u.protocol === 'https:' ? u.href : '';
    } catch (_) { return ''; }
  };
  const imgsrc = el => {
    if (!el) return '';
    const raw = [el.getAttribute('data-original'), el.getAttribute('data-src'),
      el.getAttribute('data-lazy-src'), el.getAttribute('data-zoom'),
      el.getAttribute('href'), el.getAttribute('srcset')?.split(',')[0]?.trim().split(/\s+/)[0],
      el.currentSrc, el.getAttribute('src')];
    return raw.map(image).find(Boolean) || '';
  };
  const sections = [];
  const seen = new Set();
  const rules = [
    ['演员', '.movie-panel-info a[href*="/actors/"], .video-meta-panel a[href*="/actors/"], .panel-block a[href*="/actors/"], a[href*="/actors/"]'],
    ['导演', '.movie-panel-info a[href*="/directors/"], .panel-block a[href*="/directors/"], a[href*="/directors/"]'],
    ['制作商', '.movie-panel-info a[href*="/makers/"], a[href*="/makers/"]'],
    ['发行商', '.movie-panel-info a[href*="/publishers/"], a[href*="/publishers/"]'],
    ['系列', '.movie-panel-info a[href*="/series/"], a[href*="/series/"]'],
    ['标签', '.tags a[href], .movie-panel-info a[href*="/tags/"], .video-meta-panel a[href*="/tags/"], a[href*="/tags/"]'],
    ['番号', '.movie-panel-info a[href*="/video_codes/"], a[href*="/video_codes/"]']
  ];
  rules.forEach(([title, selector]) => {
    const links = [];
    document.querySelectorAll(selector).forEach(a => {
      const url = href(a.getAttribute('href'));
      const label = tidy(a.textContent || a.getAttribute('title')).slice(0,80);
      if (!url || !label || links.some(x => x.url === url)) return;
      links.push({title: label, url});
    });
    if (links.length) sections.push({title, links: links.slice(0,40)});
  });
  const previews = [];
  document.querySelectorAll('.tile-images.preview-images a, .preview-images a, .tile-images a, .screenshot a, .screenshots a, .sample-images a').forEach(a => {
    const url = imgsrc(a.querySelector('img')) || image(a.getAttribute('href'));
    if (url && !seen.has(url)) { seen.add(url); previews.push(url); }
  });
  const related = [];
  const relatedSeen = new Set();
  document.querySelectorAll('.related-videos a[href], .related-movies a[href], .similar-videos a[href], .movie-list a.box.item, .movie-list a[href*="/v/"], .video-related a[href]').forEach(a => {
    const url = href(a.getAttribute('href'));
    if (!url || !/\/v\//.test(url) || url === location.href || relatedSeen.has(url)) return;
    relatedSeen.add(url);
    const cover = imgsrc(a.querySelector('img'));
    related.push({url, cover, title: tidy(a.querySelector('.video-title,.title')?.textContent || a.getAttribute('title') || a.textContent).slice(0,160)});
  });
  const rateNode = document.querySelector('.score .value, .rating .value, .movie-panel-info .score, .rating-score, [itemprop="ratingValue"]');
  const score = tidy(rateNode?.getAttribute('content') || rateNode?.textContent).slice(0,40);
  const reviewNode = document.querySelector('.review-count, .reviews-count, .movie-panel-info .review-count, .movie-tab[data-tab="reviews"]');
  const reviewCount = tidy(reviewNode?.textContent).slice(0,80);
  const actions = [];
  document.querySelectorAll('.movie-panel-info a[href], .video-detail a[href], .video-meta-panel a[href], .movie-tabs a[href], .tabs a[href], .review-tabs a[href], a[href*="/reviews"], a[href*="/lists"]').forEach(a => {
    if (actions.length >= 30) return;
    const url = href(a.getAttribute('href'));
    const title = tidy(a.textContent || a.getAttribute('title') || a.getAttribute('aria-label')).slice(0,60);
    if (!url || !title || url === location.href || actions.some(x => x.url === url)) return;
    if (/磁力|短评|短評|评论|評論|清单|清單|列表|收藏|想看|看过|看過|评分|評分|预览|預覽|相关|相關/i.test(title) ||
        /\/(?:reviews|lists|collections|wishlists)\b/.test(url)) actions.push({title,url});
  });
  return JSON.stringify({
    url: location.href, score, reviewCount, sections,
    previews: previews.slice(0,40), related: related.slice(0,24), actions
  });
})();