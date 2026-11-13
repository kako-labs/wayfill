(function(root){
  'use strict';
  const sequence=Array.from({length:16},(_,index)=>index);
  root.SunsetMotionData={
    sheets:{
      calm:{file:'assets/sunset/podong-calm-sheet.png',columns:4,rows:4},
      recommend:{file:'../design/motion-candidates/2026-11-13-old-acting-reference/podong-recommend-v2-sheet.png',columns:4,rows:4},
      small:{file:'../design/motion-candidates/2026-11-13-old-acting-reference/podong-small-v2-sheet.png',columns:4,rows:4},
      full:{file:'../design/motion-candidates/2026-11-13-old-acting-reference/podong-full-v2-sheet.png',columns:4,rows:4},
      recovery:{file:'../design/motion-candidates/2026-11-13-old-acting-reference/podong-recovery-v2-sheet.png',columns:4,rows:4},
    },
    clips:{
      calm:{sheet:'calm',frames:[0,4,5,6,7,8],durationsMs:[5200,100,80,80,100,440]},
      recommend:{sheet:'recommend',frames:sequence,durationsMs:Array(16).fill(100)},
      small:{sheet:'small',frames:sequence,durationsMs:Array(16).fill(100)},
      full:{sheet:'full',frames:sequence,durationsMs:Array(16).fill(125)},
      recovery:{sheet:'recovery',frames:sequence,durationsMs:Array(16).fill(125)},
    },
  };
})(typeof globalThis!=='undefined'?globalThis:this);
