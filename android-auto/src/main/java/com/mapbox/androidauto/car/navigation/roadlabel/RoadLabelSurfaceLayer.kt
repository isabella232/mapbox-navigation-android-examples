package com.mapbox.androidauto.car.navigation.roadlabel

import android.graphics.Color
import androidx.car.app.CarContext
import androidx.car.app.Screen
import com.mapbox.androidauto.car.map.MapboxCarMap
import com.mapbox.androidauto.car.map.MapboxCarMapSurface
import com.mapbox.androidauto.logAndroidAuto
import com.mapbox.androidauto.logAndroidAutoFailure
import com.mapbox.androidauto.surfacelayer.CarSurfaceLayer
import com.mapbox.androidauto.surfacelayer.textview.CarTextLayerHost
import com.mapbox.maps.LayerPosition
import com.mapbox.maps.plugin.locationcomponent.LocationComponentConstants
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.road.model.RoadComponent
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.ui.shield.api.MapboxRouteShieldApi
import com.mapbox.navigation.ui.shield.model.RouteShield

/**
 * This will show the current road name at the bottom center of the screen.
 *
 * In your [Screen], create an instance of this class and enable by
 * registering it to the [MapboxCarMap.registerObserver]. Disable by
 * removing the listener with [MapboxCarMap.unregisterObserver].
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class RoadLabelSurfaceLayer(
    val carContext: CarContext,
    val mapboxNavigation: MapboxNavigation,
    private val mapboxCarMap: MapboxCarMap,
) : CarSurfaceLayer() {

    private val roadLabelRenderer = RoadLabelRenderer(carContext.resources)
    private val carTextLayerHost = CarTextLayerHost()
    private val routeShieldApi = MapboxRouteShieldApi()

    private val roadNameObserver = object : RoadNameObserver(mapboxNavigation, routeShieldApi, mapboxCarMap) {

        override fun onRoadUpdate(road: List<RoadComponent>, shields: List<RouteShield>) {
            val bitmap = roadLabelRenderer.render(road, shields, roadLabelOptions())
            carTextLayerHost.offerBitmap(bitmap)
        }
    }

    override fun children() = listOf(carTextLayerHost.mapScene)

    override fun loaded(mapboxCarMapSurface: MapboxCarMapSurface) {
        logAndroidAuto("RoadLabelSurfaceLayer carMapSurface loaded")
        super.loaded(mapboxCarMapSurface)

        val style = mapboxCarMapSurface.style

        val aboveLayer = style.styleLayers.last().id.takeUnless {
            it == BELOW_LAYER
        }

        style.addPersistentStyleCustomLayer(
            layerId = CAR_NAVIGATION_VIEW_LAYER_ID,
            carTextLayerHost,
            LayerPosition(aboveLayer, BELOW_LAYER, null)
        ).error?.let {
            logAndroidAutoFailure("Add custom layer exception $it")
        }

        val bitmap = roadLabelRenderer.render(
            roadNameObserver.currentRoad,
            roadNameObserver.currentShields,
            roadLabelOptions()
        )
        carTextLayerHost.offerBitmap(bitmap)
        mapboxNavigation.registerLocationObserver(roadNameObserver)
    }

    override fun detached(mapboxCarMapSurface: MapboxCarMapSurface) {
        logAndroidAuto("RoadLabelSurfaceLayer carMapSurface detached")
        mapboxCarMapSurface.style.removeStyleLayer(CAR_NAVIGATION_VIEW_LAYER_ID)
        mapboxNavigation.unregisterLocationObserver(roadNameObserver)
        routeShieldApi.cancel()
        super.detached(mapboxCarMapSurface)
    }

    private fun roadLabelOptions(): RoadLabelOptions =
        if (carContext.isDarkMode) {
            DARK_OPTIONS
        } else {
            LIGHT_OPTIONS
        }

    private companion object {
        private const val CAR_NAVIGATION_VIEW_LAYER_ID = "car_road_label_layer_id"
        private const val BELOW_LAYER = LocationComponentConstants.LOCATION_INDICATOR_LAYER

        private val DARK_OPTIONS = RoadLabelOptions.Builder()
            .shadowColor(null)
            .roundedLabelColor(Color.BLACK)
            .textColor(Color.WHITE)
            .build()

        private val LIGHT_OPTIONS = RoadLabelOptions.Builder()
            .roundedLabelColor(Color.WHITE)
            .textColor(Color.BLACK)
            .build()
    }
}
