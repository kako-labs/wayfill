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
  function transition(name,elapsedMs,still=false){
    const current=sample(name,elapsedMs,still);
    if(still||current.done)return {...current,nextFrame:current.frame,nextCell:current.cell,blend:0};
    const clip=data.clips[current.clip];
    const time=current.clip==='calm'?Math.max(0,elapsedMs)%clip.durationsMs.reduce((a,b)=>a+b,0):Math.max(0,elapsedMs);
    const start=clip.durationsMs.slice(0,current.frame).reduce((a,b)=>a+b,0);
    const frameDuration=clip.durationsMs[current.frame];
    const blendWindow=Math.max(1,Math.min(45,Math.round(frameDuration*.45)));
    const raw=Math.max(0,Math.min(1,(time-start-(frameDuration-blendWindow))/blendWindow));
    const eased=raw*raw*(3-2*raw);
    const nextFrame=Math.min(current.frame+1,clip.frames.length-1);
    return {...current,nextFrame,nextCell:clip.frames[nextFrame],blend:nextFrame===current.frame?0:eased};
  }
  const images=new Map();
  function mount(canvas,name='calm',options={}){
    const reduced=options.still??root.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const shown=reduced?'calm':name;
    const clip=data.clips[shown],sheet=data.sheets[clip.sheet],file=sheet.file;
    let atlas=images.get(file);
    if(!atlas){atlas=new Image();atlas.src=file;images.set(file,atlas);}
    const context=canvas.getContext('2d');
    const started=options.startedAt??performance.now();
    const duration=options.durationMs??clip.durationsMs.reduce((a,b)=>a+b,0);
    let stopped=false,completed=false,timer,animationFrame;
    function drawCell(cell,alpha=1){
      const col=cell%sheet.columns,row=Math.floor(cell/sheet.columns);
      const x=Math.floor(col*atlas.naturalWidth/sheet.columns),y=Math.floor(row*atlas.naturalHeight/sheet.rows);
      const w=Math.floor((col+1)*atlas.naturalWidth/sheet.columns)-x,h=Math.floor((row+1)*atlas.naturalHeight/sheet.rows)-y;
      context.globalAlpha=alpha;
      context.drawImage(atlas,x,y,w,h,0,0,canvas.width,canvas.height);
    }
    function tick(){
      if(stopped||!canvas.isConnected)return;
      const elapsed=performance.now()-started,s=transition(name,elapsed,reduced);
      if(!atlas.complete||!atlas.naturalWidth)return;
      context.clearRect(0,0,canvas.width,canvas.height);
      drawCell(s.cell);
      if(s.blend>0)drawCell(s.nextCell,s.blend);
      context.globalAlpha=1;
      canvas.dataset.frame=String(s.frame);canvas.dataset.clip=s.clip;canvas.dataset.blend=s.blend.toFixed(3);
      if(reduced||s.done&&elapsed>=duration){if(!completed){completed=true;options.onDone?.();}return;}
      if(s.done){timer=setTimeout(tick,Math.max(16,duration-elapsed));return;}
      if(s.clip==='calm'&&s.frame===0&&s.blend===0)timer=setTimeout(tick,Math.max(16,(s.nextMs??45)-45));
      else animationFrame=requestAnimationFrame(tick);
    }
    atlas.addEventListener('load',tick,{once:true});
    if(atlas.complete&&atlas.naturalWidth)tick();
    return ()=>{stopped=true;clearTimeout(timer);cancelAnimationFrame(animationFrame);atlas.removeEventListener('load',tick);};
  }
  const api={sample,transition,mount};if(typeof module!=='undefined')module.exports=api;root.SunsetPlayer=api;
})(typeof globalThis!=='undefined'?globalThis:this);
