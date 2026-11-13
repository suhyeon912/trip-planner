package com.tripplanner.data

import androidx.lifecycle.LiveData
import androidx.room.withTransaction

/**
 * 화면(ViewModel)은 DAO 를 직접 보지 않고 이 Repository 를 통해서만 데이터를 다룹니다.
 * 공유 중인 여행이 바뀌면 changeListener 로 알려서 서버(Firebase)에 올립니다.
 */
class TripRepository(private val db: AppDatabase) {

    private val dao = db.tripDao()

    /** 여행 내용이 바뀌면 여행 id 로 알림 (공유 동기화가 듣고 있음) */
    var changeListener: ((Long) -> Unit)? = null
    private fun changed(tripId: Long) { changeListener?.invoke(tripId) }

    // 여행
    fun observeTripsWithCounts(): LiveData<List<TripWithCounts>> = dao.observeTripsWithCounts()
    fun observeTrip(id: Long): LiveData<Trip?> = dao.observeTrip(id)
    suspend fun getTrip(id: Long) = dao.getTrip(id)
    suspend fun addTrip(trip: Trip) = dao.insertTrip(trip)
    suspend fun updateTrip(trip: Trip) { dao.updateTrip(trip); changed(trip.id) }
    suspend fun deleteTrip(trip: Trip) = dao.deleteTrip(trip)

    // 비행기
    fun observeFlights(tripId: Long) = dao.observeFlights(tripId)
    suspend fun getFlights(tripId: Long) = dao.getFlights(tripId)
    suspend fun saveFlight(f: Flight) { if (f.id == 0L) dao.insertFlight(f) else dao.updateFlight(f); changed(f.tripId) }
    suspend fun deleteFlight(f: Flight) { dao.deleteFlight(f); changed(f.tripId) }

    // 숙소
    fun observeLodgings(tripId: Long) = dao.observeLodgings(tripId)
    suspend fun getLodgings(tripId: Long) = dao.getLodgings(tripId)
    suspend fun saveLodging(l: Lodging) { if (l.id == 0L) dao.insertLodging(l) else dao.updateLodging(l); changed(l.tripId) }
    suspend fun deleteLodging(l: Lodging) { dao.deleteLodging(l); changed(l.tripId) }

    // 장소
    fun observePlaces(tripId: Long) = dao.observePlaces(tripId)
    suspend fun getPlaces(tripId: Long) = dao.getPlaces(tripId)
    suspend fun getPlace(id: Long) = dao.getPlace(id)
    suspend fun savePlace(p: Place) { if (p.id == 0L) dao.insertPlace(p) else dao.updatePlace(p); changed(p.tripId) }
    suspend fun updatePlaces(list: List<Place>) { dao.updatePlaces(list); list.map { it.tripId }.distinct().forEach { changed(it) } }
    suspend fun deletePlace(p: Place) {
        // 장소에 붙은 자료(파일)도 함께 지움
        dao.getPlaceAttachments(p.id).forEach { deleteAttachmentFile(it); dao.deleteAttachment(it) }
        dao.deletePlace(p); changed(p.tripId)
    }

    // 내가 만든 장소 종류 (모든 여행 공통)
    fun observeTypes() = dao.observeTypes()
    suspend fun getTypes() = dao.getTypes()
    suspend fun saveType(t: PlaceType): Long {
        if (t.id == 0L) return dao.insertType(t)
        dao.updateType(t); dao.tripsUsingType(t.id).forEach { changed(it) }
        return t.id
    }
    suspend fun deleteType(t: PlaceType) {
        val trips = dao.tripsUsingType(t.id)
        db.withTransaction { dao.clearType(t.id); dao.deleteType(t) }
        trips.forEach { changed(it) }
    }
    /** 공유로 받은 종류: 같은 이름이 있으면 그것, 없으면 새로 만듦 → id */
    suspend fun findOrCreateType(name: String, icon: String, color: Int): Long =
        dao.getTypes().firstOrNull { it.name == name }?.id ?: dao.insertType(PlaceType(name = name, icon = icon, color = color))

    // 예약·티켓 자료 (기기에만 · 공유하지 않음 → changed 를 부르지 않음, 활동 기록은 ShareManager 가 따로 남김)
    fun observeAttachments(tripId: Long) = dao.observeAttachments(tripId)
    suspend fun getAttachments(tripId: Long) = dao.getAttachments(tripId)
    suspend fun getPlaceAttachments(placeId: Long) = dao.getPlaceAttachments(placeId)
    var attachmentListener: ((Attachment) -> Unit)? = null
    suspend fun addAttachment(a: Attachment): Long = dao.insertAttachment(a).also { attachmentListener?.invoke(a) }
    suspend fun renameAttachment(a: Attachment, name: String) = dao.updateAttachment(a.copy(name = name))
    suspend fun deleteAttachment(a: Attachment) { deleteAttachmentFile(a); dao.deleteAttachment(a) }
    private fun deleteAttachmentFile(a: Attachment) { runCatching { java.io.File(a.path).delete() } }

    // ---------- 공유 ----------
    suspend fun getSharedTrips() = dao.getSharedTrips()
    suspend fun getTripByShareCode(code: String) = dao.getTripByShareCode(code)
    /** 공유 정보만 바꿈 (서버에 다시 올리지 않음) */
    suspend fun setShareCode(trip: Trip, code: String?) = dao.updateTrip(trip.copy(shareCode = code))

    /** 멤버 정보만 바꿈 (서버에 다시 올리지 않음) */
    suspend fun setMembers(tripId: Long, inviterName: String, memberNames: String) {
        val t = dao.getTrip(tripId) ?: return
        if (t.inviterName != inviterName || t.memberNames != memberNames)
            dao.updateTrip(t.copy(inviterName = inviterName, memberNames = memberNames))
    }

    /** 서버에서 받은 여행으로 통째로 바꿈 (내 기기의 같은 여행 id 유지). 서버로 다시 올리지 않음 */
    suspend fun replaceTripContents(trip: Trip, places: List<Place>, flights: List<Flight>, lodgings: List<Lodging>) {
        db.withTransaction {
            // 장소에 붙은 자료는 장소 이름으로 다시 연결 (장소 id 가 새로 매겨지므로)
            val oldNames = dao.getPlaces(trip.id).associate { it.id to it.name }
            val files = dao.getAttachments(trip.id).filter { it.placeId != null }
            dao.updateTrip(trip)
            dao.deletePlacesOf(trip.id); dao.deleteFlightsOf(trip.id); dao.deleteLodgingsOf(trip.id)
            val newIds = places.associate { it.name to dao.insertPlace(it.copy(id = 0, tripId = trip.id)) }
            flights.forEach { dao.insertFlight(it.copy(id = 0, tripId = trip.id)) }
            lodgings.forEach { dao.insertLodging(it.copy(id = 0, tripId = trip.id)) }
            dao.updateAttachments(files.map { a -> a.copy(placeId = newIds[oldNames[a.placeId]]) })
        }
    }

    /** 초대받은 여행을 새로 만들기 → 새 여행 id */
    suspend fun insertSharedTrip(trip: Trip, places: List<Place>, flights: List<Flight>, lodgings: List<Lodging>): Long =
        db.withTransaction {
            val id = dao.insertTrip(trip.copy(id = 0))
            places.forEach { dao.insertPlace(it.copy(id = 0, tripId = id)) }
            flights.forEach { dao.insertFlight(it.copy(id = 0, tripId = id)) }
            lodgings.forEach { dao.insertLodging(it.copy(id = 0, tripId = id)) }
            id
        }

    /** 처음 실행했을 때만 예시 여행(부산·도쿄·타이베이)을 넣어 둡니다 */
    /** 처음 실행이면 예시 여행 넣기. cover: 예시 표지 그림을 앱 사진 폴더로 복사 → 경로 */
    suspend fun seedIfEmpty(cover: (String) -> String? = { null }) {
        if (dao.countTrips() > 0) return
        db.withTransaction {
            SampleData.insertAll(dao, cover)
        }
    }
}
