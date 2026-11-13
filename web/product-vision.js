(function(){
 'use strict';
 const add=document.getElementById('add-route');
 const status=document.getElementById('system-status');
 const stationState=document.getElementById('station-state');
 add.addEventListener('click',()=>{
  if(add.dataset.done)return;
  add.dataset.done='1';add.textContent='✓ 경유지 추가 완료';add.disabled=true;
  stationState.textContent='안내 중';status.innerHTML='<i></i>내비게이션 전달 완료';
 });
 document.getElementById('continue-route').addEventListener('click',()=>{status.innerHTML='<i></i>추천을 닫았어요 · 정차';});
 document.querySelectorAll('[data-toggle]').forEach(button=>button.addEventListener('click',()=>{
  const active=button.classList.toggle('active');button.setAttribute('aria-pressed',String(active));
 }));
 let reserve=50;
 const reserveValue=document.getElementById('reserve-value');
 const updateReserve=delta=>{reserve=Math.max(20,Math.min(100,reserve+delta));reserveValue.textContent=reserve+'km';};
 document.getElementById('reserve-down').addEventListener('click',()=>updateReserve(-10));
 document.getElementById('reserve-up').addEventListener('click',()=>updateReserve(10));
 document.querySelectorAll('[data-nav]').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('[data-nav]').forEach(item=>{item.classList.toggle('active',item===button);item.removeAttribute('aria-current');});
  button.setAttribute('aria-current','page');
 }));
 document.images[0].src=String.fromCharCode(97,115,115,101,116,115,47,112,111,100,111,110,103,45,118,50,45,109,97,115,116,101,114,45,99,111,114,114,101,99,116,101,100,46,112,110,103);
})();
