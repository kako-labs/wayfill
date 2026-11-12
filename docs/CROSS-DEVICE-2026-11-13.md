# 다른 노트북에서 이어가기 · 2026-11-13

현재 브랜치는 `product/soft-sunset-prepleos-20261113`이다. 사용자가 승인한 GitHub 인계 브랜치다. [11월 13일 최신 Release](https://github.com/kako-labs/fuel-pilot/releases/tag/fuel-pilot-soft-sunset-2026-11-13)에 APK·소스 ZIP·Git bundle·검증 기록을 함께 둔다. 과거 `main`, `integrate/podong-approved`, 11월 11일 Release와 구분한다.

## GitHub에서 최신 소스 받기

```powershell
git clone --branch product/soft-sunset-prepleos-20261113 --single-branch https://github.com/kako-labs/fuel-pilot.git fuel-pilot-sunset
cd fuel-pilot-sunset
git log -2 --oneline
```

기존 작업이 있는 폴더에서는 변경을 보존한 뒤 `git fetch origin`을 하고, 별도 폴더에 `git worktree add --detach ../fuel-pilot-sunset origin/product/soft-sunset-prepleos-20261113`으로 받을 수 있다. `git reset --hard`로 기존 변경을 지우지 않는다.

## 전달 폴더

`dist/prepleos-20261113/`에 debug APK, unsigned release APK, 소스 ZIP, Git bundle, 검증 요약, 체크섬과 기준 커밋 파일을 둔다. **SHA256SUMS.txt**로 받은 파일을 확인하고 **SOURCE-REVISION.txt**와 소스 커밋을 비교한다. 다른 머신의 debug 서명/빌드 환경으로 재빌드한 APK가 같은 해시라고 가정하지 않는다.

Git 이력을 포함해 그대로 받으려면 전달한 bundle 파일이 있는 폴더에서:

```powershell
git clone -b product/soft-sunset-prepleos-20261113 fuel-pilot-prepleos-20261113.bundle fuel-pilot
cd fuel-pilot
git log -1 --oneline
git status --short
```

소스 ZIP만 풀어도 빌드할 수 있다. ZIP에는 `.git`이 없으므로 브랜치/커밋 작업을 이어가려면 bundle을 권장한다. 기존 팀 앱 폴더에 통째로 덮어쓰지 않는다. 기존 작업 브랜치에서 변경을 비교해 적용한다.

## 먼저 볼 화면

1. `docs/screen-gallery.html`: 일반 Android의 실제 Compose 캡처와 웹 시안을 구분한 화면 모음.
2. `web/companion-preview.html`: 홈/주유 안내/시연 기록/설정, 작은 창과 상태 변경을 직접 확인.
3. `web/sunset-motion-review.html`: 새 프레임, 주행·모션 감소의 정지 표시, 인사 종료 확인.
4. `docs/PRE-PLEOS-READINESS-2026-11-13.md`: 구현 범위, 실제 실행 결과, 아직 연결할 외부 입력.

웹의 숫자와 기록은 합성 시연이며 Android 저장소와 별개다. 웹은 파일을 직접 열어도 동작한다. Windows의 모션 감소가 켜져 있으면 캐릭터가 정지하는 것이 정상이다.

## 빌드와 일반 Android 검증

JDK17, Android SDK35/platform-tools/build-tools35, Python3, Node22, Git을 준비한다. `JAVA_HOME`, `ANDROID_HOME`은 그 노트북 환경에서 설정한다. 개인 SDK 경로나 `local.properties`, 키를 Git에 넣지 않는다.

```powershell
python scripts/dev.py doctor
python scripts/dev.py verify
npm.cmd test
npm.cmd run verify
python -m unittest discover -s scripts/tests -v
python -m unittest discover -s server/tests -v
.\gradlew.bat :app:assembleDebugAndroidTest :app:assembleRelease --no-daemon --max-workers=1
python scripts/check_demo_isolation.py --apk app/build/outputs/apk/release/app-release-unsigned.apk
```

기기 검사는 전용 일반 AOSP API35 에뮬레이터를 사용한다. 테스트가 앱 시연 데이터, 권한과 위치 설정을 조작하므로 일상 사용 기기에서 실행하지 않는다. 전체 화면 검사에는 1682×1210/density160이 필요하다. 실제 연결된 serial로 아래 변수를 바꾼다.

```powershell
adb devices -l
$reviewSerial = "emulator-5554"
adb -s $reviewSerial shell wm size 1682x1210
adb -s $reviewSerial shell wm density 160
python scripts/ui_adb.py --serial $reviewSerial --output dist/ui-tests.txt
```

이 도구는 명시한 에뮬레이터에 debug/test APK를 설치한 뒤 **전체 AndroidJUnitRunner**를 실행한다. 실패·미완료·건너뜀을 통과로 만들지 않고 JSON 요약을 쓴다. 실제 장치는 거절하며 공유 ADB 서버를 재시작하지 않는다. Gradle 연결이 정상인 환경에서는 같은 전용 화면 설정 후 `ANDROID_SERIAL`을 지정하고 `python scripts/ui_tests.py --connected`도 사용할 수 있다. 기본 작은 휴대폰 크기의 managed-device 결과를 차량 크기 검사와 혼동하지 않는다.

GitHub UI job도 dedicated AOSP API35와 같은 wm size/density를 준비하도록 바꿨다. 설정은 [액션의 공식 사용 문서](https://github.com/ReactiveCircus/android-emulator-runner#configurations)를 참고했다. **원격 job은 이번에 실행하지 않았다.**

## APK 실행과 실제 PLEOS 준비

- debug APK는 설치·검토용 서명 빌드다. `Fuel Pilot`은 운영 경로라 SDK가 없으면 잠긴 Unknown/Unsupported 화면을 보여준다. `Fuel Pilot 시연`은 debug 전용 별도 합성 런처다.
- release APK는 unsigned이며 설치/배포용 서명이 아니다. 시연 런처·클래스·합성 마커가 빠졌는지 검사하는 산출물이다.
- 실제 PLEOS에서는 먼저 `scripts/pleos_probe.py --serial <대상>`로 환경을 기록한다. 운영 주유 전용 이벤트, 실제 연료 종류, 좌표 변환, 도로/우회 거리와 경로 수집 범위, 공급자 승인·키·HTTPS 주소를 연결한다. 단순 range 증가나 공급자 반경 거리를 검증된 데이터로 대신하지 않는다.
- 정상 내비 응답/취소/오래된 알림 액션, 주행 중 창과 시스템 HUN, car-service 재시작 후 복원은 실제 대상에서 별도로 관찰한다. 소프트 선셋의 배경 주유소는 장식이며 실제 추천 주유소 사진이 아니다.
