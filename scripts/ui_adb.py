"""Run the full UI suite on one named dedicated emulator; never restart shared ADB."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[1]

def result_summary(text):
    codes=[int(n) for n in re.findall(r'^INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)\s*$',text,re.M)]
    total=re.findall(r'^INSTRUMENTATION_STATUS: numtests=(\d+)\s*$',text,re.M)
    passed=codes.count(0)
    failed=codes.count(-1)+codes.count(-2)
    skipped=codes.count(-3)+codes.count(-4)
    reported=int(total[-1]) if total else 0
    complete=reported>0 and passed+failed+skipped==reported
    terminal=bool(re.search(r'^INSTRUMENTATION_CODE:\s*-1\s*$',text,re.M))
    ok=bool(re.search(r'^OK \(\d+ tests?\)\s*$',text,re.M))
    return dict(status='PASS' if complete and terminal and ok and passed==reported else 'FAIL',
                passed=passed,failed=failed,skipped=skipped,reported=reported,complete=complete,pleos_verified=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--adb')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if not re.fullmatch(r'emulator-\d+',args.serial):
        parser.error('Tests mutate permissions/location. Use an explicit dedicated emulator-NNNN.')
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    adb=args.adb or (str(Path(sdk)/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')) if sdk else shutil.which('adb'))
    if not adb:
        parser.error('Set ANDROID_HOME or --adb.')
    def run(tail,timeout=120):
        return subprocess.run([adb,'-s',args.serial,*tail],capture_output=True,text=True,
            encoding='utf-8',errors='replace',timeout=timeout)
    try:
        deadline=time.monotonic()+90
        while True:
            ready=run(['shell','getprop','sys.boot_completed'],10)
            if ready.returncode==0 and ready.stdout.strip()=='1':
                break
            if time.monotonic()>=deadline:
                raise RuntimeError('Named emulator is not ready. No pass claimed.')
            time.sleep(1)
        for relative in ('debug/app-debug.apk','androidTest/debug/app-debug-androidTest.apk'):
            apk=ROOT/'app/build/outputs/apk'/relative
            if not apk.is_file():
                raise RuntimeError('Build debug and AndroidTest APKs first.')
            installed=run(['install','-r',str(apk)])
            if installed.returncode or 'Success' not in installed.stdout:
                raise RuntimeError('APK install failed: '+installed.stderr[:200])
        result=run(['shell','am','instrument','-w','-r','dev.fuelpilot.test/androidx.test.runner.AndroidJUnitRunner'],600)
        text=result.stdout+'\n'+result.stderr
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(text,encoding='utf-8')
        summary=result_summary(text)
        if result.returncode:
            summary['status']='FAIL'
        summary['serial']=args.serial
        summary['evidence_kind']='general-android-instrumentation'
        args.output.with_suffix('.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(summary))
        return 0 if summary['status']=='PASS' else 1
    except (OSError,RuntimeError,subprocess.TimeoutExpired) as error:
        print('Cannot verify UI: '+str(error),file=sys.stderr)
        return 2

if __name__=='__main__':
    raise SystemExit(main())
