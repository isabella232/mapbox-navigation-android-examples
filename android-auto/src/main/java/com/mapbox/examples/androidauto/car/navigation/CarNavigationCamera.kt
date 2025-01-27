@file:Suppress("TooManyFunctions")

package com.mapbox.examples.androidauto.car.navigation

import android.graphics.Rect
import android.location.Location
import com.mapbox.androidauto.car.RendererUtils.dpToPx
import com.mapbox.androidauto.car.map.MapboxCarMapObserver
import com.mapbox.androidauto.car.map.MapboxCarMapSurface
import com.mapbox.androidauto.logAndroidAuto
import com.mapbox.examples.androidauto.car.routes.NavigationRoutesProvider
import com.mapbox.examples.androidauto.car.routes.RoutesListener
import com.mapbox.examples.androidauto.car.routes.RoutesProvider
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_INITIAL_ZOOM = 15.0

/**
 * Integrates the Android Auto [MapboxCarMapSurface] with the [NavigationCamera].
 */
class CarNavigationCamera internal constructor(
    private val mapboxNavigation: MapboxNavigation,
    private val initialCarCameraMode: CarCameraMode,
    private val alternativeCarCameraMode: CarCameraMode?,
    private val routesProvider: RoutesProvider,
    private val initialCameraOptions: CameraOptions? = CameraOptions.Builder()
        .zoom(DEFAULT_INITIAL_ZOOM)
        .build()
) : MapboxCarMapObserver {

    private var mapboxCarMapSurface: MapboxCarMapSurface? = null
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    private val _nextCameraMode = MutableStateFlow(alternativeCarCameraMode)
    val nextCameraMode: StateFlow<CarCameraMode?> = _nextCameraMode

    private val overviewPaddingPx by lazy {
        mapboxNavigation.navigationOptions.applicationContext.dpToPx(
            OVERVIEW_PADDING_DP
        )
    }
    private val followingPaddingPx by lazy {
        mapboxNavigation.navigationOptions.applicationContext.dpToPx(
            FOLLOWING_OVERVIEW_PADDING_DP
        )
    }

    private var isLocationInitialized = false

    private val locationObserver = object : LocationObserver {

        override fun onNewRawLocation(rawLocation: Location) {
            // not handled
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            // Initialize the camera at the current location. The next location will
            // transition into the following or overview mode.
            viewportDataSource.onLocationChanged(locationMatcherResult.enhancedLocation)
            viewportDataSource.evaluate()
            if (!isLocationInitialized) {
                isLocationInitialized = true
                val instantTransition = NavigationCameraTransitionOptions.Builder()
                    .maxDuration(0)
                    .build()
                when (initialCarCameraMode) {
                    CarCameraMode.IDLE -> navigationCamera.requestNavigationCameraToIdle()
                    CarCameraMode.FOLLOWING -> navigationCamera.requestNavigationCameraToFollowing(
                        stateTransitionOptions = instantTransition,
                    )
                    CarCameraMode.OVERVIEW -> navigationCamera.requestNavigationCameraToOverview(
                        stateTransitionOptions = instantTransition,
                    )
                }
            }
        }
    }

    private val routesListener = RoutesListener { routes ->
        if (routes.isEmpty()) {
            viewportDataSource.clearRouteData()
        } else {
            viewportDataSource.onRouteChanged(routes.first())
        }
        viewportDataSource.evaluate()
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        viewportDataSource.onRouteProgressChanged(routeProgress)
        viewportDataSource.evaluate()
    }

    constructor(
        mapboxNavigation: MapboxNavigation,
        initialCarCameraMode: CarCameraMode,
        alternativeCarCameraMode: CarCameraMode?,
        initialCameraOptions: CameraOptions? = CameraOptions.Builder()
            .zoom(DEFAULT_INITIAL_ZOOM)
            .build(),
    ) : this(
        mapboxNavigation,
        initialCarCameraMode,
        alternativeCarCameraMode,
        NavigationRoutesProvider(mapboxNavigation),
        initialCameraOptions,
    )

    override fun loaded(mapboxCarMapSurface: MapboxCarMapSurface) {
        super.loaded(mapboxCarMapSurface)
        this.mapboxCarMapSurface = mapboxCarMapSurface
        logAndroidAuto("CarNavigationCamera loaded $mapboxCarMapSurface")

        val mapboxMap = mapboxCarMapSurface.mapSurface.getMapboxMap()
        initialCameraOptions?.let { mapboxMap.setCamera(it) }
        viewportDataSource = MapboxNavigationViewportDataSource(
            mapboxCarMapSurface.mapSurface.getMapboxMap()
        )
        navigationCamera = NavigationCamera(
            mapboxMap,
            mapboxCarMapSurface.mapSurface.camera,
            viewportDataSource
        )

        mapboxNavigation.registerLocationObserver(locationObserver)
        routesProvider.registerRoutesListener(routesListener)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
    }

    override fun visibleAreaChanged(visibleArea: Rect, edgeInsets: EdgeInsets) {
        super.visibleAreaChanged(visibleArea, edgeInsets)
        logAndroidAuto("CarNavigationCamera visibleAreaChanged $visibleArea $edgeInsets")

        viewportDataSource.overviewPadding = EdgeInsets(
            edgeInsets.top + overviewPaddingPx,
            edgeInsets.left + overviewPaddingPx,
            edgeInsets.bottom + overviewPaddingPx,
            edgeInsets.right + overviewPaddingPx
        )

        val visibleHeight = visibleArea.bottom - visibleArea.top
        viewportDataSource.followingPadding = EdgeInsets(
            edgeInsets.top + followingPaddingPx,
            edgeInsets.left + followingPaddingPx,
            edgeInsets.bottom + visibleHeight * BOTTOM_FOLLOWING_FRACTION,
            edgeInsets.right + followingPaddingPx
        )

        viewportDataSource.evaluate()
    }

    override fun scroll(
        mapboxCarMapSurface: MapboxCarMapSurface,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        logAndroidAuto("CarNavigationCamera handling pan")
        updateCameraMode(CarCameraMode.IDLE)

        return false
    }

    override fun scale(
        mapboxCarMapSurface: MapboxCarMapSurface,
        anchor: ScreenCoordinate,
        fromZoom: Double,
        toZoom: Double
    ): Boolean {
        updateCameraMode(CarCameraMode.IDLE)

        return toZoom.coerceIn(MIN_ZOOM_OUT, MAX_ZOOM_IN) != toZoom
    }

    fun updateCameraMode(carCameraMode: CarCameraMode) {
        _nextCameraMode.value = if (carCameraMode != initialCarCameraMode) {
            initialCarCameraMode
        } else {
            alternativeCarCameraMode
        }
        when (carCameraMode) {
            CarCameraMode.IDLE -> navigationCamera.requestNavigationCameraToIdle()
            CarCameraMode.FOLLOWING -> navigationCamera.requestNavigationCameraToFollowing()
            CarCameraMode.OVERVIEW -> navigationCamera.requestNavigationCameraToOverview()
        }
    }

    /**
     * Function dedicated to zoom in map action buttons.
     */
    fun zoomInAction() = scaleEaseBy(ZOOM_ACTION_DELTA)

    /**
     * Function dedicated to zoom in map action buttons.
     */
    fun zoomOutAction() = scaleEaseBy(-ZOOM_ACTION_DELTA)

    /**
     * If true the camera may recalculate and update the zoom level. If false
     * the feature is disabled.
     */
    fun zoomUpdatesAllowed(allowed: Boolean) {
        viewportDataSource.options.followingFrameOptions.zoomUpdatesAllowed = allowed
        viewportDataSource.options.overviewFrameOptions.zoomUpdatesAllowed = allowed
    }

    /**
     * Indicates whether the following camera is configured to recalculate and update the zoom level.
     */
    fun followingZoomUpdatesAllowed(): Boolean {
        return if (this::viewportDataSource.isInitialized) {
            viewportDataSource.options.followingFrameOptions.zoomUpdatesAllowed
        } else {
            true
        }
    }

    private fun scaleEaseBy(delta: Double) {
        val mapSurface = mapboxCarMapSurface?.mapSurface
        val fromZoom = mapSurface?.getMapboxMap()?.cameraState?.zoom ?: return
        val toZoom = (fromZoom + delta).coerceIn(MIN_ZOOM_OUT, MAX_ZOOM_IN)
        mapSurface.camera.easeTo(cameraOptions { zoom(toZoom) })
    }

    override fun detached(mapboxCarMapSurface: MapboxCarMapSurface) {
        super.detached(mapboxCarMapSurface)
        logAndroidAuto("CarNavigationCamera detached $mapboxCarMapSurface")

        routesProvider.unregisterRoutesListener(routesListener)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        this.mapboxCarMapSurface = null
        isLocationInitialized = false
    }

    private companion object {
        /**
         * While following the location puck, inset the bottom by 1/3 of the screen.
         */
        private const val BOTTOM_FOLLOWING_FRACTION = 1.0 / 3.0

        /**
         * The following state will go into a zero-pitch state which requires padding for the left
         * top and right edges.
         */
        private const val FOLLOWING_OVERVIEW_PADDING_DP = 5

        /**
         * While overviewing a route, add padding to the viewport.
         */
        private const val OVERVIEW_PADDING_DP = 5

        /**
         * When zooming the camera by a delta, this is an estimated min-zoom.
         */
        private const val MIN_ZOOM_OUT = 6.0

        /**
         * When zooming the camera by a delta, this is an estimated max-zoom.
         */
        private const val MAX_ZOOM_IN = 20.0

        /**
         * Simple zoom delta to associate with the zoom action buttons.
         */
        private const val ZOOM_ACTION_DELTA = 0.5
    }
}
