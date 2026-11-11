package com.tripplanner.ui

import android.content.Context
import android.graphics.Paint
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap (osmdroid). 키가 필요 없어서 카카오·구글을 쓸 수 없을 때 대신 사용.
 * 지도 위에 "© OpenStreetMap contributors" 를 꼭 표시해야 합니다 (CopyrightOverlay).
 */
class OsmMapEngine(private val ctx: Context, private val onTap: (Long?) -> Unit) : MapEngine {

    private val map = MapView(ctx).apply {
        id = com.tripplanner.R.id.osmMap
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        isTilesScaledToDpi = true
        minZoomLevel = 3.0
        maxZoomLevel = 19.0
        controller.setZoom(5.0)
        controller.setCenter(GeoPoint(36.5, 127.8))
    }
    override val view get() = map
    private val density = density(ctx)

    override fun render(pins: List<MapPin>, paths: List<MapPath>, fit: Boolean) {
        map.overlays.clear()
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { onTap(null); return true }
            override fun longPressHelper(p: GeoPoint?) = false
        }))
        paths.filter { it.points.size >= 2 }.forEach { path ->
            map.overlays.add(Polyline(map).apply {
                setPoints(path.points.map { (la, ln) -> GeoPoint(la, ln) })
                outlinePaint.color = path.color
                outlinePaint.strokeWidth = 4 * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                infoWindow = null
                setOnClickListener { _, _, _ -> false }
            })
        }
        pins.forEach { pin ->
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(pin.lat, pin.lng)
                icon = PinIcons.drawable(ctx, pin)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                infoWindow = null
                title = pin.a11y
                setOnMarkerClickListener { _, _ -> onTap(pin.key); true }
            })
        }
        map.overlays.add(CopyrightOverlay(ctx).apply { setTextSize(10); setAlignRight(true) })
        map.invalidate()
        if (fit) fitTo(pins.distinctCoords())
    }

    private fun fitTo(points: List<Pair<Double, Double>>) {
        if (points.isEmpty()) return
        val apply = {
            if (points.size == 1) {
                map.controller.setZoom(15.0)
                map.controller.setCenter(GeoPoint(points[0].first, points[0].second))
            } else {
                // 가장자리 점이 버튼·카드에 가리지 않게 범위를 넉넉히 (1.4배)
                val box = BoundingBox.fromGeoPointsSafe(points.map { GeoPoint(it.first, it.second) }).increaseByScale(1.4f)
                map.zoomToBoundingBox(box, false, (40 * density).toInt())
                if (map.zoomLevelDouble > 16.5) map.controller.setZoom(16.5)
            }
        }
        if (map.width > 0 && map.height > 0) apply() else map.addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
    }

    override fun onResume() = map.onResume()
    override fun onPause() = map.onPause()
    override fun onDestroy() = map.onDetach()
}
