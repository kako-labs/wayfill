const {test}=require('node:test');
const assert=require('node:assert/strict');
const data=require('../web/sunset-motion-data.js');
const {sample,transition}=require('../web/sunset-player.js');

test('new drawn clips have bounded grid cells and one calm blink per six seconds',()=>{
 for(const clip of Object.values(data.clips)){
  assert.equal(clip.frames.length,clip.durationsMs.length);
  const sheet=data.sheets[clip.sheet];
  assert.ok(clip.frames.every(n=>n>=0&&n<sheet.columns*sheet.rows));assert.ok(clip.durationsMs.every(n=>n>0));
 }
 assert.equal(sample('calm',5199).frame,0);
 assert.equal(sample('calm',5300).cell,5);
 assert.equal(sample('calm',6000).cell,0);
 assert.equal(sample('calm',0).nextMs,5200);
});
test('moving and reduced motion select a still calm frame even during a refuel greeting',()=>{
 const still=sample('full',900,true);
 assert.deepEqual(still,{clip:'calm',frame:0,cell:0,done:false,nextMs:null});
});
test('elapsed event time seeks ahead and active clips never loop',()=>{
 assert.equal(sample('recommend',-10).cell,0);
 assert.equal(sample('small',800).cell,16);
 for(const name of ['recommend','small','full','recovery']){
  const held=sample(name,10000);assert.equal(held.cell,31);assert.equal(held.done,true);assert.equal(held.nextMs,null);
 }
});
test('adjacent cells use a short bounded smoothstep transition without changing clip time',()=>{
 const early=transition('recommend',10);assert.equal(early.frame,0);assert.equal(early.blend,0);
 const nearBoundary=transition('recommend',50);assert.equal(nearBoundary.frame,0);assert.equal(nearBoundary.nextFrame,1);
 assert.ok(nearBoundary.blend>0&&nearBoundary.blend<1);
 const boundary=transition('recommend',60);assert.equal(boundary.frame,1);assert.equal(boundary.blend,0);
 const held=transition('full',10000);assert.equal(held.nextFrame,31);assert.equal(held.blend,0);
});
