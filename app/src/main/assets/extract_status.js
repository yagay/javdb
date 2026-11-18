(function () {
  'use strict';
  const title = String(document.title || '').trim();
  const text = String(document.body?.innerText || document.body?.textContent || '').replace(/\s+/g,' ').slice(0,450);
  const movieCards = document.querySelectorAll('.movie-list .item, .movie-list a[href*="/v/"]').length;
  const infoFields = document.querySelectorAll('.movie-panel-info .panel-block, .video-meta-panel .panel-block').length;
  const directory = document.querySelectorAll('.actor-list .item, .actors-list .item, .maker-list .item, .series-list .item').length;
  const challenge = /(?:just a moment|cloudflare|captcha|access denied|存取被拒|安全驗證|安全验证|人機驗證|人机验证|verify you are human)/i.test(title + ' ' + text);
  return JSON.stringify({
    url: location.href, title: title.slice(0,120),
    movieCards, infoFields, directory, challenge,
    ready: document.readyState,
    bodyLength: document.body?.textContent?.length || 0
  });
})();