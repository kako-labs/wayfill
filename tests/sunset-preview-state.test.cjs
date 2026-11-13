const {test}=require('node:test'),assert=require('node:assert/strict');
const {restore,reduce}=require('../web/sunset-preview-state.js');
const now=100*86400000;
test('synthetic preview never fabricates records and validates settings',()=>{
 assert.deepEqual(restore({},now),{reserve:50,character:true,records:[],processed:[]});
 assert.equal(restore({reserve:55},now).reserve,50);
 assert.equal(reduce(restore({},now),{type:'reserve',value:80},now).reserve,80);
 assert.equal(restore({records:[{id:'production-event',observedAt:now}]},now).records.length,0);
});
test('record deletion keeps deduplication; unknown quantities remain unknown',()=>{
 const event={type:'refuel',value:{id:'preview-one',liters:NaN,range:-1}};
 const one=reduce({},event,now);assert.equal(one.records.length,1);assert.equal(one.records[0].liters,null);assert.equal(one.records[0].range,null);
 const cleared=reduce(one,{type:'clear'},now);
 assert.equal(reduce(cleared,event,now).records.length,0);
 assert.equal(one.records.length,1);
});
test('preview history is bounded, ages out, and ignores corrupt records',()=>{
 const records=Array.from({length:130},(_,i)=>({id:'preview-'+i,observedAt:now-i,liters:5,range:300,full:false}));
 const state=restore({records:[...records,{id:'preview-old',observedAt:0},null]},now);
 assert.equal(state.records.length,100);assert.equal(state.records[0].id,'preview-0');
 assert.equal(restore(state,now+91*86400000).records.length,0);
});
