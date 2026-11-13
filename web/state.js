(function(root){
 'use strict';
 const createState=()=>({screen:'recommendation',motion:'parked',routeStage:'start',celebration:null,refuel:null,seenIds:[],characterEnabled:true,reserveKm:50,pendingStation:null,routeStation:null,error:null});
 function variant(e){if(e.isFull&&e.wasLow)return 'recovery';if(e.isFull)return 'full';if(Number.isFinite(e.liters)&&e.liters>0&&e.liters<=10)return 'small';return 'normal';}
 const durations={normal:2000,small:1600,full:2400,recovery:2400};
 function poseAt(ms,v='normal'){
  const d=durations[v]||2000;if(ms<0||ms>=d)return null;
  if(v==='small')return ms<150?'peek':ms<700?'wave':'tuck';
  const t=ms*2000/d;return t<300?'peek':t<700?'wave':t<1100?'cheek':t<1600?'wiggle':t<1900?'tuck':'sparkle';
 }
 function reduce(s,a){
  switch(a.type){
   case 'MOTION':return {...s,motion:a.motion,celebration:a.motion==='parked'?s.celebration:null,screen:a.motion!=='parked'&&['refuel','recommendation','analysis','settings','longdrive'].includes(s.screen)?'widget':s.screen};
   case 'REFUEL':{
    const e=a.event;if(!e||typeof e.id!=='string'||!e.id.trim()||s.seenIds.includes(e.id))return s;
    const refuel={...e,liters:Number.isFinite(e.liters)&&e.liters>0?e.liters:null,rangeKm:Number.isFinite(e.rangeKm)&&e.rangeKm>=0?e.rangeKm:null};
    return {...s,refuel,routeStage:'refuel',routeStation:null,pendingStation:null,seenIds:[...s.seenIds,e.id],screen:s.motion==='parked'?'refuel':'widget',celebration:s.motion==='parked'&&s.characterEnabled?{id:e.id,variant:variant(refuel)}:null};
   }
   case 'DONE':return s.celebration?.id===a.id?{...s,celebration:null}:s;
   case 'CHARACTER':return {...s,characterEnabled:!!a.enabled,celebration:a.enabled?s.celebration:null};
   case 'RESERVE':{
    if(s.motion!=='parked')return s;
    const value=Math.max(20,Math.min(100,Math.round(Number(a.value)/10)*10));
    return Number.isFinite(value)?{...s,reserveKm:value}:s;
   }
   case 'SELECT_STATION':return a.id&&!s.pendingStation?{...s,pendingStation:a.id,screen:'adding',error:null}:s;
   case 'ROUTE_ADDED':return s.pendingStation===a.id?{...s,pendingStation:null,routeStation:a.id,screen:'added',error:null}:s;
   case 'ROUTE_FAILED':return s.pendingStation===a.id?{...s,pendingStation:null,screen:'recommendation',error:'경유지를 추가하지 못했어요. 다시 시도해 주세요.'}:s;
   case 'CONTINUE':return {...s,screen:'widget',celebration:null};
   case 'SCREEN':return {...s,screen:a.screen,celebration:null};
   default:return s;
  }
 }
 const routeContext=s=>{
  if(s.routeStage==='passing')return {stationId:'B',stationKm:3,rangeKm:18,destinationKm:38};
  if(s.routeStage==='early')return {stationId:'A',stationKm:10,rangeKm:65,destinationKm:85};
  if(s.routeStage==='longdrive')return {stationId:null,stationKm:null,rangeKm:610,destinationKm:235};
  if(s.routeStage==='refuel')return {stationId:null,stationKm:null,rangeKm:s.refuel?.rangeKm,destinationKm:35};
  return {stationId:'A',stationKm:20,rangeKm:75,destinationKm:95};
 };
 const characterPolicy=s=>({visible:true,mode:s.motion==='parked'&&s.characterEnabled?'playful':'calm'});
 const api={createState,reduce,variant,poseAt,durations,routeContext,characterPolicy};if(typeof module!=='undefined')module.exports=api;root.FuelState=api;
})(typeof globalThis!=='undefined'?globalThis:this);
