const fs=require('fs');
const F=[['Cinzel','cinzel',[600,700],'normal'],['Chakra Petch','chakra-petch',[500,600,700],'normal'],['Barlow','barlow',[400,500,600],'normal'],['Barlow','barlow',[400],'italic']];
let css='';
for(const [fam,pkg,ws,st] of F) for(const w of ws){
  const p=`node_modules/@fontsource/${pkg}/files/${pkg}-latin-${w}-${st}.woff2`;
  css+=`@font-face{font-family:"${fam}";font-style:${st};font-weight:${w};font-display:swap;src:url(data:font/woff2;base64,${fs.readFileSync(p).toString('base64')}) format("woff2")}\n`;
}
const src=fs.readFileSync('src/app.html','utf8').replace('/*@FONTS@*/',css);
fs.mkdirSync('dist',{recursive:true});
fs.writeFileSync('dist/index.html',src);
// android asset: full document
fs.writeFileSync('dist/android-index.html',`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body>${src}</body></html>`);
console.log((src.length/1024).toFixed(0)+'KB');
