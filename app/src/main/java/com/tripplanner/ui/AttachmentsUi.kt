package com.tripplanner.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.tripplanner.R
import com.tripplanner.TripPlannerApp
import com.tripplanner.data.Attachment
import com.tripplanner.data.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 예약·티켓 자료 (시안 PlaceView · TravelInfo).
 *  · 올리기: 사진 보관함 / 파일 / 카메라로 찍기 → 앱 저장소(files/attachments)에 복사
 *  · 줄 ⋮: 열기 / 이름 바꾸기 / 다른 앱으로 보내기 / 삭제(확인)
 * 자료 파일은 이 기기에만 있고 함께 보는 사람에게는 가지 않습니다 (활동 기록에는 '올렸어요'가 남음).
 *
 * Activity 의 필드로 만들어야 합니다 (registerForActivityResult 는 화면이 시작되기 전에 등록).
 */
class AttachmentsUi(
    private val activity: AppCompatActivity,
    /** 새 자료가 붙을 곳: (여행 id, 장소 id, 종류) */
    private val target: () -> Triple<Long, Long?, AttachmentKind>,
    /** 목록이 바뀌었을 때 (다시 그리기) */
    private val onChanged: () -> Unit
) {
    private val repo get() = (activity.application as TripPlannerApp).repository
    private var cameraFile: File? = null

    private val pickMedia = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) import(uri, null)
    }
    private val pickFile = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) import(uri, null)
    }
    private val takePhoto = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) import(Uri.fromFile(f), activity.getString(R.string.camera_file_name,
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd_HHmm"))))
    }

    /** '파일 · 사진 올리기' 메뉴 */
    fun showUploadMenu(anchor: View) {
        val rows = mutableListOf(
            SelectRow.Option(activity.getString(R.string.upload_gallery)) {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            SelectRow.Option(activity.getString(R.string.upload_file)) {
                runCatching { pickFile.launch(arrayOf("*/*")) }.onFailure { activity.showSnack(activity.getString(R.string.no_app_to_open)) }
            }
        )
        if (activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY))
            rows += SelectRow.Option(activity.getString(R.string.upload_camera)) {
                val dir = File(activity.cacheDir, "camera").apply { mkdirs() }
                val f = File(dir, "attach.jpg").also { cameraFile = it }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", f)
                runCatching { takePhoto.launch(uri) }.onFailure { activity.showSnack(activity.getString(R.string.photo_failed)) }
            }
        showSelect(anchor, rows, widthDp = 200)
    }

    private fun import(uri: Uri, forcedName: String?) {
        val (tripId, placeId, kind) = target()
        activity.lifecycleScope.launch {
            val a = withContext(Dispatchers.IO) { copy(activity, uri, forcedName) }
            cameraFile?.delete(); cameraFile = null
            if (a == null) { activity.showSnack(activity.getString(R.string.upload_failed)); return@launch }
            repo.addAttachment(Attachment(tripId = tripId, placeId = placeId, kind = kind, name = a.name, path = a.path,
                mime = a.mime, size = a.size, uploader = uploaderName(activity), createdAt = System.currentTimeMillis()))
            Haptics.confirm(activity.window.decorView)
            activity.showSnack(activity.getString(R.string.upload_done, a.name))
            onChanged()
        }
    }

    private class Copied(val name: String, val path: String, val mime: String, val size: Long)

    private fun copy(context: Context, uri: Uri, forcedName: String?): Copied? = runCatching {
        val cr = context.contentResolver
        var name = forcedName
        if (name == null && uri.scheme == "content") cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0)
        }
        val mime = (if (uri.scheme == "file") "image/jpeg" else cr.getType(uri)) ?: "application/octet-stream"
        val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        val display = (name ?: uri.lastPathSegment ?: "file").let { if (it.contains('.')) it else "$it.$ext" }
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + "." + display.substringAfterLast('.', ext).take(8))
        val stream = if (uri.scheme == "file") File(uri.path!!).inputStream() else cr.openInputStream(uri)
        stream!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        Copied(display, file.absolutePath, mime, file.length())
    }.getOrNull()

    // ---------------- 목록 줄 ----------------
    /** 카드 안에 자료 줄들 (showKind = 교통·숙소 화면처럼 어디에 붙은 자료인지도 보임) */
    fun bindRows(card: LinearLayout, list: List<Attachment>, showKind: Boolean) {
        card.removeAllViews()
        card.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(activity)
        list.forEach { a ->
            val v = inflater.inflate(R.layout.item_simple, card, false)
            v.findViewById<View>(R.id.divider).visibility = if (card.childCount == 0) View.GONE else View.VISIBLE
            v.findViewById<TextView>(R.id.tvTitle).text = a.name
            v.findViewById<TextView>(R.id.tvSub).text = subtitle(a, showKind)
            v.findViewById<ImageView>(R.id.ivIcon).apply {
                visibility = View.VISIBLE
                setImageResource(iconOf(a))
                setBackgroundResource(R.drawable.bg_thumb_neutral)
                backgroundTintList = ColorStateList.valueOf(activity.color(R.color.primary_light))
                imageTintList = ColorStateList.valueOf(activity.color(R.color.on_primary_container))
            }
            v.findViewById<View>(R.id.row).apply {
                setOnClickListener { open(a) }
                contentDescription = "${a.name}, ${subtitle(a, showKind)}"
            }
            v.findViewById<ImageButton>(R.id.btnMore).apply {
                contentDescription = activity.getString(R.string.item_menu_a11y, a.name)
                setOnClickListener { menu(it, a) }
            }
            card.addView(v)
        }
    }

    private fun iconOf(a: Attachment) = when {
        a.isImage -> R.drawable.ic_image
        a.mime == "application/pdf" -> R.drawable.ic_pdf
        else -> R.drawable.ic_attach
    }

    /** "교통 · 지우 · 11.01 · 214KB" */
    fun subtitle(a: Attachment, showKind: Boolean): String {
        val date = java.time.Instant.ofEpochMilli(a.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val kind = if (!showKind) null else activity.getString(when (a.kind) {
            AttachmentKind.PLACE -> R.string.attach_kind_place
            AttachmentKind.TRANSPORT -> R.string.attach_kind_transport
            AttachmentKind.LODGING -> R.string.attach_kind_lodging
            AttachmentKind.TRIP -> R.string.attach_kind_trip
        })
        return listOfNotNull(kind, a.uploader.ifBlank { null }, "${date.monthValue}.%02d".format(date.dayOfMonth), sizeLabel(a.size))
            .joinToString(activity.getString(R.string.list_separator))
    }

    private fun sizeLabel(bytes: Long) = when {
        bytes >= 1024 * 1024 -> "%.1fMB".format(bytes / 1024.0 / 1024.0)
        else -> "${(bytes / 1024).coerceAtLeast(1)}KB"
    }

    private fun uri(a: Attachment) = FileProvider.getUriForFile(activity, "${activity.packageName}.files", File(a.path))

    fun open(a: Attachment) {
        if (!File(a.path).exists()) { activity.showSnack(activity.getString(R.string.attach_missing)); return }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(a), a.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { activity.startActivity(i) } catch (e: ActivityNotFoundException) { activity.showSnack(activity.getString(R.string.no_app_to_open)) }
    }

    private fun send(a: Attachment) {
        if (!File(a.path).exists()) { activity.showSnack(activity.getString(R.string.attach_missing)); return }
        val i = Intent(Intent.ACTION_SEND).setType(a.mime).putExtra(Intent.EXTRA_STREAM, uri(a))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(i, activity.getString(R.string.attach_send)))
    }

    private fun menu(anchor: View, a: Attachment) {
        showSelect(anchor, listOf(
            SelectRow.Option(activity.getString(R.string.attach_open)) { open(a) },
            SelectRow.Option(activity.getString(R.string.attach_rename)) { rename(a) },
            SelectRow.Option(activity.getString(R.string.attach_send)) { send(a) },
            SelectRow.Option(activity.getString(R.string.action_delete), danger = true) {
                confirmDelete(activity, a.name, activity.getString(R.string.attach_delete_confirm)) {
                    activity.lifecycleScope.launch { repo.deleteAttachment(a); onChanged() }
                }
            }
        ), widthDp = 200)
    }

    /** 이름 바꾸기 (확장자는 그대로 붙임) */
    private fun rename(a: Attachment) {
        val ext = a.name.substringAfterLast('.', "").takeIf { a.name.contains('.') }
        val base = if (ext != null) a.name.removeSuffix(".$ext") else a.name
        showFormDialog(activity, activity.getString(R.string.attach_rename), listOf(
            Field(activity.getString(R.string.attach_name), base, InputType.TYPE_CLASS_TEXT)
        )) { (name) ->
            if (name.isBlank()) false
            else {
                val full = if (ext != null && !name.endsWith(".$ext")) "$name.$ext" else name
                activity.lifecycleScope.launch { repo.renameAttachment(a, full); onChanged() }
                true
            }
        }
    }

    companion object {
        /** 올린 사람 이름: 공유용 닉네임, 없으면 '나' */
        fun uploaderName(context: Context): String =
            context.getSharedPreferences("profile", Context.MODE_PRIVATE).getString("nickname", "").orEmpty()
                .ifBlank { context.getString(R.string.me) }
    }
}

/** 관련 링크 줄 (시안: 링크 아이콘 + 이름 + 파란 주소 + ⋮) */
fun bindLinkRows(card: LinearLayout, links: List<com.tripplanner.data.PlaceLink>,
                 onOpen: (com.tripplanner.data.PlaceLink) -> Unit, onMenu: (View, Int) -> Unit) {
    val ctx = card.context
    card.removeAllViews()
    card.visibility = if (links.isEmpty()) View.GONE else View.VISIBLE
    links.forEachIndexed { i, l ->
        val v = LayoutInflater.from(ctx).inflate(R.layout.item_simple, card, false)
        v.findViewById<View>(R.id.divider).visibility = if (i == 0) View.GONE else View.VISIBLE
        v.findViewById<TextView>(R.id.tvTitle).text = l.name.ifBlank { l.url }
        v.findViewById<TextView>(R.id.tvSub).apply {
            text = l.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
            setTextColor(ctx.color(R.color.primary))
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        v.findViewById<ImageView>(R.id.ivIcon).apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_link)
            background = null
            imageTintList = ColorStateList.valueOf(ctx.color(R.color.on_surface))
            layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).apply { width = (24 * ctx.resources.displayMetrics.density).toInt(); height = width }
            setPadding(0, 0, 0, 0)
        }
        v.findViewById<View>(R.id.row).apply {
            minimumHeight = (64 * ctx.resources.displayMetrics.density).toInt()
            setOnClickListener { onOpen(l) }
            contentDescription = "${l.name}, ${l.url}"
        }
        v.findViewById<ImageButton>(R.id.btnMore).apply {
            contentDescription = ctx.getString(R.string.item_menu_a11y, l.name)
            setOnClickListener { onMenu(it, i) }
        }
        card.addView(v)
    }
}
