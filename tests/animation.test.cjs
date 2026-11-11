const {test}=require('node:test'),assert=require('node:assert/strict');
const {sample}=require('../web/podong-animation.js');
const data=require('../web/animation-data.js');
test('active clips use sixteen sequential frames and measured timing',()=>{
 for(const [name,c] of Object.entries(data.clips)){
  assert.equal(c.frames.length,name==='calm'?8:16);assert.equal(sample(name,0).index,0);
  assert.equal(sample(name,c.durationsMs[0]-1).index,0);
  assert.equal(sample(name,c.durationsMs[0]).index,1);
  assert.equal(sample(name,c.durationMs-1).index,c.frames.length-1);
  assert.equal(sample(name,c.durationMs).done,true);
 }
});
test('calm blinks once in six seconds; reduced motion keeps presence',()=>{
 assert.equal(sample('calm',1500,true).index,7);
 assert.equal(sample('calm',6000,true).index,0);
 assert.equal(sample('full',500,false,true).index,0);
 assert.equal(sample('full',500,false,true).clip,'calm');
});
test('elapsed event time seeks forward rather than replaying on remount',()=>{
 const clip=data.clips.full;
 assert.equal(sample('full',clip.durationsMs[0]*5).index,5);
 assert.equal(sample('full',clip.durationMs).done,true);
});
test('player respects settling deadline and reduced motion finishes while drawing a still',()=>{
 const {mount}=require('../web/podong-animation.js');
 global.Image=class {complete=true;naturalWidth=2048;};
 let tick,draws=0,done=0;global.requestAnimationFrame=fn=>(tick=fn,1);global.cancelAnimationFrame=()=>{};
 const canvas={isConnected:true,dataset:{},getContext:()=>({clearRect(){},drawImage(){draws++;}})};
 mount(canvas,'full',{reduced:false,startedAt:0,durationMs:2400,onDone:()=>done++});
 tick(data.clips.full.durationMs);assert.equal(done,0);assert.equal(canvas.dataset.frame,String(data.clips.full.frames.length-1));
 tick(2400);assert.equal(done,1);
 done=0;mount(canvas,'full',{reduced:true,startedAt:0,onDone:()=>done++});tick(0);
 assert.equal(canvas.dataset.clip,'calm');assert.equal(done,1);assert.ok(draws>0);
});
