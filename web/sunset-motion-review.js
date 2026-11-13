(() => {
 'use strict';
 const clips=[['calm','차분한 기본 모습','6초에 한 번 살짝 눈을 깜박여요.'],['recommend','주유 추천 인사 · 1.8초','방향을 알려 주듯 한 팔을 크게 들고 차분하게 돌아와요.'],['small','소량 주유 · 1.6초','작게 통통 튀며 가볍고 짧게 반응해요.'],['full','충분한 주유 · 2.4초','몸을 낮췄다가 양팔을 열어 크게 기뻐해요.'],['recovery','저연료 회복 · 2.4초','지친 모습에서 기지개를 켜며 안도해요.']];
 const stops=new Map();
 const still=document.getElementById('still');
 document.getElementById('motion-grid').innerHTML=clips.map(([name,title,description])=>`<article class="motion-card glass"><canvas width="384" height="384" data-name="${name}" role="img" aria-label="${title} 뽀동이"></canvas><h2>${title}</h2><p>${description}</p><button type="button" data-play="${name}">재생하기</button><small id="status-${name}" role="status">차분한 정지 모습</small></article>`).join('');
 function draw(name,play){
  stops.get(name)?.();
  const reduced=!play||still.checked||matchMedia('(prefers-reduced-motion: reduce)').matches;
  const canvas=document.querySelector(`canvas[data-name="${name}"]`);
  document.getElementById('status-'+name).textContent=reduced?'차분한 정지 모습':name==='calm'?'6초 주기로 한 번 깜박임':'정차 인사 재생 중';
  stops.set(name,SunsetPlayer.mount(canvas,name,{still:reduced,durationMs:name==='small'?1600:name==='recommend'?1800:2400,onDone:()=>{if(!reduced)document.getElementById('status-'+name).textContent='인사가 끝났어요 · 마지막 표정 유지';}}));
 }
 function reset(){clips.forEach(([name])=>draw(name,false));}
 document.getElementById('motion-grid').addEventListener('click',e=>{const b=e.target.closest('[data-play]');if(b)draw(b.dataset.play,true);});
 still.addEventListener('change',reset);document.getElementById('stop-all').addEventListener('click',reset);reset();
})();
