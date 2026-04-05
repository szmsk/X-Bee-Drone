package com.xbeedrone.controller.ui.map

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.controller.R
import com.xbeedrone.controller.databinding.ActivityMapBinding
import com.xbeedrone.controller.model.Waypoint
import com.xbeedrone.controller.ui.main.MainViewModel
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var mapView: MapView

    // Overlays
    private var droneMarker: Marker? = null
    private var homeMarker: Marker? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val waypointMarkers = mutableListOf<Marker>()
    private var waypointPathOverlay: Polyline? = null
    private var droneTrailOverlay: Polyline? = null
    private val droneTrailPoints = mutableListOf<GeoPoint>()

    // Tryby mapy
    private var isAddingWaypoints = false
    private var followDrone = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // OSMDroid wymaga ustawienia user-agent przed inicjalizacją
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mapView = binding.mapView

        setupMap()
        setupButtons()
        observeDroneState()
        observeWaypoints()
    }

    // ─── Map setup ───────────────────────────────────────────────────────────

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(18.0)

        // Domyślna pozycja (Gdańsk)
        val startPoint = GeoPoint(54.352, 18.646)
        mapView.controller.setCenter(startPoint)

        // Lokalizacja telefonu
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        mapView.overlays.add(myLocationOverlay)

        // Kompas
        val compassOverlay = CompassOverlay(this, mapView).apply {
            enableCompass()
        }
        mapView.overlays.add(compassOverlay)

        // Skala
        val scaleBarOverlay = ScaleBarOverlay(mapView).apply {
            setCentred(true)
            setAlignBottom(true)
        }
        mapView.overlays.add(scaleBarOverlay)

        // Ślad drona
        droneTrailOverlay = Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#FF6600")
            outlinePaint.strokeWidth = 4f
            outlinePaint.alpha = 180
        }
        mapView.overlays.add(droneTrailOverlay)

        // Marker drona
        droneMarker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Dron X-Bee 9.5"
            icon = resources.getDrawable(R.drawable.ic_drone_marker, null)
        }
        mapView.overlays.add(droneMarker)

        // Marker domu
        homeMarker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Dom"
            icon = resources.getDrawable(R.drawable.ic_home_marker, null)
        }

        // Obsługa dotknięć mapy (dodawanie waypointów)
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (isAddingWaypoints) {
                    addWaypointAtPoint(p)
                    return true
                }
                return false
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                addWaypointAtPoint(p)
                return true
            }
        }
        mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))
    }

    // ─── Buttons ─────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnClose.setOnClickListener { finish() }

        binding.btnFollowDrone.setOnClickListener {
            followDrone = !followDrone
            binding.btnFollowDrone.text = if (followDrone) "📡 Śledź" else "🔓 Wolna"
            binding.btnFollowDrone.alpha = if (followDrone) 1f else 0.6f
        }

        binding.btnAddWaypoint.setOnClickListener {
            isAddingWaypoints = !isAddingWaypoints
            binding.btnAddWaypoint.text = if (isAddingWaypoints) "✅ Dodaj WP" else "📍 Dodaj WP"
            binding.btnAddWaypoint.alpha = if (isAddingWaypoints) 1f else 0.6f
            if (isAddingWaypoints) {
                Toast.makeText(this, "Dotknij mapę aby dodać punkt trasy", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearWaypoints.setOnClickListener {
            viewModel.clearWaypoints()
            clearWaypointMarkers()
            Toast.makeText(this, "Punkty trasy wyczyszczone", Toast.LENGTH_SHORT).show()
        }

        binding.btnStartMission.setOnClickListener {
            if (viewModel.waypoints.value.isEmpty()) {
                Toast.makeText(this, "Dodaj punkty trasy!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.startWaypointMission()
            Toast.makeText(this, "🚁 Misja rozpoczęta!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSetHome.setOnClickListener {
            val state = viewModel.droneState.value
            if (state.isGpsFixed) {
                val home = GeoPoint(state.latitude, state.longitude)
                homeMarker?.position = home
                if (homeMarker !in mapView.overlays) mapView.overlays.add(homeMarker)
                mapView.invalidate()
                Toast.makeText(this, "🏠 Punkt domu ustawiony", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Brak sygnału GPS!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnMapType.setOnClickListener {
            // Przełącz między mapą a satelitą
            if (mapView.tileProvider.tileSource == TileSourceFactory.MAPNIK) {
                mapView.setTileSource(TileSourceFactory.USGS_SAT)
                binding.btnMapType.text = "🗺️ Mapa"
            } else {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                binding.btnMapType.text = "🛰️ Satelita"
            }
        }

        binding.btnClearTrail.setOnClickListener {
            droneTrailPoints.clear()
            droneTrailOverlay?.setPoints(emptyList())
            mapView.invalidate()
        }
    }

    // ─── Waypoints ───────────────────────────────────────────────────────────

    private fun addWaypointAtPoint(point: GeoPoint) {
        viewModel.addWaypoint(point.latitude, point.longitude)
    }

    private fun clearWaypointMarkers() {
        waypointMarkers.forEach { mapView.overlays.remove(it) }
        waypointMarkers.clear()
        waypointPathOverlay?.let { mapView.overlays.remove(it) }
        waypointPathOverlay = null
        mapView.invalidate()
    }

    private fun drawWaypoints(waypoints: List<Waypoint>) {
        clearWaypointMarkers()

        val points = waypoints.map { GeoPoint(it.latitude, it.longitude) }

        // Rysuj linię trasy
        if (points.size >= 2) {
            waypointPathOverlay = Polyline(mapView).apply {
                setPoints(points)
                outlinePaint.color = Color.parseColor("#00BCD4")
                outlinePaint.strokeWidth = 5f
                outlinePaint.pathEffect = android.graphics.DashPathEffect(
                    floatArrayOf(20f, 10f), 0f
                )
            }
            mapView.overlays.add(waypointPathOverlay)
        }

        // Markery waypointów
        waypoints.forEachIndexed { index, wp ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(wp.latitude, wp.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "WP ${index + 1}"
                snippet = String.format("Alt: %.0fm", wp.altitude)
                icon = resources.getDrawable(R.drawable.ic_waypoint_marker, null)
            }
            mapView.overlays.add(marker)
            waypointMarkers.add(marker)
        }

        mapView.invalidate()
        binding.tvWaypointCount.text = "Waypoints: ${waypoints.size}"
    }

    // ─── Observe state ───────────────────────────────────────────────────────

    private fun observeDroneState() {
        lifecycleScope.launch {
            viewModel.droneState.collect { state ->
                if (state.isGpsFixed && state.latitude != 0.0) {
                    val dronePos = GeoPoint(state.latitude, state.longitude)

                    // Aktualizuj marker drona
                    droneMarker?.position = dronePos
                    droneMarker?.rotation = state.heading.toFloat()

                    // Dodaj do śladu
                    droneTrailPoints.add(dronePos)
                    if (droneTrailPoints.size > 500) droneTrailPoints.removeAt(0)
                    droneTrailOverlay?.setPoints(droneTrailPoints)

                    // Śledź drona
                    if (followDrone) {
                        mapView.controller.animateTo(dronePos)
                    }

                    mapView.invalidate()
                }

                // Aktualizuj dane HUD na mapie
                binding.tvMapAlt.text = String.format("ALT: %.1fm", state.altitude)
                binding.tvMapSpeed.text = String.format("SPD: %.1fkm/h", state.speed)
                binding.tvMapBat.text = "BAT: ${state.batteryPercent}%"
                binding.tvMapDist.text = String.format("DIST: %.0fm", state.distanceFromHome)
                binding.tvMapGps.text = if (state.isGpsFixed)
                    "GPS: ${state.gpsFixCount}sat" else "NO GPS"

                val lat = String.format("%.5f", state.latitude)
                val lon = String.format("%.5f", state.longitude)
                binding.tvMapCoords.text = "$lat, $lon"
            }
        }
    }

    private fun observeWaypoints() {
        lifecycleScope.launch {
            viewModel.waypoints.collect { waypoints ->
                drawWaypoints(waypoints)
            }
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
}
