(() => {
  'use strict';
  const $=id=>document.getElementById(id);
  const model=PleosScenarioState;
  const initialParams=new URLSearchParams(location.search);
  const requestedStep=initialParams.get('step');
  const initialStep=model.steps.some(step=>step.id===requestedStep)?requestedStep:'parked';
  const requestedViewport=initialParams.get('viewport');
  const initialViewport=['standard','wide','compact'].includes(requestedViewport)?requestedViewport:'standard';
  let state=model.create(initialStep);
  let players=[];
  let hunDeadline=0;
  let hunTicker=null;

  const statusCopy={
    'monitoring-started':'모니터링을 켰습니다. 이제 출발 상황을 눌러보세요.',
    'monitoring-background':'WAYFILL은 백그라운드에서 최신 입력만 확인합니다.',
    'hun-shown':'시스템 HUN이 15초 동안 두 행동을 제공합니다.',
    'guide-requested':'현재 추천 ID를 다시 확인하고 내비 SDK에 요청했습니다.',
    'deferred-15m':'같은 추천은 15분 동안 다시 알리지 않습니다.',
    'expired-no-action':'15초 동안 선택이 없어 아무 요청도 보내지 않았습니다.',
    'route-confirmed':'내비 SDK 성공 응답을 받은 뒤에만 경로 반영을 표시합니다.',
    'station-arrived':'정차가 확인됐습니다. 주유 이벤트를 기다립니다.',
    'refuel-confirmed-small':'검증된 소량 주유 이벤트에 짧게 한 번 반응합니다.',
    'refuel-confirmed-full':'검증된 충분한 주유 이벤트에 크게 한 번 반응합니다.',
    'refuel-confirmed-recovery':'저연료 상태에서 회복된 이벤트에 안도하며 한 번 반응합니다.',
    'inputs-blocked':'불확실한 입력에서는 추천과 내비 행동을 모두 차단합니다.',
  };

  function stopPlayers(){players.forEach(stop=>stop());players=[];}
  function mountCharacters(step){
    stopPlayers();
    document.querySelectorAll('canvas[data-character]').forEach(canvas=>{
      const clip=canvas.dataset.character;
      const still=step.motion!=='parked'||canvas.dataset.still==='true';
      players.push(SunsetPlayer.mount(canvas,clip,{still}));
    });
  }
  function action(label,action,primary=false){return `<button type="button" data-action="${action}" class="${primary?'primary':''}">${label}</button>`;}
  function metrics(){return `<div class="metric-row"><div class="metric"><small>주행 가능</small><b>75 km</b></div><div class="metric"><small>목적지까지</small><b>95 km</b></div></div>`;}
  function fullSurface(step){
    if(step.id==='parked')return `<div class="app-shell"><div class="app-header"><div class="brand-lockup">WAYFILL</div><span class="state-chip">P · 정차 중</span></div><div class="app-hero"><div class="app-copy"><h2>가는 길에<br>주유하고 가요</h2><p>목적지까지 가려면 중간 주유가 필요해요.</p>${metrics()}</div><div class="character-wrap"><canvas width="420" height="420" data-character="recommend"></canvas></div></div><div class="recommend-card"><div><h3>주유소 A · 20 km 앞</h3><p>경로 +1분 · 현재 연료로 도착 가능</p></div><div class="screen-actions">${action('바로 안내','guide-preview',true)}${action('나중에','later-preview')}</div></div></div>`;
    if(step.id==='monitoring')return `<div class="app-shell"><div class="app-header"><div class="brand-lockup">WAYFILL</div><span class="state-chip">P · 정차 중</span></div><div class="app-hero"><div class="monitor-card"><span class="kicker">BACKGROUND ASSIST</span><h2>출발 준비가 됐어요</h2><p>앱이 보이지 않을 때도 최신 연료·경로·위치를 확인합니다. 추천이 생기기 전에는 내비 화면 위에 아무것도 띄우지 않습니다.</p><div class="screen-actions">${action('출발하기','depart',true)}</div></div><div class="character-wrap"><canvas width="420" height="420" data-character="calm"></canvas></div></div></div>`;
    const variant=state.outcome?.replace('refuel-confirmed-','')||'full';
    const refuelCopy={small:['조금 먹고\n힘냈어요!','소량 주유','small'],full:['든든하게\n채웠어요!','충분한 주유','full'],recovery:['다시 갈 힘이\n생겼어요!','저연료 회복','recovery']}[variant];
    return `<div class="app-shell"><div class="app-header"><div class="brand-lockup">WAYFILL</div><span class="state-chip">P · 주유 확인</span></div><div class="app-hero"><div class="app-copy"><span class="kicker">REFUEL CONFIRMED · ${refuelCopy[1]}</span><h2>${refuelCopy[0].replace('\n','<br>')}</h2><p>같은 주유 이벤트에는 이 인사를 다시 재생하지 않아요.</p></div><div class="character-wrap"><canvas width="420" height="420" data-character="${refuelCopy[2]}"></canvas></div></div><div class="recommend-card"><div><h3>확인 시각 · 오후 2:58</h3><p>새 연료 상태를 확인해 다음 주행 판단을 준비합니다.</p></div></div></div>`;
  }
  function compactSurface(step){
    if(step.id==='unknown')return `<div class="compact-shell compact-unknown"><div class="compact-head"><b>WAYFILL</b><span>차량 확인 중</span></div><div class="compact-main"><span class="kicker">INPUTS BLOCKED</span><h2>차량 연결을<br>확인하고 있어요</h2><p>상태가 확실해질 때까지 주유소 안내 버튼을 표시하지 않아요.</p><div class="compact-character"><canvas width="320" height="320" data-character="calm" data-still="true"></canvas><div class="station-summary"><small>추천 상태</small><b>사용할 수 없음</b></div></div></div><p class="compact-note">오래된 값이나 시연 값으로 대신하지 않습니다.</p></div>`;
    if(step.id==='arrived')return `<div class="compact-shell"><div class="compact-head"><b>WAYFILL</b><span>P · 도착</span></div><div class="compact-main"><span class="kicker">STATION ARRIVAL</span><h2>주유소에<br>도착했어요</h2><p>정차 상태가 확인됐습니다. 검증된 주유 결과에 맞는 반응을 골라 확인해 보세요.</p><div class="compact-character"><canvas width="320" height="320" data-character="calm"></canvas><div class="station-summary"><small>현재 위치</small><b>주유소 A</b></div></div><div class="screen-actions">${action('소량','refuel-small')}${action('충분히','refuel-full',true)}${action('저연료 회복','refuel-recovery')}</div></div></div>`;
    return `<div class="compact-shell"><div class="compact-head"><b>WAYFILL</b><span>주행 중</span></div><div class="compact-main"><span class="kicker">NEXT FUEL STOP</span><h2>20km 앞에서<br>주유해요</h2><p>경로 +1분 · 현재 연료로 도착할 수 있어요.</p><div class="compact-character"><canvas width="320" height="320" data-character="calm" data-still="true"></canvas><div class="station-summary"><small>추천 주유소</small><b>주유소 A</b><small>내비 경로 반영 완료</small></div></div><div class="screen-actions">${action('주유소 도착','arrive',true)}</div></div></div>`;
  }
  function hunMarkup(){return `<span class="hun-badge">시스템 HUN 예상 영역 · 위치/외형 미확정</span><div class="hun-head"><span class="hun-icon">W</span><div><b>WAYFILL</b><small>주유 추천 · 지금</small></div></div><h2>20km 앞 주유소 A</h2><p>목적지까지 가려면 중간 주유가 필요해요 · 경로 +1분</p><div class="hun-actions">${action('바로 안내','guide',true)}${action('나중에','later')}</div><div class="hun-progress"><i id="hun-progress"></i></div>`;}
  function messageMarkup(step){
    if(step.id==='routing')return `내비게이션 응답 확인 중 · 아직 성공으로 표시하지 않아요 ${action('성공 응답','navigation-success',true)} ${action('연결 끊김','unknown')}`;
    if(state.outcome==='deferred-15m')return '나중에 선택 · 같은 추천 15분 재알림 없음';
    if(state.outcome==='expired-no-action')return '15초 만료 · 내비 요청을 보내지 않음';
    return '';
  }
  function renderScenarioList(){
    $('scenario-list').innerHTML=model.steps.map((step,index)=>`<button type="button" class="scenario-button ${step.id===state.step?'active':''}" data-step="${step.id}" aria-current="${step.id===state.step?'step':'false'}"><span>${String(index+1).padStart(2,'0')}</span><b>${step.label}</b></button>`).join('');
  }
  function updateHunTimer(){
    clearInterval(hunTicker);hunTicker=null;
    if(state.step!=='hun')return;
    hunDeadline=Date.now()+15000;
    hunTicker=setInterval(()=>{
      const remain=Math.max(0,hunDeadline-Date.now());
      const bar=$('hun-progress');if(bar)bar.style.width=`${remain/150}%`;
      if(remain<=0){clearInterval(hunTicker);hunTicker=null;setState(model.act(state,'expire'));}
    },100);
  }
  function setState(next){
    const enteringHun=next.step==='hun'&&state.step!=='hun';
    state=next;render();if(enteringHun)updateHunTimer();
  }
  function render(){
    const step=model.current(state),index=model.steps.findIndex(item=>item.id===step.id);
    renderScenarioList();
    $('pleos-screen').classList.toggle('split-mode',step.surface==='split');
    $('host-navigation').hidden=step.surface==='app';
    $('fuel-surface').hidden=!['app','split'].includes(step.surface);
    $('hun').hidden=step.surface!=='hun';
    $('system-message').hidden=step.id!=='routing'&&!['deferred-15m','expired-no-action'].includes(state.outcome);
    if(step.surface==='app')$('fuel-surface').innerHTML=fullSurface(step);
    else if(step.surface==='split')$('fuel-surface').innerHTML=compactSurface(step);
    else $('fuel-surface').innerHTML='';
    $('hun').innerHTML=step.surface==='hun'?hunMarkup():'';
    $('system-message').innerHTML=messageMarkup(step);
    $('motion-dot').className=step.motion;
    $('motion-label').textContent=step.motion==='parked'?'정차 중':step.motion==='moving'?'주행 중':'상태 미확인';
    $('surface-label').textContent=step.surface==='app'?'WAYFILL 전체 화면':step.surface==='split'?'내비 + WAYFILL 분할창':step.surface==='hun'?'PLEOS 시스템 HUN 예상':'내비 전체 화면 · 앱 백그라운드';
    $('step-number').textContent=String(index+1).padStart(2,'0');$('step-title').textContent=step.title;$('step-detail').textContent=state.outcome&&statusCopy[state.outcome]?statusCopy[state.outcome]:step.detail;
    $('previous').disabled=index===0;$('next').disabled=index===model.steps.length-1;
    $('host-distance').textContent=step.id==='arrived'?'0 km':step.id==='split'?'20 km':'95 km';
    $('turn-distance').textContent=step.id==='split'?'20 km':step.id==='arrived'?'도착':'1.2 km';
    try{
      const url=new URL(location.href);url.searchParams.set('step',state.step);url.searchParams.set('viewport',$('viewport').value);
      history.replaceState(null,'',url);
    }catch(_){/* Local file previews can disable history mutation without affecting the simulation. */}
    mountCharacters(step);
  }

  $('scenario-list').addEventListener('click',event=>{const button=event.target.closest('[data-step]');if(button){clearInterval(hunTicker);setState(model.select(state,button.dataset.step));if(button.dataset.step==='hun')updateHunTimer();}});
  $('pleos-screen').addEventListener('click',event=>{const button=event.target.closest('[data-action]');if(!button)return;const action=button.dataset.action;if(action==='guide-preview')setState(model.select(state,'routing','guide-requested'));else if(action==='later-preview')setState(model.select(state,'driving','deferred-15m'));else setState(model.act(state,action));});
  $('previous').addEventListener('click',()=>setState(model.previous(state)));
  $('next').addEventListener('click',()=>{const next=model.next(state);setState(next);if(next.step==='hun')updateHunTimer();});
  $('viewport').addEventListener('change',event=>{$('vehicle-frame').className=`vehicle-frame ${event.target.value}`;render();});
  $('viewport').value=initialViewport;$('vehicle-frame').className=`vehicle-frame ${initialViewport}`;
  render();
})();
