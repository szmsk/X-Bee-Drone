package com.xbeedrone.app.ui.map

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.xbeedrone.app.DroneViewModel
import com.xbeedrone.app.databinding.FragmentMapBinding
import com.xbeedrone.app.model.Waypoint
import kotlinx.coroutines.launch
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

class MapFragment : Fragment() {

    private var _b: FragmentMapBinding? = null
    private val b get() = _b!!
    private val vm: DroneViewModel by activityViewModels()

    private lateinit var map: MapView
    private var myLocOverlay: MyLocationNewOverlay? = null
    private var droneMarker: Marker? = null
    private val waypointMarkers = mutableMapOf<Int, Marker>()
    private val flightTrackPts  = mutableListOf<GeoPoint>()
    private var flightTrack: Polyline? = null
    private var waypointRoute: Polyline? = null
    private var addingWaypoints = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMapBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap(); setupButtons(); observe()
    }

    private fun setupMap() {
        map = b.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(17.0)

        map.overlays.add(CompassOverlay(requireContext(), map).also { it.enableCompass() })

        myLocOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map).also {
            it.enableMyLocation(); map.overlays.add(it)
        }

        flightTrack = Polyline(map).also {
            it.outlinePaint.color = Color.parseColor("#FF5722")
            it.outlinePaint.strokeWidth = 4f
            map.overlays.add(it)
        }

        waypointRoute = Polyline(map).also {
            it.outlinePaint.color = Color.parseColor("#2196F3")
            it.outlinePaint.strokeWidth = 5f
            it.outlinePaint.pathEffect =
                android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
            map.overlays.add(it)
        }

        droneMarker = Marker(map).apply {
            title = "Dron X-Bee 9.5"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (addingWaypoints) { vm.addWaypoint(p); showToast("Waypoint dodany"); return true }
                return false
            }
            override fun longPressHelper(p: GeoPoint): Boolean {
                vm.addWaypoint(p); showToast("Waypoint dodany"); return true
            }
        }))
    }

    private fun setupButtons() {
        b.btnCenterDrone.setOnClickListener {
            vm.droneLocation.value?.let { map.controller.animateTo(it) }
                ?: showToast("Brak GPS drona")
        }
        b.btnCenterMe.setOnClickListener {
            myLocOverlay?.myLocation?.let {
                map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            }
        }
        b.btnAddWaypoint.setOnClickListener {
            addingWaypoints = !addingWaypoints
            b.btnAddWaypoint.alpha = if (addingWaypoints) 1f else 0.5f
            b.tvWaypointHint.visibility = if (addingWaypoints) View.VISIBLE else View.GONE
        }
        b.btnClearWaypoints.setOnClickListener {
            vm.clearWaypoints()
            waypointMarkers.values.forEach { map.overlays.remove(it) }
            waypointMarkers.clear()
            waypointRoute?.setPoints(emptyList())
            map.invalidate()
            showToast("Waypoints wyczyszczone")
        }
        b.btnClearTrack.setOnClickListener {
            flightTrackPts.clear(); flightTrack?.setPoints(emptyList()); map.invalidate()
        }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.droneLocation.collect { pos ->
                pos ?: return@collect
                if (droneMarker?.map == null) map.overlays.add(droneMarker)
                droneMarker?.position = pos
                flightTrackPts.add(pos)
                flightTrack?.setPoints(flightTrackPts.toList())
                map.invalidate()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.waypoints.collect { wps ->
                syncMarkers(wps)
                waypointRoute?.setPoints(wps.map { it.geoPoint })
                b.tvWaypointCount.text = "Waypoints: ${wps.size}"
                map.invalidate()
            }
        }
    }

    private fun syncMarkers(waypoints: List<Waypoint>) {
        val ids = waypoints.map { it.id }.toSet()
        waypointMarkers.keys.toList().forEach { id ->
            if (id !in ids) { map.overlays.remove(waypointMarkers[id]); waypointMarkers.remove(id) }
        }
        waypoints.forEach { wp ->
            if (!waypointMarkers.containsKey(wp.id)) {
                val m = Marker(map).apply {
                    position = wp.geoPoint
                    title = "WP #${wp.id}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(m); waypointMarkers[wp.id] = m
            }
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onResume()  { super.onResume();  map.onResume();  myLocOverlay?.enableMyLocation() }
    override fun onPause()   { super.onPause();   map.onPause();   myLocOverlay?.disableMyLocation() }
    override fun onDestroyView() { map.onDetach(); _b = null; super.onDestroyView() }
}
