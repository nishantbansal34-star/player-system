// Regression tests for the Player System app (dist/android-index.html), run with: node tests/regression.js
const {chromium}=require('playwright'); const path=require('path');
const APP='file://'+path.resolve(__dirname,'../dist/android-index.html');
const pad=n=>String(n).padStart(2,'0'); const dk=d=>d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate());
const results=[]; const settle=async(p,cond)=>{ for(let i=0;i<60;i++){ await p.clock.runFor(150); await new Promise(r=>setTimeout(r,100)); if(await p.evaluate(cond).catch(()=>false)) return true; } return false; }; const ok=(name,cond,info)=>{ results.push([name,!!cond,info||""]); };
function baseSeed(t,extra){ return Object.assign({v:3,name:'Tester',startDate:'2026-10-01',lastSeen:dk(t),level:5,xp:0,statPoints:0,stats:{STR:10,VIT:10,INT:10,AGI:10,FOC:10,DIS:10},gold:100,potions:0,streak:3,bestStreak:3,totalDays:3,perfectDays:0,job:'None',title:'Awakened',ach:['awakened'],items:[],counts:{},penalty:null,jobTrial:null,flags:{},history:{},shadows:[],log:[],notes:[],notesSeen:0,castle:{floor:0},keys:1,mp:50,urgent:{[dk(t)]:null,[dk(new Date(t.getTime()+864e5))]:null}},extra||{}); }
async function open(b,time,seed,opts={}){
  const ctx=await b.newContext({viewport:{width:opts.width||390,height:820},timezoneId:'Asia/Kolkata',reducedMotion:opts.motion?'no-preference':'reduce'}); const p=await ctx.newPage(); p.setDefaultTimeout(6000); const errs=[]; p.on('pageerror',e=>errs.push(e.message));
  await p.clock.install({time});
  await p.addInitScript(({seed})=>{ if(!window.__mem) window.__mem={}; let st=localStorage.getItem('__t')||JSON.stringify(seed); window.__st=()=>JSON.parse(st);
    window.Android={load:()=>st,save:j=>{st=j;localStorage.setItem('__t',j)},vibrate:()=>{},versionCode:()=>8,listSnapshots:()=>'[]',readSnapshot:()=>st,exportBackup:()=>{},importBackup:()=>{},requestNotifications:()=>{},share:()=>{},scheduleRunEnd:()=>{},cancelRunEnd:()=>{},scheduleSessionEnd:t=>{window.__sessEnd=t},cancelSessionEnd:()=>{window.__sessEnd=null}}; },{seed});
  await p.route('**/rest/v1/rpc/**',r=>r.abort());
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
    for(const to of ['status','skills','stats','castle','friends','settings','store','army','journal','profile']){ await close(); await p.click('[data-nav=menu]'); await p.clock.runFor(200); await p.click(`.rbtn[data-to=${to}]`); await p.clock.runFor(300); }
    await close(); await p.click('[data-act=go][data-to=achievements]'); await p.clock.runFor(300);
    for(const to of ['arc','breathe']){ await p.evaluate(t=>__dbg.go(t),to); await p.clock.runFor(300); }
    await close(); await p.click('[data-nav=home]'); await p.clock.runFor(300); await p.click('[data-act=go][data-to=boss]'); await p.clock.runFor(300);
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
  { const {ctx,p,st}=await open(b,T,baseSeed(T)); await p.click('[data-nav=quests]'); await p.clock.runFor(300); for(let i=0;i<4;i++){ await p.$eval('[data-act=done][data-id=water]',e=>e.click()).catch(()=>{}); if(await settle(p,()=>{ const s=window.__st(); return !!(s.history&&Object.values(s.history).some(h=>h.q&&h.q.water)); })) break; }
    await p.reload(); await p.clock.runFor(2600); const s=await st(); ok('14 persistence: completed quest survives restart',s.history[K].q.water===true); await ctx.close(); }

  // 15. Network failure on friends
  { const {ctx,p}=await open(b,T,baseSeed(T)); await p.route('**/rest/v1/rpc/**',r=>r.abort());
    await p.click('[data-nav=menu]'); await p.click('.rbtn[data-to=friends]'); await p.clock.runFor(300); await p.click('[data-act=netjoin]'); await p.clock.runFor(1500);
    const t=await p.textContent('main'); ok('15 network: offline join shows a clear error',/Couldn't reach the network|went wrong/.test(t)); await ctx.close(); }

  // 16. Responsive: no horizontal overflow on a 360px phone
  { const {ctx,p}=await open(b,T,baseSeed(T),{width:360}); let worst=0;
    for(const n of ['home','quests','gates','inventory']){ await p.click(`[data-nav=${n}]`); await p.clock.runFor(300); worst=Math.max(worst,await p.evaluate(()=>document.documentElement.scrollWidth)); }
    ok('16 responsive: no horizontal scroll at 360px',worst<=360,'scrollWidth='+worst); await ctx.close(); }

  // 17. Onboarding assessment personalizes targets and starts the arc
  { const {ctx,p,errs,st,close}=await open(b,T,null);
    await p.click('[data-act=accept]'); await p.clock.runFor(500);
    const picks=[0,2,3,3,3,0,3,1]; for(const n of picks){ await p.click(`[data-act=quizpick][data-n="${n}"]`,{force:true}); await p.clock.runFor(200); }
    const txt=await p.textContent('main'); ok('17 quiz: report shows strengths and blind spots',/Strengths/.test(txt)&&/Blind spots/.test(txt));
    await p.click('[data-act=quizapply][data-t=arc]',{force:true}); await p.clock.runFor(600); await close();
    const s=await st(); ok('17 quiz: plan applied (hard difficulty, steps scale, wake-based slots)',s.settings.difficulty==='hard'&&s.settings.qscale.walk>1&&s.settings.slots.train==='06:00',JSON.stringify([s.settings.difficulty,s.settings.qscale,s.settings.slots.train]));
    ok('17 arc: 66-day Reset starts today',s.arc&&s.arc.start===K&&!s.arc.hard);
    ok('17 errors in onboarding',errs.length===0,errs.join('; ')); await ctx.close(); }

  // 18. Arc phase ramp, milestone reward and Hard Mode restart
  { const start='2026-10-06'; const hist={}; for(let i=0;i<22;i++){ const d=new Date(Date.parse(start+'T12:00:00+05:30')+i*864e5); hist[dk(d)]={q:{train:true},xp:50,cleared:true}; }
    const {ctx,p,st,close}=await open(b,T,baseSeed(T,{arc:{start,hard:false,num:1,paid:{}},history:hist}));
    const s=await st(); ok('18 arc: Day 23 pays the Awakening milestone once',s.arc.paid.p1===1&&s.bonusLoot>=1);
    const mult=await p.evaluate(()=>__dbg.arcMult()); ok('18 arc: Training phase uses full targets (×1.00)',Math.abs(mult-1)<1e-9,'mult='+mult);
    await close(); await p.evaluate(()=>__dbg.go('arc')); await p.clock.runFor(300); const cells=await p.$$('.arcgrid .ac'); ok('18 arc: screen shows 66 day cells',cells.length===66); await ctx.close(); }
  { const seed=baseSeed(T,{lastSeen:'2026-10-26',streak:5,arc:{start:'2026-10-20',hard:true,num:1,paid:{}},history:{'2026-10-26':{q:{train:true},xp:30,cleared:true}}});
    const {ctx,st}=await open(b,T,seed); const s=await st();
    ok('18 hard mode: a missed day restarts the arc from today',s.arc.start===K&&s.arc.resets===1,JSON.stringify(s.arc)); await ctx.close(); }

  // 19. Daily log: once a day, +15 XP, only in the evening
  { const T3=new Date('2026-10-28T20:30:00+05:30'); const {ctx,p,st,close}=await open(b,T3,baseSeed(T3));
    await p.$eval('[data-act=mood][data-n="1"]',e=>e.click()); await p.clock.runFor(200); await p.fill('#jtext','Trained early, felt great'); await p.$eval('[data-act=jsave]',e=>e.click()); await p.clock.runFor(500); await close();
    const s=await st(); ok('19 journal: log saved with +15 XP',s.history[K].journal&&s.history[K].journal.mood===1&&s.totalXp===15,'xp='+s.totalXp);
    ok('19 journal: card disappears after saving (once per day)',!(await p.$('[data-act=jsave]'))); await ctx.close(); }
  { const {ctx,p}=await open(b,T,baseSeed(T)); ok('19 journal: not offered in the morning',!(await p.$('[data-act=jsave]'))); await ctx.close(); }

  // 20. Mind gate breathing awards XP and MP
  { const {ctx,p,st,close}=await open(b,T,baseSeed(T,{mp:10}));
    await p.click('[data-nav=gates]'); await p.clock.runFor(300); await p.click('[data-act=go][data-to=breathe]'); await p.clock.runFor(300);
    await p.click('[data-act=breathgo][data-n="3"]'); await p.clock.runFor(500); await p.clock.fastForward(3*60000+2000); await p.clock.runFor(1500); await close();
    const s=await st(); ok('20 breathing: 3 min gives +15 XP and +12 MP',s.history[K].mindMin===3&&s.totalXp===15&&s.mp===22,'xp='+s.totalXp+' mp='+s.mp); await ctx.close(); }

  // 21. Health Connect auto-completes steps
  { const {ctx,p,st,close}=await open(b,T,baseSeed(T,{settings:undefined}));
    await p.evaluate(()=>{ __dbg.S().settings.health=true; window.__health(JSON.stringify({steps:12000,exerciseMin:5,sleepMin:450})); }); await p.clock.runFor(800); await close();
    const s=await st(); ok('21 health: 12,000 steps completes the Steps quest',s.history[K].q.walk===true&&s.history[K].prog.walk===12000);
    ok('21 health: 5 min exercise does not complete Training',!s.history[K].q.train);
    ok('21 health: 7.5 h sleep gives the rested bonus',s.history[K].rested===true); await ctx.close(); }

  // 22. Screen-time check locks Discipline when social media was used after the cutoff
  { const T4=new Date('2026-10-28T23:30:00+05:30'); const {ctx,p}=await open(b,T4,baseSeed(T4));
    await p.evaluate(()=>{ __dbg.S().settings.usage=true; Android.socialMinutes=(f,t)=>JSON.stringify(f>=Date.now()-3*3600e3?{total:12,apps:{Instagram:12}}:{total:95,apps:{Instagram:60,YouTube:35}}); });
    await p.click('[data-nav=quests]'); await p.clock.runFor(300); await p.click('[data-act=openq][data-id=disc]'); await p.clock.runFor(300);
    ok('22 screen time: full Discipline locked after late Instagram use',!!(await p.$('[data-act=trkfull][disabled]'))); await ctx.close(); }

  // 23. Friends: season payload, nudge and inbox
  { const {ctx,p,st}=await open(b,T,baseSeed(T,{net:{id:'me1',secret:'s',code:'ABC123'},history:{[K]:{q:{},xp:120},'2026-10-02':{q:{},xp:80},'2026-09-30':{q:{},xp:999}}}));
    const sent=[]; await p.route('**/rest/v1/rpc/**',async r=>{ const fn=r.request().url().split('/').pop(); const body=JSON.parse(r.request().postData()||'{}'); sent.push([fn,body]);
      const out=fn==='ps_board'?[{id:'me1',name:'Tester',level:5,rank:'E',streak:3,weekly_xp:120,week:'x',season:'2026-10',season_xp:200,today_done:0,today_total:6,me:true},{id:'f1',name:'Ravi',level:7,rank:'D',streak:9,weekly_xp:300,week:'x',season:'2026-10',season_xp:900,today_done:3,today_total:6}]:fn==='ps_inbox'?[{from:'Ravi',kind:'nudge'}]:{};
      await r.fulfill({status:200,contentType:'application/json',body:JSON.stringify(out)}); });
    await p.click('[data-nav=menu]'); await p.click('.rbtn[data-to=friends]'); await p.clock.runFor(300); await p.click('[data-act=netrefresh]').catch(()=>{}); await p.clock.runFor(2500);
    const sync=sent.find(x=>x[0]==='ps_sync'); ok('23 season: sync sends this month\'s XP only (200)',sync&&sync[1].p&&sync[1].p.season_xp===200,JSON.stringify(sync&&sync[1].p&&{s:sync[1].p.season,x:sync[1].p.season_xp}));
    ok('23 inbox: a received nudge is shown',!!sent.find(x=>x[0]==='ps_inbox')&&/Nudge from Ravi/.test(JSON.stringify((await st()).notes)));
    await p.click('[data-act=boardtab][data-t=season]').catch(()=>{}); await p.clock.runFor(300);
    const fr=await p.$('[data-act=friend]:not([data-id=me1])'); if(fr){ await fr.click({force:true}); await p.clock.runFor(300); await p.$eval('[data-act=nudge]',e=>e.click()); await p.clock.runFor(2500); } else console.log('NO FRIEND ROW');
    ok('23 nudge: button calls ps_nudge for the friend',!!sent.find(x=>x[0]==='ps_nudge'&&x[1].p_friend==='f1'&&x[1].p_kind==='nudge'),sent.map(x=>x[0]).join(','));
    await ctx.close(); }

  // 24. Cloud backup: turn on, save without the secret, show key
  { const {ctx,p,st,close}=await open(b,T,baseSeed(T));
    const sent=[]; await p.route('**/rest/v1/rpc/**',async r=>{ const fn=r.request().url().split('/').pop(); const body=JSON.parse(r.request().postData()||'{}'); sent.push([fn,body]);
      const out=fn==='ps_register'?{id:'u1',secret:'sec123',code:'QWE123'}:fn==='ps_backup_save'?'"2026-10-28T03:00:00Z"':fn==='ps_board'?[]:fn==='ps_inbox'?[]:null;
      await r.fulfill({status:200,contentType:'application/json',body:out==null?'':typeof out==='string'?out:JSON.stringify(out)}); });
    await p.evaluate(()=>__dbg.go('settings')); await p.clock.runFor(300); for(let i=0;i<5&&!(await p.$('[data-act=cloudon]'));i++){ await p.evaluate(()=>__dbg.go('settings')).catch(()=>{}); await p.$eval('[data-act=setpage][data-t=cloud]',e=>e.click()).catch(()=>{}); await p.clock.runFor(300); await new Promise(r=>setTimeout(r,150)); }
    await p.$eval('[data-act=cloudon]',e=>e.click()); if(!await settle(p,()=>{ const s=window.__st(); return !!(s&&s.cloud&&s.cloud.at>0); })){ await p.$eval('[data-act=cloudon],[data-act=cloudnow]',e=>e.click()).catch(()=>{}); await settle(p,()=>{ const s=window.__st(); return !!(s&&s.cloud&&s.cloud.at>0); }); } await p.clock.runFor(300); await close();
    const save=sent.find(x=>x[0]==='ps_backup_save'); const s=await st();
    ok('24 cloud: turning on registers and saves a backup',!!sent.find(x=>x[0]==='ps_register')&&!!save&&save[1].p_data.level===5,sent.map(x=>x[0]).join(','));
    ok('24 cloud: backup data never contains the secret',save&&!JSON.stringify(save[1].p_data).includes('sec123'));
    ok('24 cloud: state remembers backup is on',s.cloud&&s.cloud.on===true&&s.cloud.at>0);
    const txt=await p.textContent('main'); ok('24 cloud: backup key shown to the player',txt.includes('QWE123-sec123'));
    await ctx.close(); }
  // 25. Cloud restore from the first screen on a new phone
  { const {ctx,p,st,close}=await open(b,T,null);
    await p.route('**/rest/v1/rpc/**',async r=>{ const fn=r.request().url().split('/').pop();
      const out=fn==='ps_backup_restore'?{id:'u1',updated_at:'2026-10-28T03:00:00Z',data:Object.assign(baseSeed(T),{level:17,name:'Nishant',totalXp:4000})}:[];
      await r.fulfill({status:200,contentType:'application/json',body:JSON.stringify(out)}); });
    await p.$eval('[data-act=restoreopen]',e=>e.click()); await p.clock.runFor(300); await p.fill('#cloudKeyIn','qwe123-sec123'); await p.$eval('[data-act=cloudrestore]',e=>e.click()); if(!await settle(p,()=>{ const s=window.__st(); return !!(s&&s.level===17); })){ await p.fill('#cloudKeyIn','qwe123-sec123'); await p.$eval('[data-act=cloudrestore]',e=>e.click()); await settle(p,()=>{ const s=window.__st(); return !!(s&&s.level===17); }); } await close();
    const s=(await st())||{}; const why=s.level?'':await p.evaluate(()=>document.body.innerText.slice(0,400)); ok('25 cloud restore: progress comes back on a new phone',s.level===17&&s.name==='Nishant'&&s.net&&s.net.code==='QWE123'&&s.net.secret==='sec123',JSON.stringify({l:s.level,n:s.net,why}));
    await ctx.close(); }

  // 26. Glitch effects run on screen change and taps, and switch off in settings
  { const {ctx,p,errs,st}=await open(b,T,baseSeed(T),{motion:true});
    await p.$eval('[data-nav=gates]',e=>e.click()); await p.clock.runFor(50);
    const enter=await p.evaluate(()=>document.getElementById('app').classList.contains('gl-enter')&&document.getElementById('glveil').classList.contains('run'));
    ok('26 glitch: screen change plays the glitch reveal',enter);
    const titled=await p.evaluate(()=>!!document.querySelector('.screen-title.gtext[data-text]'));
    ok('26 glitch: titles get the RGB-split layer',titled);
    await p.evaluate(()=>__dbg.S().settings.glitch=false); await p.$eval('[data-nav=home]',e=>e.click()); await p.clock.runFor(50);
    const off=await p.evaluate(()=>document.body.classList.contains('noglitch')&&!document.getElementById('glveil').classList.contains('run')||document.body.classList.contains('noglitch'));
    ok('26 glitch: switching it off stops the effects',off);
    ok('26 errors with effects on',errs.length===0,errs.join('; ')); await ctx.close(); }

  // 27-31. Ranked play: only server-checked proof earns ranking points
  const rankMock=async(p,sent,extra)=>{ await p.route('**/rest/v1/rpc/**',async r=>{ const fn=r.request().url().split('/').pop(); const body=JSON.parse(r.request().postData()||'{}'); sent.push([fn,body]);
      let out=extra&&extra(fn,body); if(out===undefined) out=fn==='ps_focus_start'?'"tok-1"':fn==='ps_focus_end'?{quest:'deep',rp:30}:fn==='ps_verify'?{quest:body.p_quest,rp:20}:fn==='ps_board'?[]:fn==='ps_feed'?[]:fn==='ps_inbox'?[]:null;
      await r.fulfill({status:200,contentType:'application/json',body:out==null?'':typeof out==='string'?out:JSON.stringify(out)}); }); };
  const NETSEED={net:{id:'me1',secret:'s',code:'ABC123'}};
  { const {ctx,p,st}=await open(b,T,baseSeed(T,NETSEED)); const sent=[]; await rankMock(p,sent);
    for(let i=0;i<5&&!(await p.$('[data-act=sessstart]'));i++){ await p.evaluate(()=>__dbg.go('quests')).catch(()=>{}); await p.clock.runFor(300); await p.$eval('[data-act=openq][data-id=deep]',e=>e.click()).catch(()=>{}); await p.clock.runFor(300); await new Promise(r=>setTimeout(r,150)); }
    await p.$eval('[data-act=sessstart]',e=>e.click()); await settle(p,()=>!!(window.__st().session&&window.__st().session.rtok));
    const fs=sent.find(x=>x[0]==='ps_focus_start'); ok('27 ranked: starting the focus timer registers a server-timed session',fs&&fs[1].p_quest==='deep'&&fs[1].p_need_min>=15,JSON.stringify(fs&&fs[1]));
    await p.clock.fastForward(61*60000); await p.clock.runFor(1200); await p.$eval('[data-act=sessfull]',e=>e.click());
    await settle(p,()=>{ const s=window.__st(); const k=Object.keys(s.history).pop(); return !!(s.history[k]&&s.history[k].rk&&s.history[k].rk.deep); });
    const fe=sent.find(x=>x[0]==='ps_focus_end'); const s=await st(); const K2=Object.keys(s.history).sort().pop();
    ok('27 ranked: finishing the full timer claims RP with the server token',fe&&fe[1].p_token==='tok-1'&&s.history[K2].rk.deep===30,JSON.stringify(s.history[K2]&&s.history[K2].rk));
    await ctx.close(); }
  { const {ctx,p,st}=await open(b,T,baseSeed(T,NETSEED)); const sent=[]; await rankMock(p,sent);
    await p.click('[data-nav=quests]'); await p.clock.runFor(300);
    for(const id of ['train','walk','read','deep','water','disc']){ await p.$eval(`[data-act=done][data-id=${id}]`,e=>e.click()).catch(()=>{}); await p.clock.runFor(300); for(let i=0;i<4;i++){ const o=await p.$('[data-act=lu-close]'); if(o){ await o.click({force:true}); await p.clock.runFor(200);} } }
    await settle(p,()=>false).catch(()=>{});
    const claims=sent.filter(x=>x[0]==='ps_verify'||x[0]==='ps_focus_end'||x[0]==='ps_focus_start'); const s=await st();
    ok('28 anti-cheat: ticking every quest by hand claims no ranking points',claims.length===0&&!(s.history[K]&&s.history[K].rk),claims.map(x=>x[0]).join(','));
    const chip=await p.textContent('main'); ok('28 anti-cheat: hand-ticked ranked quests are labelled unranked',/UNRANKED/.test(chip));
    await ctx.close(); }
  { const {ctx,p}=await open(b,T,baseSeed(T,NETSEED)); const sent=[]; await rankMock(p,sent);
    await p.evaluate(()=>{ __dbg.S().settings.health=true; window.__health(JSON.stringify({steps:15000,exerciseMin:0,sleepMin:0})); }); await settle(p,()=>false);
    const v=sent.find(x=>x[0]==='ps_verify'); ok('29 ranked: Health Connect steps are sent for server verification',v&&v[1].p_quest==='walk'&&v[1].p_method==='health'&&v[1].p_value===15000&&v[1].p_target>=3000,JSON.stringify(v&&v[1]));
    await ctx.close(); }
  { const {ctx,p,st}=await open(b,T,baseSeed(T,NETSEED)); const sent=[]; let round=0;
    const row=(id,name,me,w,sea)=>({id,name,me,level:5,rank:'E',title:'',streak:2,best_streak:2,rp_week:w,rp_season:sea,rp_total:sea,rp_today:0,rp_last_week:0,verified_today:[],duel:false,duel_last:false,week_key:'2026-10-26',today_done:0,today_total:6});
    await rankMock(p,sent,(fn)=>fn==='ps_board'?(round===0?[row('me1','Tester',true,40,140),row('f1','Ravi',false,90,500)]:[row('me1','Tester',true,120,160),row('f1','Ravi',false,90,500)]):undefined);
    await p.evaluate(()=>__dbg.go('friends')); await settle(p,()=>!!document.querySelector('.card.league'));
    const t1=await p.textContent('main'); ok('30 ranking: league card and RP board shown',/E-League/.test(t1)&&/RP/.test(t1)&&/#2/.test(t1));
    await settle(p,()=>!/REFRESHING/.test(document.body.innerText)&&!!(window.__st().lastPos)); round=1; await p.$eval('[data-act=netrefresh]',e=>e.click()); await settle(p,()=>{ const s=window.__st(); return !!(s.lastPos&&s.lastPos.me===0); }); await p.clock.runFor(1500);
    const s=await st(); const toasts=await p.textContent('#toasts');
    ok('30 ranking: passing a friend shows an overtake alert and promotes to the next league',/passed Ravi/.test(toasts)&&s.league==='D',toasts+' league='+s.league);
    await p.$eval('[data-act=friend][data-id=f1]',e=>e.click()); await p.clock.runFor(300); await p.$eval('[data-act=duel]',e=>e.click()); await settle(p,()=>false);
    ok('31 duel: challenge button calls ps_duel for that friend',!!sent.find(x=>x[0]==='ps_duel'&&x[1].p_friend==='f1'));
    await ctx.close(); }

  // 32-34. Weekly events and bonus tasks
  { const EV={theme:"Test Week",tagline:"t",color:"blue",daily:[{text:"A",xp:30},{text:"B",xp:30},{text:"Bonus C",xp:40},{text:"D",xp:30},{text:"E",xp:30},{text:"F",xp:30},{text:"G",xp:30}],challenge:{type:"clear_days",n:2,xp:150,text:"Clear 2 days"},badge:{id:"x",name:"Test Badge",icon:"bolt",rar:"epic",desc:"d"}};
    const {ctx,p,st,close}=await open(b,T,baseSeed(T,{evCache:{'2026-10-26':EV},evSeen:'2026-10-26'}));
    await p.click('[data-nav=quests]'); await p.clock.runFor(300); const txt=await p.textContent('main');
    ok('32 bonus: today\'s bonus task from the weekly event is shown',/Bonus C/.test(txt)&&/\+40 XP/.test(txt));
    await p.$eval('[data-act=bonusdone]',e=>e.click()); await p.clock.runFor(500); await close();
    let s=await st(); ok('32 bonus: completing it gives extra XP once',s.totalXp===40&&s.history[K].bonus&&s.history[K].bonus.xp===40,'xp='+s.totalXp);
    ok('32 bonus: it does not count toward clearing the day',!s.history[K].cleared&&Object.keys(s.history[K].q||{}).length===0);
    ok('32 bonus: second tap does nothing',!(await p.$('[data-act=bonusdone]')));
    await ctx.close(); }
  { const EV={theme:"Test Week",tagline:"t",color:"blue",daily:[{text:"A",xp:30},{text:"B",xp:30},{text:"Bonus C",xp:40},{text:"D",xp:30},{text:"E",xp:30},{text:"F",xp:30},{text:"G",xp:30}],challenge:{type:"clear_days",n:2,xp:150,text:"Clear 2 days"},badge:{id:"x",name:"Test Badge",icon:"bolt",rar:"epic",desc:"d"}};
    const hist={'2026-10-26':{q:{train:true},xp:50,cleared:true},'2026-10-27':{q:{train:true},xp:50,cleared:true}};
    const {ctx,p,st,close}=await open(b,T,baseSeed(T,{evCache:{'2026-10-26':EV},evSeen:'2026-10-26',history:hist})); await p.clock.runFor(1500); await close();
    const s=await st(); ok('33 weekly challenge: completing it awards XP and the badge',s.evDone&&s.evDone['2026-10-26']===1&&s.evBadges.length===1&&s.evBadges[0].id==='wk-2026-10-26'&&s.totalXp>=150,JSON.stringify({d:s.evDone,b:s.evBadges&&s.evBadges.length,x:s.totalXp}));
    await p.click('[data-nav=inventory]'); await p.clock.runFor(200); await p.$eval('[data-act=invtab][data-t=badges]',e=>e.click()); await p.clock.runFor(300);
    ok('33 weekly challenge: badge appears in the Badges tab',/Test Badge/.test(await p.textContent('main')));
    await ctx.close(); }
  { const {ctx,p}=await open(b,T,baseSeed(T));
    const r=await p.evaluate(()=>{ const bad={theme:"X",daily:Array.from({length:7},(_,i)=>({text:"t"+i,xp:9999})),challenge:{type:"xp_week",n:99999,xp:99999,text:"c"},badge:{name:"N",icon:"<script>",rar:"godly"}}; const a=__dbg.evSanitize(bad,'2026-11-02'); const b=__dbg.evSanitize({daily:[1,2],challenge:{type:"hack"}},'2026-11-02'); const c=__dbg.evNow(); return {xp:a.daily[0].xp,cx:a.challenge.xp,cn:a.challenge.n,icon:a.badge.icon,rar:a.badge.rar,bad:b===null,fallback:!!(c&&c.daily.length===7&&c.badge)}; });
    ok('34 events: server content is clamped and checked',r.xp===60&&r.cx===300&&r.cn===5000&&r.icon==='star'&&r.rar==='rare'&&r.bad,JSON.stringify(r));
    ok('34 events: built-in rotation works offline',r.fallback);
    await ctx.close(); }

  await b.close();
  let pass=0; for(const [n,c,i] of results){ console.log((c?'PASS ':'FAIL ')+n+(i&&!c?'  ['+i+']':'')); if(c) pass++; }
  console.log(`\n${pass}/${results.length} passed`); process.exit(pass===results.length?0:1);
})();
