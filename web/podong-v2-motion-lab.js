const poses=[
  {id:'calm',kicker:'CALM DRIVING',title:'주행 중에는 조용히 곁에',description:'시선을 빼앗지 않는 호흡만 남기고, 팔은 몸 가까이에 둡니다.',expression:'차분한 눈, 작은 미소',motion:'아주 느린 호흡',usage:'차량 이동 중 상시',cue:'차분한 호흡 · 시선 방해 최소화',image:'assets/podong-v2-calm-key.png',alt:'차분하게 주행을 돕는 뽀동이 V2 키포즈'},
  {id:'recommend',kicker:'RECOMMENDATION',title:'추천은 자신 있게, 강요 없이',description:'선택지를 둥근 손으로 부드럽게 내보이고 사용자의 결정을 기다립니다.',expression:'열린 눈, 자신 있는 미소',motion:'한 번 내보이고 멈춤',usage:'주유소 추천 메시지',cue:'한 번만 제시 · 선택을 기다림',image:'assets/podong-v2-recommend-key.png',alt:'주유소 선택지를 안내하는 뽀동이 V2 키포즈'},
  {id:'small',kicker:'SMALL REFUEL',title:'조금 채워도 기분 좋게',description:'작은 주유 이벤트에는 짧고 귀여운 반응만 보여 과장하지 않습니다.',expression:'감은 눈, 작게 만족한 미소',motion:'작은 한 번의 통통 튀기',usage:'소량 주유 확인',cue:'작고 짧게 · 보상은 분명하게',image:'assets/podong-v2-small-key.png',alt:'소량 주유에 작게 기뻐하는 뽀동이 V2 키포즈'},
  {id:'full',kicker:'FULL REFUEL',title:'가득 채우면 시원하게 쭉',description:'충분히 주유했을 때만 몸 전체를 써서 가장 큰 만족감을 표현합니다.',expression:'감은 눈, 환한 미소',motion:'늘어나며 한 번 크게 점프',usage:'충분한 주유 완료',cue:'가장 큰 보상 · 한 번만 재생',image:'assets/podong-v2-full-key.png',alt:'가득 주유해 두 팔을 뻗는 뽀동이 V2 키포즈'},
  {id:'recovery',kicker:'RECOVERY',title:'아슬아슬했다면 안도의 숨',description:'연료 부족 상태를 벗어나면 긴장이 풀리는 표정과 천천히 펴지는 몸으로 회복을 보여줍니다.',expression:'안도한 감은 눈, 작은 눈물',motion:'천천히 펴지고 안정',usage:'저연료 상태에서 회복',cue:'긴장 해제 · 천천히 안정',image:'assets/podong-v2-recovery-key.png',alt:'저연료 상태에서 회복해 안도하는 뽀동이 V2 키포즈'}
];
const list=document.querySelector('#pose-list');
const stage=document.querySelector('#pose-stage');
const image=document.querySelector('#pose-image');
const toggle=document.querySelector('#motion-toggle');
function selectPose(index){
  const pose=poses[index];
  document.querySelector('#pose-index').textContent=String(index+1).padStart(2,'0')+' / '+String(poses.length).padStart(2,'0');
  document.querySelector('#pose-kicker').textContent=pose.kicker;
  document.querySelector('#stage-title').textContent=pose.title;
  document.querySelector('#pose-description').textContent=pose.description;
  document.querySelector('#pose-expression').textContent=pose.expression;
  document.querySelector('#pose-motion').textContent=pose.motion;
  document.querySelector('#pose-usage').textContent=pose.usage;
  document.querySelector('#pose-cue').textContent=pose.cue;
  image.src=pose.image;image.alt=pose.alt;stage.dataset.pose=pose.id;
  document.querySelectorAll('.pose-card').forEach((card,i)=>card.classList.toggle('active',i===index));
}
poses.forEach((pose,index)=>{
  const button=document.createElement('button');
  const figure=document.createElement('figure');
  const thumb=document.createElement('img');
  const label=document.createElement('span');
  const title=document.createElement('strong');
  const note=document.createElement('small');
  button.type='button';button.className='pose-card'+(index===0?' active':'');
  thumb.src=pose.image;thumb.alt='';thumb.loading='lazy';
  label.textContent=String(index+1).padStart(2,'0')+' · '+pose.kicker;
  title.textContent=pose.title;note.textContent=pose.motion;
  figure.append(thumb);button.append(figure,label,title,note);
  button.addEventListener('click',()=>selectPose(index));list.append(button);
});
toggle.addEventListener('click',()=>{
  const paused=stage.classList.toggle('paused');
  toggle.setAttribute('aria-pressed',String(!paused));
  toggle.textContent=paused?'모션 다시 재생하기':'모션 잠시 멈추기';
});
