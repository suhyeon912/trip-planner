# 디자인 시안

앱 화면의 기준이 되는 디자인 시안이에요. 화면을 바꿀 때는 이 시안과 맞춰 주세요.

## 바로 보기 (PDF)

| 파일 | 내용 |
|---|---|
| [`export/TripPlanner_시안_화면흐름.pdf`](export/TripPlanner_시안_화면흐름.pdf) | 화면 흐름 순서대로, A4 가로 한 쪽에 화면 3~4개 |
| [`export/TripPlanner_시안_디자인토큰_컴포넌트.pdf`](export/TripPlanner_시안_디자인토큰_컴포넌트.pdf) | 색 · 글자 · 간격 토큰, 아이콘, 레이아웃, 뱃지, 컴포넌트 (보드 모양에 따라 가로 · 세로 섞임) |

PDF의 아이콘은 Material Symbols Rounded 원본으로 다시 그린 근사치라 캔버스와 모양이 조금 다를 수 있어요.

## 원본 파일

| 파일 | 내용 |
|---|---|
| `*.dc.html` | 캔버스 보드 하나 = 화면 하나 (예: `Main.dc.html`, `Route.dc.html`) |
| `canvas.json` | 캔버스의 보드 배치 |
| `ds/tripplanner/` | 디자인 시스템: `tokens.css`, 컴포넌트 CSS, Pretendard 글꼴 |
| `tp-icons.css` | 시안에서 쓰는 아이콘 목록 |

- `.dc.html`을 브라우저로 바로 열면 다른 보드를 겹쳐 그리는 팝업 · 메뉴 보드는 바탕이 비어 보여요 (캔버스 전용 `support.js`가 없어서).
- 표지 사진(`/_blob/...`)은 캔버스 저장소에만 있어서 여기서는 안 보여요.
- 원래 캔버스(Claude Design)는 팀장 계정에 있어요. 같이 보려면 팀장이 캔버스를 공유해 줘야 해요. 공유 없이도 위 PDF로 전체를 볼 수 있어요.

## 앱과의 연결

| 시안 | 앱 |
|---|---|
| 색 · 글자 · 간격 토큰 | `app/src/main/res/values` (`colors.xml`, `themes.xml`, `dimens.xml`, `motion.xml`) |
| 컴포넌트 (버튼 · 입력칸 · 탭 · 팝업 …) | `ui/widget` 자체 컴포넌트 |
| 아이콘 | `res/drawable/ic_*.xml` (Material Symbols Rounded), 종류 아이콘 120개는 `ti_*.xml` |
