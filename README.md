# 여행 플래너 (TripPlanner)

가고 싶은 장소를 **후보**로 모아 두고, 일차별로 **확정**한 뒤, 운영시간·브레이크 타임·휴무를 피해 가는 **경로를 제안**받는 안드로이드 여행 계획 앱입니다. 국내 여행은 카카오맵, 해외 여행은 Google Maps로 지도와 길찾기를 자동으로 나눠 씁니다. 6자리 코드로 친구를 초대하면 같은 여행을 함께 편집할 수 있습니다.

스마트모바일프로그래밍 기말 프로젝트 · 2인 1조 · 14주차 발표

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [시작하기](#시작하기)
- [키 설정 (선택)](#키-설정-선택)
- [경로 계산 방식](#경로-계산-방식)
- [지도](#지도)
- [공유·초대](#공유초대)
- [데이터 구조](#데이터-구조)
- [폴더 구조](#폴더-구조)
- [테스트](#테스트)
- [디자인 시안](#디자인-시안)
- [협업 규칙](#협업-규칙)
- [다음 할 일](#다음-할-일)

## 주요 기능

| 화면 | 할 수 있는 것 |
|---|---|
| 여행 목록 | 여행 추가, 길게 눌러 수정·삭제, 확정·후보 개수, 여행 카드(색·D-day·교통 아이콘), 넓은 화면(840dp 이상)은 2열 |
| 여행 상세 | 일차 칩으로 일차 고르기, 확정/후보 탭, 교통·숙소 요약, 여행 메모, 목록 ↔ 지도 전환, 장소 순서 끌어서 바꾸기 |
| 장소 카드 | 눌러서 펼침·접힘, 운영시간·브레이크·휴무·예약 필수·메모, 지도 앱으로 열기, 수정, 일차 변경, 후보 ↔ 확정, 삭제 |
| 장소 추가·수정 | 장소 검색으로 자동 입력, 후보/확정, 일차, 종류(관광·맛집·카페·쇼핑·체험·기타), 사진, 머무는 시간, 위치 |
| 교통·숙소 | 비행기·기차(KTX)·버스·배·렌터카, 숙소 추가·수정·삭제, 달력·시계로 날짜와 시각 입력 (숙소 위치 = 경로 출발점) |
| 경로 제안 | 일차별 A 최단 / B 시간 맞춤 / C 후보 포함 비교, 출발 시각 바꾸기, 지도에 경로 표시, 확정 저장, 지도 앱 길찾기 |
| 공유·초대 | 6자리 코드 만들기·보내기, 코드로 참여, 실시간 함께 편집 |

- 다크 모드는 휴대폰 설정을 따라갑니다.
- 처음 실행하면 예시 여행 3개(부산 · 오사카 · 타이베이)가 자동으로 들어갑니다.
- 키가 하나도 없어도 앱 전체가 동작합니다. 지도는 OpenStreetMap으로 대신 표시되고, 공유 메뉴는 '설정 필요' 안내가 나옵니다.

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어 · UI | Kotlin, XML Views, Material Components |
| 구조 | MVVM: Activity → ViewModel(LiveData) → Repository → Room DAO |
| DB | Room (KSP), DB 버전 4, 마이그레이션 포함 |
| 지도 | 카카오맵 SDK v2, Google Maps SDK, osmdroid(OpenStreetMap) |
| 검색 | 카카오 로컬 API(국내), Google Places API (New)(해외) |
| 공유 | Firebase Authentication(익명) + Cloud Firestore |
| 빌드 | AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9, JVM target 11 |
| SDK | minSdk 26 · targetSdk 35 · compileSdk 35 · 패키지 `com.tripplanner` |
| 글꼴 | Pretendard (SIL OFL 1.1). 가장 얇은 굵기는 Medium |

라이브러리 버전은 `gradle/libs.versions.toml`에 모여 있습니다.

## 시작하기

1. 저장소를 클론합니다.
   ```bash
   git clone https://github.com/suhyeon912/trip-planner.git
   ```
2. Android Studio에서 **Open** → 클론한 폴더 선택 → Gradle 동기화가 끝날 때까지 기다립니다.
3. 에뮬레이터나 실제 기기를 고르고 ▶ 실행합니다.

키 없이 바로 실행됩니다. 카카오·구글 지도, 장소 검색, 공유까지 쓰려면 아래 키 설정을 하세요.

## 키 설정 (선택)

키는 각자 PC의 `local.properties`(프로젝트 루트)에 넣습니다. 이 파일과 `google-services.json`은 `.gitignore`에 들어 있어 깃에 올라가지 않습니다.

```properties
KAKAO_NATIVE_APP_KEY=카카오_네이티브_앱_키
KAKAO_REST_API_KEY=카카오_REST_API_키
MAPS_API_KEY=구글_API_키
```

넣은 뒤 **Sync Now** → 다시 빌드합니다.

| 키 | 쓰는 곳 | 없으면 |
|---|---|---|
| `KAKAO_NATIVE_APP_KEY` | 앱 안 지도(국내) | OpenStreetMap으로 표시 |
| `KAKAO_REST_API_KEY` | 장소 검색(국내) | 국내 검색 안 됨 |
| `MAPS_API_KEY` | 앱 안 지도(해외), 장소 검색(해외) | OpenStreetMap으로 표시, 해외 검색 안 됨 |
| `app/google-services.json` | 공유·초대 | 공유 메뉴에 '설정 필요' 안내 |

<details>
<summary><b>카카오 (국내 지도·검색)</b> — https://developers.kakao.com</summary>

1. 내 애플리케이션 → 앱 만들기 → **네이티브 앱 키**, **REST API 키** 복사
2. 앱 설정 → 플랫폼 → Android: 패키지명 `com.tripplanner`, **키 해시** 등록
   - 키 해시는 PC(디버그 키)마다 다르므로 **두 사람 모두 각자 키 해시를 등록**
   - 키 해시를 모르면 키만 넣고 실행 → 인증에 실패하면 지도 아래에 키 해시가 표시됨 (Logcat `KakaoMapEngine`에도 출력)
3. 카카오맵 사용 설정 **ON**
4. 실제 폰은 바로 동작합니다. **PC 에뮬레이터에서 보려면** (확인 완료: Pixel 5 · API 30 · Google APIs · x86_64)
   - API 30(Android 11) 이미지 사용 (API 33·37은 카카오 엔진 변환 실패)
   - 에뮬레이터 ⋮ → Extended Controls → Settings → Advanced → OpenGL ES API level을 **Renderer maximum (up to OpenGL ES 3.1)** 로 → Cold Boot
   - `gradle.properties`의 `android.buildOnlyTargetAbi=false`, `app/build.gradle.kts`의 `useLegacyPackaging = true`는 이 때문에 넣은 설정이니 지우지 마세요.

</details>

<details>
<summary><b>Google (해외 지도·검색)</b> — https://console.cloud.google.com</summary>

1. 프로젝트 만들기 → **결제 계정 연결** (카드 등록 필요, 안드로이드 지도 표시는 요금 없음으로 안내됨)
2. API 및 서비스 → **Maps SDK for Android**, **Places API (New)** 사용 설정
3. 사용자 인증 정보 → API 키 만들기 → (권장) Android 앱으로 제한: 패키지 `com.tripplanner` + SHA-1, API 제한에 위 두 API 추가
4. Google Play 서비스가 있는 기기·에뮬레이터(Google APIs 이미지)에서만 지도가 표시됩니다.

</details>

<details>
<summary><b>Firebase (공유·초대)</b> — https://console.firebase.google.com</summary>

1. 프로젝트 추가 (Analytics 끄기)
2. Android 앱 추가: 패키지 `com.tripplanner`, 디버그 SHA-1 → **`google-services.json`을 `app/` 폴더에** (팀원 SHA-1도 프로젝트 설정에 추가)
3. Authentication → Sign-in method → **익명(Anonymous)** 사용
4. Firestore Database 만들기 (asia-northeast3 서울, 프로덕션 모드) → **규칙 탭에 `firestore.rules` 내용을 붙여 넣고 게시**

</details>

## 경로 계산 방식

`route/RoutePlanner.kt` — 안드로이드와 무관한 순수 코틀린이라 단위 테스트로 검증합니다.

- 길찾기 API 없이 **직선거리 × 1.3**으로 거리를, **평균 15km/h**로 이동 시간을 추정
- 숙소 출발(기본 10:00, 경로 화면에서 바꾸면 여행에 저장) → 장소들 → 숙소 복귀
- 장소 8곳 이하는 모든 순서를 비교
- **A 최단**: 총 거리가 가장 짧은 순서
- **B 시간 맞춤**: 휴무·운영시간 밖·브레이크 타임에 걸리는 순서를 피하고, 문 열 때까지 기다리는 시간이 적은 순서
- **C 후보 포함**: A 경로에서 3km 이내로 돌아가면 들를 수 있는 후보를 최대 2곳 추가
- 예시 데이터로 확인: 부산 1일차(돼지국밥집 브레이크 타임), 오사카 1일차(저녁만 여는 야키니쿠), 타이베이 2일차(우육면집 브레이크 타임)

## 지도

### 앱 안 지도 (`ui/TripMap.kt` + `KakaoMapEngine` · `GoogleMapEngine` · `OsmMapEngine`)

- 여행의 장소·숙소가 **모두 국내면 카카오맵 SDK**, **해외가 한 곳이라도 있으면 Google Maps SDK** (국내 출발 해외여행 = 구글, 탭·일차를 바꿔도 같은 지도)
- 키가 없거나, 기기가 지원하지 않거나(카카오: x86 에뮬레이터), 인증에 실패하면 **OpenStreetMap으로 대신 표시**
- 여행 상세: 툴바 지도 버튼으로 목록 ↔ 지도 전환 (일차별 색, n일차는 숙소 → 1 → 2 → … → 숙소, 점을 누르면 카드)
- 경로 제안: 선택한 A/B/C안을 지도에 표시, C안에서 추가된 후보는 청록색

### 지도 앱으로 넘기기 (`ui/MapLauncher.kt`, `route/Region.kt`)

- 좌표가 대한민국 안(제주·울릉도·독도 포함, 대략적인 다각형으로 판별)이면 **카카오맵**, 밖이면 **Google Maps**
- 위치가 없는 장소는 같은 여행의 다른 장소·숙소 위치로 국내/해외를 짐작
- 카카오맵 앱이 없으면 카카오맵 모바일 웹으로 열림
- 카카오맵 길찾기: 자동차·도보는 경유지 포함 전체 경로(경유지 최대 5곳), 대중교통은 경유지를 지원하지 않아 구간을 하나 골라서 열기
- URL 스킴만 사용하므로 이 부분은 API 키가 필요 없습니다.

## 공유·초대

`share/ShareManager.kt`, `share/ShareCodec.kt`, `ui/ShareDialogs.kt`

- 여행 상세 툴바의 공유 버튼 → 6자리 코드 만들기 → 친구에게 보내기·복사
- 여행 목록 툴바의 참여 버튼 → 코드 입력 → 같은 여행을 실시간으로 함께 편집
- 여행 하나를 Firestore 문서 하나로 저장하고, 여행 단위로 **마지막 저장 우선**으로 동기화
- 장소 사진은 기기에만 저장되고 공유되지 않습니다.

## 데이터 구조

`data/Models.kt` — 여행(Trip) 하나 아래에 교통편(Flight) · 숙소(Lodging) · 장소(Place)가 `tripId`로 연결됩니다. 여행을 지우면 딸린 항목도 함께 지워집니다.

| 엔티티 | 주요 칸 |
|---|---|
| `Trip` | 이름, 시작·끝 날짜, 메모, 출발 시각, 공유 코드, 수정 시각 |
| `Flight` | 교통 종류(`TransportMode`), 편명·열차 번호, 출발지·도착지, 출발·도착 시각 |
| `Lodging` | 이름, 체크인·체크아웃, 위도·경도 (경로 출발점), 주소·상세 주소·위치 메모 |
| `Place` | 이름, 후보/확정(`PlaceStatus`), 종류(`PlaceCategory`), 운영시간·브레이크·휴무, 예약 필수, 메모, 위도·경도, 주소·상세 주소·위치 메모, 일차·순서, 머무는 시간, 사진 |

- 위도·경도가 0이면 '위치 모름'으로 보고 경로 계산에서 빠집니다.
- DB 구조(엔티티)를 바꾸면 `AppDatabase`의 `version`을 1 올리고 `MIGRATION`을 추가하세요.
- 예시 데이터(`SampleData.kt`)의 운영시간·편명은 화면 확인용 임의 값입니다.

## 폴더 구조

```
trip-planner/
├─ app/src/main/java/com/tripplanner/
│  ├─ TripPlannerApp.kt         DB · Repository · 공유 매니저를 한 번만 만듦
│  ├─ data/
│  │  ├─ Models.kt              Room 엔티티와 enum
│  │  ├─ TripDao.kt             쿼리 모음
│  │  ├─ AppDatabase.kt         DB 버전 · 마이그레이션
│  │  ├─ TripRepository.kt      화면은 이것만 통해서 데이터 접근
│  │  └─ SampleData.kt          예시 여행 (처음 실행 시)
│  ├─ route/
│  │  ├─ RoutePlanner.kt        경로 A/B/C 계산
│  │  ├─ TimeUtil.kt            "11:00 ~ 21:00" 해석, 요일, 일차 날짜
│  │  ├─ Region.kt              좌표가 국내인지 판별
│  │  └─ Geo.kt                 좌표 거리 (하버사인)
│  ├─ share/
│  │  ├─ ShareManager.kt        Firebase 익명 로그인 + Firestore 동기화
│  │  └─ ShareCodec.kt          여행 ↔ Firestore 문서 변환
│  └─ ui/
│     ├─ MainActivity · TripListViewModel · TripAdapter            여행 목록
│     ├─ TripDetailActivity · TripDetailViewModel · PlaceAdapter   여행 상세
│     ├─ TripDetailMap · DayChipAdapter                            상세 지도 · 일차 칩
│     ├─ AddPlaceActivity · PlaceSearch · PlaceSearchDialog        장소 추가·수정 · 검색
│     ├─ TravelInfoActivity · TravelInfoViewModel · SimpleAdapter  교통·숙소
│     ├─ RouteActivity · RouteViewModel · RouteAdapter             경로 제안
│     ├─ TripMap · KakaoMapEngine · GoogleMapEngine · OsmMapEngine 앱 안 지도
│     ├─ MapLauncher.kt              카카오맵 / Google Maps 앱 열기
│     ├─ ShareDialogs.kt             공유 · 초대 다이얼로그
│     ├─ FormDialog.kt · Pickers.kt  입력 다이얼로그 · 달력·시계
│     ├─ Photos.kt                   장소 사진 줄여서 저장
│     ├─ EmptyState.kt · ScreenChrome.kt          빈 화면 · 툴바 공통
│     └─ Labels.kt · TripFormat.kt · Visuals.kt   문구 · 날짜 표시 · 아이콘과 색
├─ app/src/main/res/            layout(-w840dp) · drawable · font · values(-night, -w840dp) · menu
├─ app/src/test/                단위 테스트
├─ design/                      디자인 시안 (아래 참고)
├─ docs/                        프로젝트 정리 · 중간 보고 · UI/UX 개선안 (HTML)
└─ firestore.rules              Firestore 보안 규칙
```

## 테스트

```bash
./gradlew test        # Windows: gradlew.bat test
```

| 파일 | 개수 | 확인하는 것 |
|---|---|---|
| `route/RoutePlannerTest.kt` | 8 | 경로 A/B/C 계산 |
| `route/RegionTest.kt` | 2 | 국내/해외 판별 |
| `share/ShareCodecTest.kt` | 3 | 공유 문서 변환 |
| `ui/MapProviderTest.kt` | 4 | 카카오 / 구글 / OpenStreetMap 지도 선택 |
| `ui/PlaceSearchTest.kt` | 3 | 검색 결과의 종류 · 운영시간 해석 |
| `ui/TripFormatTest.kt` | 2 | 여행 날짜 표시 |

## 디자인 시안

`design/` 폴더는 앱을 새로 다듬을 기준이 되는 디자인 시안입니다.

- `design/export/TripPlanner_시안_화면흐름.pdf` — 화면 흐름 순서대로 정리한 A4 PDF
- `design/export/TripPlanner_시안_디자인토큰_컴포넌트.pdf` — 색·글자·간격 토큰, 아이콘, 레이아웃, 뱃지, 컴포넌트
- `design/*.dc.html`, `design/canvas.json` — 캔버스 원본 보드 파일 (자세한 내용은 `design/README.md`)

## 협업 규칙

- `local.properties`, `google-services.json`은 절대 커밋하지 않습니다. 키는 각자 PC에 넣습니다.
- 카카오 키 해시, Firebase SHA-1은 팀원 PC마다 다르므로 각자 등록합니다.
- DB 구조를 바꾸면 `AppDatabase` 버전과 마이그레이션을 함께 올립니다.
- 커밋 전에 `./gradlew test`가 통과하는지 확인합니다.

## 다음 할 일

- [ ] 디자인 시안에 맞춰 화면 다듬기 (`design/export` PDF 기준)
- [x] 주소 · 상세 주소 · 위치 메모 (DB 4, 직접 쓴 주소는 Android Geocoder 로 좌표 변환)
- [ ] 장소·교통·숙소 첨부 파일 공유 (Firebase Storage, Blaze 요금제 필요)
- [ ] 여행 활동 기록, 경로 구간별 이동 수단 고르기 (시안에 '새 기능'으로 표시)
- [ ] LLM API로 경로 설명·추천 (10주차)
