(function(root){
 'use strict';
 const esc=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const date=at=>new Intl.DateTimeFormat('ko-KR',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false}).format(new Date(at));
 const number=n=>Number.isInteger(n)?String(n):n.toFixed(1);
 const character=(clip='calm',still=false)=>`<canvas class="sunset-character" width="384" height="384" data-motion="${clip}" ${still?'data-still="true"':''} role="img" aria-label="안내하는 뽀동이"></canvas>`;
 const navigation=page=>`<nav class="app-bottom" aria-label="정차 화면 메뉴">${[['home','홈'],['assist','주유 안내'],['records','기록'],['settings','설정']].map(([id,label])=>`<button type="button" data-page="${id}" ${page===id?'class="selected" aria-current="page"':''}>${label}</button>`).join('')}</nav>`;
 function records(data,selected){
  const detail=data.records.find(r=>r.id===selected);
  const quantity=r=>r.liters===null?'주유량 미수신':`${number(r.liters)} L`;
  const range=r=>r.range===null?'주행 가능 거리 미수신':`확인 후 주행 가능 ${number(r.range)} km`;
  const card=r=>`<div><small>${date(r.observedAt)}</small><h2>${r.full?'가득 주유를 확인했어요':'주유를 확인했어요'}</h2><p>${range(r)}</p></div><strong>${quantity(r)}</strong>`;
  return `<section class="sunset-page history-page"><header><div><p class="eyebrow">이 기기의 시연 기록</p><h1>${detail?'주유 기록 상세':'주유 기록'}</h1></div>${detail?'<button type="button" data-history="back">목록으로</button>':data.records.length?'<button type="button" data-history="delete">기록 삭제</button>':''}</header>
   ${!data.records.length?`<div class="empty-history">${character('calm',true)}<h2>아직 확인한 주유가 없어요</h2><p>위의 ‘주유 시연’으로 기록 흐름을 확인할 수 있어요.</p></div>`:detail?`<article class="history-card glass">${card(detail)}</article><dl class="record-detail glass"><div><dt>앱에서 확인한 시각</dt><dd>${date(detail.observedAt)}</dd></div><div><dt>확인된 주유량</dt><dd>${quantity(detail)}</dd></div><div><dt>주유소·결제 금액·연비</dt><dd>이 기록으로 확인할 수 없어요</dd></div></dl>`:`<p class="page-note">최근 90일 · ${data.records.length}건</p><div class="history-list">${data.records.map(r=>`<button type="button" class="history-card glass" data-record="${esc(r.id)}">${card(r)}</button>`).join('')}</div>`}
   <p class="page-note">최대 100건을 이 브라우저에 저장해요. 실제 차량 기록이 아닌 합성 시연입니다.</p></section>${navigation('records')}`;
 }
 function settings(data){return `<section class="sunset-page settings-page"><header><div><p class="eyebrow">정차 중에 여유 있게</p><h1>주유 안내 설정</h1></div>${character('calm',!data.character)}</header><div class="settings-grid">
  <section class="glass setting-card"><h2>주유 판단 여유거리</h2><p>목적지 도착 뒤 남겨둘 연료 여유를 설정해요.</p><strong class="reserve-value">${data.reserve}<small> km</small></strong><div class="reserve-actions"><button type="button" data-reserve="${data.reserve-10}" ${data.reserve<=20?'disabled':''}>−10 km</button><button type="button" data-reserve="${data.reserve+10}" ${data.reserve>=100?'disabled':''}>+10 km</button></div><p>20~100 km · 10 km 단위</p></section>
  <section class="glass setting-card"><h2>정차 중 귀여운 움직임</h2><p>정차·주유 후에 짧게 반응해요.<br>꺼도 뽀동이는 계속 곁에 있어요.</p><button type="button" class="motion-toggle" data-character="${!data.character}" aria-pressed="${data.character}">${data.character?'움직임 켜짐':'움직임 꺼짐'}</button><hr><h2>주행 중에는 차분하게</h2><p>출발하면 정지 모습으로 바뀌고,<br>현재 주유소와 필요한 행동만 보여줘요.</p></section></div><p class="page-note">이 화면의 설정은 웹 시연용입니다. Android 앱의 실제 설정과는 별도로 저장합니다.</p></section>${navigation('settings')}`;}
 function refuel(event){return `<section class="sunset-page refuel-page"><div>${character(event.full?(event.recovery?'recovery':'full'):'small')}<h1>${event.full?'든든하게 채웠어요':'조금 더 가볍게 출발해요'}</h1><p>주유를 확인했어요. 새 연료 상태로 다시 살펴볼게요.</p></div><section class="glass refuel-facts"><span>확인된 주유량<strong>${number(event.liters)} L</strong></span><span>확인 후 주행 가능<strong>${number(event.range)} km</strong></span></section><div class="actions"><button type="button" data-action="later">확인했어요</button></div></section>${navigation('home')}`;}
 root.SunsetPages={records,settings,refuel,character,navigation};
})(typeof globalThis!=='undefined'?globalThis:this);
