package com.tripplanner.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * DB 에 묻는 질문(쿼리) 모음.
 * LiveData 를 돌려주는 함수는 데이터가 바뀌면 화면에 자동으로 다시 알려 줍니다.
 * suspend 함수는 코루틴 안에서 불러야 합니다 (화면이 멈추지 않도록).
 */
@Dao
interface TripDao {

    // ---------- 여행 ----------
    @Query("""
        SELECT t.*,
          (SELECT COUNT(*) FROM places p WHERE p.tripId = t.id AND p.status = 'CONFIRMED') AS confirmedCount,
          (SELECT COUNT(*) FROM places p WHERE p.tripId = t.id AND p.status = 'CANDIDATE') AS candidateCount,
          (SELECT f.mode FROM flights f WHERE f.tripId = t.id ORDER BY f.departAt LIMIT 1) AS firstMode
        FROM trips t ORDER BY t.startDate
    """)
    fun observeTripsWithCounts(): LiveData<List<TripWithCounts>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: Long): LiveData<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTrip(id: Long): Trip?

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun countTrips(): Int

    @Insert suspend fun insertTrip(trip: Trip): Long
    @Update suspend fun updateTrip(trip: Trip)
    @Delete suspend fun deleteTrip(trip: Trip)

    // ---------- 비행기 ----------
    @Query("SELECT * FROM flights WHERE tripId = :tripId ORDER BY departAt")
    fun observeFlights(tripId: Long): LiveData<List<Flight>>

    @Query("SELECT * FROM flights WHERE tripId = :tripId ORDER BY departAt")
    suspend fun getFlights(tripId: Long): List<Flight>

    @Insert suspend fun insertFlight(flight: Flight): Long
    @Update suspend fun updateFlight(flight: Flight)
    @Delete suspend fun deleteFlight(flight: Flight)

    // ---------- 숙소 ----------
    @Query("SELECT * FROM lodgings WHERE tripId = :tripId ORDER BY checkIn")
    fun observeLodgings(tripId: Long): LiveData<List<Lodging>>

    @Query("SELECT * FROM lodgings WHERE tripId = :tripId ORDER BY checkIn")
    suspend fun getLodgings(tripId: Long): List<Lodging>

    @Insert suspend fun insertLodging(lodging: Lodging): Long
    @Update suspend fun updateLodging(lodging: Lodging)
    @Delete suspend fun deleteLodging(lodging: Lodging)

    // ---------- 장소 ----------
    /** 일차 → 방문 순서 → 등록 순으로 정렬 (일차/순서가 없는 건 뒤로) */
    @Query("""
        SELECT * FROM places WHERE tripId = :tripId
        ORDER BY day IS NULL, day, orderInDay IS NULL, orderInDay, id
    """)
    fun observePlaces(tripId: Long): LiveData<List<Place>>

    @Query("SELECT * FROM places WHERE tripId = :tripId")
    suspend fun getPlaces(tripId: Long): List<Place>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getPlace(id: Long): Place?

    @Insert suspend fun insertPlace(place: Place): Long
    @Update suspend fun updatePlace(place: Place)
    @Update suspend fun updatePlaces(places: List<Place>)
    @Delete suspend fun deletePlace(place: Place)

    // ---------- 내가 만든 장소 종류 ----------
    @Query("SELECT * FROM place_types ORDER BY id") fun observeTypes(): LiveData<List<PlaceType>>
    @Query("SELECT * FROM place_types ORDER BY id") suspend fun getTypes(): List<PlaceType>
    @Insert suspend fun insertType(t: PlaceType): Long
    @Update suspend fun updateType(t: PlaceType)
    @Delete suspend fun deleteType(t: PlaceType)
    /** 지운 종류를 쓰던 장소는 '기타'(또는 원래 기본 종류)로 */
    @Query("UPDATE places SET typeId = NULL WHERE typeId = :typeId") suspend fun clearType(typeId: Long)
    @Query("SELECT DISTINCT tripId FROM places WHERE typeId = :typeId") suspend fun tripsUsingType(typeId: Long): List<Long>

    // ---------- 예약·티켓 자료 ----------
    @Query("SELECT * FROM attachments WHERE tripId = :tripId ORDER BY createdAt")
    fun observeAttachments(tripId: Long): LiveData<List<Attachment>>
    @Query("SELECT * FROM attachments WHERE tripId = :tripId ORDER BY createdAt")
    suspend fun getAttachments(tripId: Long): List<Attachment>
    @Query("SELECT * FROM attachments WHERE placeId = :placeId ORDER BY createdAt")
    suspend fun getPlaceAttachments(placeId: Long): List<Attachment>
    @Insert suspend fun insertAttachment(a: Attachment): Long
    @Update suspend fun updateAttachment(a: Attachment)
    @Update suspend fun updateAttachments(list: List<Attachment>)
    @Delete suspend fun deleteAttachment(a: Attachment)

    // ---------- 공유: 다른 사람이 바꾼 내용으로 여행 하나를 통째로 갈아끼울 때 ----------
    @Query("DELETE FROM places WHERE tripId = :tripId") suspend fun deletePlacesOf(tripId: Long)
    @Query("DELETE FROM flights WHERE tripId = :tripId") suspend fun deleteFlightsOf(tripId: Long)
    @Query("DELETE FROM lodgings WHERE tripId = :tripId") suspend fun deleteLodgingsOf(tripId: Long)
    @Query("SELECT * FROM trips WHERE shareCode IS NOT NULL") suspend fun getSharedTrips(): List<Trip>
    @Query("SELECT * FROM trips WHERE shareCode = :code LIMIT 1") suspend fun getTripByShareCode(code: String): Trip?
}
