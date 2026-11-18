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
