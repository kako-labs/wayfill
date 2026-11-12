(function(root){
  'use strict';
  const sequence=Array.from({length:16},(_,i)=>i);
  const data={columns:4,rows:4,sheets:{calm:'assets/sunset/podong-calm-sheet.png',wave:'assets/sunset/podong-wave-sheet.png',full:'assets/sunset/podong-full-sheet.png',recovery:'assets/sunset/podong-recovery-sheet.png'},clips:{
    calm:{sheet:'calm',frames:[0,4,5,6,7,8],durationsMs:[5200,100,80,80,100,440]},
    recommend:{sheet:'wave',frames:sequence,durationsMs:Array(16).fill(100)},
    small:{sheet:'wave',frames:sequence,durationsMs:Array(16).fill(100)},
    full:{sheet:'full',frames:sequence,durationsMs:Array(16).fill(125)},
    recovery:{sheet:'recovery',frames:sequence,durationsMs:Array(16).fill(125)},
  }};
  if(typeof module!=='undefined')module.exports=data;
  root.SunsetMotionData=data;
})(typeof globalThis!=='undefined'?globalThis:this);
