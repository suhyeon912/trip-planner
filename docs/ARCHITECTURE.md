# 구조와 동작 방식

코드를 처음 볼 때 읽는 문서입니다. 설정은 [`SETUP.md`](SETUP.md)를 보세요.

## 목차

1. [한눈에 보기](#한눈에-보기)
2. [파일별 역할](#파일별-역할)
3. [데이터](#데이터)
4. [경로 계산](#경로-계산)
5. [지도](#지도)
6. [공유 · 초대 · 활동 기록](#공유--초대--활동-기록)
7. [화면 공통 (디자인 시스템 · 모션)](#화면-공통-디자인-시스템--모션)

---

## 한눈에 보기

```
화면 (Activity)  ──보여 주기──  ViewModel (LiveData)  ──읽기·쓰기──  Repository  ──  Room DAO  ──  기기 DB
                                                                          │
                                                                          └─ 바뀌면 알림 ─→ ShareManager ─→ Firestore (공유 중인 여행만)
```

- 화면은 DAO를 직접 보지 않고 **Repository만** 통해 데이터를 다룹니다.
- 경로 계산(`route/`)과 공유 문서 변환(`share/ShareCodec`, `share/ActivityLog`)은 안드로이드와 무관한 순수 코틀린이라 단위 테스트로 확인합니다.

---

## 파일별 역할

```
app/src/main/java/com/tripplanner/
├─ TripPlannerApp.kt          DB · Repository · 공유 매니저를 한 번만 만듦, 내가 만든 종류 지켜보기
├─ data/
│  ├─ Models.kt               Room 엔티티와 enum
│  ├─ TripDao.kt              쿼리 모음
│  ├─ AppDatabase.kt          DB 버전 · 마이그레이션
│  ├─ TripRepository.kt       화면은 이것만 통해서 데이터 접근
│  ├─ SampleData.kt           예시 여행 (처음 실행 시)
│  └─ DayAnchors.kt           하루의 시작 · 끝 저장 형식
├─ route/
│  ├─ RoutePlanner.kt         경로 안 6가지 계산 · 같은 순서 합치기 · 구간 이동 시간
│  ├─ TimeUtil.kt             "11:00 ~ 21:00" 해석, 요일, 일차 날짜
│  ├─ Region.kt               좌표가 국내인지 판별
│  └─ Geo.kt                  좌표 거리 (하버사인)
├─ share/
│  ├─ ShareManager.kt         Firebase 익명 로그인 + Firestore 동기화
│  ├─ ShareCodec.kt           여행 ↔ Firestore 문서 변환
│  └─ ActivityLog.kt          활동 기록 (바뀐 것 찾기 · 문서 변환)
└─ ui/
   ├─ 화면별
   │  ├─ MainActivity · TripListViewModel · TripAdapter · TripArrange    여행 목록
   │  ├─ TripFormActivity · CoverCropActivity · CoverCropView · CoverPhoto 여행 만들기·수정 · 표지 사진
   │  ├─ TripDetailActivity · TripDetailViewModel · PlaceAdapter         여행 상세
   │  ├─ TripDetailMap · DayChipAdapter · CardOnlyMoveAnimator           상세 지도 · 일차 칩 · 끌기 애니메이션
   │  ├─ PlaceViewActivity · AttachmentsUi                               장소 정보 · 예약·티켓 · 관련 링크
   │  ├─ AddPlaceActivity · PlaceSearch · PlaceSearchDialog · Geocoding  장소 추가·수정 · 검색 · 주소→좌표
   │  ├─ TravelInfoActivity · TravelInfoViewModel · TravelFormActivity   교통·숙소
   │  ├─ RouteActivity · RouteViewModel · RouteAdapter · LegModes        경로 제안 · 구간 이동 수단
   │  ├─ Directions · DayEndsUi                                         길찾기 (Routes API) · 하루의 시작 · 끝
   │  ├─ ActivityLogActivity · ShareDialogs                              활동 기록 · 공유·초대 팝업
   │  └─ SettingsActivity · TypeEditor · TypeIcons · AppPrefs            설정 · 내가 만든 종류 · 아이콘 120개
   ├─ 지도
   │  ├─ TripMap                                    지도 고르기 · 점 그림(PinIcons)
   │  ├─ KakaoMapEngine · GoogleMapEngine · OsmMapEngine
   │  └─ MapLauncher                                카카오맵 / Google Maps 앱 열기
   ├─ 공통 조각
   │  ├─ SelectPopup · SelectField · SegmentedGroup 메뉴(Select) · 고르기 칸 · 세그먼트
   │  ├─ WheelView · WheelPopup · CalendarDialog · Pickers   시간 휠 · 기간 달력
   │  ├─ FormDialog · EmptyState · ScreenChrome     입력 팝업 · 빈 화면 · 상단 바
   │  ├─ Motion · Haptics · ListDiff                모션 · 진동 · 목록 변화
   │  └─ Labels · TripFormat · Visuals · Photos     문구 · 날짜 표시 · 아이콘과 색 · 사진 저장
   └─ widget/                 자체 컴포넌트 (Material 대신)
      TpButton · TpFab · TpTextField · TpTabs · TpSwitch · TpSlider · TpProgressBar
      TpChipGroup · FlowLayout · CardFrame · TopBarLayout · TpDialog · TpSnackbar
```

---

## 데이터

`data/Models.kt` — 여행(Trip) 하나 아래에 교통편 · 숙소 · 장소 · 자료가 `tripId`로 연결됩니다. 여행을 지우면 딸린 항목도 함께 지워집니다.

| 엔티티 | 주요 칸 |
|---|---|
| `Trip` | 이름, 시작·끝 날짜, 메모, 출발 시각 · 도착 목표 시각, 공유 코드, 내가 만든 여행인지 · 만든 사람 · 함께 보는 사람, 표지 사진(기기에만) · 위치 · 확대 |
| `Flight` | 교통 종류(`TransportMode`), 편명 · 열차 번호, 출발지 · 도착지, 출발 · 도착 시각 |
| `Lodging` | 이름, 체크인 · 체크아웃, 위도 · 경도 (경로 출발점), 주소 · 상세 주소 · 위치 메모 |
| `Place` | 이름, 후보/확정(`PlaceStatus`), 종류(`PlaceCategory` 또는 내가 만든 종류 `typeId`), 운영시간 · 브레이크 · 휴무, 예약 필수, 메모, 위치 · 주소, 일차 · 순서, 머무는 시간, 사진, 관련 링크, Google 장소 id |
| `PlaceType` | 내가 만든 장소 종류: 이름, 아이콘 이름, 바탕색 번호 (모든 여행 공통) |
| `Attachment` | 예약·티켓 자료: 여행 · 장소, 붙은 곳, 파일 이름 · 짧은 설명 · 경로 · 형식 · 크기, 올린 사람 · 시각 (기기에만) |

- 위도 · 경도가 0이면 '위치 모름'으로 보고 경로 계산에서 빠집니다.
- 기기에만 있는 것: 표지 · 장소 사진, 예약·티켓 파일, 구간 이동 수단 선택, 테마 · 정렬 설정.

**DB 버전 기록** (`AppDatabase.kt`)

| 버전 | 바뀐 것 |
|---:|---|
| 3 | 출발 시각 · 공유 정보, 장소 종류 · 사진, 교통 종류 |
| 4 | 장소 · 숙소 주소 · 상세 주소 · 위치 메모 |
| 5 | 내가 만든 여행 / 초대받은 여행, 닉네임 |
| 6 | 표지 사진 · 위치 · 확대 |
| 7 | 숙소 도착 목표 시각 |
| 8 | 관련 링크 · 내가 만든 종류 · Google 장소 id, 종류 · 자료 표 |
| 9 | 자료 짧은 설명 |
| 10 | 일차별 하루의 시작 · 끝 장소 |

DB 구조를 바꾸면 `version`을 1 올리고 `MIGRATION_n_n+1`을 추가하세요. 기존 데이터는 지우지 않습니다.

---

## 경로 계산

`route/RoutePlanner.kt`

**하루의 시작 · 끝** (`data/DayAnchors.kt`, `ui/DayEndsUi.kt`)
- 일차마다 시작 · 끝을 따로 고릅니다: 숙소(아무 숙소나) · 그날 장소 · 정하지 않음. 고르지 않으면 그날 묵는 숙소입니다.
- 숙소면 출발점 · 도착점, 장소면 그 장소를 처음 · 마지막에 들르고(순서 고정) 숙소로 가거나 오지 않습니다.
- `Trip.dayAnchors`에 이름으로 저장해서 공유됩니다. 장소 이름을 바꾸면 같이 바뀌고, 없어진 숙소 · 장소는 기본(그날 숙소)으로 돌아갑니다.

**거리 · 시간**
- 기본은 **직선거리 × 1.3**으로 거리를 어림합니다.
- **Google Routes API** (`ui/Directions.kt`)를 켜면 구간마다 실제 이동 시간 · 거리 · 노선 이름 · 타는 횟수를 받아 씁니다.
  - 순서 비교에 쓰는 구간(시작점 · 장소 · 도착점 사이, 장소 7곳까지)과 화면에 보이는 구간을 받아 오고, 받으면 다시 계산합니다.
  - 결과는 기기에 30일 저장해서 같은 구간을 다시 묻지 않습니다. 길이 없거나 지원하지 않는 곳(예: 한국의 도보 · 차)은 하루 동안 '없음'으로 저장하고 어림값을 씁니다.
  - 대중교통 시간표는 여행 날이 30일 안이면 그날, 아니면 가장 가까운 같은 요일 기준입니다.
  - 경로 화면 부제에 기준이 보입니다: 'Google 길찾기 기준' · '일부 구간은 어림' · '직선거리 기반 추정치'.
- 받지 못한 구간은 수단별로 어림합니다.

  | 수단 | 시간 |
  |---|---|
  | 도보 | 4.5km/h |
  | 대중교통 | 기다림 · 환승 5분 + 22km/h |
  | 택시 · 차 | 부르기 · 주차 3분 + 24km/h |

  고르지 않은 구간은 1.2km 이하면 도보, 그보다 멀면 대중교통입니다. 고른 수단은 기기에 여행마다 저장됩니다(`LegModes`).
- 하루는 숙소 출발(기본 10:00) → 장소들 → 숙소 복귀입니다. 숙소가 없으면 첫 장소에서 시작합니다.
- 문 열기 전에 도착하면 기다리고, 휴무 · 운영시간 밖 · 브레이크 타임과 겹치면 경고를 붙입니다.

**경로 안 6가지**

| 안 | 순서 기준 |
|---|---|
| 이동 거리 우선 | 총 거리가 가장 짧은 순서 (8곳 이하는 모든 순서 비교) |
| 가까운 곳부터 | 숙소에서 가장 가까운 곳 → 거기서 또 가장 가까운 곳 … |
| 환승 적은 순 | 버스 · 지하철을 타는 횟수가 가장 적은 순서 (길찾기를 못 받은 구간이 있으면 '(어림)') |
| 지금 순서 그대로 | 여행 상세에서 정한 순서 |
| 시간 맞춤 | 경고가 적고 기다리는 시간이 짧은 순서 |
| 후보 포함 | 최단 경로에서 3km 이내로 돌아가면 들를 수 있는 후보를 최대 2곳 추가 |

- 순서가 같은 안은 카드 하나로 합치고 앞에서부터 A · B · C …를 붙입니다.
- 확인할 것(경고)이 가장 적은 안에 '추천'이 붙습니다.
- 예시 데이터로 확인할 수 있는 경우: 부산 1일차(돼지국밥집 브레이크), 오사카 1일차(저녁만 여는 야키니쿠), 타이베이 2일차(우육면집 브레이크).

---

## 지도

**앱 안 지도** (`ui/TripMap.kt`)
- 여행의 장소 · 숙소가 **모두 국내면 카카오맵**, **해외가 한 곳이라도 있으면 Google Maps**. 탭 · 일차를 바꿔도 같은 지도를 씁니다.
- 키가 없거나, 기기가 지원하지 않거나, 인증에 실패하면 **OpenStreetMap**으로 대신 보여 줍니다.
- 점 그림(번호 · 숙소 · 후보 · 시간 이름표)은 세 지도가 같은 그림(`PinIcons`)을 씁니다.

**지도 앱으로 넘기기** (`ui/MapLauncher.kt`, `route/Region.kt`)
- 좌표가 대한민국 안(제주 · 울릉도 · 독도 포함)이면 **카카오맵**, 밖이면 **Google Maps**.
- 위치가 없는 장소는 같은 여행의 다른 장소 · 숙소로 국내/해외를 짐작합니다.
- 카카오맵 앱이 없으면 모바일 웹으로 엽니다. URL 스킴만 써서 키가 필요 없습니다.

---

## 공유 · 초대 · 활동 기록

`share/ShareManager.kt` · `share/ShareCodec.kt` · `share/ActivityLog.kt` · `ui/ShareDialogs.kt`

**동기화**
- 여행 하나 = Firestore 문서 하나 (`trips/{초대 코드}`).
- 내가 바꾸면 1초 뒤 전체를 올리고, 다른 사람이 바꾸면 실시간으로 받습니다. 여행 단위 **마지막 저장 우선**입니다.
- 공유되는 것: 여행 정보, 장소(링크 · 내가 만든 종류 포함), 교통 · 숙소.
- 공유되지 않는 것: 사진, 예약·티켓 파일 (파일 공유는 Firebase Storage = Blaze 요금제가 필요해서 제외).
- 내가 만든 종류는 이름으로 짝지어 받는 쪽 기기의 종류에 연결합니다.

**닉네임**
- 공유를 시작하거나 참여할 때 닉네임을 받아 문서의 `names`에 올립니다.
- 메인의 '내 여행 / 초대받은' 탭, 'n명과 공유 중' · '○○님이 초대함' 뱃지가 이 정보를 씁니다.

**활동 기록**
- 올릴 때 지난번 맞춘 내용과 비교해서 바뀐 것을 기록합니다: 후보 추가, 일차 확정, 순서 옮김, 운영시간 변경, 정보 변경, 삭제, 교통 · 숙소 변경, 파일 올림, 참여.
- 같은 문서의 `activity` 칸에 최근 100개까지 남깁니다. 보안 규칙은 바꾸지 않아도 됩니다.

---

## 화면 공통 (디자인 시스템 · 모션)

**토큰** (`res/values`)
- 색: `colors.xml` (다크 모드는 `values-night/colors.xml`)
- 글자: `themes.xml`의 `TextAppearance.TripPlanner.*` (Pretendard, 가장 얇은 굵기 Medium)
- 간격 · 모서리: `dimens.xml` (좌우 여백 16)
- 모션: `motion.xml` (100 · 150 · 200 · 250 · 300ms), `res/interpolator` (standard · decelerate · accelerate)

**자체 컴포넌트** (`ui/widget`)
- Material 대신 시안 모양 그대로 만든 버튼 · 입력칸 · 탭 · 스위치 · 슬라이더 · 칩 · 팝업 · 알림입니다.
- 새 화면도 이 컴포넌트와 `Widget.TripPlanner.*` 스타일을 쓰세요.

**모션 · 진동**
- 눌림: 평평한 12% 덧칠 (물결 효과 없음)
- 화면 전환: 상세 화면은 옆에서, 입력 화면은 아래에서 등장
- 목록 변화: `ListDiff` (DiffUtil)로 추가 · 삭제 · 순서 변화가 자연스럽게 움직임
- 진동: `Haptics` — 확인 · 삭제 · 입력 오류 · 끌기 놓기 (기기의 터치 진동 설정을 따름)
