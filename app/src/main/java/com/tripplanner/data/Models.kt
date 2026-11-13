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
/** 장소 종류 · 새 장소의 기본 머무는 시간(분) */
enum class PlaceCategory(val label: String, val defaultStay: Int) {
    SIGHT("관광", 90), FOOD("맛집", 60), CAFE("카페", 60), SHOPPING("쇼핑", 120), ACTIVITY("체험", 120), ETC("기타", 60)
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
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    /** 내가 만든 여행이면 true, 초대 코드로 참여한 여행이면 false — DB 5 */
    @ColumnInfo(defaultValue = "1") val ownedByMe: Boolean = true,
    /** 초대받은 여행: 만든 사람 닉네임 (모르면 빈 글자) */
    @ColumnInfo(defaultValue = "") val inviterName: String = "",
    /** 함께 보는 다른 사람들 닉네임 (", " 로 이음, 나 제외) */
    @ColumnInfo(defaultValue = "") val memberNames: String = "",
    /** 표지 사진 (앱 내부 저장소 경로, 기기에만 · 공유 안 함) · 보이는 위치(사진 높이 중 0~1) · 확대(1~3) — DB 6 */
    val coverPhoto: String? = null,
    @ColumnInfo(defaultValue = "0.5") val coverY: Float = 0.5f,
    @ColumnInfo(defaultValue = "1.0") val coverScale: Float = 1f,
    /** 경로 제안의 숙소 도착 목표 시각 (분, null = 자유) — DB 7 */
    val endMinutes: Int? = null
) {
    /** 함께 보는 다른 사람 목록 */
    val members: List<String> get() = memberNames.split(", ").filter { it.isNotBlank() }
}

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
    @ColumnInfo(defaultValue = "") val locationMemo: String = "",
    /** 관련 링크 (PlaceLinks 형식: 한 줄에 '이름\t주소') — DB 8 */
    @ColumnInfo(defaultValue = "") val links: String = "",
    /** 내가 만든 장소 종류 (place_types.id, 없으면 기본 종류 category) — DB 8 */
    val typeId: Long? = null,
    /** 검색으로 채운 장소의 Google 장소 id ('다시 가져오기'용, 모르면 빈 글자) — DB 8 */
    @ColumnInfo(defaultValue = "") val sourceId: String = ""
) {
    val hasLocation: Boolean get() = lat != 0.0 || lng != 0.0
}

/** 장소의 관련 링크 하나 */
data class PlaceLink(val name: String, val url: String)

/** 관련 링크 ↔ 저장 글자 (탭·줄바꿈은 공백으로 바꿔 저장) */
object PlaceLinks {
    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
    fun parse(raw: String): List<PlaceLink> = raw.lines().filter { it.isNotBlank() }.map {
        val i = it.indexOf('\t')
        if (i < 0) PlaceLink(it.trim(), it.trim()) else PlaceLink(it.substring(0, i), it.substring(i + 1))
    }
    fun encode(list: List<PlaceLink>): String = list.joinToString("\n") { "${clean(it.name)}\t${clean(it.url)}" }
}

/**
 * 내가 만든 장소 종류 (설정 › 장소 종류, 모든 여행에 함께 쓰임) — DB 8
 * icon = 아이콘 이름 (TypeIcons), color = 바탕색 번호 (0~7)
 */
@Entity(tableName = "place_types")
data class PlaceType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val color: Int
)

/** 자료가 붙은 곳 */
enum class AttachmentKind { PLACE, TRANSPORT, LODGING, TRIP }

/**
 * 예약·티켓 자료 (파일·사진) — DB 8. 파일은 앱 저장소에만 있고 공유하지 않음.
 * placeId: 장소에 붙은 자료면 그 장소 (공유로 장소가 다시 만들어지면 이름으로 다시 연결)
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"],
        childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val placeId: Long? = null,
    val kind: AttachmentKind,
    val name: String,
    /** 앱 내부 저장소 파일 경로 */
    val path: String,
    val mime: String,
    val size: Long,
    /** 올린 사람 닉네임 */
    val uploader: String,
    val createdAt: Long
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

/** 여행 목록 화면용: 여행 + 확정/후보 개수 */
data class TripWithCounts(
    @Embedded val trip: Trip,
    val confirmedCount: Int,
    val candidateCount: Int,
    /** 첫 교통편 종류 (카드 아이콘용, 없으면 null) */
    val firstMode: TransportMode? = null
)
