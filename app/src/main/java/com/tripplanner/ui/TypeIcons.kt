package com.tripplanner.ui

import com.tripplanner.R

/**
 * 내가 만든 장소 종류에 고를 수 있는 아이콘 120개 (Phosphor Regular, 고른 상태는 Fill) (시안 Settings '아이콘 · 120개').
 * 이름(key)은 저장·공유에 쓰고, 검색은 한글 낱말(keywords)로.
 */
object TypeIcons {
    enum class Group { PLACE, FOOD, ACTIVITY, MOVE }

    data class TypeIcon(val key: String, val res: Int, val group: Group, val keywords: String, val fill: Int)

    val ALL = listOf(
        TypeIcon("nightlight", R.drawable.ti_nightlight, Group.PLACE, "야경 밤 달", R.drawable.ti_nightlight_fill),
        TypeIcon("attractions", R.drawable.ti_attractions, Group.PLACE, "관람차 놀이공원 테마파크", R.drawable.ti_attractions_fill),
        TypeIcon("location_on", R.drawable.ti_location_on, Group.PLACE, "장소 위치 핀", R.drawable.ti_location_on_fill),
        TypeIcon("landscape", R.drawable.ti_landscape, Group.PLACE, "풍경 산 자연", R.drawable.ti_landscape_fill),
        TypeIcon("mountain_flag", R.drawable.ti_mountain_flag, Group.PLACE, "산 정상 등산", R.drawable.ti_mountain_flag_fill),
        TypeIcon("forest", R.drawable.ti_forest, Group.PLACE, "숲 나무 자연", R.drawable.ti_forest_fill),
        TypeIcon("park", R.drawable.ti_park, Group.PLACE, "공원 나무", R.drawable.ti_park_fill),
        TypeIcon("beach_access", R.drawable.ti_beach_access, Group.PLACE, "해변 바다 파라솔", R.drawable.ti_beach_access_fill),
        TypeIcon("water", R.drawable.ti_water, Group.PLACE, "호수 물 강", R.drawable.ti_water_fill),
        TypeIcon("waves", R.drawable.ti_waves, Group.PLACE, "바다 파도 해변", R.drawable.ti_waves_fill),
        TypeIcon("temple_buddhist", R.drawable.ti_temple_buddhist, Group.PLACE, "절 사찰 불교", R.drawable.ti_temple_buddhist_fill),
        TypeIcon("temple_hindu", R.drawable.ti_temple_hindu, Group.PLACE, "사원 탑", R.drawable.ti_temple_hindu_fill),
        TypeIcon("church", R.drawable.ti_church, Group.PLACE, "성당 교회", R.drawable.ti_church_fill),
        TypeIcon("mosque", R.drawable.ti_mosque, Group.PLACE, "모스크 사원", R.drawable.ti_mosque_fill),
        TypeIcon("museum", R.drawable.ti_museum, Group.PLACE, "박물관 미술관 전시", R.drawable.ti_museum_fill),
        TypeIcon("castle", R.drawable.ti_castle, Group.PLACE, "성 궁 궁전", R.drawable.ti_castle_fill),
        TypeIcon("stadium", R.drawable.ti_stadium, Group.PLACE, "경기장 스타디움 공연장", R.drawable.ti_stadium_fill),
        TypeIcon("local_library", R.drawable.ti_local_library, Group.PLACE, "도서관 책", R.drawable.ti_local_library_fill),
        TypeIcon("school", R.drawable.ti_school, Group.PLACE, "학교 대학 캠퍼스", R.drawable.ti_school_fill),
        TypeIcon("apartment", R.drawable.ti_apartment, Group.PLACE, "건물 빌딩 전망대", R.drawable.ti_apartment_fill),
        TypeIcon("house", R.drawable.ti_house, Group.PLACE, "집 마을 한옥", R.drawable.ti_house_fill),
        TypeIcon("cabin", R.drawable.ti_cabin, Group.PLACE, "산장 캠핑장", R.drawable.ti_cabin_fill),
        TypeIcon("villa", R.drawable.ti_villa, Group.PLACE, "별장 리조트", R.drawable.ti_villa_fill),
        TypeIcon("hotel", R.drawable.ti_hotel, Group.PLACE, "숙소 호텔 잠", R.drawable.ti_hotel_fill),
        TypeIcon("storefront", R.drawable.ti_storefront, Group.PLACE, "가게 상점 시장", R.drawable.ti_storefront_fill),
        TypeIcon("store", R.drawable.ti_store, Group.PLACE, "상점 매장 백화점", R.drawable.ti_store_fill),
        TypeIcon("local_mall", R.drawable.ti_local_mall, Group.PLACE, "쇼핑몰 쇼핑 가방", R.drawable.ti_local_mall_fill),
        TypeIcon("local_florist", R.drawable.ti_local_florist, Group.PLACE, "꽃 정원 식물원", R.drawable.ti_local_florist_fill),
        TypeIcon("photo_camera", R.drawable.ti_photo_camera, Group.PLACE, "사진 포토존 카메라", R.drawable.ti_photo_camera_fill),
        TypeIcon("image", R.drawable.ti_image, Group.PLACE, "사진 풍경 그림", R.drawable.ti_image_fill),
        TypeIcon("explore", R.drawable.ti_explore, Group.PLACE, "탐험 둘러보기 나침반", R.drawable.ti_explore_fill),
        TypeIcon("map", R.drawable.ti_map, Group.PLACE, "지도 산책 코스", R.drawable.ti_map_fill),
        TypeIcon("star", R.drawable.ti_star, Group.PLACE, "별 즐겨찾기 꼭 가기", R.drawable.ti_star_fill),
        TypeIcon("favorite", R.drawable.ti_favorite, Group.PLACE, "좋아요 하트 추천", R.drawable.ti_favorite_fill),
        TypeIcon("shopping_cart", R.drawable.ti_shopping_cart, Group.PLACE, "마트 장보기 편의점", R.drawable.ti_shopping_cart_fill),
        TypeIcon("shopping_bag", R.drawable.ti_shopping_bag, Group.PLACE, "쇼핑 가방 기념품", R.drawable.ti_shopping_bag_fill),
        TypeIcon("redeem", R.drawable.ti_redeem, Group.PLACE, "선물 기념품", R.drawable.ti_redeem_fill),
        TypeIcon("diamond", R.drawable.ti_diamond, Group.PLACE, "보석 명품", R.drawable.ti_diamond_fill),
        TypeIcon("checkroom", R.drawable.ti_checkroom, Group.PLACE, "옷 패션 의류", R.drawable.ti_checkroom_fill),
        TypeIcon("local_atm", R.drawable.ti_local_atm, Group.PLACE, "환전 ATM 돈", R.drawable.ti_local_atm_fill),
        TypeIcon("restaurant", R.drawable.ti_restaurant, Group.FOOD, "식당 맛집 밥", R.drawable.ti_restaurant_fill),
        TypeIcon("ramen_dining", R.drawable.ti_ramen_dining, Group.FOOD, "라멘 국수 면", R.drawable.ti_ramen_dining_fill),
        TypeIcon("lunch_dining", R.drawable.ti_lunch_dining, Group.FOOD, "버거 햄버거 점심", R.drawable.ti_lunch_dining_fill),
        TypeIcon("dinner_dining", R.drawable.ti_dinner_dining, Group.FOOD, "파스타 저녁 양식", R.drawable.ti_dinner_dining_fill),
        TypeIcon("rice_bowl", R.drawable.ti_rice_bowl, Group.FOOD, "덮밥 밥 한식", R.drawable.ti_rice_bowl_fill),
        TypeIcon("set_meal", R.drawable.ti_set_meal, Group.FOOD, "정식 생선 일식", R.drawable.ti_set_meal_fill),
        TypeIcon("bakery_dining", R.drawable.ti_bakery_dining, Group.FOOD, "빵 베이커리 크루아상", R.drawable.ti_bakery_dining_fill),
        TypeIcon("icecream", R.drawable.ti_icecream, Group.FOOD, "아이스크림 디저트", R.drawable.ti_icecream_fill),
        TypeIcon("cake", R.drawable.ti_cake, Group.FOOD, "케이크 디저트 생일", R.drawable.ti_cake_fill),
        TypeIcon("local_pizza", R.drawable.ti_local_pizza, Group.FOOD, "피자", R.drawable.ti_local_pizza_fill),
        TypeIcon("tapas", R.drawable.ti_tapas, Group.FOOD, "타파스 안주 꼬치", R.drawable.ti_tapas_fill),
        TypeIcon("brunch_dining", R.drawable.ti_brunch_dining, Group.FOOD, "브런치 아침", R.drawable.ti_brunch_dining_fill),
        TypeIcon("breakfast_dining", R.drawable.ti_breakfast_dining, Group.FOOD, "아침 토스트 조식", R.drawable.ti_breakfast_dining_fill),
        TypeIcon("kebab_dining", R.drawable.ti_kebab_dining, Group.FOOD, "케밥 꼬치 고기", R.drawable.ti_kebab_dining_fill),
        TypeIcon("egg", R.drawable.ti_egg, Group.FOOD, "달걀 계란 아침", R.drawable.ti_egg_fill),
        TypeIcon("local_cafe", R.drawable.ti_local_cafe, Group.FOOD, "카페 커피", R.drawable.ti_local_cafe_fill),
        TypeIcon("coffee", R.drawable.ti_coffee, Group.FOOD, "커피 원두 카페", R.drawable.ti_coffee_fill),
        TypeIcon("emoji_food_beverage", R.drawable.ti_emoji_food_beverage, Group.FOOD, "차 찻집 티", R.drawable.ti_emoji_food_beverage_fill),
        TypeIcon("local_drink", R.drawable.ti_local_drink, Group.FOOD, "음료 주스", R.drawable.ti_local_drink_fill),
        TypeIcon("local_bar", R.drawable.ti_local_bar, Group.FOOD, "바 칵테일 술", R.drawable.ti_local_bar_fill),
        TypeIcon("sports_bar", R.drawable.ti_sports_bar, Group.FOOD, "맥주 펍 술집", R.drawable.ti_sports_bar_fill),
        TypeIcon("wine_bar", R.drawable.ti_wine_bar, Group.FOOD, "와인 와이너리", R.drawable.ti_wine_bar_fill),
        TypeIcon("liquor", R.drawable.ti_liquor, Group.FOOD, "술 양조장 사케", R.drawable.ti_liquor_fill),
        TypeIcon("nightlife", R.drawable.ti_nightlife, Group.FOOD, "나이트 클럽 밤", R.drawable.ti_nightlife_fill),
        TypeIcon("local_activity", R.drawable.ti_local_activity, Group.ACTIVITY, "체험 티켓 액티비티", R.drawable.ti_local_activity_fill),
        TypeIcon("confirmation_number", R.drawable.ti_confirmation_number, Group.ACTIVITY, "입장권 티켓 표", R.drawable.ti_confirmation_number_fill),
        TypeIcon("hiking", R.drawable.ti_hiking, Group.ACTIVITY, "등산 하이킹 트레킹", R.drawable.ti_hiking_fill),
        TypeIcon("spa", R.drawable.ti_spa, Group.ACTIVITY, "스파 마사지 휴식", R.drawable.ti_spa_fill),
        TypeIcon("hot_tub", R.drawable.ti_hot_tub, Group.ACTIVITY, "온천 목욕 사우나", R.drawable.ti_hot_tub_fill),
        TypeIcon("pool", R.drawable.ti_pool, Group.ACTIVITY, "수영장 수영", R.drawable.ti_pool_fill),
        TypeIcon("sailing", R.drawable.ti_sailing, Group.ACTIVITY, "요트 배 세일링", R.drawable.ti_sailing_fill),
        TypeIcon("kayaking", R.drawable.ti_kayaking, Group.ACTIVITY, "카약 카누 물놀이", R.drawable.ti_kayaking_fill),
        TypeIcon("surfing", R.drawable.ti_surfing, Group.ACTIVITY, "서핑 바다", R.drawable.ti_surfing_fill),
        TypeIcon("scuba_diving", R.drawable.ti_scuba_diving, Group.ACTIVITY, "스쿠버 다이빙 스노클링", R.drawable.ti_scuba_diving_fill),
        TypeIcon("downhill_skiing", R.drawable.ti_downhill_skiing, Group.ACTIVITY, "스키 겨울", R.drawable.ti_downhill_skiing_fill),
        TypeIcon("snowboarding", R.drawable.ti_snowboarding, Group.ACTIVITY, "스노보드 겨울", R.drawable.ti_snowboarding_fill),
        TypeIcon("ice_skating", R.drawable.ti_ice_skating, Group.ACTIVITY, "스케이트 빙상", R.drawable.ti_ice_skating_fill),
        TypeIcon("golf_course", R.drawable.ti_golf_course, Group.ACTIVITY, "골프", R.drawable.ti_golf_course_fill),
        TypeIcon("sports_tennis", R.drawable.ti_sports_tennis, Group.ACTIVITY, "테니스", R.drawable.ti_sports_tennis_fill),
        TypeIcon("sports_soccer", R.drawable.ti_sports_soccer, Group.ACTIVITY, "축구", R.drawable.ti_sports_soccer_fill),
        TypeIcon("sports_baseball", R.drawable.ti_sports_baseball, Group.ACTIVITY, "야구 경기", R.drawable.ti_sports_baseball_fill),
        TypeIcon("fitness_center", R.drawable.ti_fitness_center, Group.ACTIVITY, "헬스 운동", R.drawable.ti_fitness_center_fill),
        TypeIcon("pedal_bike", R.drawable.ti_pedal_bike, Group.ACTIVITY, "자전거 라이딩", R.drawable.ti_pedal_bike_fill),
        TypeIcon("skateboarding", R.drawable.ti_skateboarding, Group.ACTIVITY, "스케이트보드", R.drawable.ti_skateboarding_fill),
        TypeIcon("paragliding", R.drawable.ti_paragliding, Group.ACTIVITY, "패러글라이딩 하늘", R.drawable.ti_paragliding_fill),
        TypeIcon("camping", R.drawable.ti_camping, Group.ACTIVITY, "캠핑 텐트", R.drawable.ti_camping_fill),
        TypeIcon("fireplace", R.drawable.ti_fireplace, Group.ACTIVITY, "모닥불 불멍", R.drawable.ti_fireplace_fill),
        TypeIcon("pets", R.drawable.ti_pets, Group.ACTIVITY, "동물원 동물 반려동물", R.drawable.ti_pets_fill),
        TypeIcon("eco", R.drawable.ti_eco, Group.ACTIVITY, "자연 생태 친환경", R.drawable.ti_eco_fill),
        TypeIcon("theater_comedy", R.drawable.ti_theater_comedy, Group.ACTIVITY, "공연 연극 뮤지컬", R.drawable.ti_theater_comedy_fill),
        TypeIcon("movie", R.drawable.ti_movie, Group.ACTIVITY, "영화 극장", R.drawable.ti_movie_fill),
        TypeIcon("music_note", R.drawable.ti_music_note, Group.ACTIVITY, "음악 콘서트 공연", R.drawable.ti_music_note_fill),
        TypeIcon("festival", R.drawable.ti_festival, Group.ACTIVITY, "축제 행사", R.drawable.ti_festival_fill),
        TypeIcon("celebration", R.drawable.ti_celebration, Group.ACTIVITY, "파티 기념일 축하", R.drawable.ti_celebration_fill),
        TypeIcon("toys", R.drawable.ti_toys, Group.ACTIVITY, "장난감 키즈", R.drawable.ti_toys_fill),
        TypeIcon("sports_esports", R.drawable.ti_sports_esports, Group.ACTIVITY, "게임 오락실", R.drawable.ti_sports_esports_fill),
        TypeIcon("palette", R.drawable.ti_palette, Group.ACTIVITY, "그림 공방 만들기", R.drawable.ti_palette_fill),
        TypeIcon("brush", R.drawable.ti_brush, Group.ACTIVITY, "공예 체험 그리기", R.drawable.ti_brush_fill),
        TypeIcon("auto_stories", R.drawable.ti_auto_stories, Group.ACTIVITY, "책 독서 북카페", R.drawable.ti_auto_stories_fill),
        TypeIcon("self_care", R.drawable.ti_self_care, Group.ACTIVITY, "뷰티 피부 관리", R.drawable.ti_self_care_fill),
        TypeIcon("event", R.drawable.ti_event, Group.ACTIVITY, "일정 예약 날짜", R.drawable.ti_event_fill),
        TypeIcon("category", R.drawable.ti_category, Group.ACTIVITY, "기타 모음", R.drawable.ti_category_fill),
        TypeIcon("flight", R.drawable.ti_flight, Group.MOVE, "비행기 공항 항공", R.drawable.ti_flight_fill),
        TypeIcon("train", R.drawable.ti_train, Group.MOVE, "기차 열차 KTX", R.drawable.ti_train_fill),
        TypeIcon("subway", R.drawable.ti_subway, Group.MOVE, "지하철 전철", R.drawable.ti_subway_fill),
        TypeIcon("tram", R.drawable.ti_tram, Group.MOVE, "트램 노면전차", R.drawable.ti_tram_fill),
        TypeIcon("directions_bus", R.drawable.ti_directions_bus, Group.MOVE, "버스", R.drawable.ti_directions_bus_fill),
        TypeIcon("airport_shuttle", R.drawable.ti_airport_shuttle, Group.MOVE, "셔틀 밴 공항버스", R.drawable.ti_airport_shuttle_fill),
        TypeIcon("directions_car", R.drawable.ti_directions_car, Group.MOVE, "자동차 렌터카 드라이브", R.drawable.ti_directions_car_fill),
        TypeIcon("local_taxi", R.drawable.ti_local_taxi, Group.MOVE, "택시", R.drawable.ti_local_taxi_fill),
        TypeIcon("two_wheeler", R.drawable.ti_two_wheeler, Group.MOVE, "오토바이 스쿠터", R.drawable.ti_two_wheeler_fill),
        TypeIcon("electric_scooter", R.drawable.ti_electric_scooter, Group.MOVE, "킥보드 전동", R.drawable.ti_electric_scooter_fill),
        TypeIcon("directions_boat", R.drawable.ti_directions_boat, Group.MOVE, "배 페리 유람선", R.drawable.ti_directions_boat_fill),
        TypeIcon("directions_walk", R.drawable.ti_directions_walk, Group.MOVE, "걷기 도보 산책", R.drawable.ti_directions_walk_fill),
        TypeIcon("route", R.drawable.ti_route, Group.MOVE, "경로 길 코스", R.drawable.ti_route_fill),
        TypeIcon("local_gas_station", R.drawable.ti_local_gas_station, Group.MOVE, "주유소", R.drawable.ti_local_gas_station_fill),
        TypeIcon("local_parking", R.drawable.ti_local_parking, Group.MOVE, "주차장", R.drawable.ti_local_parking_fill),
        TypeIcon("ev_station", R.drawable.ti_ev_station, Group.MOVE, "충전소 전기차", R.drawable.ti_ev_station_fill),
        TypeIcon("luggage", R.drawable.ti_luggage, Group.MOVE, "짐 캐리어 보관", R.drawable.ti_luggage_fill),
        TypeIcon("tour", R.drawable.ti_tour, Group.MOVE, "가이드 투어 깃발", R.drawable.ti_tour_fill),
    )

    private val byKey = ALL.associateBy { it.key }

    /** 저장된 이름 → 아이콘 (모르는 이름이면 위치 핀) */
    fun res(key: String): Int = byKey[key]?.res ?: R.drawable.ti_location_on

    /** 고른 상태(칩 선택 등)에 쓰는 채운 아이콘 (Phosphor Fill) */
    fun fillRes(key: String): Int = byKey[key]?.fill ?: R.drawable.ti_location_on_fill

    fun has(key: String) = key in byKey

    /** 검색: 낱말 일부가 맞으면 (빈 글자면 전부) */
    fun search(query: String): List<TypeIcon> {
        val q = query.trim()
        if (q.isEmpty()) return ALL
        return ALL.filter { i -> q.split(" ").filter { it.isNotBlank() }.all { w -> i.keywords.contains(w, ignoreCase = true) || i.key.contains(w, ignoreCase = true) } }
    }
}
