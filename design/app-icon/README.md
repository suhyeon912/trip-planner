# 앱 아이콘

- `source.png`: 원본 (Adobe Firefly로 만든 그림, 1254×1254)
- 앱에 들어간 것: 원본에서 흰 그림만 꺼내 108dp 캔버스 가운데 60dp 크기로 맞춘 전경 이미지
  - `app/src/main/res/mipmap-*/ic_launcher_foreground.png` (mdpi ~ xxxhdpi)
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher(_round).xml`: 바탕색 + 전경 + 테마 아이콘(monochrome)
- 바탕색: 실사용 `#2563EB` (`values/ic_launcher.xml`), 개발 버전 `#475569` (`app/src/dev/res/values/ic_launcher.xml`)
