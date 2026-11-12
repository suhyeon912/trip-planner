package com.tripplanner.data

import com.tripplanner.data.PlaceCategory.ACTIVITY
import com.tripplanner.data.PlaceCategory.CAFE
import com.tripplanner.data.PlaceCategory.FOOD
import com.tripplanner.data.PlaceCategory.SHOPPING
import com.tripplanner.data.PlaceCategory.SIGHT
import com.tripplanner.data.PlaceStatus.CANDIDATE
import com.tripplanner.data.PlaceStatus.CONFIRMED

/**
 * 처음 실행할 때 넣는 예시 여행 4개 (디자인 시안의 화면과 같은 내용).
 * 운영시간·편명 등은 화면 확인용이라 실제와 다를 수 있습니다.
 *
 * @param cover 예시 표지 그림(assets/covers/…)을 앱 사진 폴더로 복사하고 경로를 돌려줌 (실패하면 null)
 */
object SampleData {

    suspend fun insertAll(dao: TripDao, cover: (String) -> String? = { null }) {
        tokyo(dao, cover("covers/cover_tokyo.jpg"))
        taipei(dao, cover("covers/cover_taipei.jpg"))
        busan(dao, cover("covers/cover_busan.jpg"))
        osaka(dao)
    }

    /** 도쿄 3박 4일 — 내가 만든 여행 · 확정 9곳(1일차 5곳, 위치 없는 곳 1) · 후보 3곳 */
    private suspend fun tokyo(dao: TripDao, coverPath: String?) {
        val t = dao.insertTrip(Trip(title = "도쿄 3박 4일", startDate = "2026-11-20", endDate = "2026-11-23",
            note = "· 스카이트리 야경 보기\n· 하코네 당일치기 할지 고민", coverPhoto = coverPath))
        dao.insertFlight(Flight(tripId = t, flightNo = "KE2707", fromAirport = "김포", toAirport = "하네다",
            departAt = "2026-11-20 08:30", arriveAt = "2026-11-20 10:50"))
        dao.insertFlight(Flight(tripId = t, flightNo = "KE2708", fromAirport = "하네다", toAirport = "김포",
            departAt = "2026-11-23 12:40", arriveAt = "2026-11-23 15:20"))
        dao.insertLodging(Lodging(tripId = t, name = "호텔 그레이스리 아사쿠사", checkIn = "2026-11-20",
            checkOut = "2026-11-23", lat = 35.7110, lng = 139.7963, address = "도쿄도 다이토구 아사쿠사"))
        listOf(
            // 1일차 (시안 순서: 스카이트리 → 츠지한 → 긴자 식스 → 블루보틀 → 쓰키지)
            Place(tripId = t, name = "도쿄 스카이트리", category = SIGHT, status = CONFIRMED, openingHours = "10:00 ~ 21:00",
                isReservationRequired = true, memo = "전망대 티켓 온라인 예매", lat = 35.7101, lng = 139.8107,
                day = 1, orderInDay = 1, stayMinutes = 90),
            Place(tripId = t, name = "츠지한 니혼바시", category = FOOD, status = CONFIRMED, openingHours = "11:00 ~ 21:00",
                breakTime = "15:00 ~ 17:00", isReservationRequired = true, memo = "카이센동 · 줄 서기 전 도착",
                lat = 35.6820, lng = 139.7745, day = 1, orderInDay = 2, stayMinutes = 60,
                address = "도쿄도 주오구 니혼바시", addressDetail = "2층", locationMemo = "니혼바시역 B6 출구 바로 앞"),
            Place(tripId = t, name = "긴자 식스", category = SHOPPING, status = CONFIRMED, openingHours = "11:00 ~ 21:00",
                lat = 35.6696, lng = 139.7640, day = 1, orderInDay = 3, stayMinutes = 120),
            Place(tripId = t, name = "블루보틀 기요스미", category = CAFE, status = CONFIRMED, openingHours = "08:00 ~ 19:00",
                lat = 35.6806, lng = 139.8015, day = 1, orderInDay = 4, stayMinutes = 60),
            Place(tripId = t, name = "쓰키지 장외시장", category = FOOD, status = CONFIRMED, openingHours = "05:00 ~ 14:00",
                day = 1, orderInDay = 5, stayMinutes = 60),
            // 2 ~ 4일차
            Place(tripId = t, name = "메이지 신궁", category = SIGHT, status = CONFIRMED, openingHours = "06:00 ~ 16:30",
                lat = 35.6764, lng = 139.6993, day = 2, orderInDay = 1, stayMinutes = 90),
            Place(tripId = t, name = "신주쿠 교엔", category = SIGHT, status = CONFIRMED, openingHours = "09:00 ~ 16:00",
                closedDays = "월요일", lat = 35.6852, lng = 139.7101, day = 2, orderInDay = 2, stayMinutes = 90),
            Place(tripId = t, name = "도쿄 타워", category = SIGHT, status = CONFIRMED, openingHours = "09:00 ~ 22:30",
                isReservationRequired = true, memo = "메인 데크 티켓", lat = 35.6586, lng = 139.7454,
                day = 3, orderInDay = 1, stayMinutes = 90),
            Place(tripId = t, name = "우에노 공원", category = SIGHT, status = CONFIRMED, openingHours = "05:00 ~ 23:00",
                lat = 35.7156, lng = 139.7745, day = 4, orderInDay = 1, stayMinutes = 60),
            // 후보
            Place(tripId = t, name = "센소지", category = SIGHT, status = CANDIDATE, openingHours = "06:00 ~ 17:00",
                memo = "가미나리몬 앞에서 사진", lat = 35.7148, lng = 139.7967, stayMinutes = 90),
            Place(tripId = t, name = "시부야 스카이", category = SIGHT, status = CANDIDATE, openingHours = "10:00 ~ 22:30",
                isReservationRequired = true, memo = "해 질 무렵 전망대", lat = 35.6585, lng = 139.7022, stayMinutes = 90),
            Place(tripId = t, name = "도쿄 디즈니랜드", category = ACTIVITY, status = CANDIDATE, openingHours = "09:00 ~ 21:00",
                memo = "입장권 미리 사야 함", lat = 35.6329, lng = 139.8804, stayMinutes = 480)
        ).forEach { dao.insertPlace(it) }
    }

    /** 타이베이 2박 3일 — 민지님이 초대한 여행 · 확정 2곳 · 후보 7곳 */
    private suspend fun taipei(dao: TripDao, coverPath: String?) {
        val t = dao.insertTrip(Trip(title = "타이베이 2박 3일", startDate = "2026-12-25", endDate = "2026-12-27",
            note = "· 이지카드 충전\n· 펑리수 선물 사기", ownedByMe = false, inviterName = "민지",
            coverPhoto = coverPath))
        dao.insertFlight(Flight(tripId = t, flightNo = "CI161", fromAirport = "인천", toAirport = "타오위안",
            departAt = "2026-12-25 08:30", arriveAt = "2026-12-25 10:10"))
        dao.insertFlight(Flight(tripId = t, flightNo = "CI160", fromAirport = "타오위안", toAirport = "인천",
            departAt = "2026-12-27 11:40", arriveAt = "2026-12-27 15:05"))
        dao.insertLodging(Lodging(tripId = t, name = "시먼딩 예시 호텔", checkIn = "2026-12-25",
            checkOut = "2026-12-27", lat = 25.0422, lng = 121.5079))
        listOf(
            Place(tripId = t, name = "타이베이 101", category = SIGHT, status = CONFIRMED, openingHours = "10:00 ~ 21:00",
                memo = "전망대는 해 질 무렵에", lat = 25.0340, lng = 121.5645, day = 1, orderInDay = 1, stayMinutes = 90),
            Place(tripId = t, name = "국립고궁박물원", category = SIGHT, status = CONFIRMED, openingHours = "09:00 ~ 17:00",
                closedDays = "월요일", memo = "오디오 가이드 빌리기", lat = 25.1024, lng = 121.5485,
                day = 2, orderInDay = 1, stayMinutes = 150),
            Place(tripId = t, name = "스린 야시장", category = FOOD, status = CANDIDATE, openingHours = "16:00 ~ 24:00",
                memo = "지파이 · 버블티", lat = 25.0880, lng = 121.5241, stayMinutes = 90),
            Place(tripId = t, name = "딘타이펑 신이점", category = FOOD, status = CANDIDATE, openingHours = "11:00 ~ 21:00",
                memo = "번호표 앱으로 받기", lat = 25.0336, lng = 121.5655),
            Place(tripId = t, name = "지우펀", category = SIGHT, status = CANDIDATE, openingHours = "상점마다 다름",
                memo = "예스·진과스 투어로 갈지 고민", lat = 25.1092, lng = 121.8443, stayMinutes = 180),
            Place(tripId = t, name = "융캉제", category = CAFE, status = CANDIDATE, openingHours = "상점마다 다름",
                memo = "망고빙수", lat = 25.0325, lng = 121.5290),
            Place(tripId = t, name = "단수이", category = SIGHT, status = CANDIDATE, openingHours = "상시",
                memo = "노을 보러", lat = 25.1696, lng = 121.4405, stayMinutes = 120),
            Place(tripId = t, name = "시먼딩", category = SHOPPING, status = CANDIDATE, openingHours = "상시",
                lat = 25.0421, lng = 121.5068, stayMinutes = 90),
            Place(tripId = t, name = "베이터우 온천", category = ACTIVITY, status = CANDIDATE, openingHours = "09:00 ~ 17:00",
                closedDays = "월요일", lat = 25.1366, lng = 121.5070, stayMinutes = 120)
        ).forEach { dao.insertPlace(it) }
    }

    /** 부산 2박 3일 — 국내 예시(지도 버튼이 카카오맵으로 열림) · 확정 5곳 · 후보 2곳 */
    private suspend fun busan(dao: TripDao, coverPath: String?) {
        val t = dao.insertTrip(Trip(title = "부산 2박 3일", startDate = "2027-02-12", endDate = "2027-02-14",
            note = "· KTX 왕복 예매\n· 밀면 먹기\n· 광안대교 야경", coverPhoto = coverPath))
        dao.insertLodging(Lodging(tripId = t, name = "서면 예시 호텔", checkIn = "2027-02-12",
            checkOut = "2027-02-14", lat = 35.1557, lng = 129.0590))
        dao.insertFlight(Flight(tripId = t, flightNo = "KTX 101", fromAirport = "서울역", toAirport = "부산역",
            departAt = "2027-02-12 07:00", arriveAt = "2027-02-12 09:40", mode = TransportMode.TRAIN))
        dao.insertFlight(Flight(tripId = t, flightNo = "KTX 170", fromAirport = "부산역", toAirport = "서울역",
            departAt = "2027-02-14 18:30", arriveAt = "2027-02-14 21:10", mode = TransportMode.TRAIN))
        listOf(
            Place(tripId = t, name = "감천문화마을", category = SIGHT, status = CONFIRMED, openingHours = "09:00 ~ 18:00",
                memo = "어린왕자 포토존", lat = 35.0975, lng = 129.0106, day = 1, stayMinutes = 90),
            Place(tripId = t, name = "자갈치시장", category = SHOPPING, status = CONFIRMED, openingHours = "05:00 ~ 22:00",
                memo = "회 먹기", lat = 35.0967, lng = 129.0306, day = 1, stayMinutes = 60),
            Place(tripId = t, name = "예시 돼지국밥집", category = FOOD, status = CONFIRMED, openingHours = "10:00 ~ 21:00",
                breakTime = "11:30 ~ 14:00", closedDays = "월요일", memo = "점심 피크 피하기",
                lat = 35.1577, lng = 129.0596, day = 1, stayMinutes = 50),
            Place(tripId = t, name = "해운대해수욕장", category = SIGHT, status = CONFIRMED, openingHours = "상시",
                memo = "아침 산책", lat = 35.1587, lng = 129.1604, day = 2, stayMinutes = 90),
            Place(tripId = t, name = "예시 광안리 횟집", category = FOOD, status = CONFIRMED, openingHours = "17:00 ~ 24:00",
                isReservationRequired = true, memo = "광안대교 보이는 창가 자리 예약",
                lat = 35.1532, lng = 129.1187, day = 2, stayMinutes = 90),
            Place(tripId = t, name = "해운대 블루라인파크", category = ACTIVITY, status = CANDIDATE, openingHours = "09:30 ~ 18:00",
                isReservationRequired = true, memo = "스카이캡슐은 예약 필수", lat = 35.1600, lng = 129.1730),
            Place(tripId = t, name = "흰여울문화마을", category = SIGHT, status = CANDIDATE, openingHours = "상시",
                memo = "바다 보이는 카페", lat = 35.0782, lng = 129.0453)
        ).forEach { dao.insertPlace(it) }
    }

    /** 오사카 3박 4일 — 이미 다녀온 여행 (메인의 '지난 여행'에 보임) */
    private suspend fun osaka(dao: TripDao) {
        val t = dao.insertTrip(Trip(title = "오사카 3박 4일", startDate = "2026-08-14", endDate = "2026-08-17",
            note = "· 타코야키 먹기\n· 교토 당일치기"))
        dao.insertFlight(Flight(tripId = t, flightNo = "KE721", fromAirport = "김포", toAirport = "간사이",
            departAt = "2026-08-14 09:00", arriveAt = "2026-08-14 10:45"))
        dao.insertFlight(Flight(tripId = t, flightNo = "KE722", fromAirport = "간사이", toAirport = "김포",
            departAt = "2026-08-17 12:00", arriveAt = "2026-08-17 13:55"))
        dao.insertLodging(Lodging(tripId = t, name = "난바 예시 호텔", checkIn = "2026-08-14",
            checkOut = "2026-08-17", lat = 34.6662, lng = 135.5008))
        listOf(
            Place(tripId = t, name = "오사카성", category = SIGHT, status = CONFIRMED, openingHours = "09:00 ~ 17:00",
                lat = 34.6873, lng = 135.5262, day = 1, orderInDay = 1, stayMinutes = 90),
            Place(tripId = t, name = "도톤보리", category = FOOD, status = CONFIRMED, openingHours = "상시",
                memo = "타코야키", lat = 34.6687, lng = 135.5013, day = 1, orderInDay = 2, stayMinutes = 90),
            Place(tripId = t, name = "유니버설 스튜디오 재팬", category = ACTIVITY, status = CONFIRMED, openingHours = "09:00 ~ 21:00",
                isReservationRequired = true, lat = 34.6654, lng = 135.4323, day = 2, orderInDay = 1, stayMinutes = 480),
            Place(tripId = t, name = "후시미 이나리 신사", category = SIGHT, status = CONFIRMED, openingHours = "상시",
                lat = 34.9671, lng = 135.7727, day = 3, orderInDay = 1, stayMinutes = 120),
            Place(tripId = t, name = "구로몬 시장", category = FOOD, status = CANDIDATE, openingHours = "09:00 ~ 18:00",
                lat = 34.6655, lng = 135.5066)
        ).forEach { dao.insertPlace(it) }
    }
}
