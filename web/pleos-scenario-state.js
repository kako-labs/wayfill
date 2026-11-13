(function(root){
  'use strict';

  const steps=[
    {id:'parked',label:'정차 홈',surface:'app',motion:'parked',title:'P단에서 앱을 열어요',detail:'주유 필요 이유와 추천 주유소를 확인합니다.'},
    {id:'monitoring',label:'모니터링 시작',surface:'app',motion:'parked',title:'사용자가 직접 모니터링을 켜요',detail:'정차·권한 확인 후에만 백그라운드 관찰을 시작합니다.'},
    {id:'driving',label:'주행·백그라운드',surface:'navigation',motion:'moving',title:'주행 중에는 내비만 보여요',detail:'추천이 없을 때 WAYFILL은 화면을 덮지 않습니다.'},
    {id:'hun',label:'주유 추천 HUN',surface:'hun',motion:'moving',title:'필요한 순간에 두 행동만 제안해요',detail:'15초 안에 바로 안내 또는 나중에를 선택합니다.'},
    {id:'routing',label:'내비 요청 중',surface:'navigation',motion:'moving',title:'실제 내비 응답을 기다려요',detail:'SDK 성공 전에는 경로가 바뀌었다고 표시하지 않습니다.'},
    {id:'split',label:'내비 옆 작은 창',surface:'split',motion:'moving',title:'내비 옆에서 핵심 정보만 유지해요',detail:'주행 중 뽀동이는 정지하며 추가 조작을 최소화합니다.'},
    {id:'arrived',label:'주유소 도착',surface:'split',motion:'parked',title:'도착 후 정차 상태를 확인해요',detail:'P단이 확인된 뒤에만 큰 캐릭터 반응을 준비합니다.'},
    {id:'refueling',label:'주유 확인',surface:'app',motion:'parked',title:'검증된 주유 이벤트에 한 번 반응해요',detail:'같은 이벤트 ID는 기록과 인사를 반복하지 않습니다.'},
    {id:'unknown',label:'연결 미확인',surface:'split',motion:'unknown',title:'정보가 불확실하면 행동을 막아요',detail:'오래되거나 지원되지 않는 입력으로 추천하지 않습니다.'},
  ];

  const indexById=new Map(steps.map((step,index)=>[step.id,index]));
  function create(step='parked'){
    if(!indexById.has(step))throw new Error('Unknown scenario step: '+step);
    return {step,outcome:null,history:[step]};
  }
  function current(state){return steps[indexById.get(state.step)]||steps[0];}
  function select(state,step,outcome=null){
    if(!indexById.has(step))return state;
    return {step,outcome,history:[...state.history,step].slice(-24)};
  }
  function next(state){
    const index=indexById.get(state.step)??0;
    return select(state,steps[Math.min(steps.length-1,index+1)].id);
  }
  function previous(state){
    const index=indexById.get(state.step)??0;
    return select(state,steps[Math.max(0,index-1)].id);
  }
  function act(state,action){
    switch(action){
      case 'start': return state.step==='parked'?select(state,'monitoring','monitoring-started'):state;
      case 'depart': return state.step==='monitoring'?select(state,'driving','monitoring-background'):state;
      case 'recommend': return state.step==='driving'?select(state,'hun','hun-shown'):state;
      case 'guide': return state.step==='hun'?select(state,'routing','guide-requested'):state;
      case 'later': return state.step==='hun'?select(state,'driving','deferred-15m'):state;
      case 'expire': return state.step==='hun'?select(state,'driving','expired-no-action'):state;
      case 'navigation-success': return state.step==='routing'?select(state,'split','route-confirmed'):state;
      case 'arrive': return ['routing','split'].includes(state.step)?select(state,'arrived','station-arrived'):state;
      case 'refuel':
      case 'refuel-full': return state.step==='arrived'?select(state,'refueling','refuel-confirmed-full'):state;
      case 'refuel-small': return state.step==='arrived'?select(state,'refueling','refuel-confirmed-small'):state;
      case 'refuel-recovery': return state.step==='arrived'?select(state,'refueling','refuel-confirmed-recovery'):state;
      case 'unknown': return select(state,'unknown','inputs-blocked');
      case 'reset': return create();
      default:return state;
    }
  }

  const api={steps,create,current,select,next,previous,act};
  if(typeof module!=='undefined')module.exports=api;
  root.PleosScenarioState=api;
})(typeof globalThis!=='undefined'?globalThis:this);
