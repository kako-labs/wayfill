(() => {
  'use strict';
  const $=id=>document.getElementById(id);
  const esc=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const paths={pump:'M5 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h14M5 10h10M15 8h2l3 3v6a1.5 1.5 0 0 0 3 0V9l-3-3',route:'M5 19V7a3 3 0 0 1 6 0v10a3 3 0 0 0 6 0V5M14 8l3-3 3 3',check:'M5 12l4 4L19 6',shield:'M12 3l8 3v6c0 4-8 9-8 9s-8-5-8-9V6l8-3M8 11l3 3 5-6',home:'M3 11l9-8 9 8M5 9v12h5v-7h4v7h5V9',settings:'M9 4h6l1 3 3 1 1 5-3 2-1 4-5 1-2-3-4-1-1-5 3-2 2-5M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',clock:'M12 8v5l4 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0'};
  const icon=name=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${paths[name]||paths.pump}"/></svg>`;
  const portrait=(calm=true)=>SunsetPages.character(calm?'calm':'recommend',calm);
  let request=null,result=null,dismissed=false,expired=false,timer=null,expiresAt=null;
  let page='home',selectedRecord=null,refuelEvent=null,lastRefuel=null,players=[],recommendationStartedAt=performance.now();
  const storageKey='wayfill.sunset.synthetic-v1';
  let preferences;
  try{preferences=SunsetPreviewState.restore(JSON.parse(localStorage.getItem(storageKey)||'{}'));}
  catch(_){preferences=SunsetPreviewState.restore({});}
  function save(event){
    const next=SunsetPreviewState.reduce(preferences,event);
    try{localStorage.setItem(storageKey,JSON.stringify(next));preferences=next;return true;}
    catch(_){$('status').textContent='시연 설정·기록을 저장하지 못했어요. 브라우저의 저장 공간 설정을 확인해 주세요.';return false;}
  }
  function mountCharacters(s){
    const still=s.motion!=='parked'||!preferences.character||matchMedia('(prefers-reduced-motion: reduce)').matches;
    players=[...document.querySelectorAll('#companion canvas[data-motion]')].map(c=>SunsetPlayer.mount(c,c.dataset.motion,{still:still||c.dataset.still==='true',startedAt:refuelEvent?refuelEvent.startedAt:c.dataset.motion==='recommend'?recommendationStartedAt:undefined,durationMs:c.dataset.motion==='small'?1600:c.dataset.motion==='full'||c.dataset.motion==='recovery'?2400:undefined}));
  }

  const get=()=>({surface:$('surface').value,motion:$('motion').value,data:$('data').value,scenario:$('scenario').value});
  function facts(s){const passing=s.scenario!=='destination';return{passing,station:s.scenario==='long'?'경부고속도로 서울방향 만남의광장 주유소':passing?'주유소 B':'주유소 A',distance:passing?'3':'20',extra:passing?'3':'1',range:passing?'18':'75',destination:passing?'38':'95',shortage:'20'};}
  const actions=()=>`<div class="actions"><button type="button" data-action="guide">바로 안내 ${icon('route')}</button><button type="button" data-action="later">나중에</button></div>`;
  function header(s){return `<div class="app-head"><div class="brand"><div class="brand-icon">${icon('pump')}</div><div><b>WAYFILL</b><small>다음 주유 기회를 함께 보는 뽀동이</small></div></div><span class="motion-state">${s.motion==='parked'?'정차 중':s.motion==='moving'?'주행 중':'차량 확인 중'}</span></div>`;}
  function waitCopy(s){if(s.scenario==='noaction')return ['지금은 여유 있게 갈 수 있어요','이번 예시 경로는 중간 주유가 필요하지 않아요.'];if(s.motion==='unknown')return ['차량 연결을 기다리고 있어요','신호가 확인되면 현재 경로에 맞춰 안내할게요.'];if(s.data==='stale')return ['최신 정보를 확인하고 있어요','이전 거리로 안내하지 않고 새 정보를 기다릴게요.'];if(s.data==='unsupported')return ['차량 연결이 필요해요','지원되는 차량에서 연료와 경로를 받아 안내해요.'];if(request)return ['내비게이션에 전달했어요','경로가 바뀌었는지 응답을 기다리고 있어요.'];if(result)return ['주유소 경로가 준비됐어요','차량 내비게이션의 경로를 따라가세요.'];return ['필요한 순간에 다시 알려드릴게요','이 추천은 미뤘어요. 새로운 주유 기회가 생기면 확인할게요.'];}
  function waiting(s){const [title,body]=waitCopy(s);return `<div class="compact-content waiting"><div class="waiting-portrait">${portrait()}</div><p class="eyebrow">${request?'내비 응답 확인':result?'경로 변경 완료':'뽀동이가 살피고 있어요'}</p><h1>${title}</h1><p>${body}</p>${!request&&!result&&!dismissed?'<div class="signal-list"><div><span>차량 연료</span><b>확인 대기</b></div><div><span>현재 경로</span><b>확인 대기</b></div></div>':''}<div class="waiting-foot">${s.motion==='moving'||s.motion==='unknown'?'확인 중에는 주유소 안내 버튼을 표시하지 않아요.':'연결되는 동안 설정한 주유 여유거리는 유지돼요.'}</div></div>`;}
  function full(s,f){return `
    <div class="app-content sunset-home">
      <section class="sunset-hero">
        <div class="hero-copy">
          <h1>${f.passing?'앞 주유소에서<br>주유하고 가요':'가는 길에<br class="mobile-break"> 주유하고 가요'}</h1>
          <p>${f.passing?'이곳을 지나면 다음 주유소까지 연료가 부족할 수 있어요':'목적지까지 가려면 중간 주유가 필요해요'}</p>
          <div class="route-strip">
            <div>${icon('pump')}<div><small>주행 가능</small><b>${f.range}<span>km</span></b></div></div>
            <div>${icon('route')}<div><small>목적지까지</small><b>${f.destination}<span>km</span></b></div></div>
          </div>
        </div>
        <div class="hero-art">${portrait(false)}</div>
      </section>
      <section class="sunset-recommendation glass">
        <div class="recommendation-copy">
          <h2>${icon('route')}이번 경로의 추천</h2>
          <div class="station-heading"><span class="pump">${icon('pump')}</span><div>
            <h3 class="station-name">${esc(f.station)}</h3>
            <p class="station-facts">${f.distance} km 앞 <span>│</span> 경로 +${f.extra}분</p>
          </div></div>
          <p class="arrival-note">${icon('check')}현재 연료로 도착할 수 있어요</p>
        </div>
        <div class="recommendation-actions">${actions()}<p>${icon('settings')}설정한 여유거리 <b>${preferences.reserve} km</b></p></div>
      </section>
    </div>
    <nav class="app-bottom" aria-label="정차 화면 메뉴">
      <button type="button" data-page="home" class="selected">${icon('home')}홈</button>
      <button type="button" data-page="assist">${icon('pump')}주유 안내</button>
      <button type="button" data-page="records">${icon('clock')}기록</button>
      <button type="button" data-page="settings">${icon('settings')}설정</button>
    </nav>`;}
  function compact(s,f){return `<div class="compact-content"><p class="eyebrow">${f.passing?'지나치기 전에 확인해요':'이번 경로의 주유 추천'}</p><div class="compact-heading"><h1>${f.passing?'앞 주유소에서<br>주유하고 가세요':'목적지 전에<br>한 번 주유해요'}</h1>${portrait()}</div><div class="compact-distance"><strong>${f.distance}</strong><span>km 앞</span><small>경로 +${f.extra}분</small></div><section class="compact-station"><h2 class="station-name">${esc(f.station)}</h2><p>${f.passing?'다음 주유소까지 <span>연료 부족 예상</span>':'목적지까지 <span>중간 주유 필요</span>'}</p></section>${actions()}<p class="compact-note">현재 경로에서 확인한 주유 기회예요.</p></div>`;}
  function explainer(s){if(s.surface==='background')return `<span class="section-label">SYSTEM NOTIFICATION</span><h2>앱이 뒤로 가면,<br>필요한 내용만 알림으로.</h2><p>왼쪽은 시스템에 전달할 문구와 행동의 시안입니다. 알림이 뜨는 위치와 외형을 WAYFILL이 직접 정하지 않습니다.</p><figure><img src="../reference/motion-care-system-notification.png" alt="Motion Care PLEOS 에뮬레이터 실제 알림 캡처"><figcaption>Motion Care의 실제 측정 캡처. 오른쪽 앱 영역의 왼쪽 상단에 OS 카드가 표시됐습니다. WAYFILL 실행 화면이 아니며 같은 위치가 보장되지는 않습니다.</figcaption></figure>`;return `<span class="section-label">COMPACT WINDOW · 420dp</span><h2>내비 옆에서도,<br>지금 필요한 판단이 먼저.</h2><p>주유소까지 거리와 추가 시간을 크게 읽고 바로 안내를 요청할 수 있는 작은 앱 창입니다.</p><ul><li>별도 지도를 그리지 않는 독립 앱 창</li><li>주행 중에는 정지 포동이와 두 행동만</li><li>상태 미확인·오래된 정보에서는 행동 차단</li></ul><p>정차 홈과 같은 색·글자·버튼 체계를 사용하고, 정보량과 배치만 줄였습니다.</p>`;}
  function render(){const s=get(),f=facts(s),eligible=s.motion!=='unknown'&&s.data==='ready'&&!request&&!result&&!dismissed&&!refuelEvent&&s.scenario!=='noaction';const display=s.surface==='full'&&s.motion!=='parked'?'split':s.surface;players.forEach(stop=>stop());players=[];if(s.motion!=='parked'){page='home';$('history-dialog').close();}$('vehicle').className=`vehicle ${display} ${s.motion}`;const content=display==='full'&&page==='records'?SunsetPages.records(preferences,selectedRecord):display==='full'&&page==='settings'?SunsetPages.settings(preferences):display==='full'&&refuelEvent?SunsetPages.refuel(refuelEvent):eligible?(display==='full'?full(s,f):compact(s,f)):waiting(s)+(display==='full'?SunsetPages.navigation(page):'');$('companion').innerHTML=header(s)+content;document.querySelectorAll('[data-page]').forEach(n=>{n.classList.toggle('selected',n.dataset.page===page);if(n.dataset.page===page)n.setAttribute('aria-current','page');else n.removeAttribute('aria-current');});mountCharacters(s);$('surface-explainer').innerHTML=explainer({...s,surface:display});const hun=s.surface==='background'&&eligible&&!expired;$('notification').classList.toggle('empty',!hun);$('notification').innerHTML=`<p class="system-preview-label">알림 내용 시안 · 실제 OS 카드 위치 아님</p><div class="system-notification">${hun?`<div class="system-head"><span class="brand-icon">${icon('pump')}</span><div><b>WAYFILL</b><small>주유 안내</small></div></div><h2>${f.distance}km 앞 · ${esc(f.station)}</h2><p>${f.passing?'이곳을 지나면 다음 주유소까지 연료가 부족할 수 있어요.':'목적지 전에 주유가 필요해요.'} 경로 +${f.extra}분.</p>${actions()}`:'<h2>지금은 새 알림이 없어요</h2><p>정보 확인 중이거나, 이미 미룬 추천입니다.</p>'}</div><p class="system-note">이 화면은 알림 내용 검토용입니다. 브랜드 색·카드 크기·버튼 배치가 실제 PLEOS에 그대로 표시된다는 뜻은 아닙니다.</p>`;$('response').hidden=!request;clearTimeout(timer);if(hun){expiresAt??=Date.now()+15000;timer=setTimeout(()=>{expired=true;render();$('status').textContent='15초 무응답으로 시연 알림을 닫았습니다. 안내 요청은 보내지 않았습니다.';},Math.max(0,expiresAt-Date.now()));}}
  function reset(){recommendationStartedAt=performance.now();page='home';selectedRecord=null;refuelEvent=null;request=null;result=null;dismissed=false;expired=false;expiresAt=null;render();}
  $('controls').addEventListener('change',e=>{if(e.target.id==='surface')render();else reset();$('status').textContent='예시 데이터로 화면을 비교하고 있습니다. 실제 차량·PLEOS 연동 전 시안입니다.';});$('reset').addEventListener('click',reset);
  $('vehicle').addEventListener('click',e=>{
    const nav=e.target.closest('[data-page]');if(nav){page=nav.dataset.page;selectedRecord=null;render();return;}
    const record=e.target.closest('[data-record]');if(record){selectedRecord=record.dataset.record;render();return;}
    const history=e.target.closest('[data-history]');if(history){if(history.dataset.history==='delete')$('history-dialog').showModal();else{selectedRecord=null;render();}return;}
    const reserve=e.target.closest('[data-reserve]');if(reserve){if(save({type:'reserve',value:Number(reserve.dataset.reserve)}))render();return;}
    const motion=e.target.closest('[data-character]');if(motion){if(save({type:'character',value:motion.dataset.character==='true'}))render();return;}
    const b=e.target.closest('[data-action]');if(!b)return;if(b.dataset.action==='guide'){request='demo-request';$('status').textContent='내비 응답을 기다립니다. 아래에서 성공·실패 응답을 시연할 수 있습니다.';}else{dismissed=true;refuelEvent=null;$('status').textContent='이 추천을 미뤘습니다. 화면을 바꿔도 같은 추천을 다시 띄우지 않습니다.';}render();});
  $('simulate-refuel').addEventListener('click',()=>{
    if(get().motion!=='parked'){$('status').textContent='정차 상태에서 주유 인사를 시연해 주세요.';return;}
    const variant=$('refuel-variant').value;
    const event={startedAt:performance.now(),id:'preview-'+Date.now()+'-'+Math.random().toString(36).slice(2),liters:variant==='small'?5:40,range:550,full:variant!=='small',recovery:variant==='recovery'};
    if(save({type:'refuel',value:event})){lastRefuel=event;reset();$('surface').value='full';refuelEvent=event;render();$('status').textContent='합성 주유 이벤트입니다. 실제 차량 기록이 아닙니다.';}
  });
  $('duplicate-refuel').addEventListener('click',()=>{if(lastRefuel&&save({type:'refuel',value:lastRefuel})){render();$('status').textContent='같은 주유 이벤트는 기록과 인사를 다시 만들지 않습니다.';}});
  $('cancel-history-delete').addEventListener('click',()=>$('history-dialog').close());
  $('confirm-history-delete').addEventListener('click',()=>{if(save({type:'clear'})){$('history-dialog').close();selectedRecord=null;render();}});
  $('response').addEventListener('click',e=>{const b=e.target.closest('[data-result]');if(!b||!request)return;request=null;result=b.dataset.result==='success'?'success':null;$('status').textContent=result?'시연 성공 응답을 받았습니다.':'시연 실패 응답을 받았습니다. 다시 안내를 요청할 수 있습니다.';render();});render();
})();
