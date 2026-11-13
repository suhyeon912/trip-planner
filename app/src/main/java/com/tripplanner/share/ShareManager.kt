package com.tripplanner.share

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.tripplanner.BuildConfig
import com.tripplanner.data.Trip
import com.tripplanner.data.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 공유 · 초대 (Firebase 익명 로그인 + Firestore).
 *
 *  - 공유하기: 6자리 초대 코드를 만들고 여행 전체를 문서 trips/{코드} 하나로 올림
 *  - 참여하기: 코드를 입력하면 그 문서를 받아 내 기기에 여행을 만들고 members 에 내 id 추가
 *  - 동기화: 공유 중인 여행은 서로 실시간으로 받음. 내가 바꾸면 1초 뒤 전체를 다시 올림
 *    (여행 단위 '마지막 저장 우선' — 발표 범위에 맞춘 단순한 방식)
 *
 * app/google-services.json 이 없으면 isConfigured = false → 화면에서 '설정 필요' 안내만 보여 줌.
 */
class ShareManager(private val context: Context, private val repo: TripRepository) {

    companion object {
        private const val TAG = "ShareManager"
        private const val COLLECTION = "trips"
    }

    class NotConfiguredException : Exception()
    class NotFoundException : Exception()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val listeners = mutableMapOf<Long, ListenerRegistration>()
    private val pushJobs = mutableMapOf<Long, Job>()
    /** 서버에서 받아 적용하는 중인 여행 (이때 생긴 변경은 다시 올리지 않음) */
    private val applying = mutableSetOf<Long>()
    /** 활동 기록: 마지막으로 서버와 맞춘 여행 내용 (내가 바꾼 것을 찾는 기준) · 서버의 기록 · 아직 안 올린 기록 */
    private val baseline = mutableMapOf<Long, TripSnapshot>()
    private val serverActivity = mutableMapOf<Long, List<ActivityEvent>>()
    private val pendingEvents = mutableMapOf<Long, MutableList<ActivityEvent>>()

    val isConfigured: Boolean by lazy {
        BuildConfig.HAS_FIREBASE && runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        }.getOrDefault(false)
    }

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    /** 익명 로그인 (처음 한 번, 이후 기기에 유지) → 내 사용자 id */
    private suspend fun uid(): String {
        if (!isConfigured) throw NotConfiguredException()
        auth.currentUser?.let { return it.uid }
        return auth.signInAnonymously().await().user!!.uid
    }

    /** 앱 시작 시: 공유 중인 여행들 실시간 받기 시작 + 내 변경을 올리도록 연결 */
    fun start() {
        if (!isConfigured) return
        repo.changeListener = { tripId -> schedulePush(tripId) }
        // 자료 파일은 올리지 않지만 '올렸어요' 기록은 남김
        repo.attachmentListener = { a ->
            scope.launch {
                val trip = repo.getTrip(a.tripId)
                if (trip?.shareCode != null) {
                    val me = runCatching { uid() }.getOrNull() ?: return@launch
                    pendingEvents.getOrPut(a.tripId) { mutableListOf() } +=
                        ActivityEvent(me, nickname, ActivityEvent.Type.FILE, a.name, at = System.currentTimeMillis())
                    schedulePush(a.tripId)
                }
            }
        }
        scope.launch {
            runCatching { repo.getSharedTrips().forEach { listen(it) } }
                .onFailure { Log.w(TAG, "공유 여행 불러오기 실패", it) }
        }
    }

    /** 내 닉네임 (기기에 저장, 공유·참여할 때 서버 문서 names 에 함께 올림) */
    var nickname: String
        get() = context.getSharedPreferences("profile", Context.MODE_PRIVATE).getString("nickname", "") ?: ""
        set(v) = context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().putString("nickname", v.trim()).apply()

    /** 여행을 공유 → 초대 코드 */
    suspend fun share(tripId: Long): String {
        val me = uid()
        val trip = repo.getTrip(tripId) ?: throw NotFoundException()
        trip.shareCode?.let { return it }
        // 겹치지 않는 코드 찾기
        var code: String
        do { code = ShareCodec.newCode() } while (db.collection(COLLECTION).document(code).get().await().exists())
        val data = ShareCodec.toMap(snapshot(trip)) + mapOf(
            "ownerUid" to me, "members" to listOf(me), "names" to mapOf(me to nickname),
            "updatedBy" to me, "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection(COLLECTION).document(code).set(data).await()
        baseline[tripId] = snapshot(trip)
        repo.setShareCode(trip, code)
        listen(trip.copy(shareCode = code))
        return code
    }

    /** 초대 코드로 참여 → 내 기기의 여행 id (이미 있으면 그 여행) */
    suspend fun join(rawCode: String): Long {
        val code = ShareCodec.normalize(rawCode)
        repo.getTripByShareCode(code)?.let { return it.id }
        val me = uid()
        val ref = db.collection(COLLECTION).document(code)
        val doc = ref.get().await()
        if (!doc.exists()) throw NotFoundException()
        // 멤버에 나 + 닉네임 추가. 예전 보안 규칙(names 를 못 바꿈)이면 멤버만 추가
        runCatching { ref.update(mapOf("members" to FieldValue.arrayUnion(me), "names.$me" to nickname)).await() }
            .onFailure { ref.update("members", FieldValue.arrayUnion(me)).await() }
        val data = doc.data ?: emptyMap()
        val (inviter, others) = ShareCodec.memberInfo(data, me)
        val base = Trip(title = "", startDate = "", endDate = "", shareCode = code,
            ownedByMe = false, inviterName = inviter, memberNames = others.joinToString(", "))
        val s = ShareCodec.fromMap(data, base)
        val id = repo.insertSharedTrip(s.trip, withLocalTypes(s), s.flights, s.lodgings)
        baseline[id] = s
        serverActivity[id] = ActivityLog.fromList(data["activity"])
        // '여행에 참여했어요' 기록 (참여 규칙은 멤버만 바꿀 수 있어서 내용 올리기와 함께)
        pendingEvents.getOrPut(id) { mutableListOf() } += ActivityEvent(me, nickname, ActivityEvent.Type.JOIN, at = System.currentTimeMillis())
        repo.getTrip(id)?.let { listen(it) }
        schedulePush(id)
        return id
    }

    /** 공유 그만두기: 내 기기의 여행은 그대로 두고 연결만 끊음 */
    suspend fun stop(tripId: Long) {
        val trip = repo.getTrip(tripId) ?: return
        val code = trip.shareCode ?: return
        listeners.remove(tripId)?.remove()
        runCatching {
            val me = uid()
            val ref = db.collection(COLLECTION).document(code)
            runCatching { ref.update(mapOf("members" to FieldValue.arrayRemove(me), "names.$me" to FieldValue.delete())).await() }
                .onFailure { ref.update("members", FieldValue.arrayRemove(me)).await() }
        }
        repo.setShareCode(trip, null)
    }

    /** 활동 기록 화면: 멤버 (uid → 이름, 들어온 순서) · 기록 (최근 것이 위). 그만 볼 때 remove() */
    fun observeActivity(code: String, onUpdate: (members: List<Pair<String, String>>, events: List<ActivityEvent>, me: String) -> Unit): ListenerRegistration? {
        if (!isConfigured) return null
        var reg: ListenerRegistration? = null
        var stopped = false
        scope.launch {
            val me = runCatching { uid() }.getOrNull() ?: return@launch
            if (stopped) return@launch
            reg = db.collection(COLLECTION).document(code).addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val names = (snap.get("names") as? Map<String, Any?>).orEmpty().mapValues { it.value as? String ?: "" }
                val members = (snap.get("members") as? List<*>)?.mapNotNull { it as? String }.orEmpty().map { it to names[it].orEmpty() }
                onUpdate(members, ActivityLog.fromList(snap.get("activity")).sortedByDescending { it.at }, me)
            }
        }
        return object : ListenerRegistration { override fun remove() { stopped = true; reg?.remove() } }
    }

    /** 이 여행을 함께 보는 사람 수 (나 포함) */
    suspend fun memberCount(code: String): Int = runCatching {
        uid()
        (db.collection(COLLECTION).document(code).get().await().get("members") as? List<*>)?.size ?: 1
    }.getOrDefault(1)

    // ---------------- 동기화 ----------------
    private suspend fun snapshot(trip: Trip) =
        TripSnapshot(trip, repo.getPlaces(trip.id), repo.getFlights(trip.id), repo.getLodgings(trip.id),
            types = repo.getTypes().associateBy { it.id })

    /** 받은 장소들의 내가 만든 종류를 내 기기의 종류로 연결 */
    private suspend fun withLocalTypes(s: TripSnapshot) = s.places.mapIndexed { i, p ->
        val t = s.remoteTypes.getOrNull(i)
        if (t == null) p.copy(typeId = null) else p.copy(typeId = repo.findOrCreateType(t.name, t.icon, t.color))
    }

    private fun listen(trip: Trip) {
        val code = trip.shareCode ?: return
        if (listeners.containsKey(trip.id)) return
        scope.launch {
            val me = runCatching { uid() }.getOrNull() ?: return@launch
            listeners[trip.id] = db.collection(COLLECTION).document(code).addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "동기화 오류", err); return@addSnapshotListener }
                if (snap == null || !snap.exists() || snap.metadata.hasPendingWrites()) return@addSnapshotListener
                // 함께 보는 사람 이름은 매번 맞춤 (참여·나가기는 updatedBy 가 바뀌지 않음)
                val (inviter, others) = ShareCodec.memberInfo(snap.data ?: emptyMap(), me)
                scope.launch { repo.setMembers(trip.id, inviter, others.joinToString(", ")) }
                serverActivity[trip.id] = ActivityLog.fromList(snap.get("activity"))
                if (snap.getString("updatedBy") == me) {   // 내가 올린 것
                    if (trip.id !in baseline) scope.launch { baseline[trip.id] = ShareCodec.fromMap(snap.data ?: return@launch, trip) }
                    return@addSnapshotListener
                }
                scope.launch { applyRemote(trip.id, snap.data ?: return@launch, inviter, others) }
            }
        }
    }

    private suspend fun applyRemote(tripId: Long, data: Map<String, Any?>, inviter: String, others: List<String>) {
        val local = repo.getTrip(tripId) ?: return
        val s = ShareCodec.fromMap(data, local.copy(inviterName = inviter, memberNames = others.joinToString(", ")))
        // 사진은 기기에만 있으므로 같은 이름 장소의 내 사진을 그대로 유지
        val photos = repo.getPlaces(tripId).filter { it.photoUri != null }.associate { it.name to it.photoUri }
        baseline[tripId] = s
        applying += tripId
        try {
            repo.replaceTripContents(s.trip, withLocalTypes(s).map { it.copy(photoUri = photos[it.name]) }, s.flights, s.lodgings)
        } finally { applying -= tripId }
    }

    private fun schedulePush(tripId: Long) {
        if (tripId in applying) return
        pushJobs[tripId]?.cancel()
        pushJobs[tripId] = scope.launch {
            delay(1000)   // 연달아 바꾸면 마지막 것만 올림
            val trip = repo.getTrip(tripId) ?: return@launch
            val code = trip.shareCode ?: return@launch
            runCatching {
                val me = uid()
                val now = snapshot(trip)
                // 지난번 맞춘 내용과 비교해 내가 바꾼 것을 활동 기록으로
                val events = (baseline[tripId]?.let { ActivityLog.diff(it, now, me, nickname, System.currentTimeMillis()) } ?: emptyList()) +
                    pendingEvents.remove(tripId).orEmpty()
                val activity = ActivityLog.append(serverActivity[tripId].orEmpty(), events)
                val data = ShareCodec.toMap(now) + mapOf(
                    "updatedBy" to me, "updatedAt" to FieldValue.serverTimestamp(),
                    "activity" to activity.map { ActivityLog.toMap(it) })
                db.collection(COLLECTION).document(code).set(data, SetOptions.merge()).await()
                baseline[tripId] = now
                serverActivity[tripId] = activity
            }.onFailure { Log.w(TAG, "올리기 실패", it) }
        }
    }
}
