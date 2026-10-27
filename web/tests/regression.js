// Regression tests for the Player System app (dist/android-index.html), run with: node tests/regression.js
const {chromium}=require('playwright'); const path=require('path');
const APP='file://'+path.resolve(__dirname,'../dist/android-index.html');
const pad=n=>String(n).padStart(2,'0'); const dk=d=>d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate());
const results=[]; const ok=(name,cond,info)=>{ results.push([name,!!cond,info||""]); };
function baseSeed(t,extra){ return Object.assign({v:3,name:'Tester',startDate:'2026-10-01',lastSeen:dk(t),level:5,xp:0,statPoints:0,stats:{STR:10,VIT:10,INT:10,AGI:10,FOC:10,DIS:10},gold:100,potions:0,streak:3,bestStreak:3,totalDays:3,perfectDays:0,job:'None',title:'Awakened',ach:['awakened'],items:[],counts:{},penalty:null,jobTrial:null,flags:{},history:{},shadows:[],log:[],notes:[],notesSeen:0,castle:{floor:0},keys:1,mp:50,urgent:{[dk(t)]:null,[dk(new Date(t.getTime()+864e5))]:null}},extra||{}); }
async function open(b,time,seed,opts={}){
  const ctx=await b.newContext({viewport:{width:opts.width||390,height:820},timezoneId:'Asia/Kolkata'}); const p=await ctx.newPage(); p.setDefaultTimeout(6000); const errs=[]; p.on('pageerror',e=>errs.push(e.message));
  await p.clock.install({time});
  await p.addInitScript(({seed})=>{ if(!window.__mem) window.__mem={}; let st=localStorage.getItem('__t')||JSON.stringify(seed); window.__st=()=>JSON.parse(st);
    window.Android={load:()=>st,save:j=>{st=j;localStorage.setItem('__t',j)},vibrate:()=>{},versionCode:()=>8,listSnapshots:()=>'[]',readSnapshot:()=>st,exportBackup:()=>{},importBackup:()=>{},requestNotifications:()=>{},share:()=>{},scheduleRunEnd:()=>{},cancelRunEnd:()=>{},scheduleSessionEnd:t=>{window.__sessEnd=t},cancelSessionEnd:()=>{window.__sessEnd=null}}; },{seed});
  await p.goto(APP); await p.clock.runFor(2600);
  const st=()=>p.evaluate(()=>window.__st()); const close=async()=>{ for(let i=0;i<4;i++){ const o=await p.$('[data-act=lu-close]'); if(o){ await o.click({force:true}); await p.clock.runFor(300);} else break; } };
  return {ctx,p,errs,st,close};
}
(async()=>{
  const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'});
  const T=new Date('2026-10-28T08:30:00+05:30'), K=dk(T);

  // 1. Navigation across every screen
  { const {ctx,p,errs,close}=await open(b,T,baseSeed(T));
    for(const n of ['quests','gates','inventory','home']){ await p.click(`[data-nav=${n}]`); await p.clock.runFor(300); }
    for(const to of ['status','skills','stats','castle','friends','settings','store','army','achievements','profile']){ await close(); await p.click('[data-nav=menu]'); await p.clock.runFor(200); await p.click(`.rbtn[data-to=${to}]`); await p.clock.runFor(300); }
    await p.click('[data-nav=home]'); await p.click('[data-act=go][data-to=boss]'); await p.clock.runFor(300);
    ok('1 navigation: all screens open without errors',errs.length===0,errs.join('; ')); await ctx.close(); }

  // 2,5,6,10,12. Completion, haste XP, level, duplicates, streak
  { const {ctx,p,errs,st,close}=await open(b,T,baseSeed(T,{xp:90}));
    await p.click('[data-nav=quests]'); await p.clock.runFor(300);
    await p.click('[data-act=done][data-id=train]'); await p.clock.runFor(500);
    let s=await st(); ok('5 XP: training at 8:30 earns 30 × 1.5 haste = 45',s.totalXp===45,'totalXp='+s.totalXp);
    ok('6 level: crossing 100 XP at Lv5 levels up to 6',s.level===6&&s.xp===35,'level='+s.level+' xp='+s.xp);
    await close(); const dbl=await p.$('[data-act=done][data-id=train]'); ok('10 duplicate: completed quest has no active complete button',!dbl);
    s=await st(); ok('10 duplicate: count recorded once',s.counts.train===1);
    for(const id of ['walk','read','deep']){ await close(); await p.click(`[data-act=done][data-id=${id}]`); await p.clock.runFor(400); }
    s=await st(); ok('2 completion: four quests mark the day cleared',s.history[K].cleared===true);
    ok('12 streak: clearing the day adds 1 to the streak',s.streak===4,'streak='+s.streak);
    ok('2 combo: quests within 20 min build a combo',s.combo>=1,'combo='+s.combo);
    ok('errors in completion flow',errs.length===0,errs.join('; ')); await ctx.close(); }

  // 3. Minimum vs full, and minimum limit
  { const {ctx,p,st,close}=await open(b,new Date('2026-10-28T19:00:00+05:30'),baseSeed(new Date('2026-10-28T19:00:00+05:30')));
    await p.click('[data-nav=quests]'); await p.clock.runFor(300);
    await p.click('[data-act=openq][data-id=read]'); await p.clock.runFor(300);
    let s; ok('3 minimum: blocked before reaching the minimum target',!!(await p.$('[data-act=trkmin][disabled]')));
    await p.click('[data-act=trk][data-n="1"]'); await p.clock.runFor(300); await p.click('[data-act=trkmin]'); await p.clock.runFor(500);
    s=await st(); ok('3 minimum: claimable at 1 page, 40% XP (20 × 0.4 = 8)',s.history[K].min.read===true&&s.totalXp===8,'xp='+s.totalXp);
    await close(); await p.click('[data-act=openq][data-id=water]'); await p.clock.runFor(300); for(let i=0;i<2;i++){ await p.click('[data-act=trk][data-n="1"]'); await p.clock.runFor(200); } await p.click('[data-act=trkmin]'); await p.clock.runFor(400);
    await close(); await p.click('[data-act=openq][data-id=disc]'); await p.clock.runFor(300); const m3=await p.$('[data-act=trkmin]');
    ok('3 minimum: third minimum of the day is not offered',!m3); await ctx.close(); }

  // 4. Timer accuracy, pause, backgrounding and restart
  { const {ctx,p,st}=await open(b,T,baseSeed(T));
    await p.click('[data-nav=quests]'); await p.click('[data-act=openq][data-id=deep]'); await p.clock.runFor(300); await p.click('[data-act=sessstart]'); await p.clock.runFor(300);
    await p.clock.fastForward(10*60000); let s=await st(); const el1=await p.evaluate(()=>{ const ss=window.__st().session; return Date.now()-ss.start-ss.pausedMs; });
    ok('4 timer: elapsed tracks wall clock (10 min)',Math.abs(el1-600000)<5000,'el='+el1);
    const full=await p.$('[data-act=sessfull][disabled]'); ok('4 timer: full completion locked before the full duration',!!full);
    await p.click('[data-act=sesspause]'); await p.clock.fastForward(5*60000);
    const el2=await p.evaluate(()=>{ const ss=window.__st().session; return ss.pausedAt-ss.start-ss.pausedMs; }); ok('4 timer: paused time is not counted',Math.abs(el2-600300)<6000,'el='+el2);
    await p.reload(); await p.clock.runFor(2600); s=await st(); ok('4 timer: session survives an app restart',!!s.session&&s.session.id==='deep');
    await p.click('[data-act=go][data-to=session]').catch(()=>{}); await p.clock.runFor(300); await p.click('[data-act=sessstart]'); await p.clock.fastForward(36*60000); await p.clock.runFor(1200);
    await p.click('[data-act=sessfull]'); await p.clock.runFor(600); s=await st();
    ok('4 timer: full completion after 45 min awards deep work once',s.history[K].q.deep===true&&!s.session,'');
    await ctx.close(); }

  // 7,8,9. Boss HP, victory, reward eligibility
  { const {ctx,p,st,close}=await open(b,T,baseSeed(T));
    await p.click('[data-act=go][data-to=boss]'); await p.clock.runFor(300); let hp=await p.textContent('.card .xp-row .num');
    ok('7 boss: starts at full HP 140/140',hp.trim()==='140 / 140',hp);
    await p.click('[data-nav=quests]'); for(const id of ['train','walk','read','deep','water','disc']){ await close(); await p.click(`[data-act=done][data-id=${id}]`); await p.clock.runFor(400); }
    await p.clock.runFor(1500); await close(); let s=await st();
    ok('8 boss: all quests defeats the boss',s.history[K].bossDown===true);
    ok('9 reward: swift kill before 1 PM grants exactly one chest',s.bonusLoot===1&&/Swift/.test(s.history[K].bossReward),s.history[K].bossReward);
    await p.click('[data-nav=home]'); await p.click('[data-act=go][data-to=boss]'); await p.clock.runFor(300); const atk=await p.$('[data-act=openq]');
    ok('8 boss: no attack button after victory',!atk); await ctx.close(); }
  { const T2=new Date('2026-10-28T20:00:00+05:30'); const {ctx,p,st,close}=await open(b,T2,baseSeed(T2));
    await p.click('[data-nav=quests]'); for(const id of ['train','walk','read','deep','water','disc']){ await close(); await p.click(`[data-act=done][data-id=${id}]`); await p.clock.runFor(400); }
    await p.clock.runFor(1500); const s=await st(); ok('9 reward: after 6 PM no time bonus is granted',!(s.bonusLoot>0)&&s.history[K].bossReward.includes('no time bonus')); await ctx.close(); }

  // 11. Daily reset and penalty
  { const y=new Date('2026-10-27T10:00:00+05:30'); const seed=baseSeed(y,{lastSeen:'2026-10-27',streak:6,history:{'2026-10-27':{q:{train:true},xp:30}}});
    const {ctx,st}=await open(b,new Date('2026-10-28T09:00:00+05:30'),seed); const s=await st();
    ok('11 reset: missed day issues a penalty and resets streak',!!s.penalty&&s.streak===0,'streak='+s.streak); await ctx.close(); }

  // 13. Inventory update from loot
  { const seed=baseSeed(T,{history:{[K]:{q:{},xp:0,cleared:true,loot:true}}}); const {ctx,p,st,close}=await open(b,T,seed);
    await p.click('[data-nav=quests]'); await p.clock.runFor(300); await p.evaluate(()=>{ const R=Math.random; Math.random=()=>0.05; document.querySelector('[data-act=loot]').click(); Math.random=R; }); await p.clock.runFor(500);
    const s=await st(); ok('13 inventory: loot box adds a rare item',s.items.length===1,JSON.stringify(s.items)); await close();
    await p.click('[data-nav=inventory]'); await p.clock.runFor(300); const owned=await p.$$('.tile:not(.locked)'); ok('13 inventory: item shows as owned',owned.length>=2,'owned tiles '+owned.length); await ctx.close(); }

  // 14. Persistence after restart
  { const {ctx,p,st}=await open(b,T,baseSeed(T)); await p.click('[data-nav=quests]'); await p.click('[data-act=done][data-id=water]'); await p.clock.runFor(500);
    await p.reload(); await p.clock.runFor(2600); const s=await st(); ok('14 persistence: completed quest survives restart',s.history[K].q.water===true); await ctx.close(); }

  // 15. Network failure on friends
  { const {ctx,p}=await open(b,T,baseSeed(T)); await p.route('**/rest/v1/rpc/**',r=>r.abort());
    await p.click('[data-nav=menu]'); await p.click('.rbtn[data-to=friends]'); await p.clock.runFor(300); await p.click('[data-act=netjoin]'); await p.clock.runFor(1500);
    const t=await p.textContent('main'); ok('15 network: offline join shows a clear error',/Couldn't reach the network|went wrong/.test(t)); await ctx.close(); }

  // 16. Responsive: no horizontal overflow on a 360px phone
  { const {ctx,p}=await open(b,T,baseSeed(T),{width:360}); let worst=0;
    for(const n of ['home','quests','gates','inventory']){ await p.click(`[data-nav=${n}]`); await p.clock.runFor(300); worst=Math.max(worst,await p.evaluate(()=>document.documentElement.scrollWidth)); }
    ok('16 responsive: no horizontal scroll at 360px',worst<=360,'scrollWidth='+worst); await ctx.close(); }

  await b.close();
  let pass=0; for(const [n,c,i] of results){ console.log((c?'PASS ':'FAIL ')+n+(i&&!c?'  ['+i+']':'')); if(c) pass++; }
  console.log(`\n${pass}/${results.length} passed`); process.exit(pass===results.length?0:1);
})();
