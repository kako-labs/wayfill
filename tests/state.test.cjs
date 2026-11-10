const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createState,reduce,variant,poseAt,routeContext,characterPolicy}=require('../web/state.js');
const event=(id='fuel-1',more={})=>({type:'REFUEL',event:{id,liters:42,isFull:true,wasLow:false,rangeKm:610,...more}});
test('parked confirmed refuel plays once; duplicate and delayed completion cannot replay or erase a newer event',()=>{
 let s=reduce(createState(),event());assert.equal(s.celebration.id,'fuel-1');
 s=reduce(s,{type:'DONE',id:'fuel-1'});assert.equal(s.celebration,null);
 s=reduce(s,event());assert.equal(s.celebration,null);
 s=reduce(s,event('fuel-2'));s=reduce(s,{type:'DONE',id:'fuel-1'});assert.equal(s.celebration.id,'fuel-2');
});
test('moving interrupts and returning to parked does not restart',()=>{
 let s=reduce(reduce(createState(),event()),{type:'MOTION',motion:'moving'});assert.equal(s.celebration,null);assert.equal(s.screen,'widget');
 s=reduce(s,{type:'MOTION',motion:'parked'});assert.equal(s.celebration,null);
});
test('unknown motion suppresses large character, confirmed event is consumed',()=>{
 let s=reduce(createState(),{type:'MOTION',motion:'unknown'});s=reduce(s,event());assert.equal(s.celebration,null);
 s=reduce(s,{type:'MOTION',motion:'parked'});s=reduce(s,event());assert.equal(s.celebration,null);
});
test('turning character off cancels current motion and keeps refuel confirmation',()=>{
 let s=reduce(createState(),event());s=reduce(s,{type:'CHARACTER',enabled:false});assert.equal(s.celebration,null);assert.equal(s.refuel.id,'fuel-1');
});
test('verified variants; missing liters never invent a volume',()=>{
 assert.equal(variant({liters:8}),'small');assert.equal(variant({isFull:true,wasLow:true}),'recovery');assert.equal(variant({isFull:true}),'full');assert.equal(variant({}),'normal');
 const s=reduce(createState(),event('missing',{liters:null,rangeKm:null}));assert.equal(s.refuel.liters,null);assert.equal(s.refuel.rangeKm,null);
});
test('waypoint selection waits for confirmed result, failure leaves actionable retry',()=>{
 let s=reduce(createState(),{type:'SELECT_STATION',id:'A'});assert.equal(s.screen,'adding');assert.equal(s.routeStation,null);
 s=reduce(s,{type:'ROUTE_FAILED',id:'A'});assert.equal(s.screen,'recommendation');assert.equal(s.routeStation,null);
 s=reduce(s,{type:'SELECT_STATION',id:'A'});s=reduce(s,{type:'ROUTE_ADDED',id:'B'});assert.equal(s.routeStation,null);
 s=reduce(s,{type:'ROUTE_ADDED',id:'A'});assert.equal(s.routeStation,'A');assert.equal(s.screen,'added');
});
test('invalid event IDs are ignored; duplicate suppression survives serialization',()=>{
 const s=reduce(createState(),event(''));assert.equal(s.refuel,null);
 const restored=JSON.parse(JSON.stringify(reduce(createState(),event())));restored.celebration=null;
 assert.equal(reduce(restored,event()).celebration,null);
});
test('motion ends rather than looping, small variant is shorter',()=>{
 assert.equal(poseAt(0,'normal'),'peek');assert.equal(poseAt(800,'normal'),'cheek');assert.equal(poseAt(2100,'normal'),null);assert.equal(poseAt(1600,'small'),null);
});
test('departing the long-drive greeting switches to the quiet widget',()=>{
 const s=reduce({...createState(),screen:'longdrive'},{type:'MOTION',motion:'moving'});assert.equal(s.screen,'widget');
});
test('repeated station selection cannot replace the pending navigation request',()=>{
 const pending=reduce(createState(),{type:'SELECT_STATION',id:'A'});
 assert.equal(reduce(pending,{type:'SELECT_STATION',id:'B'}).pendingStation,'A');
});
test('passing alert continue keeps B next, even when parked again',()=>{
 let s={...createState(),screen:'passing',motion:'moving',routeStage:'passing'};
 s=reduce(s,{type:'CONTINUE'});assert.equal(routeContext(s).stationId,'B');assert.equal(routeContext(s).stationKm,3);
 s=reduce(s,{type:'MOTION',motion:'parked'});assert.equal(routeContext(s).rangeKm,18);assert.equal(routeContext(s).destinationKm,38);
});
test('character stays with the message after celebration finishes, motion only becomes calm',()=>{
 let s=reduce(createState(),event());s=reduce(s,{type:'DONE',id:'fuel-1'});assert.equal(characterPolicy(s).visible,true);
 s=reduce(s,{type:'MOTION',motion:'moving'});assert.equal(characterPolicy(s).visible,true);assert.equal(characterPolicy(s).mode,'calm');
});
test('unknown motion and animation disabled still retain a calm character',()=>{
 for(const s of [{...createState(),motion:'unknown'},{...createState(),characterEnabled:false}]){assert.equal(characterPolicy(s).visible,true);assert.equal(characterPolicy(s).mode,'calm');}
});
