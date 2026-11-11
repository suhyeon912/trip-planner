package com.tripplanner.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tripplanner.R
import com.tripplanner.route.Region

/** 지도에 넘길 지점 */
data class MapPoint(val name: String, val lat: Double, val lng: Double)

/**
 * 국내면 카카오맵, 해외면 구글맵으로 여는 도우미. API 키 없이 URL(인텐트)만 사용.
 *  - 카카오맵: kakaomap:// 앱 스킴, 앱이 없으면 모바일 웹(m.map.kakao.com/scheme/…)으로
 *  - 카카오맵 경유지는 최대 5곳, 대중교통은 경유지를 지원하지 않음
 */
object MapLauncher {

    private const val KAKAO_MAX_WAYPOINTS = 5

    fun isDomestic(points: List<MapPoint>): Boolean =
        points.isNotEmpty() && points.all { Region.isKorea(it.lat, it.lng) }

    fun mapName(context: Context, domestic: Boolean): String =
        context.getString(if (domestic) R.string.map_kakao else R.string.map_google)

    // ---------------- 장소 하나 ----------------

    /** 좌표가 있으면 그 위치를, 없으면 이름으로 검색 */
    fun openPlace(context: Context, name: String, point: MapPoint?, domestic: Boolean) {
        if (domestic) {
            val path = if (point != null) "look?p=${point.lat},${point.lng}" else "search?q=${Uri.encode(name)}"
            openKakao(context, path)
        } else {
            val query = if (point != null) "${point.lat},${point.lng}" else name
            open(context, "https://www.google.com/maps/search/?api=1&query=" + Uri.encode(query))
        }
    }

    // ---------------- 경로 ----------------

    /** 출발 → 경유지들 → 도착. 국내면 이동수단을 고르게 함 */
    fun openRoute(context: Context, points: List<MapPoint>) {
        if (points.size < 2) {
            points.firstOrNull()?.let { openPlace(context, it.name, it, isDomestic(points)) }
            return
        }
        if (isDomestic(points)) chooseKakaoMode(context, points) else openGoogleRoute(context, points)
    }

    private fun openGoogleRoute(context: Context, points: List<MapPoint>) {
        val url = buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&origin=").append(Uri.encode(points.first().coord()))
            append("&destination=").append(Uri.encode(points.last().coord()))
            val mid = points.drop(1).dropLast(1)
            if (mid.isNotEmpty()) append("&waypoints=").append(Uri.encode(mid.joinToString("|") { it.coord() }))
        }
        open(context, url)
    }

    private fun chooseKakaoMode(context: Context, points: List<MapPoint>) {
        val hasWaypoints = points.size > 2
        val items = arrayOf(
            context.getString(R.string.mode_car),
            context.getString(R.string.mode_walk),
            context.getString(if (hasWaypoints) R.string.mode_transit_legs else R.string.mode_transit))
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.kakao_mode_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openKakaoRoute(context, points, "car")
                    1 -> openKakaoRoute(context, points, "foot")
                    else -> if (hasWaypoints) chooseLeg(context, points)
                            else openKakaoRoute(context, points, "publictransit")
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** 대중교통은 경유지가 안 되니 '숙소 → 센소지' 같은 구간을 하나 골라서 엶 */
    private fun chooseLeg(context: Context, points: List<MapPoint>) {
        val legs = points.zipWithNext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pick_leg_title)
            .setItems(legs.mapIndexed { i, (a, b) -> context.getString(R.string.leg_item, i + 1, a.name, b.name) }.toTypedArray()) { _, which ->
                openKakaoRoute(context, legs[which].toList(), "publictransit")
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openKakaoRoute(context: Context, points: List<MapPoint>, by: String) {
        var mid = points.drop(1).dropLast(1)
        if (mid.size > KAKAO_MAX_WAYPOINTS) {
            Toast.makeText(context, context.getString(R.string.kakao_waypoint_limit, KAKAO_MAX_WAYPOINTS),
                Toast.LENGTH_LONG).show()
            mid = mid.take(KAKAO_MAX_WAYPOINTS)
        }
        val path = buildString {
            append("route?sp=").append(points.first().coord())
            mid.forEachIndexed { i, p -> append(if (i == 0) "&vp=" else "&vp${i + 1}=").append(p.coord()) }
            append("&ep=").append(points.last().coord())
            append("&by=").append(by)
        }
        openKakao(context, path)
    }

    // ---------------- 공통 ----------------

    /** 카카오맵 앱으로 열고, 앱이 없으면 같은 경로의 모바일 웹으로 */
    private fun openKakao(context: Context, path: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://$path")))
        } catch (e: ActivityNotFoundException) {
            open(context, "http://m.map.kakao.com/scheme/$path")
        }
    }

    private fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_map_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun MapPoint.coord() = "$lat,$lng"
}
