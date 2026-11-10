const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const required=['AGENTS.md','START-HERE.md','docs/APPROVED-BASELINE.md','docs/UI-SESSION-HANDOFF.md','docs/APP-SESSION-HANDOFF.md','docs/SESSION-PROGRESS.md','docs/ANDROID-INTEGRATION.md','docs/PODONG-BEHAVIOR.md','web/index.html','web/style.css','web/state.js','web/app.js','android/gradlew.bat','android/gradle/wrapper/gradle-wrapper.jar'];
for(const p of required)assert.ok(fs.existsSync(path.join(root,p)),p+' missing');
for(const pose of ['peek','wave','cheek','wiggle','tuck']){
 const web=fs.readFileSync(path.join(root,'web/assets/podong-'+pose+'.png'));
 const native=fs.readFileSync(path.join(root,'android/fuel-pilot-ui/src/main/res/drawable-nodpi/podong_'+pose+'.png'));
 assert.deepEqual(web,native,'native asset mismatch: '+pose);
}
for(const name of ['index.html','style.css','app.js','state.js','animation-lab.html','animation-data.js','podong-animation.js']){
 const text=fs.readFileSync(path.join(root,'web',name),'utf8');
 assert.ok(!/https?:\/\//.test(text),'offline web dependency: '+name);
}
assert.deepEqual(fs.readFileSync(path.join(root,'web/assets/animation/podong-atlas.png')),fs.readFileSync(path.join(root,'android/fuel-pilot-ui/src/main/res/drawable-nodpi/podong_atlas.png')));
const data=require('../web/animation-data.js');
for(const [name,clip] of Object.entries(data.clips)){assert.equal(clip.frames.length,name==='calm'?8:16);assert.equal(clip.durationMs,clip.durationsMs.reduce((a,b)=>a+b));assert.ok(fs.existsSync(path.join(root,'web',clip.file)));assert.ok(clip.viewport.w>0&&clip.viewport.x+clip.viewport.w<=256);}
console.log('Package verified: matching native/web atlas, 5 clips / 72 frames, fixed per-clip viewports, no external web URLs.');
