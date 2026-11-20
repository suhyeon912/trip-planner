# 여행 플래너 (TripPlanner)

가고 싶은 장소를 **후보**로 모아 두고, 일차별로 **확정**한 뒤, 운영시간·브레이크 타임·휴무를 피해 가는 **경로를 제안**받는 안드로이드 여행 계획 앱입니다.

- 국내 여행은 카카오맵, 해외 여행은 Google Maps로 지도와 길찾기를 자동으로 나눠 씁니다.
- 6자리 코드로 친구를 초대하면 같은 여행을 실시간으로 함께 편집합니다.

> 스마트모바일프로그래밍 기말 프로젝트 · 2인 1조 · 14주차 발표

---

## 목차

1. [문서 안내](#문서-안내)
2. [빠른 시작](#빠른-시작)
3. [팀원 준비물](#팀원-준비물)
4. [주요 기능](#주요-기능)
5. [기술 스택](#기술-스택)
6. [폴더 구조](#폴더-구조)
7. [테스트](#테스트)
8. [협업 규칙](#협업-규칙)
9. [남은 일](#남은-일)

---

## 문서 안내

| 보고 싶은 것 | 위치 |
|---|---|
| 키 설정 (카카오 · Google · Firebase), 에뮬레이터, 문제 해결 | [`docs/SETUP.md`](docs/SETUP.md) |
| 코드 구조, 데이터, 경로 계산, 지도, 공유 동작 방식 | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) |
| 디자인 시안 PDF (화면 흐름 · 토큰 · 컴포넌트) | [`design/export/`](design/export) · 설명은 [`design/README.md`](design/README.md) |
| 프로젝트 정리 · 중간 보고 · UI/UX 개선안 (HTML) | [`docs/`](docs) · 목록은 [`docs/README.md`](docs/README.md) |
| Firestore 보안 규칙 | [`firestore.rules`](firestore.rules) |

---

## 빠른 시작

```bash
git clone https://github.com/suhyeon912/trip-planner.git
```

1. Android Studio → **Open** → 클론한 `trip-planner` 폴더 선택
2. Gradle 동기화가 끝날 때까지 기다리기
3. 왼쪽 아래 **Build Variants**에서 버전 고르기
   - `devDebug`: 개발 · 시연용 (앱 이름 '여행 플래너 개발', 예시 여행 4개)
   - `realDebug`: 실제 여행용 (앱 이름 '여행 플래너', 빈 목록에서 시작)
   - 두 버전은 패키지가 달라서 한 폰에 같이 설치돼요. 자세한 건 [`docs/SETUP.md`](docs/SETUP.md#두-버전-실사용--개발)
4. 에뮬레이터나 폰을 고르고 ▶ 실행

**키가 하나도 없어도 앱 전체가 동작합니다.**

- 지도는 OpenStreetMap으로 대신 보여요.
- 공유 메뉴는 '설정 필요' 안내가 나와요.
- 개발 버전은 처음 실행하면 시안과 같은 예시 여행 4개(도쿄 · 타이베이 · 부산 · 지난 여행 오사카)가 들어가요. 실사용 버전은 빈 목록이에요.

카카오 · Google 지도, 장소 검색, 공유까지 쓰려면 [`docs/SETUP.md`](docs/SETUP.md)를 따라 키를 넣으세요.

---

## 팀원 준비물

깃에 올라가지 않는 것들이라 **팀장에게 따로 받거나 각자 등록**해야 합니다.

| 무엇 | 받는 방법 | 넣는 곳 |
|---|---|---|
| 카카오 네이티브 앱 키 · REST API 키 | 팀장에게 개인 메시지로 받기 | `local.properties` |
| Google API 키 (`MAPS_API_KEY`) | 팀장에게 개인 메시지로 받기 | `local.properties` |
| `google-services.json` (Firebase) | 팀장에게 파일로 받기 | `app/` 폴더 |
| 내 PC의 **카카오 키 해시** | 각자 확인해서 팀장에게 전달 | 카카오 개발자 콘솔 |
| 내 PC의 **디버그 SHA-1** | 각자 확인해서 팀장에게 전달 | Google Cloud · Firebase 콘솔 |

- 키 해시와 SHA-1은 PC마다 달라요. 팀장 PC에서 되는 키도 내 PC에서는 지도 인증에 실패할 수 있어요. 확인 방법은 [`docs/SETUP.md`](docs/SETUP.md#내-pc-정보-확인)에 있어요.
- 키는 깃허브 이슈 · 커밋 · 단체방처럼 기록이 남는 곳에 올리지 마세요.

---

## 주요 기능

**여행 목록**
- 여행 카드 (표지 사진 · D-day · 교통 아이콘 · 확정/후보 개수 · 공유 뱃지)
- 전체 / 내 여행 / 초대받은 탭, 정렬 · 지난 여행 숨기기
- 넓은 화면(840dp 이상)은 2열

**여행 상세**
- 일정 / 후보 탭, 일차 칩
- '전체'의 교통 · 숙소 · 예약·티켓 요약
- 장소 순서 끌어서 바꾸기 (손잡이 ⠿)
- 하루의 출발 · 도착 = 그날 넣은 순서의 첫 장소 · 마지막 장소 (숙소에서 출발해 돌아오는 날은 숙소를 1번 · 마지막에 장소로 넣기)
- 목록 ↔ 지도 전환: n일차는 점마다 도착–떠남 시각

**장소**
- 장소 정보: 운영 정보, 예약·티켓 파일·사진, 관련 링크
- 장소 추가·수정: 검색으로 자동 입력, 운영 정보 '다시 가져오기', 종류 (기본 6가지 + 내가 만든 종류), 사진, 주소
  - 운영시간 · 브레이크 타임은 시작 · 종료 칸을 각각 시각 휠로, 휴무일은 월~일 칩, 머무는 시간은 시간 · 분 휠

**교통 · 숙소**
- 비행기 · 기차 · 버스 · 배 · 렌터카, 숙소
- 여행의 모든 예약·티켓 자료

**경로 제안**
- 6가지 안 비교: 이동 거리 우선 · 가까운 곳부터 · 환승 적은 순 · 지금 순서 그대로 · 시간 맞춤 · 후보 포함
- 안을 누르면 그 안의 화면: 지도 · 시간표 · 길찾기 · 적용 (뒤로 가면 안 목록)
- 출발 시각, 도착 목표 시각 (늦으면 알림), 첫 · 마지막 장소는 그 자리 그대로 두고 가운데 순서만 비교
- 구간마다 이동 수단 (도보 · 대중교통 · 택시·차), Google 길찾기로 실제 이동 시간 · 노선 이름 · 환승 수 (Routes API를 켰을 때)
- 떠나는 시각을 고르면 머무는 시간 저장, 확정 저장, 지도 앱 길찾기

**공유 · 초대**
- 6자리 코드 만들기 · 보내기 · 코드로 참여
- 실시간 함께 편집, 활동 기록 (누가 무엇을 변경했는지)

**백업 · 복구**
- 모든 여행을 서버에 자동 백업 (사진 · 예약·티켓 파일은 기기에만)
- 설정 → 복구 코드: 앱을 지웠다 깔거나 폰을 바꿔도 코드로 내 여행을 다시 불러오기

**설정**
- 기본 정렬, 테마 (기기 설정 / 밝게 / 어둡게)
- 장소 종류: 기본 종류 숨기기, 내가 만든 종류 (아이콘 120개 · 바탕색 8가지)
- 백업 · 복구: 복구 코드 보기 · 복구 코드로 불러오기

**앱 아이콘**
- 종이비행기 · 점선 경로 · 지도 핀 (원본 `design/app-icon/`). 실사용은 파랑, 개발 버전은 회색 바탕

---

## 기술 스택

| 구분 | 사용 |
|---|---|
| 언어 · UI | Kotlin, XML Views (AppCompat) + 자체 컴포넌트 `ui/widget` (Material 컴포넌트 사용 안 함) |
| 구조 | MVVM: Activity → ViewModel (LiveData) → Repository → Room DAO |
| DB | Room (KSP), DB 버전 10, 마이그레이션 포함 |
| 지도 | 카카오맵 SDK v2 · Google Maps SDK · osmdroid (OpenStreetMap) |
| 검색 · 길찾기 | 카카오 로컬 API (국내 검색) · Google Places API (New) (해외 검색) · Google Routes API (이동 시간 · 노선) |
| 공유 | Firebase Authentication (익명) + Cloud Firestore |
| 빌드 | AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.9 · JVM target 11 |
| SDK | minSdk 26 · targetSdk 35 · compileSdk 35 · 패키지 `com.tripplanner` |
| 글꼴 | Pretendard (SIL OFL 1.1), 가장 얇은 굵기는 Medium |

라이브러리 버전은 `gradle/libs.versions.toml`에 모여 있습니다.

---

## 폴더 구조

```
trip-planner/
├─ app/src/main/java/com/tripplanner/
│  ├─ data/      Room 엔티티 · DAO · DB · Repository · 예시 데이터
│  ├─ route/     경로 계산 (안드로이드와 무관한 순수 코틀린)
│  ├─ share/     Firebase 공유 · 문서 변환 · 활동 기록
│  └─ ui/        화면 (Activity · ViewModel · Adapter)
│     └─ widget/ 자체 컴포넌트 (버튼 · 입력칸 · 탭 · 스위치 · 팝업 · 알림 …)
├─ app/src/main/res/   레이아웃 · 아이콘 · 글꼴 · 색 · 문구
├─ app/src/test/       단위 테스트
├─ design/             디자인 시안 (PDF · 캔버스 원본)
├─ docs/               설정 안내 · 구조 설명 · 보고서
└─ firestore.rules     Firestore 보안 규칙
```

파일별 역할은 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#파일별-역할)에 있습니다.

---

## 테스트

```bash
./gradlew test        # Windows: gradlew.bat test (두 버전 모두)
```

| 파일 | 개수 | 확인하는 것 |
|---|---:|---|
| `route/RoutePlannerTest.kt` | 18 | 경로 안 6가지 · 같은 순서 합치기 · 구간 이동 수단 · 첫 · 마지막 장소 고정 |
| `data/DayAnchorsTest.kt` | 2 | (예전 기능) 하루의 시작 · 끝 저장 형식 · 이름 바뀜 |
| `ui/DirectionsTest.kt` | 3 | 길찾기 응답 해석 (시간 · 노선 · 환승) · 시간표 날짜 |
| `route/RegionTest.kt` | 2 | 국내 / 해외 판별 |
| `share/ShareCodecTest.kt` | 6 | 공유 문서 변환 (링크 · 내가 만든 종류 포함) |
| `share/RecoveryCodeTest.kt` | 3 | 복구 코드 만들기 · 읽기 · 로그인용 주소 |
| `share/ActivityLogTest.kt` | 4 | 활동 기록: 바뀐 것 찾기 · 문서 변환 · 100개 제한 |
| `ui/MapProviderTest.kt` | 4 | 카카오 / 구글 / OpenStreetMap 지도 고르기 |
| `ui/PlaceSearchTest.kt` | 4 | 검색 결과의 종류 · 운영시간 · 브레이크 · 휴무일 |
| `ui/TripFormatTest.kt` | 5 | 여행 날짜 표시 |
| `ui/TripArrangeTest.kt` | 3 | 메인 목록 배치 · 정렬 · 지난 여행 |
| `ui/CoverPhotoTest.kt` | 2 | 표지 사진 보이는 범위 계산 |
| `ui/GeocodingTest.kt` | 2 | 직접 쓴 주소 → 좌표 |
| `ui/AppPrefsTest.kt` | 1 | 숨긴 장소 종류 |

---

## 협업 규칙

- **키 파일은 커밋하지 않습니다.** `local.properties`, `google-services.json`은 `.gitignore`에 들어 있어요.
- **DB 구조를 바꾸면** `AppDatabase`의 `version`을 1 올리고 `MIGRATION`을 함께 추가합니다.
- **커밋 전에** `./gradlew test`가 통과하는지 확인합니다.
- 화면은 디자인 시안(`design/export`)이 기준입니다. 색 · 글자 크기는 `res/values`의 토큰을 씁니다.
- 커밋 메시지는 한국어로, 첫 줄에 무엇을 바꿨는지 씁니다.

---

## 남은 일

- [x] 길찾기 API 연결 (Google Routes API: 실제 이동 시간 · 노선 이름 · 환승 수, 안 되는 구간은 어림)
- [ ] 국내 도보 · 차 길찾기 (Google이 한국은 지원하지 않음 → 필요하면 TMAP · 카카오모빌리티 등)
- [x] 하루의 출발 · 도착 = 첫 · 마지막 장소 (숙소도 장소로 넣기)
- [ ] 예약·티켓 파일 공유 (Firebase Storage, Blaze 요금제 필요) — 지금은 기기에만 저장
- [ ] LLM API로 경로 설명 · 추천
- [x] 디자인 시안 반영 (화면 · 컴포넌트 · 모션)
- [x] 활동 기록 · 구간 이동 수단 · 예약·티켓 · 관련 링크 · 내가 만든 종류 · 운영 정보 다시 가져오기 · 지도 시간 이름표
