package com.tripplanner.ui

import com.tripplanner.R

/**
 * 내가 만든 장소 종류에 고를 수 있는 아이콘 120개 (시안 Settings '아이콘 · 120개').
 * 이름(key)은 저장·공유에 쓰고, 검색은 한글 낱말(keywords)로.
 */
object TypeIcons {
    enum class Group { PLACE, FOOD, ACTIVITY, MOVE }

    data class TypeIcon(val key: String, val res: Int, val group: Group, val keywords: String)

    val ALL = listOf(
        TypeIcon("nightlight", R.drawable.ti_nightlight, Group.PLACE, "야경 밤 달"),
        TypeIcon("attractions", R.drawable.ti_attractions, Group.PLACE, "관람차 놀이공원 테마파크"),
        TypeIcon("location_on", R.drawable.ti_location_on, Group.PLACE, "장소 위치 핀"),
        TypeIcon("landscape", R.drawable.ti_landscape, Group.PLACE, "풍경 산 자연"),
        TypeIcon("mountain_flag", R.drawable.ti_mountain_flag, Group.PLACE, "산 정상 등산"),
        TypeIcon("forest", R.drawable.ti_forest, Group.PLACE, "숲 나무 자연"),
        TypeIcon("park", R.drawable.ti_park, Group.PLACE, "공원 나무"),
        TypeIcon("beach_access", R.drawable.ti_beach_access, Group.PLACE, "해변 바다 파라솔"),
        TypeIcon("water", R.drawable.ti_water, Group.PLACE, "호수 물 강"),
        TypeIcon("waves", R.drawable.ti_waves, Group.PLACE, "바다 파도 해변"),
        TypeIcon("temple_buddhist", R.drawable.ti_temple_buddhist, Group.PLACE, "절 사찰 불교"),
        TypeIcon("temple_hindu", R.drawable.ti_temple_hindu, Group.PLACE, "사원 탑"),
        TypeIcon("church", R.drawable.ti_church, Group.PLACE, "성당 교회"),
        TypeIcon("mosque", R.drawable.ti_mosque, Group.PLACE, "모스크 사원"),
        TypeIcon("museum", R.drawable.ti_museum, Group.PLACE, "박물관 미술관 전시"),
        TypeIcon("castle", R.drawable.ti_castle, Group.PLACE, "성 궁 궁전"),
        TypeIcon("stadium", R.drawable.ti_stadium, Group.PLACE, "경기장 스타디움 공연장"),
        TypeIcon("local_library", R.drawable.ti_local_library, Group.PLACE, "도서관 책"),
        TypeIcon("school", R.drawable.ti_school, Group.PLACE, "학교 대학 캠퍼스"),
        TypeIcon("apartment", R.drawable.ti_apartment, Group.PLACE, "건물 빌딩 전망대"),
        TypeIcon("house", R.drawable.ti_house, Group.PLACE, "집 마을 한옥"),
        TypeIcon("cabin", R.drawable.ti_cabin, Group.PLACE, "산장 캠핑장"),
        TypeIcon("villa", R.drawable.ti_villa, Group.PLACE, "별장 리조트"),
        TypeIcon("hotel", R.drawable.ti_hotel, Group.PLACE, "숙소 호텔 잠"),
        TypeIcon("storefront", R.drawable.ti_storefront, Group.PLACE, "가게 상점 시장"),
        TypeIcon("store", R.drawable.ti_store, Group.PLACE, "상점 매장 백화점"),
        TypeIcon("local_mall", R.drawable.ti_local_mall, Group.PLACE, "쇼핑몰 쇼핑 가방"),
        TypeIcon("local_florist", R.drawable.ti_local_florist, Group.PLACE, "꽃 정원 식물원"),
        TypeIcon("photo_camera", R.drawable.ti_photo_camera, Group.PLACE, "사진 포토존 카메라"),
        TypeIcon("image", R.drawable.ti_image, Group.PLACE, "사진 풍경 그림"),
        TypeIcon("explore", R.drawable.ti_explore, Group.PLACE, "탐험 둘러보기 나침반"),
        TypeIcon("map", R.drawable.ti_map, Group.PLACE, "지도 산책 코스"),
        TypeIcon("star", R.drawable.ti_star, Group.PLACE, "별 즐겨찾기 꼭 가기"),
        TypeIcon("favorite", R.drawable.ti_favorite, Group.PLACE, "좋아요 하트 추천"),
        TypeIcon("shopping_cart", R.drawable.ti_shopping_cart, Group.PLACE, "마트 장보기 편의점"),
        TypeIcon("shopping_bag", R.drawable.ti_shopping_bag, Group.PLACE, "쇼핑 가방 기념품"),
        TypeIcon("redeem", R.drawable.ti_redeem, Group.PLACE, "선물 기념품"),
        TypeIcon("diamond", R.drawable.ti_diamond, Group.PLACE, "보석 명품"),
        TypeIcon("checkroom", R.drawable.ti_checkroom, Group.PLACE, "옷 패션 의류"),
        TypeIcon("local_atm", R.drawable.ti_local_atm, Group.PLACE, "환전 ATM 돈"),
        TypeIcon("restaurant", R.drawable.ti_restaurant, Group.FOOD, "식당 맛집 밥"),
        TypeIcon("ramen_dining", R.drawable.ti_ramen_dining, Group.FOOD, "라멘 국수 면"),
        TypeIcon("lunch_dining", R.drawable.ti_lunch_dining, Group.FOOD, "버거 햄버거 점심"),
        TypeIcon("dinner_dining", R.drawable.ti_dinner_dining, Group.FOOD, "파스타 저녁 양식"),
        TypeIcon("rice_bowl", R.drawable.ti_rice_bowl, Group.FOOD, "덮밥 밥 한식"),
        TypeIcon("set_meal", R.drawable.ti_set_meal, Group.FOOD, "정식 생선 일식"),
        TypeIcon("bakery_dining", R.drawable.ti_bakery_dining, Group.FOOD, "빵 베이커리 크루아상"),
        TypeIcon("icecream", R.drawable.ti_icecream, Group.FOOD, "아이스크림 디저트"),
        TypeIcon("cake", R.drawable.ti_cake, Group.FOOD, "케이크 디저트 생일"),
        TypeIcon("local_pizza", R.drawable.ti_local_pizza, Group.FOOD, "피자"),
        TypeIcon("tapas", R.drawable.ti_tapas, Group.FOOD, "타파스 안주 꼬치"),
        TypeIcon("brunch_dining", R.drawable.ti_brunch_dining, Group.FOOD, "브런치 아침"),
        TypeIcon("breakfast_dining", R.drawable.ti_breakfast_dining, Group.FOOD, "아침 토스트 조식"),
        TypeIcon("kebab_dining", R.drawable.ti_kebab_dining, Group.FOOD, "케밥 꼬치 고기"),
        TypeIcon("egg", R.drawable.ti_egg, Group.FOOD, "달걀 계란 아침"),
        TypeIcon("local_cafe", R.drawable.ti_local_cafe, Group.FOOD, "카페 커피"),
        TypeIcon("coffee", R.drawable.ti_coffee, Group.FOOD, "커피 원두 카페"),
        TypeIcon("emoji_food_beverage", R.drawable.ti_emoji_food_beverage, Group.FOOD, "차 찻집 티"),
        TypeIcon("local_drink", R.drawable.ti_local_drink, Group.FOOD, "음료 주스"),
        TypeIcon("local_bar", R.drawable.ti_local_bar, Group.FOOD, "바 칵테일 술"),
        TypeIcon("sports_bar", R.drawable.ti_sports_bar, Group.FOOD, "맥주 펍 술집"),
        TypeIcon("wine_bar", R.drawable.ti_wine_bar, Group.FOOD, "와인 와이너리"),
        TypeIcon("liquor", R.drawable.ti_liquor, Group.FOOD, "술 양조장 사케"),
        TypeIcon("nightlife", R.drawable.ti_nightlife, Group.FOOD, "나이트 클럽 밤"),
        TypeIcon("local_activity", R.drawable.ti_local_activity, Group.ACTIVITY, "체험 티켓 액티비티"),
        TypeIcon("confirmation_number", R.drawable.ti_confirmation_number, Group.ACTIVITY, "입장권 티켓 표"),
        TypeIcon("hiking", R.drawable.ti_hiking, Group.ACTIVITY, "등산 하이킹 트레킹"),
        TypeIcon("spa", R.drawable.ti_spa, Group.ACTIVITY, "스파 마사지 휴식"),
        TypeIcon("hot_tub", R.drawable.ti_hot_tub, Group.ACTIVITY, "온천 목욕 사우나"),
        TypeIcon("pool", R.drawable.ti_pool, Group.ACTIVITY, "수영장 수영"),
        TypeIcon("sailing", R.drawable.ti_sailing, Group.ACTIVITY, "요트 배 세일링"),
        TypeIcon("kayaking", R.drawable.ti_kayaking, Group.ACTIVITY, "카약 카누 물놀이"),
        TypeIcon("surfing", R.drawable.ti_surfing, Group.ACTIVITY, "서핑 바다"),
        TypeIcon("scuba_diving", R.drawable.ti_scuba_diving, Group.ACTIVITY, "스쿠버 다이빙 스노클링"),
        TypeIcon("downhill_skiing", R.drawable.ti_downhill_skiing, Group.ACTIVITY, "스키 겨울"),
        TypeIcon("snowboarding", R.drawable.ti_snowboarding, Group.ACTIVITY, "스노보드 겨울"),
        TypeIcon("ice_skating", R.drawable.ti_ice_skating, Group.ACTIVITY, "스케이트 빙상"),
        TypeIcon("golf_course", R.drawable.ti_golf_course, Group.ACTIVITY, "골프"),
        TypeIcon("sports_tennis", R.drawable.ti_sports_tennis, Group.ACTIVITY, "테니스"),
        TypeIcon("sports_soccer", R.drawable.ti_sports_soccer, Group.ACTIVITY, "축구"),
        TypeIcon("sports_baseball", R.drawable.ti_sports_baseball, Group.ACTIVITY, "야구 경기"),
        TypeIcon("fitness_center", R.drawable.ti_fitness_center, Group.ACTIVITY, "헬스 운동"),
        TypeIcon("pedal_bike", R.drawable.ti_pedal_bike, Group.ACTIVITY, "자전거 라이딩"),
        TypeIcon("skateboarding", R.drawable.ti_skateboarding, Group.ACTIVITY, "스케이트보드"),
        TypeIcon("paragliding", R.drawable.ti_paragliding, Group.ACTIVITY, "패러글라이딩 하늘"),
        TypeIcon("camping", R.drawable.ti_camping, Group.ACTIVITY, "캠핑 텐트"),
        TypeIcon("fireplace", R.drawable.ti_fireplace, Group.ACTIVITY, "모닥불 불멍"),
        TypeIcon("pets", R.drawable.ti_pets, Group.ACTIVITY, "동물원 동물 반려동물"),
        TypeIcon("eco", R.drawable.ti_eco, Group.ACTIVITY, "자연 생태 친환경"),
        TypeIcon("theater_comedy", R.drawable.ti_theater_comedy, Group.ACTIVITY, "공연 연극 뮤지컬"),
        TypeIcon("movie", R.drawable.ti_movie, Group.ACTIVITY, "영화 극장"),
        TypeIcon("music_note", R.drawable.ti_music_note, Group.ACTIVITY, "음악 콘서트 공연"),
        TypeIcon("festival", R.drawable.ti_festival, Group.ACTIVITY, "축제 행사"),
        TypeIcon("celebration", R.drawable.ti_celebration, Group.ACTIVITY, "파티 기념일 축하"),
        TypeIcon("toys", R.drawable.ti_toys, Group.ACTIVITY, "장난감 키즈"),
        TypeIcon("sports_esports", R.drawable.ti_sports_esports, Group.ACTIVITY, "게임 오락실"),
        TypeIcon("palette", R.drawable.ti_palette, Group.ACTIVITY, "그림 공방 만들기"),
        TypeIcon("brush", R.drawable.ti_brush, Group.ACTIVITY, "공예 체험 그리기"),
        TypeIcon("auto_stories", R.drawable.ti_auto_stories, Group.ACTIVITY, "책 독서 북카페"),
        TypeIcon("self_care", R.drawable.ti_self_care, Group.ACTIVITY, "뷰티 피부 관리"),
        TypeIcon("event", R.drawable.ti_event, Group.ACTIVITY, "일정 예약 날짜"),
        TypeIcon("category", R.drawable.ti_category, Group.ACTIVITY, "기타 모음"),
        TypeIcon("flight", R.drawable.ti_flight, Group.MOVE, "비행기 공항 항공"),
        TypeIcon("train", R.drawable.ti_train, Group.MOVE, "기차 열차 KTX"),
        TypeIcon("subway", R.drawable.ti_subway, Group.MOVE, "지하철 전철"),
        TypeIcon("tram", R.drawable.ti_tram, Group.MOVE, "트램 노면전차"),
        TypeIcon("directions_bus", R.drawable.ti_directions_bus, Group.MOVE, "버스"),
        TypeIcon("airport_shuttle", R.drawable.ti_airport_shuttle, Group.MOVE, "셔틀 밴 공항버스"),
        TypeIcon("directions_car", R.drawable.ti_directions_car, Group.MOVE, "자동차 렌터카 드라이브"),
        TypeIcon("local_taxi", R.drawable.ti_local_taxi, Group.MOVE, "택시"),
        TypeIcon("two_wheeler", R.drawable.ti_two_wheeler, Group.MOVE, "오토바이 스쿠터"),
        TypeIcon("electric_scooter", R.drawable.ti_electric_scooter, Group.MOVE, "킥보드 전동"),
        TypeIcon("directions_boat", R.drawable.ti_directions_boat, Group.MOVE, "배 페리 유람선"),
        TypeIcon("directions_walk", R.drawable.ti_directions_walk, Group.MOVE, "걷기 도보 산책"),
        TypeIcon("route", R.drawable.ti_route, Group.MOVE, "경로 길 코스"),
        TypeIcon("local_gas_station", R.drawable.ti_local_gas_station, Group.MOVE, "주유소"),
        TypeIcon("local_parking", R.drawable.ti_local_parking, Group.MOVE, "주차장"),
        TypeIcon("ev_station", R.drawable.ti_ev_station, Group.MOVE, "충전소 전기차"),
        TypeIcon("luggage", R.drawable.ti_luggage, Group.MOVE, "짐 캐리어 보관"),
        TypeIcon("tour", R.drawable.ti_tour, Group.MOVE, "가이드 투어 깃발"),
    )

    private val byKey = ALL.associateBy { it.key }

    /** 저장된 이름 → 아이콘 (모르는 이름이면 위치 핀) */
    fun res(key: String): Int = byKey[key]?.res ?: R.drawable.ti_location_on

    fun has(key: String) = key in byKey

    /** 검색: 낱말 일부가 맞으면 (빈 글자면 전부) */
    fun search(query: String): List<TypeIcon> {
        val q = query.trim()
        if (q.isEmpty()) return ALL
        return ALL.filter { i -> q.split(" ").filter { it.isNotBlank() }.all { w -> i.keywords.contains(w, ignoreCase = true) || i.key.contains(w, ignoreCase = true) } }
    }
}
