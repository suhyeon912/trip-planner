package com.tripplanner.data

import com.tripplanner.data.PlaceStatus.CANDIDATE
import com.tripplanner.data.PlaceStatus.CONFIRMED

/** 예시 데이터 (화면 확인용 — 운영시간·편명 등은 실제와 다를 수 있음) */
object SampleData {

    suspend fun insertAll(dao: TripDao) {
        busan(dao)
        tokyo(dao)
        taipei(dao)
    }

    /** 국내 예시 — 지도 버튼이 카카오맵으로 열림 */
    private suspend fun busan(dao: TripDao) {
        val t = dao.insertTrip(Trip(title = "부산 2박 3일", startDate = "2027-02-12", endDate = "2027-02-14",
            note = "· KTX 왕복 예매\n· 밀면 먹기\n· 광안대교 야경"))
        dao.insertLodging(Lodging(tripId = t, name = "서면 예시 호텔", checkIn = "2027-02-12",
            checkOut = "2027-02-14", lat = 35.1557, lng = 129.0590))
        // 국내 교통편 예시 (KTX)
        dao.insertFlight(Flight(tripId = t, flightNo = "KTX 101", fromAirport = "서울역", toAirport = "부산역",
            departAt = "2027-02-12 07:00", arriveAt = "2027-02-12 09:40", mode = TransportMode.TRAIN))
        dao.insertFlight(Flight(tripId = t, flightNo = "KTX 170", fromAirport = "부산역", toAirport = "서울역",
            departAt = "2027-02-14 18:30", arriveAt = "2027-02-14 21:10", mode = TransportMode.TRAIN))
        listOf(
            Place(tripId = t, name = "감천문화마을", category = PlaceCategory.SIGHT, status = CONFIRMED, openingHours = "09:00 ~ 18:00",
                memo = "어린왕자 포토존", lat = 35.0975, lng = 129.0106, day = 1, stayMinutes = 90),
            Place(tripId = t, name = "자갈치시장", category = PlaceCategory.SHOPPING, status = CONFIRMED, openingHours = "05:00 ~ 22:00",
                memo = "회 먹기", lat = 35.0967, lng = 129.0306, day = 1, stayMinutes = 60),
            Place(tripId = t, name = "예시 돼지국밥집", category = PlaceCategory.FOOD, status = CONFIRMED, openingHours = "10:00 ~ 21:00",
                breakTime = "11:30 ~ 14:00", closedDays = "월요일", memo = "B가 찾은 맛집 · 점심 피크 피하기",
                lat = 35.1577, lng = 129.0596, day = 1, stayMinutes = 50),
            Place(tripId = t, name = "해운대해수욕장", category = PlaceCategory.SIGHT, status = CONFIRMED, openingHours = "상시",
                memo = "아침 산책", lat = 35.1587, lng = 129.1604, day = 2, stayMinutes = 90),
            Place(tripId = t, name = "예시 광안리 횟집", category = PlaceCategory.FOOD, status = CONFIRMED, openingHours = "17:00 ~ 24:00",
                isReservationRequired = true, memo = "광안대교 보이는 창가 자리 예약",
                lat = 35.1532, lng = 129.1187, day = 2, stayMinutes = 90),
            Place(tripId = t, name = "해운대 블루라인파크", category = PlaceCategory.ACTIVITY, status = CANDIDATE, openingHours = "09:30 ~ 18:00",
                isReservationRequired = true, memo = "스카이캡슐은 예약 필수", lat = 35.1600, lng = 129.1730),
            Place(tripId = t, name = "흰여울문화마을", category = PlaceCategory.SIGHT, status = CANDIDATE, openingHours = "상시",
                memo = "바다 보이는 카페", lat = 35.0782, lng = 129.0453),
            Place(tripId = t, name = "태종대", category = PlaceCategory.SIGHT, status = CANDIDATE, openingHours = "04:00 ~ 24:00",
                lat = 35.0532, lng = 129.0870, stayMinutes = 120)
        ).forEach { dao.insertPlace(it) }
    }

    private suspend fun tokyo(dao: TripDao) {
        val t = dao.insertTrip(Trip(title = "도쿄 3박 4일", startDate = "2026-12-20", endDate = "2026-12-23",
            note = "· 스카이트리 야경 보기\n· 하코네 당일치기 할지 고민"))
        dao.insertFlight(Flight(tripId = t, flightNo = "KE2707", fromAirport = "GMP", toAirport = "HND",
            departAt = "2026-12-20 08:30", arriveAt = "2026-12-20 10:50"))
        dao.insertFlight(Flight(tripId = t, flightNo = "KE2708", fromAirport = "HND", toAirport = "GMP",
            departAt = "2026-12-23 12:40", arriveAt = "2026-12-23 15:20"))
        dao.insertLodging(Lodging(tripId = t, name = "아사쿠사 예시 호텔", checkIn = "2026-12-20",
            checkOut = "2026-12-23", lat = 35.7119, lng = 139.7967))
        listOf(
            Place(tripId = t, name = "센소지", category = PlaceCategory.SIGHT, status = CONFIRMED, openingHours = "06:00 ~ 17:00",
                memo = "가미나리몬 앞에서 사진", lat = 35.7148, lng = 139.7967, day = 1, stayMinutes = 90),
            Place(tripId = t, name = "도쿄 스카이트리", category = PlaceCategory.SIGHT, status = CONFIRMED, openingHours = "10:00 ~ 21:00",
                isReservationRequired = true, memo = "전망대 티켓 온라인 예매", lat = 35.7101, lng = 139.8107, day = 1, stayMinutes = 120),
            Place(tripId = t, name = "예시 몬자야키집", category = PlaceCategory.FOOD, status = CONFIRMED, openingHours = "17:00 ~ 23:00",
                closedDays = "수요일", isReservationRequired = true, memo = "A가 예약 담당 · 저녁만 영업",
                lat = 35.6627, lng = 139.7811, day = 1, stayMinutes = 90),
            Place(tripId = t, name = "시부야 스카이", category = PlaceCategory.SIGHT, status = CONFIRMED, openingHours = "10:00 ~ 22:30",
                memo = "해 질 무렵 전망대", lat = 35.6585, lng = 139.7022, day = 2, stayMinutes = 90),
            Place(tripId = t, name = "예시 라멘집", category = PlaceCategory.FOOD, status = CONFIRMED, openingHours = "11:00 ~ 21:00",
                breakTime = "15:00 ~ 17:00", closedDays = "화요일", memo = "인기 메뉴: 돈코츠",
                lat = 35.6595, lng = 139.7005, day = 2, stayMinutes = 60),
            Place(tripId = t, name = "쓰키지 장외시장", category = PlaceCategory.FOOD, status = CANDIDATE, openingHours = "07:00 ~ 14:00",
                closedDays = "일부 가게 일요일", lat = 35.6654, lng = 139.7707),
            Place(tripId = t, name = "긴자", category = PlaceCategory.SHOPPING, status = CANDIDATE, openingHours = "상시",
                memo = "쇼핑", lat = 35.6717, lng = 139.7650),
            Place(tripId = t, name = "도쿄 디즈니랜드", category = PlaceCategory.ACTIVITY, status = CANDIDATE, openingHours = "날짜별 상이",
                isReservationRequired = true, memo = "입장권 미리 사야 함", lat = 35.6329, lng = 139.8804,
                stayMinutes = 480)
        ).forEach { dao.insertPlace(it) }
    }

    private suspend fun taipei(dao: TripDao) {
        val t = dao.insertTrip(Trip(title = "타이베이 3박 4일", startDate = "2027-01-15", endDate = "2027-01-18",
            note = "· 딘타이펑 가기\n· 이지카드 충전\n· 펑리수 선물 사기"))
        dao.insertFlight(Flight(tripId = t, flightNo = "CI161", fromAirport = "ICN", toAirport = "TPE",
            departAt = "2027-01-15 08:30", arriveAt = "2027-01-15 10:10"))
        dao.insertFlight(Flight(tripId = t, flightNo = "CI160", fromAirport = "TPE", toAirport = "ICN",
            departAt = "2027-01-18 11:40", arriveAt = "2027-01-18 15:05"))
        dao.insertLodging(Lodging(tripId = t, name = "시먼딩 예시 호텔", checkIn = "2027-01-15",
            checkOut = "2027-01-18", lat = 25.0422, lng = 121.5079))
        listOf(
            Place(tripId = t, name = "타이베이 101", category = PlaceCategory.SIGHT, status = CONFIRMED, openingHours = "11:00 ~ 21:00",
                memo = "전망대는 해 질 무렵에", lat = 25.0340, lng = 121.5645, day = 1, stayMinutes = 90),
            Place(tripId = t, name = "스린 야시장", category = PlaceCategory.FOOD, status = CONFIRMED, openingHours = "16:00 ~ 24:00",
                memo = "지파이 · 버블티 먹기", lat = 25.0880, lng = 121.5241, day = 1, stayMinutes = 90),
            Place(tripId = t, name = "예시 우육면집", category = PlaceCategory.FOOD, status = CONFIRMED, openingHours = "11:00 ~ 20:30",
                breakTime = "14:30 ~ 17:00", closedDays = "월요일", isReservationRequired = true,
                memo = "B가 예약 담당", lat = 25.0330, lng = 121.5297, day = 2, stayMinutes = 60),
            Place(tripId = t, name = "국립고궁박물원", category = PlaceCategory.SIGHT, status = CONFIRMED, openingHours = "09:00 ~ 17:00",
                closedDays = "월요일", memo = "오디오 가이드 빌리기", lat = 25.1024, lng = 121.5485,
                day = 2, stayMinutes = 150),
            Place(tripId = t, name = "지우펀", category = PlaceCategory.SIGHT, status = CANDIDATE, openingHours = "상점마다 다름",
                memo = "예스·진과스 투어로 갈지 고민", lat = 25.1092, lng = 121.8443, stayMinutes = 180),
            Place(tripId = t, name = "융캉제", category = PlaceCategory.CAFE, status = CANDIDATE, openingHours = "상점마다 다름",
                memo = "망고빙수", lat = 25.0325, lng = 121.5290),
            Place(tripId = t, name = "단수이", category = PlaceCategory.SIGHT, status = CANDIDATE, openingHours = "상시",
                memo = "노을 보러", lat = 25.1696, lng = 121.4405, stayMinutes = 120)
        ).forEach { dao.insertPlace(it) }
    }
}
