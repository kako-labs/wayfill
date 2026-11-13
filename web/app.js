(function(){
 'use strict';
 const demoParams=new URLSearchParams(window.location.search);
 document.body.classList.toggle('embedded',demoParams.get('embed')==='1');
 const {createState,reduce,poseAt,durations}=FuelState;
 const surface=document.getElementById('surface'),stage=document.getElementById('stage');
 const scenarioList=[['recommendation','① 주유 추천','경로를 정하면 필요한 이유와 함께 주유소 하나를 추천해요.'],['widget','② 주행 중 위젯','주행 중에는 작은 뽀동이가 차분하게 안내해요.'],['passing','③ 지나치기 전','이곳을 지나면 다음 주유소까지 연료가 부족할 때 알려요.'],['added','④ 경유지 추가','내비게이션이 경로 추가를 확인한 뒤에만 완료를 표시해요.'],['normal','⑤ 주유 완료','정차 중 주유 완료를 확인하면 뽀동이가 한 번 인사해요.'],['small','소량 주유','조금 주유하면 짧게 손인사한 뒤 곁에 머물러요.'],['full','가득 주유','가득 채웠을 때는 조금 더 든든하게 반응해요.'],['recovery','바닥에서 가득','연료가 거의 없었다가 가득 채운 상황의 회복 반응이에요.'],['longdrive','장거리 출발','출발 전 정차 상태에서만 여행 준비 인사를 보여요.'],['analysis','⑥ 경로 분석','왜 주유가 필요하고 A를 추천하는지 확인해요.'],['settings','⑦ 설정','움직임을 꺼도 뽀동이는 메시지 곁에 계속 있어요.']];
 const requestedScenario=demoParams.get('scenario');
 let state=createState(),selected=scenarioList.some(([id])=>id===requestedScenario)?requestedScenario:'recommendation',eventCounter=0,lastEvent=null,animationToken=0,animationStart=null,animationId=null;
 try{const saved=JSON.parse(sessionStorage.getItem('fuelPilotDemo')||'{}');state.characterEnabled=saved.characterEnabled!==false;state.reserveKm=Number.isFinite(saved.reserveKm)?Math.max(20,Math.min(100,saved.reserveKm)):50;state.seenIds=Array.isArray(saved.seenIds)?saved.seenIds.filter(x=>typeof x==='string'):[];}catch{}
 const esc=x=>String(x).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function persist(){try{sessionStorage.setItem('fuelPilotDemo',JSON.stringify({characterEnabled:state.characterEnabled,reserveKm:state.reserveKm,seenIds:state.seenIds}));}catch{}}
 function dispatch(action){state=reduce(state,action);persist();render();}
 function status(text){document.getElementById('event-status').textContent=text;}
 function character(){return FuelState.characterPolicy(state).mode==='playful';}
 function sprite(clip='calm',cls='podong-avatar'){return `<canvas width="256" height="256" class="${cls}" data-animation="${clip}" role="img" aria-label="안내하는 뽀동이"></canvas>`;}
 function avatar(){return sprite();}
 function identity(){return '<div class="message-identity">'+avatar()+'<span>뽀동이가 알려드려요</span></div>';}
 const actions=(id='A')=>`<div class="actions"><button class="primary" data-action="select" data-id="${id}">여기서 주유하기</button><button class="secondary" data-action="continue">계속 가기</button></div>`;
 const metricStrip=c=>{const shortage=Number.isFinite(c.rangeKm)&&Number.isFinite(c.destinationKm)?Math.max(0,c.destinationKm-c.rangeKm):null;return `<div class="route-metrics"><div><small>주행 가능</small><strong>${Number.isFinite(c.rangeKm)?c.rangeKm+'km':'확인 중'}</strong></div><div><small>목적지</small><strong>${Number.isFinite(c.destinationKm)?c.destinationKm+'km':'확인 중'}</strong></div><div class="${shortage>0?'risk':''}"><small>${shortage>0?'부족 거리':'남는 거리'}</small><strong>${shortage!==null?shortage+'km':'확인 중'}</strong></div></div>`;};
 const intro=(text)=>`<div class="intro ${character()?'':'intro-calm'}"><div class="bubble">${text}</div>${sprite(character()?'recommend':'calm','podong-large')}</div>`;
 function recommendation(){const c=FuelState.routeContext(state);return `<div class="popup-group">${intro('목적지까지 가려면<br>중간에 주유해야 해요')}<section class="panel" aria-label="주유 추천"><div class="panel-kicker"><span><i class="status-dot"></i>경로 분석 완료</span><b>정차 중</b></div>${metricStrip(c)}<div class="station-card"><span class="station-icon" aria-hidden="true"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M4 10h10M14 8h2a2 2 0 0 1 2 2v6a1.5 1.5 0 0 0 3 0V9l-3-3"/></svg></span><div class="station-copy"><small>추천 주유소 · 경로 기준</small><h2>주유소 A</h2><p class="station-detail"><strong>20km 앞</strong> · 경로에서 <strong>1분 추가</strong></p></div></div>${state.error?`<p class="error" role="alert">${esc(state.error)}</p>`:''}${actions()}<button class="text-action" data-action="analysis">추천 근거 자세히 보기 ›</button></section></div>`;}
 function widget(){
  const c=FuelState.routeContext(state),done=!!state.refuel,added=state.routeStation,enough=state.routeStage==='longdrive';
  const title=done?'연료를 채웠어요':added?'주유소 '+esc(added)+'로 안내 중':enough?'이번 경로는 주유 없이 갈 수 있어요':'다음 주유소 '+c.stationId+' · '+c.stationKm+'km 앞';
  const detail=done?(state.refuel.rangeKm!==null?'주행 가능 거리 약 '+state.refuel.rangeKm+'km':'현재 주행 가능 거리를 확인해 주세요'):added?'내비게이션 경로를 따라가세요.':enough?'목적지까지 235km · 주행 가능 약 610km':'아직 중간 주유가 필요해요';
  return `<button class="widget" data-action="analysis">${avatar()}<small>WAYFILL · ${done?'주유 완료':added?'경유지 안내 중':enough?'여유가 있어요':'주유가 필요해요'}</small><strong>${title}</strong><span>${detail}</span></button>`;
 }
 function passing(){return `<div class="popup-group"><section class="panel">${identity()}<div class="eyebrow coral">지나치기 전에 주유하세요</div><h2 class="reason-title">이곳을 지나면 다음 주유소까지<br>연료가 부족할 수 있어요</h2><div class="rule"><h2>주유소 B</h2><p class="station-detail"><strong>3km 앞 우측</strong> · 경로에서 3분 추가</p></div>${actions('B')}<button class="text-action" data-action="analysis">다음 주유소까지 거리 보기 ›</button></section></div>`;}
 function routeResult(){return `<div class="popup-group"><section class="panel">${identity()}<div class="confirmation"><span class="check">${state.screen==='adding'?'…':'✓'}</span>${state.screen==='adding'?'내비게이션에 전달했어요':'내비게이션 경로가 바뀌었어요'}</div><h2>${state.screen==='adding'?'경유지를 추가하고 있어요':'주유소 '+esc(state.routeStation||'A')+'로 안내할게요'}</h2><p class="station-detail">${state.screen==='adding'?'경로 재탐색 결과를 기다리고 있어요.':'주유소까지 내비게이션을 따라가세요.'}</p><button class="primary" style="width:100%;margin-top:24px" data-action="continue" ${state.screen==='adding'?'disabled':''}>${state.screen==='adding'?'확인 중':'지도 보기'}</button></section></div>`;}
 const messages={normal:'든든하게 채웠어요',small:'냠, 간식!',full:'빵빵해요!',recovery:'살았다…!'};
 function completion(){
  const e=state.refuel||{},v=FuelState.variant(e),active=state.celebration&&character();
  const subtitle=e.liters!==null&&e.liters!==undefined?`오늘은 ${esc(e.liters)}L 먹었어요!`:'주유가 완료됐어요!';
  const greeting=`<div class="intro completion-intro ${character()?'':'intro-calm'}"><div class="bubble">${messages[v]}<small>${subtitle}</small></div>${sprite(active?(v==='normal'?'small':v):'calm','podong-large')}</div>`;
  return `<div class="popup-group completion-group">${greeting}<section class="panel completion"><div class="confirmation"><span class="check">✓</span>주유 완료</div><h2>연료를 채웠어요</h2>${e.rangeKm!==null&&e.rangeKm!==undefined?`<div class="range">${esc(e.rangeKm)}<small>km</small></div><p class="info">지금 연료로 갈 수 있는 예상 거리예요.</p>`:'<p class="info">차량에서 주행 가능 거리를 확인 중이에요.</p>'}<button class="primary" style="width:100%;margin-top:24px" data-action="drive">이제 출발하기</button><p class="info">${active?'정차 중에는 조금 더 신나게 인사해요.':'뽀동이가 출발 준비를 함께할게요.'}</p></section></div>`;
 }
 function longDrive(){return `<div class="popup-group long-drive">${intro('여행 준비 완료!')}<section class="panel"><div class="eyebrow">출발 전 확인</div><h2>이번 경로는 주유 없이<br>갈 수 있어요</h2><p class="station-detail">주행 가능 610km · 목적지까지 235km</p><p class="info">도착 후 약 375km를 더 갈 수 있어요.</p><button class="primary" style="width:100%;margin-top:24px" data-action="drive">출발하기</button></section></div>`;}
 function analysis(){const c=FuelState.routeContext(state),passingContext=state.routeStage==='passing',done=state.refuel!==null;return `<section class="full-screen"><header class="screen-head"><div class="screen-heading">${avatar()}<h2>${done?'주유 후 연료 확인':state.routeStage==='longdrive'?'경로 연료 확인':'주유가 필요한 이유'}</h2></div><button class="back" data-action="continue">지도로 돌아가기 ›</button></header>${done?`<div class="analysis-copy"><span class="eyebrow">주유 완료</span><h3>현재 연료로 갈 수 있는 거리를<br>다시 확인해 주세요.</h3><p class="summary">${state.refuel.rangeKm!==null?'차량에서 받은 주행 가능 거리: 약 '+state.refuel.rangeKm+'km':'차량의 최신 주행 가능 거리 수신을 기다려요.'}</p><p class="note">주유 전 경로 분석은 재사용하지 않아요. 실제 앱은 최신 차량 값과 남은 경로로 다시 판단해야 해요.</p></div>`:`${state.routeStage==='longdrive'?'<div class="analysis-copy"><span class="eyebrow">연료 여유가 있어요</span><h3>이번 경로는 주유 없이 갈 수 있을 것으로 예상돼요.</h3><p class="summary">주행 가능 약 610km · 목적지까지 235km</p><p class="note">도착 후 약 375km를 더 갈 수 있어요. 실제 주행 상황에 따라 달라져요.</p></div>':`<div class="analysis-grid"><section class="analysis-copy"><span class="eyebrow">중간 주유가 필요해요</span><h3>${passingContext?'B를 지나면 다음 주유소 C까지<br>연료가 부족할 수 있어요':'지금 연료로는<br>목적지까지 갈 수 없어요'}</h3><div class="numbers"><div><small>지금 연료로 갈 수 있는 거리</small><b>${c.rangeKm}<small>km</small></b></div><div><small>${passingContext?'다음 주유소 C까지':'목적지까지 거리'}</small><b>${passingContext?35:c.destinationKm}<small>km</small></b></div></div><p class="summary">${passingContext?'C까지 약 <b>17km 부족해요.</b><br>B는 3km 앞 우측에 있어요.':'목적지까지 <b>20km 부족해요.</b><br>도착 전에 한 번 주유해야 해요.'}</p><p class="note">차량의 주행 가능 거리와 경로 거리로 비교한 예상이에요.<br>실제 주행 상황과 주유소 진입 경로에 따라 달라져요.</p></section><section class="analysis-stations"><h3>${passingContext?'지나치기 전에 확인해 주세요':'어디서 주유하면 좋을까요?'}</h3>${passingContext?'':`<div class="station-row"><small>추천 · 더 가깝고 추가 시간이 짧아요</small><h4>주유소 A</h4><p>${c.stationKm}km 앞 · 경로에서 1분 추가</p><p class="station-value">A에 도착하면 약 55km를 더 갈 수 있어요.</p></div>`}<div class="station-row"><small>${passingContext?'이번에 들를 수 있는 주유소':'갈 수 있지만 A보다 멀어요'}</small><h4>주유소 B</h4><p>${passingContext?'3':state.routeStage==='early'?'50':'60'}km 앞 · 경로에서 3분 추가</p><p class="station-value">B에 도착하면 약 15km를 더 갈 수 있어요.</p></div><div class="station-row unreachable"><small>현재 연료로 도달하기 어려워요</small><h4>주유소 C</h4><p>${passingContext?'35':state.routeStage==='early'?'82':'92'}km 앞 · 경로에서 5분 추가</p><p class="station-value">C까지 약 17km 부족해요.</p></div><button class="primary" data-action="select" data-id="${passingContext?'B':'A'}">주유소 ${passingContext?'B':'A'}에서 주유하기</button></section></div>`}`}</section>`;}
 function settings(){return `<section class="full-screen settings-screen"><header class="screen-head"><div class="screen-heading">${avatar()}<div><small>WAYFILL</small><h2>주유 안내 설정</h2></div></div><button class="back" data-action="continue">지도로 돌아가기 ›</button></header><div class="settings-layout"><section class="settings-card featured"><div class="setting-heading"><div><span class="setting-number">01</span><h3>주유 판단 여유거리</h3><p>목적지 도착 뒤 남겨둘 연료 여유를 설정해요.</p></div><strong class="reserve-value">${state.reserveKm}<small>km</small></strong></div><div class="reserve-control"><button data-action="reserve-down" ${state.reserveKm<=20?'disabled':''}>−</button><div><span style="width:${(state.reserveKm-20)/80*100}%"></span></div><button data-action="reserve-up" ${state.reserveKm>=100?'disabled':''}>＋</button></div><div class="range-labels"><span>20km</span><span>안전 여유 · 10km 단위</span><span>100km</span></div></section><section class="settings-card"><label class="setting"><div><span class="setting-number">02</span><h3>정차 중 귀여운 움직임</h3><p>정차·주유 후에는 짧게 반응해요.<br>꺼도 뽀동이는 안내 메시지 곁에 계속 보여요.</p></div><input type="checkbox" id="character-toggle" ${state.characterEnabled?'checked':''}></label></section><section class="settings-card compact"><div class="setting"><div><span class="setting-number">03</span><h3>주행 중에는 차분하게</h3><p>출발 즉시 큰 동작을 멈추고 작은 모습으로 바뀌어요.</p></div><span class="status">항상 적용</span></div><div class="setting"><div><span class="setting-number">04</span><h3>소리 없는 안내</h3><p>모든 캐릭터 모션은 무음으로 재생돼요.</p></div><span class="status">무음</span></div></section><p class="settings-note">설정은 정차 중에만 바꿀 수 있으며 이 시연에서는 현재 브라우저 탭에 저장됩니다.</p></div></section>`;}
 let stopPlayers=[];
 function animate(){
  stopPlayers.forEach(stop=>stop());stopPlayers=[];
  const c=state.celebration;
  if(c&&animationId!==c.id){animationId=c.id;animationStart=performance.now();}
  for(const canvas of surface.querySelectorAll('canvas[data-animation]')){
   const clip=canvas.dataset.animation;
   stopPlayers.push(PodongAnimation.mount(canvas,clip,{
    reduced:!state.characterEnabled||matchMedia('(prefers-reduced-motion: reduce)').matches,
    ...(c&&clip!=='calm'?{startedAt:animationStart,durationMs:durations[c.variant],onDone:()=>dispatch({type:'DONE',id:c.id})}:{})
   }));
  }
 }
 function render(){animationToken++;stage.dataset.screen=state.screen;
  document.getElementById('motion-label').textContent=state.motion==='parked'?'정차 중':state.motion==='moving'?'주행 중':'주행 상태 확인 중';
  const hideMap=['analysis','settings'].includes(state.screen);document.getElementById('navigation').hidden=hideMap;document.getElementById('eta').hidden=hideMap;
  document.getElementById('destination-distance').innerHTML=FuelState.routeContext(state).destinationKm+'<small>km</small>';
  const views={recommendation,widget,passing,adding:routeResult,added:routeResult,refuel:completion,longdrive:longDrive,analysis,settings};surface.innerHTML=(views[state.screen]||widget)();
  for(const b of document.querySelectorAll('#scenarios button'))b.setAttribute('aria-pressed',String(b.dataset.scenario===selected));animate();
 }
 function choose(key){selected=key;document.getElementById('scenario-description').textContent=scenarioList.find(x=>x[0]===key)[2];
  state={...createState(),seenIds:state.seenIds,characterEnabled:state.characterEnabled};animationId=null;
  if(['normal','small','full','recovery'].includes(key)){
   lastEvent={id:'demo-'+Date.now()+'-'+(++eventCounter),liters:key==='small'?8:key==='normal'?22:42,isFull:['full','recovery'].includes(key),wasLow:key==='recovery',rangeKm:key==='small'?180:key==='normal'?330:610};dispatch({type:'REFUEL',event:lastEvent});status('새 주유 완료 이벤트 · '+durations[key]+'ms 인사 후에도 뽀동이는 곁에 남아요.');
  }else{if(['widget','passing'].includes(key))state.motion='moving';state.screen=key;state.routeStage=key==='widget'?'early':key==='passing'?'passing':key==='longdrive'?'longdrive':'start';if(key==='added'){state.routeStation='A';}render();status('시연 예시 · 차량 데이터 대신 준비된 상황을 보여줘요.');}
 }
 document.getElementById('scenarios').innerHTML=scenarioList.map(([id,label])=>`<button data-scenario="${id}" aria-pressed="${id===selected}">${label}</button>`).join('');
 document.getElementById('scenarios').addEventListener('click',e=>{const b=e.target.closest('[data-scenario]');if(b)choose(b.dataset.scenario);});
 surface.addEventListener('click',e=>{const b=e.target.closest('[data-action]');if(!b||b.disabled)return;
  if(b.dataset.action==='select'){const id=b.dataset.id;dispatch({type:'SELECT_STATION',id});status('데모 내비게이션 응답을 기다려요. 실제 앱에서는 이 지점에서 호스트 콜백을 호출합니다.');setTimeout(()=>{dispatch({type:'ROUTE_ADDED',id});status('데모 내비게이션이 경로 추가를 확인했어요.');},700);}
  if(b.dataset.action==='continue')dispatch({type:'CONTINUE'});
  if(b.dataset.action==='analysis')dispatch({type:'SCREEN',screen:'analysis'});
  if(b.dataset.action==='drive'){dispatch({type:'MOTION',motion:'moving'});status('출발 · 뽀동이가 작고 차분한 모습으로 함께해요.');}
  if(b.dataset.action==='reserve-down')dispatch({type:'RESERVE',value:state.reserveKm-10});
  if(b.dataset.action==='reserve-up')dispatch({type:'RESERVE',value:state.reserveKm+10});
 });
 surface.addEventListener('change',e=>{if(e.target.id==='character-toggle')dispatch({type:'CHARACTER',enabled:e.target.checked});});
 document.getElementById('drive').addEventListener('click',()=>{dispatch({type:'MOTION',motion:'moving'});status('출발 · 뽀동이가 작고 차분한 모습으로 함께해요.');});
 document.getElementById('park').addEventListener('click',()=>{dispatch({type:'MOTION',motion:'parked'});status('다시 정차해도 지난 주유 인사는 반복하지 않아요.');});
 document.getElementById('replay').addEventListener('click',()=>{if(!lastEvent){status('먼저 주유 완료 상황을 선택하세요.');return;}dispatch({type:'REFUEL',event:lastEvent});status('같은 이벤트 ID를 다시 보냈어요. 모션이 재생되지 않으면 정상이에요.');});
 for(const p of ['peek','wave','cheek','wiggle','tuck']){const i=new Image();i.src='assets/podong-'+p+'.png';}
 if(selected==='recommendation')render();else choose(selected);
 window.FuelPilotDemo={getState:()=>JSON.parse(JSON.stringify(state)),dispatch,choose};
})();
