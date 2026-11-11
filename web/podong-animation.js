(function(root){
 'use strict';
 const data=typeof module!=='undefined'?require('./animation-data.js'):root.PodongData;
 function sample(name,elapsed,calmLoop=false,reduced=false){
  if(reduced)return {clip:'calm',index:0,done:false};
  const c=data.clips[name]||data.clips.calm;
  let time=Math.max(0,elapsed);if(calmLoop)time%=6000;
  let end=0;for(let i=0;i<c.frames.length;i++){end+=c.durationsMs[i];if(time<end)return {clip:name,index:i,done:false};}
  return {clip:name,index:c.frames.length-1,done:!calmLoop};
 }
 let atlas;
 function mount(canvas,name='calm',options={}){
  if(!atlas){atlas=new Image();atlas.src=data.atlas;}
  const ctx=canvas.getContext('2d'),start=options.startedAt??performance.now();let stopped=false,handle;
  const reduced=options.reduced??matchMedia('(prefers-reduced-motion: reduce)').matches;
  function tick(now){
   if(stopped||!canvas.isConnected)return;
   const s=sample(name,now-start,name==='calm',reduced),clip=data.clips[s.clip],r=clip.frames[s.index],v=clip.viewport??{x:0,y:0,w:r.w,h:r.h};
   if(atlas.complete&&atlas.naturalWidth){ctx.clearRect(0,0,256,256);ctx.drawImage(atlas,r.x+v.x,r.y+v.y,v.w,v.h,0,0,256,256);canvas.dataset.frame=String(s.index);canvas.dataset.clip=s.clip;}
   if(s.done&&now-start>=(options.durationMs??data.clips[name].durationMs)){options.onDone?.();return;}
   if(reduced&&atlas.complete&&atlas.naturalWidth){options.onDone?.();return;}
   handle=requestAnimationFrame(tick);
  }
  handle=requestAnimationFrame(tick);return ()=>{stopped=true;cancelAnimationFrame(handle);};
 }
 const api={sample,mount};if(typeof module!=='undefined')module.exports=api;root.PodongAnimation=api;
})(typeof globalThis!=='undefined'?globalThis:this);
