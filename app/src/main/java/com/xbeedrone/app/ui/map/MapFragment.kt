package com.xbeedrone.app.ui.map

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.app.DroneViewModel
import com.xbeedrone.app.R
import com.xbeedrone.app.databinding.FragmentMapBinding
import com.xbeedrone.app.model.Waypoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Ekran mapy GPS z waypointami.
 * Używa OpenStreetMap (osmdroid) – bez konieczności API key.
 *
 * Funkcje:
 *  - Mapa satelitarna / standardowa
 *  - Pozycja drona w czasie rzeczywistym (z telemetrii)
 *  - Pozycja telefonu (dla Follow Me)
 *  - Waypoints – dotknij mapę aby dodać punkt
 *  - Trasa lotu (linia łącząca waypoints)
 *  - Ślad lotu drona
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val vm: DroneViewModel by activityViewModels()

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var droneMarker: Marker? = null
    private var waypointRoute: Polyline? = null
    private var flightTrack: Polyline? = null
    private val flightTrackPoints = mutableListOf<GeoPoint>()
    private val waypointMarkers = mutableMapOf<Int, Marker>()

    private var isAddingWaypoints = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        setupButtons()
        observeState()
    }

    private fun setupMap() {
        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(17.0)

        // Kompas
        val compass = CompassOverlay(requireContext(), mapView)
        compass.enableCompass()
        mapView.overlays.add(compass)

        // Moja lokalizacja (telefon)
        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(requireContext()), mapView
        ).also {
            it.enableMyLocation()
            it.enableFollowLocation()
            mapView.overlays.add(it)
        }

        // Ślad lotu
        flightTrack = Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#FF5722")
            outlinePaint.strokeWidth = 4f
        }
        mapView.overlays.add(flightTrack)

        // Trasa waypointów
        waypointRoute = Polyline(mapView).apply {
            outlinePaint.color = Color.parseColor("#2196F3")
            outlinePaint.strokeWidth = 5f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
        mapView.overlays.add(waypointRoute)

        // Marker drona
        droneMarker = Marker(mapView).apply {
            title = "Dron X-Bee 9.5"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = requireContext().getDrawable(R.drawable.ic_drone_marker)
        }

        // Dotknięcie mapy = dodaj waypoint (jeśli aktywny tryb)
        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (isAddingWaypoints) {
                    addWaypointAt(p)
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                addWaypointAt(p)
                return true
            }
        })
        mapView.overlays.add(mapEventsOverlay)
    }

    private fun setupButtons() {
        // Centrum na drona
        binding.btnCenterDrone.setOnClickListener {
            vm.droneLocation.value?.let {
                mapView.controller.animateTo(it)
            } ?: Toast.makeText(requireContext(), "Brak sygnału GPS drona", Toast.LENGTH_SHORT).show()
        }

        // Centrum na mnie
        binding.btnCenterMe.setOnClickListener {
            myLocationOverlay?.myLocation?.let {
                mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            }
        }

        // Dodawanie waypointów
        binding.btnAddWaypoint.setOnClickListener {
            isAddingWaypoints = !isAddingWaypoints
            binding.btnAddWaypoint.alpha = if (isAddingWaypoints) 1.0f else 0.5f
            binding.tvWaypointHint.visibility = if (isAddingWaypoints) View.VISIBLE else View.GONE
        }

        // Wyczyść waypoints
        binding.btnClearWaypoints.setOnClickListener {
            vm.clearWaypoints()
            clearWaypointMarkers()
            Toast.makeText(requireContext(), "Waypoints wyczyszczone", Toast.LENGTH_SHORT).show()
        }

        // Wyczyść ślad
        binding.btnClearTrack.setOnClickListener {
            flightTrackPoints.clear()
            flightTrack?.setPoints(emptyList())
            mapView.invalidate()
        }

        // Przełącz satelita/mapa
        binding.btnMapType.setOnClickListener {
            val isSatellite = mapView.tileProvider.tileSource == TileSourceFactory.MAPNIK
            if (isSatellite) {
                // Użyj innego tile source (Esri World Imagery lub similar)
                mapView.setTileSource(TileSourceFactory.USGS_SAT)
                binding.btnMapType.text = "MAPA"
            } else {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                binding.btnMapType.text = "SATELITA"
            }
        }
    }

    private fun addWaypointAt(point: GeoPoint) {
        vm.addWaypoint(point)
        // Marker zostanie dodany przez obserwator waypointów
        Toast.makeText(requireContext(),
            "Waypoint dodany: ${"%.5f".format(point.latitude)}, ${"%.5f".format(point.longitude)}",
            Toast.LENGTH_SHORT).show()
    }

    private fun observeState() {
        // Pozycja drona
        viewLifecycleOwner.lifecycleScope.launch {
            vm.droneLocation.collect { pos ->
                pos?.let { updateDroneMarker(it) }
            }
        }

        // Waypoints
        viewLifecycleOwner.lifecycleScope.launch {
            vm.waypoints.collect { wps ->
                syncWaypointMarkers(wps)
                updateWaypointRoute(wps)
                binding.tvWaypointCount.text = "Waypoints: ${wps.size}"
            }
        }
    }

    private fun updateDroneMarker(pos: GeoPoint) {
        if (droneMarker?.map == null) {
            mapView.overlays.add(droneMarker)
        }
        droneMarker?.position = pos
        flightTrackPoints.add(pos)
        flightTrack?.setPoints(flightTrackPoints.toList())
        mapView.invalidate()
    }

    private fun syncWaypointMarkers(waypoints: List<Waypoint>) {
        // Usuń markery których nie ma w liście
        val currentIds = waypoints.map { it.id }.toSet()
        waypointMarkers.keys.toList().forEach { id ->
            if (id !in currentIds) {
                mapView.overlays.remove(waypointMarkers[id])
                waypointMarkers.remove(id)
            }
        }

        // Dodaj nowe markery
        waypoints.forEach { wp ->
            if (!waypointMarkers.containsKey(wp.id)) {
                val marker = Marker(mapView).apply {
                    position = wp.geoPoint
                    title = "Waypoint #${wp.id}"
                    snippet = "Alt: ${wp.altitudeM}m"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ ->
                        showBubble()
                        true
                    }
                }
                mapView.overlays.add(marker)
                waypointMarkers[wp.id] = marker
            }
        }
        mapView.invalidate()
    }

    private fun updateWaypointRoute(waypoints: List<Waypoint>) {
        val points = waypoints.map { it.geoPoint }
        waypointRoute?.setPoints(points)
        mapView.invalidate()
    }

    private fun clearWaypointMarkers() {
        waypointMarkers.values.forEach { mapView.overlays.remove(it) }
        waypointMarkers.clear()
        waypointRoute?.setPoints(emptyList())
        mapView.invalidate()
    }

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

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDetach()
        _binding = null
    }
}
