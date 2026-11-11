package com.tripplanner.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room 테이블(엔티티) 정의 — 9주차.
 * 여행 하나(Trip) 아래에 비행기(Flight) · 숙소(Lodging) · 장소(Place)가 tripId 로 연결됩니다.
 * 여행을 지우면 CASCADE 로 딸린 데이터도 함께 지워집니다.
 */

/** 장소 상태: 후보(아이디어 단계) / 확정(일정에 배정됨) */
enum class PlaceStatus(val label: String) {
    CANDIDATE("후보"),
    CONFIRMED("확정")
}

/** 장소 종류 (아이콘·색 구분용) */
enum class PlaceCategory(val label: String) {
    SIGHT("관광"), FOOD("맛집"), CAFE("카페"), SHOPPING("쇼핑"), ACTIVITY("체험"), ETC("기타")
}

/** 교통편 종류 — 비행기 외에 KTX·버스 같은 국내 교통도 같은 표에 저장 */
enum class TransportMode(val label: String) {
    FLIGHT("비행기"), TRAIN("기차·KTX"), BUS("버스"), SHIP("배"), CAR("렌터카·차"), ETC("기타")
}

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDate: String,      // yyyy-MM-dd
    val endDate: String,
    val note: String = "",      // 하고 싶은 거 메모
    /** 경로 제안의 하루 출발 시각 (분, 기본 10:00) */
    @ColumnInfo(defaultValue = "600") val startMinutes: Int = 600,
    /** 공유 중이면 초대 코드 (= 서버 문서 id), 아니면 null */
    val shareCode: String? = null,
    /** 마지막으로 바뀐 시각 (공유 동기화용, 밀리초) */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0
)

@Entity(
    tableName = "flights",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"],
        childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class Flight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val flightNo: String,       // 편명·열차 번호 (예: KE721, KTX 101)
    val fromAirport: String,    // 출발지 (공항 코드 또는 역 이름)
    val toAirport: String,      // 도착지
    val departAt: String,       // 출발 시각 (예: 2026-12-20 09:00)
    val arriveAt: String,       // 도착 시각
    /** 교통 종류 (기존 데이터는 비행기) */
    @ColumnInfo(defaultValue = "FLIGHT") val mode: TransportMode = TransportMode.FLIGHT
)

@Entity(
    tableName = "lodgings",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"],
        childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class Lodging(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val checkIn: String,        // yyyy-MM-dd
    val checkOut: String,
    val lat: Double,            // 경로 계산의 출발점
    val lng: Double,
    /** 주소 (검색 결과 또는 직접 입력) · 상세 주소 · 위치 메모 — DB 4 */
    @ColumnInfo(defaultValue = "") val address: String = "",
    @ColumnInfo(defaultValue = "") val addressDetail: String = "",
    @ColumnInfo(defaultValue = "") val locationMemo: String = ""
)

@Entity(
    tableName = "places",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"],
        childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val status: PlaceStatus,
    val openingHours: String,           // 운영시간 (예: 11:00 ~ 21:00)
    val breakTime: String? = null,      // 브레이크 타임 (없으면 null)
    val closedDays: String? = null,     // 휴무일 (예: 화요일)
    val isReservationRequired: Boolean = false,
    val memo: String = "",
    val lat: Double = 0.0,              // 위도·경도 0 이면 '위치 모름' → 경로 계산에서 제외
    val lng: Double = 0.0,
    val day: Int? = null,               // 확정된 경우 며칠째 (1일차, 2일차…)
    val orderInDay: Int? = null,        // 그날 방문 순서 (경로 확정 시 저장)
    val stayMinutes: Int = 60,          // 머무는 시간(분) — 경로 시간 계산용
    @ColumnInfo(defaultValue = "SIGHT") val category: PlaceCategory = PlaceCategory.SIGHT,
    /** 장소 사진 (앱 내부 저장소의 파일 경로), 없으면 null */
    val photoUri: String? = null,
    /** 주소 (검색 결과 또는 직접 입력) · 상세 주소(예: 2층) · 위치 메모(예: B6 출구 앞) — DB 4 */
    @ColumnInfo(defaultValue = "") val address: String = "",
    @ColumnInfo(defaultValue = "") val addressDetail: String = "",
    @ColumnInfo(defaultValue = "") val locationMemo: String = ""
) {
    val hasLocation: Boolean get() = lat != 0.0 || lng != 0.0
}

/** 여행 목록 화면용: 여행 + 확정/후보 개수 */
data class TripWithCounts(
    @Embedded val trip: Trip,
    val confirmedCount: Int,
    val candidateCount: Int,
    /** 첫 교통편 종류 (카드 아이콘용, 없으면 null) */
    val firstMode: TransportMode? = null
)
