package com.mapbox.androidauto.car.navigation.speedlimit

import android.graphics.Rect
import android.location.Location
import com.mapbox.androidauto.car.map.MapboxCarMapObserver
import com.mapbox.androidauto.car.map.MapboxCarMapSurface
import com.mapbox.androidauto.logAndroidAuto
import com.mapbox.examples.androidauto.car.MainCarContext
import com.mapbox.maps.EdgeInsets
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import kotlin.math.roundToInt

/**
 * Create a speed limit sign. This class is demonstrating how to
 * create a renderer. To Create a new speed limit sign experience, try creating a new class.
 */
class CarSpeedLimitRenderer(
    private val mainCarContext: MainCarContext,
) : MapboxCarMapObserver {
    private val speedLimitWidget by lazy { SpeedLimitWidget() }

    private val locationObserver = object : LocationObserver {

        @Suppress("MagicNumber")
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val speedKmph = locationMatcherResult.enhancedLocation.speed / METERS_IN_KILOMETER * SECONDS_IN_HOUR
            when (mainCarContext.mapboxNavigation.navigationOptions.distanceFormatterOptions.unitType) {
                UnitType.IMPERIAL -> {
                    val speedLimit = locationMatcherResult.speedLimit?.speedKmph?.let { speedLimitKmph ->
                        5 * (speedLimitKmph / KILOMETERS_IN_MILE / 5).roundToInt()
                    }
                    val speed = speedKmph / KILOMETERS_IN_MILE
                    speedLimitWidget.update(speedLimit, speed.roundToInt())
                }
                UnitType.METRIC -> {
                    speedLimitWidget.update(locationMatcherResult.speedLimit?.speedKmph, speedKmph.roundToInt())
                }
            }
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no op
        }
    }

    override fun loaded(mapboxCarMapSurface: MapboxCarMapSurface) {
        logAndroidAuto("CarSpeedLimitRenderer carMapSurface loaded")
        mapboxCarMapSurface.style.addPersistentStyleCustomLayer(
            SpeedLimitWidget.SPEED_LIMIT_WIDGET_LAYER_ID,
            speedLimitWidget.viewWidgetHost,
            null
        )
        mainCarContext.mapboxNavigation.registerLocationObserver(locationObserver)
    }

    override fun detached(mapboxCarMapSurface: MapboxCarMapSurface) {
        logAndroidAuto("CarSpeedLimitRenderer carMapSurface detached")
        mainCarContext.mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxCarMapSurface.style.removeStyleLayer(SpeedLimitWidget.SPEED_LIMIT_WIDGET_LAYER_ID)
        speedLimitWidget.clear()
    }

    override fun visibleAreaChanged(visibleArea: Rect, edgeInsets: EdgeInsets) {
        super.visibleAreaChanged(visibleArea, edgeInsets)
        speedLimitWidget.setEdgeInsets(edgeInsets)
    }

    private companion object {
        private const val METERS_IN_KILOMETER = 1000.0
        private const val KILOMETERS_IN_MILE = 1.609
        private const val SECONDS_IN_HOUR = 3600
    }
}
