(function(root){
 'use strict';
 const validReserve=n=>Number.isInteger(n)&&n>=20&&n<=100&&n%10===0;
 const optional=(n,positive=false)=>typeof n==='number'&&Number.isFinite(n)&&n>=(positive?Number.MIN_VALUE:0)?n:null;
 function restore(value={},now=Date.now()){
  if(!value||typeof value!=='object')value={};
  const ids=Array.isArray(value.processed)?value.processed.filter(id=>typeof id==='string'&&id.startsWith('preview-')&&id.length<=512).slice(-256):[];
  const records=(Array.isArray(value.records)?value.records:[]).filter(r=>r&&typeof r.id==='string'&&r.id.startsWith('preview-')&&r.id.length<=512&&Number.isSafeInteger(r.observedAt)&&r.observedAt>=0&&r.observedAt>=now-90*86400000)
   .map(r=>({id:r.id,observedAt:r.observedAt,liters:optional(r.liters,true),range:optional(r.range),full:r.full===true}))
   .sort((a,b)=>b.observedAt-a.observedAt||a.id.localeCompare(b.id)).filter((r,i,all)=>all.findIndex(a=>a.id===r.id)===i).slice(0,100);
  return {reserve:validReserve(value.reserve)?value.reserve:50,character:value.character!==false,records,processed:[...new Set([...ids,...records.map(r=>r.id)])].slice(-256)};
 }
 function reduce(previous,event,now=Date.now()){
  const state=restore(previous,now);
  if(event.type==='reserve'&&validReserve(event.value))return {...state,reserve:event.value};
  if(event.type==='character'&&typeof event.value==='boolean')return {...state,character:event.value};
  if(event.type==='clear')return {...state,records:[]};
  if(event.type==='refuel'){
   const r=event.value;
   if(!r||typeof r.id!=='string'||!r.id.startsWith('preview-')||state.processed.includes(r.id))return state;
   return restore({...state,records:[...state.records,{...r,observedAt:now}],processed:[...state.processed,r.id]},now);
  }
  return state;
 }
 const api={restore,reduce};if(typeof module!=='undefined')module.exports=api;root.SunsetPreviewState=api;
})(typeof globalThis!=='undefined'?globalThis:this);
