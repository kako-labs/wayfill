(function(root){
  'use strict';
  const sequence=Array.from({length:32},(_,i)=>i);
  const split=values=>values.flatMap(value=>[Math.floor(value/2),Math.ceil(value/2)]);
  const data={sheets:{calm:{file:'assets/sunset/podong-calm-sheet.png',columns:4,rows:4},recommend:{file:'assets/sunset/podong-recommend-sheet.png',columns:8,rows:4},small:{file:'assets/sunset/podong-small-sheet.png',columns:8,rows:4},full:{file:'assets/sunset/podong-full-sheet.png',columns:8,rows:4},recovery:{file:'assets/sunset/podong-recovery-sheet.png',columns:8,rows:4}},clips:{
    calm:{sheet:'calm',frames:[0,4,5,6,7,8],durationsMs:[5200,100,80,80,100,440]},
    recommend:{sheet:'recommend',frames:sequence,durationsMs:split([120,100,90,90,90,90,100,140,160,140,100,90,90,100,140,160])},
    small:{sheet:'small',frames:sequence,durationsMs:split([120,100,80,80,80,90,110,140,130,100,90,90,100,100,100,90])},
    full:{sheet:'full',frames:sequence,durationsMs:split([150,130,120,110,110,110,120,160,180,160,120,110,110,120,140,150])},
    recovery:{sheet:'recovery',frames:sequence,durationsMs:split([160,150,140,120,110,110,120,160,180,160,120,110,110,120,140,150])},
  }};
  if(typeof module!=='undefined')module.exports=data;
  root.SunsetMotionData=data;
})(typeof globalThis!=='undefined'?globalThis:this);
