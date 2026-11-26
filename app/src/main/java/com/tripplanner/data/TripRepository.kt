package com.tripplanner.data

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import kotlinx.coroutines.launch

/**
 * 화면(ViewModel)은 DAO 를 직접 보지 않고 이 Repository 를 통해서만 데이터를 다룹니다.
 * 공유 중인 여행이 바뀌면 changeListener 로 알려서 서버(Firebase)에 올립니다.
 */
class TripRepository(private val db: AppDatabase) {

    companion object {
        /** 지운 뒤 '실행취소'를 받을 수 있는 시간 (알림이 떠 있는 동안) */
        const val UNDO_MS = 6000L
    }

    private val dao = db.tripDao()

    /** 여행 내용이 바뀌면 여행 id 로 알림 (공유 동기화가 듣고 있음) */
    var changeListener: ((Long) -> Unit)? = null
    private fun changed(tripId: Long) { changeListener?.invoke(tripId) }
    /** 여행을 지우기 직전 알림 (서버 백업 · 공유에서도 빼려고) */
    var deleteListener: ((Trip) -> Unit)? = null

    // 여행
    fun observeTripsWithCounts(): LiveData<List<TripWithCounts>> = dao.observeTripsWithCounts()
    fun observeTrip(id: Long): LiveData<Trip?> = dao.observeTrip(id)
    suspend fun getTrip(id: Long) = dao.getTrip(id)
    suspend fun addTrip(trip: Trip): Long = dao.insertTrip(trip).also { changed(it) }   // 새 여행도 백업
    suspend fun updateTrip(trip: Trip) { dao.updateTrip(trip); changed(trip.id) }
    suspend fun deleteTrip(trip: Trip) { deleteListener?.invoke(trip); dao.deleteTrip(trip) }
    suspend fun getAllTrips() = dao.getAllTrips()

    // ---------- 지운 것 되돌리기 ----------

    /** 지운 직후 '실행취소' (UNDO_MS 안에만). 시간이 지나면 commit 이 실행되어 서버 · 파일까지 정리 */
    class Undo internal constructor(private val restore: suspend () -> Unit, private val job: kotlinx.coroutines.Job) {
        /** 되돌림 (이미 확정됐으면 false) */
        suspend fun undo(): Boolean {
            if (!job.isActive) return false
            job.cancel(); restore(); return true
        }
    }
    private val trashScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private fun later(commit: suspend () -> Unit) = trashScope.launch {
        kotlinx.coroutines.delay(UNDO_MS)
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { commit() }
    }

    /**
     * 여행 지우기 (되돌릴 수 있게): 기기에서는 바로 지우고, 서버 백업 · 공유에서 빼기와 파일 정리(onCommit)는 UNDO_MS 뒤에.
     * 되돌리면 같은 id 로 여행 · 장소 · 교통 · 숙소 · 자료를 그대로 다시 넣음.
     */
    suspend fun deleteTripUndoable(trip: Trip, onCommit: suspend () -> Unit = {}): Undo {
        val places = dao.getPlaces(trip.id); val flights = dao.getFlights(trip.id)
        val lodgings = dao.getLodgings(trip.id); val files = dao.getAttachments(trip.id)
        dao.deleteTrip(trip)
        val job = later { deleteListener?.invoke(trip); onCommit() }
        return Undo({
            db.withTransaction {
                dao.insertTrip(trip)
                places.forEach { dao.insertPlace(it) }; flights.forEach { dao.insertFlight(it) }
                lodgings.forEach { dao.insertLodging(it) }; files.forEach { dao.insertAttachment(it) }
            }
        }, job)
    }

    /** 장소 지우기 (되돌릴 수 있게): 붙은 자료 파일은 UNDO_MS 뒤에 지움 */
    suspend fun deletePlaceUndoable(p: Place): Undo {
        val files = dao.getPlaceAttachments(p.id)
        files.forEach { dao.deleteAttachment(it) }
        dao.deletePlace(p); changed(p.tripId)
        val job = later { files.forEach { deleteAttachmentFile(it) } }
        return Undo({
            db.withTransaction { dao.insertPlace(p); files.forEach { dao.insertAttachment(it) } }
            changed(p.tripId)
        }, job)
    }

    suspend fun deleteFlightUndoable(f: Flight): Undo {
        deleteFlight(f)
        return Undo({ dao.insertFlight(f); changed(f.tripId) }, later {})
    }

    suspend fun deleteLodgingUndoable(l: Lodging): Undo {
        deleteLodging(l)
        return Undo({ dao.insertLodging(l); changed(l.tripId) }, later {})
    }

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
    suspend fun savePlace(place: Place) {
        // 날짜를 안 정했으면 후보 (확정은 늘 일차가 있음)
        val p = withSyncId(if (place.status == PlaceStatus.CONFIRMED && place.day == null) place.copy(status = PlaceStatus.CANDIDATE, orderInDay = null) else place)
        if (p.id == 0L) dao.insertPlace(p)
        else {
            // 이름을 바꾸면 하루의 시작 · 끝에 적힌 이름도 같이
            val old = dao.getPlace(p.id)
            dao.updatePlace(p)
            if (old != null && old.name != p.name) dao.getTrip(p.tripId)?.let { t ->
                val renamed = DayAnchors.renamePlace(t.dayAnchors, old.name, p.name)
                if (renamed != t.dayAnchors) dao.updateTrip(t.copy(dayAnchors = renamed))
            }
        }
        changed(p.tripId)
    }
    /** 동기화 id 가 없는 장소(예시 데이터 · 예전 버전)에 id 매기기 — 내용은 그대로라 다시 올리지 않음 */
    suspend fun ensureSyncIds() {
        val blank = dao.getAllTrips().flatMap { t -> dao.getPlaces(t.id) }.filter { it.syncId.isBlank() }
        if (blank.isNotEmpty()) dao.updatePlaces(blank.map { withSyncId(it) })
    }

    /** 예전 '일차 미정' 확정 장소 → 후보로 (앱 시작 때 한 번씩, 바뀐 여행은 공유 · 백업에도 반영) */
    suspend fun undatedToCandidates() {
        val fixed = dao.getAllTrips().flatMap { t -> dao.getPlaces(t.id) }
            .filter { it.status == PlaceStatus.CONFIRMED && it.day == null }
            .map { it.copy(status = PlaceStatus.CANDIDATE, orderInDay = null) }
        if (fixed.isNotEmpty()) updatePlaces(fixed)
    }

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
    suspend fun renameAttachment(a: Attachment, name: String, note: String = a.note) = dao.updateAttachment(a.copy(name = name, note = note))
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
            val newIds = places.associate { it.name to dao.insertPlace(withSyncId(it).copy(id = 0, tripId = trip.id)) }
            flights.forEach { dao.insertFlight(it.copy(id = 0, tripId = trip.id)) }
            lodgings.forEach { dao.insertLodging(it.copy(id = 0, tripId = trip.id)) }
            dao.updateAttachments(files.map { a -> a.copy(placeId = newIds[oldNames[a.placeId]]) })
        }
    }

    /** 초대받은 여행을 새로 만들기 → 새 여행 id */
    suspend fun insertSharedTrip(trip: Trip, places: List<Place>, flights: List<Flight>, lodgings: List<Lodging>): Long =
        db.withTransaction {
            val id = dao.insertTrip(trip.copy(id = 0))
            places.forEach { dao.insertPlace(withSyncId(it).copy(id = 0, tripId = id)) }
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

    /** 동기화 id 가 없으면 새로 매김 */
    private fun withSyncId(p: Place) = if (p.syncId.isBlank()) p.copy(syncId = java.util.UUID.randomUUID().toString().replace("-", "")) else p
}
