const {test}=require('node:test');
const assert=require('node:assert/strict');
const data=require('../web/sunset-motion-data.js');
const {sample}=require('../web/sunset-player.js');

test('new drawn clips have bounded grid cells and one calm blink per six seconds',()=>{
 for(const clip of Object.values(data.clips)){
  assert.equal(clip.frames.length,clip.durationsMs.length);
  assert.ok(clip.frames.every(n=>n>=0&&n<16));assert.ok(clip.durationsMs.every(n=>n>0));
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
 assert.equal(sample('small',800).cell,8);
 for(const name of ['recommend','small','full','recovery']){
  const held=sample(name,10000);assert.equal(held.cell,15);assert.equal(held.done,true);assert.equal(held.nextMs,null);
 }
});
