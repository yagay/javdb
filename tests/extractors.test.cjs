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


const actorPhoto = {getAttribute: attr => attr === 'data-original' ? '//c0.jdbstatic.com/actors/avatar.jpg' : null};
const actorCard = {
  getAttribute: attr => attr === 'href' ? '/actors/xyz' : null,
  closest: selector => {
    if (selector.startsWith('header')) return null;
    if (selector.startsWith('.box')) return actorCard;
    return null;
  },
  querySelector: selector => {
    if (selector === 'img') return actorPhoto;
    if (selector === '.name, .title, .actor-name, .label') return {textContent:'Actor Example'};
    return null;
  },
  textContent:'Actor Example'
};
const directory=run('extract_directory.js',{
  querySelectorAll:()=>[actorCard],
},'https://javdb.com/actors');
assert.equal(directory.items.length,1);
assert.equal(directory.items[0].url,'https://javdb.com/actors/xyz');
assert.equal(directory.items[0].cover,'https://c0.jdbstatic.com/actors/avatar.jpg');
console.log('PASS: native entity directory source extraction');


// Verify desktop GET dropdown preserves existing active query filters.
const formSelect = {
 name: 'sort',
 disabled: false,
 tagName: 'SELECT',
 options: [
   {value:'recent',textContent:'最近',selected:false},
   {value:'rating',textContent:'评分',selected:true}
 ],
 getAttribute: key => key === 'aria-label' ? '排序' : null,
 closest:()=>null
};
const form = {
 getAttribute: key => ({method:'get',action:'/tags'})[key] || null,
 querySelectorAll:()=>[formSelect]
};
const filtersDoc = {
 querySelectorAll: selector => {
   if (selector==='form') return [form];
   return [];
 },
 querySelector:()=>null
};
const filters=run('extract_filters.js',filtersDoc,'https://javdb.com/tags?c2=5&sort=recent');
const sortGroup=filters.groups.find(g=>g.title==='排序');
assert.ok(sortGroup,'desktop GET dropdown should appear as native sort options');
assert.equal(sortGroup.links.length,2);
assert.ok(sortGroup.links[1].selected);
assert.ok(sortGroup.links[1].url.includes('c2=5'),'existing filters must be preserved');
assert.ok(sortGroup.links[1].url.includes('sort=rating'),'selected sort must be encoded');
console.log('PASS: desktop form sorting and existing-filter preservation');


// Detail previews must never swallow unrelated .tile-images cards; recommendations
// must come from the "你可能也喜歡" heading's own section.
const sampleImg = {
  tagName:'IMG', currentSrc:'',
  getAttribute:k=> k==='data-src' ? 'https://cdn.example.com/sample_01.jpg' : null,
  closest:()=>null
};
const sampleLink = {
  tagName:'A', getAttribute:k=>k==='href'?'https://cdn.example.com/sample_01_large.jpg':null,
  querySelector:sel=>sel==='img'?sampleImg:null,
  closest:()=>null
};
const sampleContainer = {querySelectorAll:()=>[sampleLink]};
const recommendationImg = {
  tagName:'IMG',currentSrc:'',
  getAttribute:k=>k==='data-original'?'https://cdn.example.com/recommendation_cover.jpg':null
};
const recommendationLink = {
  tagName:'A', textContent:'ABC-456 recommended', 
  getAttribute:k=>k==='href'?'/v/recommend456':k==='title'?'ABC-456 recommended':null,
  querySelector:sel=>sel==='img'?recommendationImg:null,
  closest:()=>null
};
const recommendationRoot = {querySelectorAll:()=>[recommendationLink]};
const recommendationHeading = {
  textContent:'你可能也喜歡',
  closest:()=>null,
  parentElement:recommendationRoot,
  nextElementSibling:null
};
const detailDocument = {
  querySelector:()=>null,
  querySelectorAll:selector=>{
    if(selector==='.preview-images') return [sampleContainer];
    if(selector.includes('h1, h2, h3')) return [recommendationHeading];
    return [];
  }
};
const extraResults=run('extract_detail_extras.js',detailDocument,'https://javdb.com/v/source123');
assert.equal(extraResults.previews.length,1);
assert.equal(extraResults.previews[0],'https://cdn.example.com/sample_01_large.jpg');
assert.equal(extraResults.recommendations.length,1);
assert.equal(extraResults.recommendations[0].url,'https://javdb.com/v/recommend456');
assert.equal(extraResults.related.length,0);
console.log('PASS: screenshot-only previews and headed recommendations');

const unrelatedImageOnly = {
  querySelector:()=>null,
  querySelectorAll:selector=>{
    // Previous broad .tile-images query would incorrectly include this item.
    if(selector.includes('.tile-images a') && selector.includes('.sample-images a'))
      return [recommendationLink];
    return [];
  }
};
const unrelatedResult=run('extract_detail_extras.js',unrelatedImageOnly,'https://javdb.com/v/source123');
assert.equal(unrelatedResult.previews.length,0);
console.log('PASS: unrelated tile pictures excluded from previews');


// Film metadata must be scoped to the movie info panel, not global recommendations
// or navbar links; SEO description is not a film synopsis.
const fakeLink = (url,title)=>({
  textContent:title,
  getAttribute:k=>k==='href'?url:k==='title'?title:null
});
const actorLink=fakeLink('/actors/current-person','Current actor');
const tagLink=fakeLink('/tags/current-tag','Current tag');
const extraneous=[fakeLink('/actors/other','Unrelated actor'),fakeLink('/tags/random','Random tag')];
const panel = {querySelectorAll: selector=>selector==='a[href]'?[actorLink,tagLink]:[]};
const scopedDoc = {
  querySelector: selector=>{
    if(selector==='.movie-panel-info, .video-meta-panel, .video-detail .movie-panel-info')return panel;
    if(selector==='meta[name="description"]')return {getAttribute:()=> 'An magnet links sharing website for adult movies'};
    return null;
  },
  querySelectorAll: selector=>{
    if(selector.includes('h1, h2, h3'))return [];
    if(selector==='a[href]')return extraneous;
    return [];
  },
  title:'ABC-123 title'
};
const strictExtras=run('extract_detail_extras.js',scopedDoc,'https://javdb.com/v/abc123');
const actorSection=strictExtras.sections.find(s=>s.title==='演员');
assert.equal(actorSection.links.length,1);
assert.equal(actorSection.links[0].title,'Current actor');
assert.equal(strictExtras.sections.find(s=>s.title==='标签').links[0].title,'Current tag');
assert.ok(!JSON.stringify(strictExtras.sections).includes('Unrelated actor'));
const strictDetails=run('extract_details.js',scopedDoc,'https://javdb.com/v/abc123');
assert.equal(strictDetails.description,'','Generic SEO description must not be displayed');
assert.equal(strictDetails.excerpt,'','Whole info panel is not a synopsis');
console.log('PASS: scoped film links and no global SEO synopsis');


// Actual /tags category groups use a selected .tag with delete button. The
// delete URL must remove only the active tag, preserving other categories.
const toggleDelete = {
  getAttribute: key => key === 'onclick'
    ? "window.location.href='/tags?c2=5&c10=1'" : null,
};
const selectedTag = {
  textContent:'Selected A',
  classList:{contains: name=>name==='is-info'},
  querySelector:sel=>sel==='button.delete'?toggleDelete:null,
  getAttribute:()=>null,
};
const otherTag = {
  textContent:'Other B',
  classList:{contains:()=>false},
  querySelector:()=>null,
  getAttribute:key=>key==='href'?'/tags?c2=5,6&c10=1':null,
};
const tagGroup = {
  getAttribute:key=>key==='data-cid'?'2':null,
  querySelector:sel=>sel==='strong'?{textContent:'主题：'}:null,
  querySelectorAll:()=>[selectedTag,otherTag]
};
const tagsDoc = {
  querySelectorAll:sel=>sel==='dt.tag-category'?[tagGroup]:[],
  querySelector:()=>null
};
const actualTags=run('extract_filters.js',tagsDoc,'https://javdb.com/tags?c2=5,7&c10=1');
assert.equal(actualTags.groups.length,1);
assert.equal(actualTags.groups[0].title,'主题');
assert.equal(actualTags.groups[0].links[0].selected,true);
assert.equal(actualTags.groups[0].links[0].url,'https://javdb.com/tags?c2=5&c10=1');
assert.equal(actualTags.groups[0].links[1].selected,false);
assert.equal(actualTags.groups[0].links[1].url,'https://javdb.com/tags?c2=5,6&c10=1');
console.log('PASS: real tag-category DOM, selected-tag removal, other filters retained');


// Regression: a global navigation anchor targeting /actors must not appear as
// Censored filter, and a tag option must remain on /tags.
const badLink={
 textContent:'Censored',
 getAttribute:key=>key==='href'?'/actors':null
};
const goodLink={
 textContent:'Censored',
 getAttribute:key=>key==='href'?'/tags?c10=1':null
};
const navTagNode={
 querySelector:()=>({textContent:'分类'}),
 getAttribute:()=>null,
 className:'tabs',
 closest:()=>null,
 querySelectorAll:()=>[badLink,goodLink]
};
const fallbackTagDocument={
 querySelector:()=>null,
 querySelectorAll:selector=>selector==='dt.tag-category'?[]:
    selector.includes('.filter-panel')?[navTagNode]:[]
};
const strictTagFilters=run('extract_filters.js',fallbackTagDocument,'https://javdb.com/tags');
assert.equal(strictTagFilters.groups.length,0,'Tag page must not fall back to navigation tabs');
const ordinaryFilters=run('extract_filters.js',fallbackTagDocument,'https://javdb.com/');
assert.ok(ordinaryFilters.groups.flatMap(g=>g.links).every(link=>!link.url.endsWith('/actors')),
  'Actor route must never be exposed as a category filter');
console.log('PASS: Censored cannot accidentally navigate to actors via filter bar');


// Even a broken/mis-associated entry inside dt.tag-category must never send
// a Censored tag action to /actors.
const badCategoryNode={
 textContent:'Censored',
 classList:{contains:()=>false},
 querySelector:()=>null,
 getAttribute:k=>k==='href'?'/actors':null,
};
const badCategoryGroup={
 getAttribute:k=>k==='data-cid'?'10':null,
 querySelector:sel=>sel==='strong'?{textContent:'类别'}:null,
 querySelectorAll:()=>[badCategoryNode]
};
const badCategoryDoc={
 querySelectorAll:sel=>sel==='dt.tag-category'?[badCategoryGroup]:[],
 querySelector:()=>null,
};
const blockedCensored=run('extract_filters.js',badCategoryDoc,'https://javdb.com/tags');
assert.equal(blockedCensored.groups.length,0);
console.log('PASS: invalid Censored actor destination blocked inside tag-category');
