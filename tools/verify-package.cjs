const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const required=['START-HERE.md','docs/ANDROID-INTEGRATION.md','docs/PODONG-BEHAVIOR.md','web/index.html','web/style.css','web/state.js','web/app.js','android/gradlew.bat','android/gradle/wrapper/gradle-wrapper.jar'];
for(const p of required)assert.ok(fs.existsSync(path.join(root,p)),p+' missing');
for(const pose of ['peek','wave','cheek','wiggle','tuck']){
 const web=fs.readFileSync(path.join(root,'web/assets/podong-'+pose+'.png'));
 const native=fs.readFileSync(path.join(root,'android/fuel-pilot-ui/src/main/res/drawable-nodpi/podong_'+pose+'.png'));
 assert.deepEqual(web,native,'native asset mismatch: '+pose);
}
for(const name of ['index.html','style.css','app.js','state.js']){
 const text=fs.readFileSync(path.join(root,'web',name),'utf8');
 assert.ok(!/https?:\/\//.test(text),'offline web dependency: '+name);
}
console.log('Package verified: required files, 5 matching native/web assets, no external web URLs.');
