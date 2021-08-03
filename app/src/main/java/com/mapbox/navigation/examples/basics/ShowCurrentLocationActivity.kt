package com.mapbox.navigation.examples.basics

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.databinding.MapboxActivityUserCurrentLocationBinding
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider

/**
 * The example demonstrates how to listen to your own location updates and represent it on the map.
 *
 * Before running the example make sure you do the following:
 * - Put your access_token in the correct place inside [app/src/main/res/values/mapbox_access_token.xml].
 *   If not present then add this file at the location mentioned above and add the following
 *   content to it.
 *   <?xml version="1.0" encoding="utf-8"?>
 *   <resources xmlns:tools="http://schemas.android.com/tools">
 *       <string name="mapbox_access_token">YOUR_ACCESS_TOKEN_HERE</string>
 *   </resources>
 * - Add MAPBOX_DOWNLOADS_TOKEN to your USER_HOME»/.gradle/gradle.properties file.
 *   To find out how to get your MAPBOX_DOWNLOADS_TOKEN follow these steps.
 *   https://docs.mapbox.com/android/beta/navigation/guides/install/#configure-credentials
 *
 * The example assumes that you have granted location permissions and does not enforce it. Since,
 * it's a standard procedure to ask for runtime permissions the example doesn't implements that
 * piece of code. However, this permission is essential for the proper functioning of this example.
 *
 * How to use this example:
 * - Click on the example with title (Render current location on a map) from the list of examples.
 * - You should see a map view with the camera transitioning to your current location.
 * - A blue circular puck should be visible at your current location.
 */
class ShowCurrentLocationActivity : AppCompatActivity() {

    private val navigationLocationProvider = NavigationLocationProvider()
    private val locationObserver = object : LocationObserver {
        /**
         * Invoked as soon as the [Location] is available.
         */
        override fun onRawLocationChanged(rawLocation: Location) {
            // Not implemented in this example. However, if you want you can also
            // use this callback to get location updates, but as the name suggests
            // these are raw location updates which are usually noisy.
        }

        /**
         * Provides the best possible location update, snapped to the route or
         * map-matched to the road if possible.
         */
        override fun onEnhancedLocationChanged(
            enhancedLocation: Location,
            keyPoints: List<Location>
        ) {
            navigationLocationProvider.changePosition(
                enhancedLocation,
                keyPoints,
            )
            // Invoke this method to move the camera to your current location.
            updateCamera(enhancedLocation)
        }
    }

    private lateinit var mapboxMap: MapboxMap
    private lateinit var mapboxNavigation: MapboxNavigation
    private lateinit var locationComponent: LocationComponentPlugin
    private lateinit var binding: MapboxActivityUserCurrentLocationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = MapboxActivityUserCurrentLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mapboxMap = binding.mapView.getMapboxMap()
        // Instantiate the location component which is the key component to fetch location updates.
        locationComponent = binding.mapView.location.apply {
            setLocationProvider(navigationLocationProvider)

            // Uncomment this block of code if you want to see a circular puck with arrow.
            /*
            locationPuck = LocationPuck2D(
                bearingImage = ContextCompat.getDrawable(
                    this@ShowCurrentLocationActivity,
                    R.drawable.mapbox_navigation_puck_icon
                )
            )
            */

            // When true, the blue circular puck is shown on the map. If set to false, user
            // location in the form of puck will not be shown on the map.
            enabled = true
        }

        init()
    }

    private fun init() {
        initStyle()
        initNavigation()
    }

    @SuppressLint("MissingPermission")
    private fun initNavigation() {
        mapboxNavigation = MapboxNavigation(
            NavigationOptions.Builder(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        ).apply {
            // This is important to call as the [LocationProvider] will only start sending
            // location updates when the trip session has started.
            startTripSession()
            // Register the location observer to listen to location updates received from the
            // location provider
            registerLocationObserver(locationObserver)
        }
    }

    private fun initStyle() {
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS)
    }

    private fun updateCamera(location: Location) {
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(1500L).build()
        binding.mapView.camera.easeTo(
            CameraOptions.Builder()
                // Centers the camera to the lng/lat specified.
                .center(Point.fromLngLat(location.longitude, location.latitude))
                // specifies the zoom value. Increase or decrease to zoom in or zoom out
                .zoom(12.0)
                // specify frame of reference from the center.
                .padding(EdgeInsets(500.0, 0.0, 0.0, 0.0))
                .build(),
            mapAnimationOptions
        )
    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        super.onStart()
        // make sure that map view is started
        binding.mapView.onStart()
        // Start the location component plugin
        locationComponent.onStart()
    }

    override fun onStop() {
        super.onStop()
        // make sure that map view is stopped
        binding.mapView.onStop()
        // Stop the location component plugin
        locationComponent.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // make sure that map view is destroyed to avoid leaks.
        binding.mapView.onDestroy()
        // make sure to stop the trip session. In this case it is being called inside `onDestroy`.
        mapboxNavigation.stopTripSession()
        // make sure to unregister the observer you have registered.
        mapboxNavigation.unregisterLocationObserver(locationObserver)
    }
}
