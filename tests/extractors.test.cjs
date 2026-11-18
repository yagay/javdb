const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.join(__dirname, '../app/src/main/assets');
function run(file, document, url='https://javdb.com/v/abc') {
  const location = {href:url};
  const script = fs.readFileSync(path.join(root, file), 'utf8');
  return JSON.parse(vm.runInNewContext(script, {document, location, URL, Map, Set}, {timeout:1000}));
}
const uri='magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567';
const elem=(attributes,text='高清 中文字幕 1.5 GiB')=>({
  innerText:text, title:'', getAttribute:k=>attributes[k]||null,
  closest:()=>({innerText:text}), parentElement:null
});
const magnets=run('extract_magnets.js',{
 querySelectorAll:()=>[elem({href:uri}),elem({'data-clipboard-text':uri})]
});
assert.equal(magnets.length,1);
assert.equal(magnets[0].size,'1.5 GiB');
assert.equal(magnets[0].uri,uri);
const wrong=run('extract_magnets.js',{querySelectorAll:()=>[elem({href:'javascript:alert(1)'})]});
assert.equal(wrong.length,0);
const nav=run('extract_navigation.js',{
 querySelectorAll:()=>[
   {getAttribute:k=>k==='href'?'/actors':'',textContent:'演员'},
   {getAttribute:k=>k==='href'?'https://evil.example/cat':'',textContent:'站外'},
 ]
});
assert.equal(nav.links.length,1);
assert.equal(nav.links[0].url,'https://javdb.com/actors');
console.log('PASS: magnet extraction, malformed links, same-site navigation');

const imageNode = {
 getAttribute: key => ({'data-original':'//c0.jdbstatic.com/covers/test.jpg',src:'data:image/gif;base64,placeholder'})[key] || null,
 currentSrc:'',
};
const anchorCard = {
 getAttribute: key => key === 'href' ? '/v/abcd12' : key === 'title' ? 'ABP-123 Test' : null,
 matches: sel => sel === 'a[href]',
 querySelector: sel => {
   if (sel === '.cover img, .cover-image img, img') return imageNode;
   if (sel === '.video-title, .title, .movie-title, .uid') return {textContent:'ABP-123 Film'};
   if (sel === '.video-title strong, .uid') return {textContent:'ABP-123'};
   return null;
 },
 textContent: 'ABP-123 Film'
};
const cards = run('extract_cards.js',{
 querySelectorAll:()=>[anchorCard],
 querySelector:()=>null,
 title:'JavDB page'
}, 'https://javdb.com/');
assert.equal(cards.items.length,1,'anchor card must be detected');
assert.equal(cards.items[0].url,'https://javdb.com/v/abcd12');
assert.equal(cards.items[0].cover,'https://c0.jdbstatic.com/covers/test.jpg');
assert.equal(cards.missingCovers,0);
console.log('PASS: JavDB anchor cards, lazy covers and magnet links');


// Desktop page uses <strong> field labels and sibling .value nodes, not colon text.
const blocks = [
  ['番號','ABC-123'],
  ['日期','2026-01-02'],
  ['片商','Example Studio'],
  ['演員','Sample Actor'],
  ['時長','120 分鐘'],
].map(([label,value])=>({
  querySelector: selector => {
    if(selector==='strong, .label, .panel-label') return {textContent:label};
    if(selector==='.value, .panel-value') return {textContent:value};
    return null;
  },
  textContent:label + ' ' + value
}));
const desktopDoc = {
 title: 'ABC-123 example',
 querySelector: selector => {
   if(selector==='.video-meta-panel h2.title') return {textContent:'ABC-123 example'};
   if(selector==='.movie-panel-info') return {innerText:'ABC-123 example',textContent:'ABC-123 example'};
   return null;
 },
 querySelectorAll: selector => {
   if(selector.startsWith('.movie-panel-info .panel-block')) return blocks;
   return [];
 }
};
const desktop=run('extract_details.js',desktopDoc,'https://javdb.com/v/abc123?ref=page');
assert.equal(desktop.code,'ABC-123');
assert.equal(desktop.releaseDate,'2026-01-02');
assert.equal(desktop.studio,'Example Studio');
assert.equal(desktop.actors,'Sample Actor');
assert.equal(desktop.duration,'120 分鐘');
console.log('PASS: desktop full-page metadata labels and values');
