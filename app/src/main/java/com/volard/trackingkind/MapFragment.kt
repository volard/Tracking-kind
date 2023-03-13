package com.volard.trackingkind

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.Projection
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*


class MapFragment : Fragment(), GoogleMap.OnMarkerClickListener,
    OnMapReadyCallback {
    // Debug
    // private final String TAG = "TRACKING_MAP";
    private val TAG: String = BluetoothService.TAG

    // Member fields
    private val markers: ArrayList<Marker> = ArrayList<Marker>()
    var lightTheme = true
    private var mGoogleMap: GoogleMap? = null
    private var showTitles = true
    private val mHandler = Handler()
    private var mContext: Context? = null

    /**
     * Returns random float value representing HUE value
     * @return random HUE float value
     */
    private val randomHue: Float
        private get() {
            val MIN = 0f
            val MAX = 360f
            val random: Random = SecureRandom()
            return MIN + random.nextFloat() * (MAX - MIN)
        }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * In this case, we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to
     * install it inside the SupportMapFragment. This method will only be triggered once the
     * user has installed Google Play services and returned to the app.
     */
    fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap

        // Initial location to show
//        LatLng Irkutsk = new LatLng(52.25077493211072, 104.34563132285335);
        // Set opportunity to see indoor map
//        googleMap.setIndoorEnabled(true);

        // Set the marker
//        Objects.requireNonNull(googleMap.addMarker(new MarkerOptions()
//                .position(Irkutsk)
//                .icon(BitmapDescriptorFactory.defaultMarker(getRandomHue()))
//                .title("Sunny road"))).showInfoWindow();
        googleMap.moveCamera(CameraUpdateFactory.zoomTo(15))
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(LatLng(52.251080, 104.356545)))
        googleMap.setOnMarkerClickListener(this)
        toggleTheme()
    }

    fun toggleTheme() {
        try {
            val themeFileName: String
            themeFileName = if (lightTheme) {
                "mapstyle_night.json"
            } else {
                "simple_map_theme.json"
            }
            lightTheme = !lightTheme
            val jsonContent = mContext!!.assets.open(themeFileName)
            var jsonStyle: String
            Scanner(jsonContent, StandardCharsets.UTF_8.name()).use { scanner ->
                jsonStyle = scanner.useDelimiter("\\A").next()
            }
            if (jsonStyle == null) throw NullPointerException()
            val style = MapStyleOptions(jsonStyle)
            mGoogleMap.setMapStyle(style)
        } catch (e: IOException) {
            Log.e(TAG, "Style json asset file is unavailable or doesn't exist")
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error parsing json asset style file")
        }
    }

    fun animateMarker(
        marker: Marker, toPosition: LatLng,
        hideMarker: Boolean
    ) {
        val start = SystemClock.uptimeMillis()
        val proj: Projection = mGoogleMap.getProjection()
        val startPoint: Point = proj.toScreenLocation(marker.getPosition())
        val startLatLng: LatLng = proj.fromScreenLocation(startPoint)
        val duration: Long = 500
        val interpolator: Interpolator = LinearInterpolator()
        mHandler.post(object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val t = interpolator.getInterpolation(elapsed.toFloat() / duration)
                val lng: Double = t * toPosition.longitude + (1 - t)
                * startLatLng.longitude
                val lat: Double = t * toPosition.latitude + (1 - t)
                * startLatLng.latitude
                marker.setPosition(LatLng(lat, lng))
                if (t < 1.0) {
                    // Post again 16ms later.
                    mHandler.postDelayed(this, 16)
                } else {
                    if (hideMarker) {
                        marker.setVisible(false)
                    } else {
                        marker.setVisible(true)
                    }
                }
            }
        })
    }

    fun toggleTitles() {
        if (showTitles) {
            for (marker in markers) {
                marker.hideInfoWindow()
            }
        } else {
            for (marker in markers) {
                marker.showInfoWindow()
            }
        }
        showTitles = !showTitles
    }

    // NOTE: we will use _title_ to show human-readable name of a marker
    // NOTE: we will use _tag_ to show defined by us id of a marker
    private fun createNewMarker(id: Int, latitude: Double, longitude: Double) {
        val newMarker: Marker = mGoogleMap.addMarker(
            MarkerOptions()
                .position(LatLng(latitude, longitude))
                .icon(BitmapDescriptorFactory.defaultMarker(randomHue))
        )
        if (newMarker != null) {
            newMarker.setTag(id)
            markers.add(newMarker)

            // This causes the marker to bounce into position where it was created
            val start = SystemClock.uptimeMillis()
            val duration: Long = 500
            val interpolator: Interpolator = BounceInterpolator()
            mHandler.post(object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - start
                    val t = Math.max(
                        1 - interpolator.getInterpolation(elapsed.toFloat() / duration), 0f
                    )
                    newMarker.setAnchor(0.5f, 1.0f + 2 * t)
                    if (t > 0.0) {
                        // Post again 16ms later.
                        mHandler.postDelayed(this, 16)
                    }
                }
            })
            if (showTitles) {
                newMarker.showInfoWindow()
            }
            Log.i(TAG,
                "New marker was created with tag = " + Objects.requireNonNull(newMarker.getTag())
                    .toString()
            )
        } else {
            Log.e(TAG, "map: Can't create the new marker with tag = $id")
            mHandler.post {
                Toast.makeText(
                    mContext,
                    "Не могу создать новую метку, хотя данные поступили",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun updateMarkerPosition(id: Int, latitude: Double, longitude: Double) {
        for (marker in markers) {
            if (marker.getTag() as Int == id) {
                animateMarker(marker, LatLng(latitude, longitude), false)
                return
            }
        }
        createNewMarker(id, latitude, longitude)
        //        Log.e(TAG, "map: Can't update position of the marker with id = " + id);
//        mHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                Toast.makeText(mContext,
//                        "Не могу обновить позицию метки, хотя данные поступили",
//                        Toast.LENGTH_SHORT).show();
//            }
//        });
    }

    // ======================== OVERRIDES ========================
    fun onMarkerClick(marker: Marker): Boolean {
        mHandler.post { }
        val result = Bundle()
        result.putString("bundleKey", Objects.requireNonNull(marker.getTag()).toString())
        getParentFragmentManager().setFragmentResult("requestKey", result)

        // We return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false
    }

    // The onCreateView method is called when Fragment should create its View object hierarchy,
    // either dynamically or via XML layout inflation.
    fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Defines the xml file for the fragment
        mContext = requireActivity().getBaseContext()
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    // This event is triggered soon after onCreateView().
    // Any view setup should occur here.  E.g., view lookups and attaching view listeners.
    fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment: SupportMapFragment =
            getChildFragmentManager().findFragmentById(R.id.map) as SupportMapFragment
        if (mapFragment != null) {
            mapFragment.getMapAsync(this)
        }
    }

    fun getMarker(id: Int): Marker? {
        for (marker in markers) {
            if (marker.getTag() as Int == id) {
                return marker
            }
        }
        return null
    }
}