(function(root){
  'use strict';
  const data=typeof module!=='undefined'?require('./sunset-motion-data.js'):root.SunsetMotionData;
  function sample(name,elapsedMs,still=false){
    if(still)return {clip:'calm',frame:0,cell:0,done:false,nextMs:null};
    const clip=data.clips[name]||data.clips.calm;
    const duration=clip.durationsMs.reduce((a,b)=>a+b,0);
    const time=name==='calm'?Math.max(0,elapsedMs)%duration:Math.max(0,elapsedMs);
    let end=0;
    for(let frame=0;frame<clip.frames.length;frame++){
      end+=clip.durationsMs[frame];
      if(time<end)return {clip:name,frame,cell:clip.frames[frame],done:false,nextMs:Math.max(16,end-time)};
    }
    return {clip:name,frame:clip.frames.length-1,cell:clip.frames.at(-1),done:name!=='calm',nextMs:null};
  }
  const images=new Map();
  function mount(canvas,name='calm',options={}){
    const reduced=options.still??root.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const shown=reduced?'calm':name;
    const clip=data.clips[shown],file=data.sheets[clip.sheet];
    let atlas=images.get(file);
    if(!atlas){atlas=new Image();atlas.src=file;images.set(file,atlas);}
    const context=canvas.getContext('2d');
    const started=options.startedAt??performance.now();
    const duration=options.durationMs??clip.durationsMs.reduce((a,b)=>a+b,0);
    let stopped=false,completed=false,timer;
    function tick(){
      if(stopped||!canvas.isConnected)return;
      const elapsed=performance.now()-started,s=sample(name,elapsed,reduced);
      if(!atlas.complete||!atlas.naturalWidth)return;
      const col=s.cell%data.columns,row=Math.floor(s.cell/data.columns);
      const x=Math.floor(col*atlas.naturalWidth/data.columns),y=Math.floor(row*atlas.naturalHeight/data.rows);
      const w=Math.floor((col+1)*atlas.naturalWidth/data.columns)-x,h=Math.floor((row+1)*atlas.naturalHeight/data.rows)-y;
      context.clearRect(0,0,canvas.width,canvas.height);
      context.drawImage(atlas,x,y,w,h,0,0,canvas.width,canvas.height);
      canvas.dataset.frame=String(s.frame);canvas.dataset.clip=s.clip;
      if(reduced||s.done&&elapsed>=duration){if(!completed){completed=true;options.onDone?.();}return;}
      timer=setTimeout(tick,s.nextMs??Math.max(16,duration-elapsed));
    }
    atlas.addEventListener('load',tick,{once:true});
    if(atlas.complete&&atlas.naturalWidth)tick();
    return ()=>{stopped=true;clearTimeout(timer);atlas.removeEventListener('load',tick);};
  }
  const api={sample,mount};if(typeof module!=='undefined')module.exports=api;root.SunsetPlayer=api;
})(typeof globalThis!=='undefined'?globalThis:this);
