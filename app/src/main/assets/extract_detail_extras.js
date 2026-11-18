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
  // Only link entities inside the film's metadata panel. Never scan the global
  // nav, recommendations, other film cards, or broad tags from the whole site.
  const sections = [];
  const panel = document.querySelector('.movie-panel-info, .video-meta-panel, .video-detail .movie-panel-info');
  const rules = [
    ['演员', '/actors/'],
    ['导演', '/directors/'],
    ['制作商', '/makers/'],
    ['发行商', '/publishers/'],
    ['系列', '/series/'],
    ['标签', '/tags/']
  ];
  if (panel) rules.forEach(([title, path]) => {
    const links = [];
    panel.querySelectorAll('a[href]').forEach(a => {
      const url = href(a.getAttribute('href'));
      const label = tidy(a.textContent || a.getAttribute('title')).slice(0,60);
      if (!url || !new URL(url).pathname.startsWith(path) || !label ||
          links.some(item => item.url === url)) return;
      links.push({title:label, url});
    });
    if (links.length) sections.push({title, links: links.slice(0,18)});
  });
  // Preview screenshots must be scoped to genuine sample/still image containers.
  // Generic ".tile-images a" also matches unrelated tiles and recommendations.
  const previews = [];
  const previewSeen = new Set();
  const screenshotSelectors = [
    '.preview-images', '.sample-images', '.sample-pictures',
    '.screenshots', '.screenshot-images', '.video-screenshots',
    '.movie-screenshots', '#preview-images', '#screenshots',
    '.tile-images.preview-images', '.tile-images.sample-images'
  ];
  const isStill = url => /^https:\/\/[^/?#]+\/[^?#]+\.(?:jpe?g|png|webp)(?:[?#]|$)/i.test(url);
  const isOtherTile = a => !!a.closest?.('.movie-list, .related-videos, .related-movies, .similar-videos, .video-related');
  screenshotSelectors.forEach(selector => {
    document.querySelectorAll(selector).forEach(container => {
      container.querySelectorAll('a[href], img').forEach(node => {
        const a = node.tagName === 'A' ? node : node.closest?.('a[href]');
        if ((a && isOtherTile(a)) || (a && href(a.getAttribute('href'))?.match(/\/v\//))) return;
        const img = node.tagName === 'IMG' ? node : node.querySelector('img');
        // Full-resolution image is preferred to the thumbnail, never movie links.
        const full = a && image(a.getAttribute('href'));
        const url = (full && isStill(full) ? full : '') || imgsrc(img);
        if (!url || !isStill(url) || previewSeen.has(url)) return;
        previewSeen.add(url);
        previews.push(url);
      });
    });
  });
  // JavDB recommendation headings differ by language; scope their cards to the
  // heading's own section/list instead of treating every site-wide movie card as related.
  const recommendationLabels = /(?:你可能也喜[歡欢]|您可能也喜[歡欢]|猜你喜欢|猜你喜歡|猜你喜欢|you may also like|you might also like|recommended for you)/i;
  const recommendations = [];
  const recommendationsSeen = new Set();
  const related = [];
  const relatedSeen = new Set();
  const addMovie = (a, target, seenUrls) => {
    const url = href(a.getAttribute('href'));
    if (!url || !/\/v\//.test(url) || url === location.href || seenUrls.has(url)) return;
    const item = a.closest?.('.item, .box, .grid-item, .movie-box') || a;
    const img = item.querySelector('img') || a.querySelector('img');
    const cover = imgsrc(img);
    const title = tidy(item.querySelector('.video-title, .title, .movie-title')?.textContent ||
      a.getAttribute('title') || img?.getAttribute('alt') || a.textContent).slice(0,160);
    if (!title && !cover) return;
    seenUrls.add(url);
    target.push({url, cover, title});
  };
  const movieLinks = root => Array.from(root.querySelectorAll(
    'a.box.item[href*="/v/"], a[href*="/v/"], .movie-list .item a[href*="/v/"]'
  ));
  const headingNodes = document.querySelectorAll(
    'h1, h2, h3, h4, .section-title, .panel-heading, .block-title, .title'
  );
  headingNodes.forEach(heading => {
    if (!recommendationLabels.test(tidy(heading.textContent))) return;
    const roots = [
      heading.closest?.('section, .panel, .box, .video-related, .related-videos, .related-movies'),
      heading.parentElement,
      heading.parentElement?.nextElementSibling,
      heading.nextElementSibling,
      heading.parentElement?.parentElement
    ].filter(Boolean);
    for (const root of roots) {
      const links = movieLinks(root);
      if (!links.length) continue;
      links.forEach(a => addMovie(a, recommendations, recommendationsSeen));
      if (recommendations.length) break;
    }
  });
  // Some JavDB layouts have a dedicated recommendation list without a heading.
  document.querySelectorAll(
    '.movie-list.you-may-also-like, .movie-list.recommendations, ' +
    '.you-may-also-like, .recommendation-movies, .recommended-videos, #recommendations'
  ).forEach(root => movieLinks(root).forEach(a =>
    addMovie(a, recommendations, recommendationsSeen)));
  document.querySelectorAll(
    '.related-videos a[href*="/v/"], .related-movies a[href*="/v/"], ' +
    '.similar-videos a[href*="/v/"], .video-related a[href*="/v/"]'
  ).forEach(a => addMovie(a, related, relatedSeen));
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
    previews: previews.slice(0,40), recommendations: recommendations.slice(0,36),
    related: related.filter(movie => !recommendationsSeen.has(movie.url)).slice(0,24), actions
  });
})();