(function(){
  'use strict';
  const scenarios=[
    {group:'핵심 흐름',id:'recommendation',title:'주유 추천',motion:'정차',description:'목적지까지 연료가 부족할 때 이유와 추천 주유소를 함께 보여줍니다.',short:'이유 → 추천 → 선택'},
    {group:'핵심 흐름',id:'widget',title:'주행 중 위젯',motion:'주행',description:'주행 중에는 지도 시야를 가리지 않는 작은 뽀동이와 핵심 정보만 남깁니다.',short:'작고 차분한 안내'},
    {group:'핵심 흐름',id:'passing',title:'지나치기 전',motion:'주행',description:'이번 주유소를 지나치면 다음 지점에 도달하기 어려운 순간을 알려줍니다.',short:'마지막 안전 기회'},
    {group:'핵심 흐름',id:'added',title:'경유지 추가',motion:'정차',description:'내비게이션의 성공 응답을 받은 뒤에만 경유지 추가 완료를 표시합니다.',short:'내비 응답 확인'},
    {group:'핵심 흐름',id:'analysis',title:'경로 분석',motion:'정차',description:'주행 가능 거리와 목적지·후보 주유소의 차이를 한 화면에서 비교합니다.',short:'추천 근거 상세'},
    {group:'핵심 흐름',id:'longdrive',title:'장거리 출발',motion:'정차',description:'연료가 충분한 장거리 출발에서는 안심 메시지와 여행 준비 인사를 보여줍니다.',short:'연료 충분 안내'},
    {group:'핵심 흐름',id:'settings',title:'설정',motion:'정차',description:'모션을 줄여도 캐릭터와 메시지는 유지되는 접근성 설정 화면입니다.',short:'모션·표시 정책'},
    {group:'주유 완료 모션',id:'normal',title:'주유 완료',motion:'정차',description:'일반 주유가 확인되면 한 번만 인사하고 마지막 모습으로 곁에 남습니다.',short:'기본 2초 인사'},
    {group:'주유 완료 모션',id:'small',title:'소량 주유',motion:'정차',description:'10L 이하 소량 주유에는 짧고 가벼운 1.6초 반응을 사용합니다.',short:'짧은 손인사'},
    {group:'주유 완료 모션',id:'full',title:'가득 주유',motion:'정차',description:'가득 채운 상황에는 양팔을 크게 펼치는 2.4초 반응을 사용합니다.',short:'큰 만족 반응'},
    {group:'주유 완료 모션',id:'recovery',title:'바닥에서 가득',motion:'정차',description:'연료가 거의 없던 상태에서 가득 채우면 회복감을 강조한 반응을 보여줍니다.',short:'회복 스트레칭'}
  ];
  const viewports={wide:{width:1440,height:760},tablet:{width:1024,height:768},compact:{width:390,height:844}};
  const list=document.getElementById('scenario-list');
  const frame=document.getElementById('preview-frame');
  const stage=document.getElementById('preview-stage');
  const sizer=document.getElementById('preview-sizer');
  const title=document.getElementById('scenario-title');
  const index=document.getElementById('scenario-index');
  const motion=document.getElementById('scenario-motion');
  const description=document.getElementById('scenario-description');
  const openCurrent=document.getElementById('open-current');
  let selected='recommendation';
  let viewport='tablet';

  function renderList(){
    let group='';
    list.innerHTML=scenarios.map((scenario,i)=>{
      const heading=scenario.group!==group?`<p class="scenario-group-title">${scenario.group}</p>`:'';
      group=scenario.group;
      return `${heading}<button class="scenario-button${scenario.id===selected?' active':''}" data-scenario="${scenario.id}"><span class="number">${String(i+1).padStart(2,'0')}</span><span><strong>${scenario.title}</strong><small>${scenario.short}</small></span><span class="rail-motion">${scenario.motion}</span></button>`;
    }).join('');
  }

  function selectScenario(id){
    const scenario=scenarios.find(item=>item.id===id)||scenarios[0];
    selected=scenario.id;
    const scenarioPosition=scenarios.indexOf(scenario)+1;
    index.textContent=String(scenarioPosition).padStart(2,'0');
    title.textContent=scenario.title;
    motion.textContent=scenario.motion;
    description.textContent=scenario.description;
    frame.title=`${scenario.title} 화면 미리보기`;
    frame.src=`index.html?scenario=${encodeURIComponent(scenario.id)}&embed=1`;
    openCurrent.href=`index.html?scenario=${encodeURIComponent(scenario.id)}`;
    renderList();
  }

  function fitPreview(){
    const size=viewports[viewport];
    const availableWidth=Math.max(280,stage.clientWidth-32);
    const availableHeight=Math.max(300,stage.clientHeight-32);
    const scale=Math.min(availableWidth/size.width,availableHeight/size.height,1);
    frame.style.width=`${size.width}px`;
    frame.style.height=`${size.height}px`;
    frame.style.transform=`scale(${scale})`;
    sizer.style.width=`${Math.round(size.width*scale)}px`;
    sizer.style.height=`${Math.round(size.height*scale)}px`;
  }

  list.addEventListener('click',event=>{
    const button=event.target.closest('[data-scenario]');
    if(button)selectScenario(button.dataset.scenario);
  });
  document.querySelector('.viewport-switcher').addEventListener('click',event=>{
    const button=event.target.closest('[data-viewport]');
    if(!button)return;
    viewport=button.dataset.viewport;
    for(const candidate of document.querySelectorAll('[data-viewport]'))candidate.classList.toggle('active',candidate===button);
    fitPreview();
  });
  if('ResizeObserver' in window)new ResizeObserver(fitPreview).observe(stage);
  else window.addEventListener('resize',fitPreview);
  selectScenario(selected);
  fitPreview();
})();
