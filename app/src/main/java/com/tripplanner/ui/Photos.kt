package com.tripplanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * 장소 사진: 고른 사진을 앱 내부 저장소(files/photos)에 줄여서 복사.
 * 원본 앨범 권한이 없어도 다시 볼 수 있고, 여행을 지워도 앨범 사진은 그대로입니다.
 */
object Photos {
    private const val MAX_SIDE = 1280

    /** 실패하면 null */
    fun importPhoto(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        file.absolutePath
    }.getOrNull()

    /** 앱에 들어 있는 그림(assets)을 사진 폴더로 복사 — 예시 여행 표지용. 실패하면 null */
    fun importAsset(context: Context, assetPath: String): String? = runCatching {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".jpg")
        context.assets.open(assetPath).use { input -> file.outputStream().use { input.copyTo(it) } }
        file.absolutePath
    }.getOrNull()

    /** 앱이 복사해 둔 사진만 지움 (다른 경로는 건드리지 않음) */
    fun delete(context: Context, path: String?) {
        val f = path?.let { File(it) } ?: return
        if (f.parentFile?.absolutePath == File(context.filesDir, "photos").absolutePath) f.delete()
    }
}
