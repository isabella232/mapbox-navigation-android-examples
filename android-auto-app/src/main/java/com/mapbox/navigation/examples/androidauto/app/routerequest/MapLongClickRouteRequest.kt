package com.mapbox.navigation.examples.androidauto.app.routerequest

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.mapbox.androidauto.MapboxCarApp
import com.mapbox.androidauto.RoutePreviewState
import com.mapbox.examples.androidauto.car.preview.CarRouteRequest
import com.mapbox.examples.androidauto.car.preview.CarRouteRequestCallback
import com.mapbox.examples.androidauto.car.search.PlaceRecord
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.gestures.OnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigationProvider
import java.util.UUID

class MapLongClickRouteRequest {
    private val carRouteRequest = CarRouteRequest(
        MapboxNavigationProvider.retrieve(),
        MapboxCarApp.carAppServices.location().navigationLocationProvider
    )

    fun observeClicks(mapView: MapView, lifecycle: Lifecycle) {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                mapView.gestures.addOnMapLongClickListener(setRouteClickListener)
            }

            override fun onPause(owner: LifecycleOwner) {
                mapView.gestures.removeOnMapLongClickListener(setRouteClickListener)
            }
        })
    }

    private val setRouteClickListener = OnMapLongClickListener { point ->
        request(point)
        true
    }

    fun request(point: Point) {
        carRouteRequest.request(
            PlaceRecord(
                id = UUID.randomUUID().toString(),
                name = "Long press location",
                coordinate = point
            ),
            object : CarRouteRequestCallback {
                override fun onRoutesReady(
                    placeRecord: PlaceRecord,
                    routes: List<NavigationRoute>
                ) {
                    MapboxNavigationProvider.retrieve().setNavigationRoutes(routes)
                    MapboxCarApp.updateCarAppState(RoutePreviewState)
                }

                override fun onUnknownCurrentLocation() {
                    // Show error message
                }

                override fun onDestinationLocationUnknown() {
                    // Show error message
                }

                override fun onNoRoutesFound() {
                    // Show error message
                }
            }
        )
    }
}
