# 설정 안내

키가 없어도 앱은 실행됩니다. 이 문서는 카카오 · Google 지도, 장소 검색, 공유 · 초대까지 쓰고 싶을 때 따라 하는 순서입니다.

## 목차

1. [키를 넣는 곳](#키를-넣는-곳)
2. [내 PC 정보 확인](#내-pc-정보-확인)
3. [카카오 (국내 지도 · 검색)](#카카오-국내-지도--검색)
4. [Google (해외 지도 · 검색)](#google-해외-지도--검색)
5. [Firebase (공유 · 초대)](#firebase-공유--초대)
6. [두 버전 (실사용 · 개발)](#두-버전-실사용--개발)
7. [에뮬레이터](#에뮬레이터)
8. [문제 해결](#문제-해결)

---

## 키를 넣는 곳

프로젝트 맨 위 폴더의 `local.properties`에 넣습니다. 이 파일은 깃에 올라가지 않습니다.

```properties
sdk.dir=...                         # Android Studio가 자동으로 넣는 줄 (그대로 두기)
KAKAO_NATIVE_APP_KEY=카카오_네이티브_앱_키
KAKAO_REST_API_KEY=카카오_REST_API_키
MAPS_API_KEY=구글_API_키
```

넣은 뒤 Android Studio 위쪽의 **Sync Now** → 다시 실행합니다.

| 키 · 파일 | 쓰는 곳 | 없으면 |
|---|---|---|
| `KAKAO_NATIVE_APP_KEY` | 앱 안 지도 (국내) | OpenStreetMap으로 표시 |
| `KAKAO_REST_API_KEY` | 장소 검색 (국내) | 국내 검색 안 됨 |
| `MAPS_API_KEY` | 앱 안 지도 (해외) · 장소 검색 (해외) · 운영 정보 다시 가져오기 | OpenStreetMap으로 표시, 해외 검색 안 됨 |
| `app/google-services.json` | 공유 · 초대 · 활동 기록 | 공유 메뉴에 '설정 필요' 안내 |

> 팀원은 키를 팀장에게 개인 메시지로 받으면 됩니다. 다만 아래 [내 PC 정보](#내-pc-정보-확인)는 PC마다 다르니 각자 확인해서 콘솔에 등록해야 합니다.

---

## 내 PC 정보 확인

카카오와 Google은 "이 앱이 진짜 우리 앱인지"를 앱 서명으로 확인합니다. 디버그 서명은 **PC마다 달라서**, 팀원 PC 정보도 콘솔에 등록해야 지도가 뜹니다.

### 디버그 SHA-1 (Google · Firebase용)

Android Studio 오른쪽 **Gradle** 창 → `trip-planner > app > Tasks > android > signingReport` 실행, 또는 터미널에서:

```bash
./gradlew signingReport        # Windows: gradlew.bat signingReport
```

출력의 `Variant: debug` 아래 `SHA1: AA:BB:...` 값을 복사합니다.

### 카카오 키 해시

가장 쉬운 방법: 카카오 키를 넣고 앱을 실행해서 국내 여행의 지도를 엽니다. 인증에 실패하면 **지도 아래에 키 해시가 표시**됩니다. Logcat에서 `KakaoMapEngine`으로 검색해도 나옵니다.

---

## 카카오 (국내 지도 · 검색)

[카카오 개발자 콘솔](https://developers.kakao.com)

1. 내 애플리케이션 → 앱 만들기 → **네이티브 앱 키**, **REST API 키** 복사
2. 앱 설정 → 플랫폼 → Android
   - 패키지명: `com.tripplanner`
   - **키 해시**: 팀원 모두 각자 것을 추가 (여러 개 등록 가능)
3. 카카오맵 사용 설정 **ON**

---

## Google (해외 지도 · 검색)

[Google Cloud 콘솔](https://console.cloud.google.com)

1. 프로젝트 만들기 → **결제 계정 연결** (카드 등록 필요)
2. API 및 서비스 → 라이브러리에서 사용 설정
   - **Maps SDK for Android** (앱 안 지도)
   - **Places API (New)** (장소 검색 · 운영 정보 다시 가져오기)
   - **Routes API** (경로 제안의 실제 이동 시간 · 노선 이름 · 환승 수) — 꺼져 있으면 어림값으로 계산하고 경로 화면에 안내가 나와요
3. 사용자 인증 정보 → API 키 만들기 → (권장) 키 제한
   - 애플리케이션 제한: Android 앱 → 패키지 `com.tripplanner` + **팀원 모두의 SHA-1**
   - API 제한: 위 세 API
4. Google Play 서비스가 있는 기기 · 에뮬레이터(Google APIs 이미지)에서만 지도가 보입니다.

> 요금은 API마다 무료 사용량이 다릅니다. 콘솔의 사용량 · 요금 페이지에서 확인하세요.

---

## Firebase (공유 · 초대)

[Firebase 콘솔](https://console.firebase.google.com)

1. 프로젝트 추가 (Analytics 꺼도 됨)
2. Android 앱 추가
   - 패키지 `com.tripplanner`, 디버그 SHA-1
   - **`google-services.json`을 내려받아 `app/` 폴더에** 넣기
   - 팀원 SHA-1도 프로젝트 설정 → 내 앱 → SHA 인증서 지문에 추가
3. Authentication → Sign-in method → **익명(Anonymous)** 사용
4. Firestore Database 만들기 (서울 `asia-northeast3`, 프로덕션 모드)
5. Firestore → **규칙** 탭에 저장소의 [`firestore.rules`](../firestore.rules) 내용을 그대로 붙여 넣고 **게시**

> `firestore.rules`가 바뀌면 콘솔에 다시 붙여 넣고 게시해야 합니다. 예전 규칙이면 닉네임 없이 참여만 됩니다.

팀원은 팀장에게 `google-services.json`을 받아 `app/` 폴더에 넣기만 하면 됩니다. 콘솔을 같이 보려면 팀장이 Firebase 프로젝트 설정 → 사용자 및 권한에서 팀원을 추가해 주세요.

---

## 두 버전 (실사용 · 개발)

| | 실사용 `real` | 개발 `dev` |
|---|---|---|
| 앱 이름 | 여행 플래너 | 여행 플래너 개발 |
| 패키지 | `com.tripplanner` | `com.tripplanner.dev` |
| 처음 열면 | 빈 목록 (여행을 다 지워도 예시가 다시 생기지 않음) | 예시 여행 4개 |
| APK 위치 | `app/build/outputs/apk/real/debug/app-real-debug.apk` | `app/build/outputs/apk/dev/debug/app-dev-debug.apk` |

- Android Studio 왼쪽 아래 **Build Variants** 창에서 `realDebug` / `devDebug`를 고른 뒤 ▶ 실행 또는 **Build → Generate App Bundles or APKs → Generate APKs**.
- 터미널: `./gradlew assembleRealDebug` · `./gradlew assembleDevDebug` (Windows는 `gradlew.bat`)
- 패키지가 달라서 한 폰에 둘 다 설치되고, 데이터도 따로예요.
- **실사용 버전은 지금 콘솔 설정 그대로** 동작해요 (패키지가 예전과 같음).
- **개발 버전도 지도 · 검색 · 길찾기 · 공유를 쓰려면** 콘솔에 패키지 `com.tripplanner.dev`를 하나 더 등록해요:
  - Google Cloud: API 키 → Android 앱 제한에 `com.tripplanner.dev` + 같은 SHA-1 추가
  - 카카오: 플랫폼 → Android 패키지명에 `com.tripplanner.dev` 추가 (키 해시는 같음)
  - Firebase: Android 앱 추가 → `com.tripplanner.dev` → **`google-services.json`을 다시 내려받아** `app/`에 덮어쓰기 (두 패키지가 한 파일에 들어 있음). 안 하면 개발 버전만 공유 메뉴가 '설정 필요'예요 (빌드는 됨)
- 예전에 설치한 앱(`com.tripplanner`)에 예시 여행이 남아 있으면 실사용 버전을 덮어 설치해도 그대로 남아요. 직접 지우거나, 앱을 삭제한 뒤 실사용 버전을 설치하세요.

---

## 에뮬레이터

- **Google 지도**: Google APIs(또는 Google Play) 이미지를 고르세요.
- **카카오 지도**: 실제 폰은 바로 동작합니다. 에뮬레이터에서 보려면 (확인: Pixel 5 · API 30 · Google APIs · x86_64)
  - **API 30 (Android 11)** 이미지 사용 (API 33 · 37은 카카오 엔진 변환 실패)
  - 에뮬레이터 ⋮ → Extended Controls → Settings → Advanced → OpenGL ES API level을 **Renderer maximum (up to OpenGL ES 3.1)** → Cold Boot
  - `gradle.properties`의 `android.buildOnlyTargetAbi=false`, `app/build.gradle.kts`의 `useLegacyPackaging = true`는 이것 때문에 넣은 설정이니 지우지 마세요.
- 지원이 안 되면 앱이 알아서 OpenStreetMap으로 보여 줍니다.

---

## 문제 해결

| 증상 | 확인할 것 |
|---|---|
| 지도가 회색 격자 + "구글 키 없음" | `local.properties`에 `MAPS_API_KEY`가 있는지, Sync 했는지 |
| 카카오 지도 자리에 키 해시가 보임 | 그 키 해시를 카카오 콘솔 → 플랫폼 → Android에 추가 |
| Google 지도가 빈 화면 | 키 제한에 내 PC의 SHA-1이 있는지, Maps SDK for Android를 켰는지 |
| 해외 검색이 "권한 없음" | Places API (New)를 켰는지, 키 API 제한에 들어 있는지 |
| 경로 화면에 "Routes API 를 켜면…" 안내 | Routes API를 켰는지, 키 API 제한에 들어 있는지 → 경로 화면을 다시 열기 |
| 경로 부제가 "일부 구간은 어림" | 그 구간은 Google이 길을 주지 않은 곳 (예: 한국의 도보 · 차). 정상이에요 |
| 공유 메뉴가 '설정 필요' | `app/google-services.json`이 있는지 → 넣은 뒤 Sync |
| 코드로 참여가 안 됨 | Firebase 익명 로그인을 켰는지, `firestore.rules`를 게시했는지 |
| 키를 넣었는데 그대로 | Sync Now 후 앱을 다시 설치 (▶ 다시 실행) |
