# 여행 플래너 (TripPlanner)

스마트모바일프로그래밍 기말 프로젝트 — 2인 1조 · 14주차 발표

## 열기
Android Studio → **Open** → 이 폴더 선택 → Gradle 동기화 대기 → 에뮬레이터 ▶
처음 실행하면 예시 여행 3개(부산 · 오사카 · 타이베이)가 자동으로 들어갑니다.

## 설정
- Kotlin · XML(Views) · Minimum SDK API 26 · compileSdk 35 · 패키지 `com.tripplanner`
- Room(DB) · ViewModel · LiveData · RecyclerView · Material TabLayout
- 글꼴: Pretendard (SIL OFL 1.1, `app/src/main/res/font`, 라이선스 `app/src/main/assets/licenses/Pretendard-OFL.txt`) — 가장 얇은 굵기는 Medium

## 기능
| 화면 | 할 수 있는 것 |
|---|---|
| 여행 목록 | 여행 추가, 길게 눌러 수정/삭제, 확정·후보 개수 |
| 여행 상세 | 비행기·숙소 요약, 확정/후보 탭, 하고 싶은 거 메모 |
| 장소 카드 | 눌러서 펼침/접힘 · 운영시간 · 브레이크 · 휴무 · 예약필수 · 메모 · 지도(카카오맵/구글맵) · 수정 · 일차 변경 · 후보↔확정 · 삭제 |
| 장소 추가/수정 | 후보/확정, 일차, 머무는 시간, 위도·경도 |
| 항공·숙소 | 비행기·숙소 추가/수정/삭제 (숙소 위치 = 경로 출발점) |
| 경로 제안 | 일차별 A 최단 / B 시간 맞춤 / C 후보 포함 비교 → 확정 저장, 지도 길찾기 |

## 경로 계산 방식 (`route/RoutePlanner.kt`)
- 길찾기 API 없이 **직선거리 × 1.3** 으로 거리를, **평균 15km/h** 로 이동 시간을 추정
- 10:00 숙소 출발 → 장소들 → 숙소 복귀, 장소 8곳 이하는 모든 순서를 비교
- **A 최단**: 총 거리가 가장 짧은 순서
- **B 시간 맞춤**: 휴무·운영시간 밖·브레이크 타임에 걸리는 순서를 피하고, 문 열 때까지 기다리는 시간이 적은 순서
- **C 후보 포함**: A 경로에서 3km 이내로 돌아가면 들를 수 있는 후보를 최대 2곳 추가
- 예시 데이터로 확인: 부산 1일차(돼지국밥집 브레이크 타임), 오사카 1일차(저녁만 여는 야키니쿠), 타이베이 2일차(우육면집 브레이크 타임)

## 국내 / 해외 지도 자동 전환 (`ui/MapLauncher.kt`, `route/Region.kt`)
- 좌표가 대한민국 안(제주·울릉도·독도 포함, 대략적인 다각형으로 판별)이면 **카카오맵**, 밖이면 **구글맵**
- 위치가 없는 장소는 같은 여행의 다른 장소·숙소 위치로 국내/해외를 짐작
- 카카오맵 앱이 없으면 카카오맵 모바일 웹으로 열림
- 카카오맵 길찾기: 자동차·도보는 경유지 포함 전체 경로(경유지 최대 5곳), 대중교통은 경유지를 지원하지 않아 구간을 하나 골라서 열기
- API 키 · 결제 등록 필요 없음 (URL 스킴만 사용)

## 앱 안 지도 (`ui/TripMap.kt` + `KakaoMapEngine` · `GoogleMapEngine` · `OsmMapEngine`)
- **국내 여행 → 카카오맵 SDK, 해외 여행 → Google Maps SDK** (여행 전체의 장소·숙소가 모두 국내면 카카오, 해외가 한 곳이라도 있으면 구글. 국내 출발 해외여행 = 구글, 탭·일차를 바꿔도 같은 지도)
- 키가 없거나, 기기가 지원하지 않거나(카카오: x86 에뮬레이터), 인증에 실패하면 **OpenStreetMap 으로 대신 표시** → 키 없이도 앱이 동작
- 여행 상세: 툴바 지도 버튼으로 목록 ↔ 지도 전환 (일차별 색, n일차는 숙소 → 1 → 2 → … → 숙소, 점을 누르면 카드)
- 경로 제안: 선택한 A/B/C안을 지도에 표시, C안에서 추가된 후보는 청록색
- 실제 길찾기는 기존처럼 카카오맵 · 구글맵 앱으로 넘김

### 지도 키 넣는 법 (각자 PC의 `local.properties`, 깃에 올라가지 않음)
```
KAKAO_NATIVE_APP_KEY=카카오_네이티브_앱_키
MAPS_API_KEY=구글_API_키
```
넣은 뒤 Sync Now → 다시 빌드.

**카카오 (국내)** — https://developers.kakao.com
1. 내 애플리케이션 → 앱 만들기 → **네이티브 앱 키** 복사
2. 앱 설정 → 플랫폼 → Android: 패키지명 `com.tripplanner`, **키 해시** 등록
   - 키 해시는 PC(디버그 키)마다 다르므로 **두 사람 모두 각자 키 해시를 등록**
   - 키 해시를 모르면 키만 넣고 실행 → 인증 실패 시 지도 아래에 키 해시가 표시됨 (Logcat `KakaoMapEngine` 에도 출력)
3. 카카오맵 사용 설정 **ON**
4. 실제 폰은 바로 동작. **PC 에뮬레이터에서 보려면** (확인 완료: Pixel 5 · API 30 · Google APIs · x86_64)
   - API 30(Android 11) 이미지 사용 (API 33·37 은 카카오 엔진 변환 실패)
   - 에뮬레이터 ⋮ → Extended Controls → Settings → Advanced → OpenGL ES API level 을 **Renderer maximum (up to OpenGL ES 3.1)** 로 → Cold Boot
   - `gradle.properties` 의 `android.buildOnlyTargetAbi=false`, `app/build.gradle.kts` 의 `useLegacyPackaging = true` 는 이 때문에 넣은 설정이니 지우지 말 것

**Google (해외)** — https://console.cloud.google.com
1. 프로젝트 만들기 → **결제 계정 연결**(카드 등록 필요, 안드로이드 지도 표시는 요금 없음으로 안내됨)
2. API 및 서비스 → **Maps SDK for Android** 사용 설정
3. 사용자 인증 정보 → API 키 만들기 → (권장) Android 앱으로 제한: 패키지 `com.tripplanner` + SHA-1
4. Google Play 서비스가 있는 기기·에뮬레이터(Google APIs 이미지)에서만 표시

## 새 기능 (디자인 2차와 함께)
- **디자인**: 여행 카드 커버(여행마다 색 · D-day · 교통 아이콘 · 확정 비율), 일차 색으로 통일된 타임라인 번호, 장소 종류 아이콘, 경로 화면은 지도 먼저, **다크 모드**(휴대폰 설정 따라감)
- **달력·시계로 입력**: 여행 기간 · 숙소 체크인/아웃(기간 선택), 교통편 출발/도착(날짜→시각)
- **출발 시각 변경**: 경로 제안 화면의 `10:00 출발` 버튼 → 여행에 저장되고 다시 계산
- **순서 직접 바꾸기**: 여행 상세에서 일차를 고른 뒤 장소를 길게 누르거나 ⋮⋮ 손잡이를 끌기
- **교통편**: 비행기 외에 기차·KTX · 버스 · 배 · 렌터카 (부산 예시에 KTX)
- **장소 종류·사진**: 관광/맛집/카페/쇼핑/체험/기타, 사진은 앱 내부에 줄여서 저장(앨범 권한 불필요)
- **장소 검색 자동 입력**: 이름·위치·종류(구글은 운영시간도) — 국내 카카오 로컬, 해외 Google Places
- **공유·초대**: 6자리 코드로 친구가 참여 → 같은 여행을 실시간으로 함께 편집 (Firebase)
- DB 버전 3 (기존 데이터는 그대로 두고 칸만 추가하는 마이그레이션 포함)

### 장소 검색 키 (local.properties)
```
KAKAO_REST_API_KEY=카카오_REST_API_키      # 카카오 콘솔 → 앱 키 → REST API 키
```
해외 검색은 지도와 같은 `MAPS_API_KEY` 사용 — Google Cloud에서 **Places API (New)** 사용 설정 + 키의 API 제한에 추가.

### 공유·초대 (Firebase) 설정
1. console.firebase.google.com → 프로젝트 추가 (Analytics 끄기)
2. Android 앱 추가: 패키지 `com.tripplanner`, 디버그 SHA-1 → **google-services.json 을 `app/` 폴더에** (팀원 SHA-1 도 프로젝트 설정에 추가)
3. Authentication → Sign-in method → **익명(Anonymous)** 사용
4. Firestore Database 만들기 (asia-northeast3 서울, 프로덕션 모드) → **규칙 탭에 `firestore.rules` 내용 붙여 넣고 게시**
- `google-services.json` 이 없으면 공유 메뉴는 '설정 필요' 안내만 나오고 나머지 기능은 그대로 동작
- 동기화 방식: 여행 단위 '마지막 저장 우선', 사진은 기기에만 저장

## 구조
```
app/src/main/java/com/tripplanner/
├─ TripPlannerApp.kt         DB · Repository 를 한 번만 만듦
├─ data/
│  ├─ Models.kt              Room 엔티티: Trip · Flight · Lodging · Place
│  ├─ TripDao.kt             쿼리 모음
│  ├─ AppDatabase.kt
│  ├─ TripRepository.kt      화면은 이것만 통해서 데이터 접근
│  └─ SampleData.kt          예시 여행 (처음 실행 시)
├─ route/
│  ├─ RoutePlanner.kt        경로 A/B/C 계산 (안드로이드와 무관한 순수 코틀린)
│  ├─ TimeUtil.kt            "11:00 ~ 21:00" 해석, 요일, 일차 날짜
│  ├─ Region.kt              좌표가 국내인지 판별
│  └─ Geo.kt                 좌표 거리
└─ ui/
   ├─ MainActivity · TripListViewModel · TripAdapter
   ├─ TripDetailActivity · TripDetailViewModel · PlaceAdapter
   ├─ AddPlaceActivity
   ├─ TravelInfoActivity · TravelInfoViewModel · SimpleAdapter
   ├─ RouteActivity · RouteViewModel · RouteAdapter
   ├─ MapLauncher.kt         카카오맵/구글맵 열기
   └─ FormDialog.kt          입력 다이얼로그 · 일차 선택 도우미
app/src/test/.../RoutePlannerTest.kt   경로 계산 단위 테스트 (8개)
app/src/test/.../RegionTest.kt         국내/해외 판별 테스트 (2개)
```

## 참고
- DB 구조(엔티티)를 바꾸면 `AppDatabase` 의 version 을 1 올리세요. 개발 중에는 기존 데이터가 지워지고 예시가 다시 들어갑니다.
- 예시 데이터의 운영시간 · 편명은 화면 확인용 임의 값입니다.

## 다음 할 일
- [ ] LLM API 로 경로 설명/추천 (10주차)
- [ ] 친구 공유 (ACTION_SEND 텍스트 공유 → 여유 있으면 Firebase)
- [ ] 출발 시각 바꾸기
- [ ] 국내 교통편(KTX 등) — 지금은 '비행기' 항목만 있음
