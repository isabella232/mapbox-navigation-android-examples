package com.mapbox.navigation.examples.basics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.ExperimentalMapboxNavigationAPI
import com.mapbox.navigation.examples.databinding.MapboxActivityShowCustomStatusBinding
import com.mapbox.navigation.ui.status.model.StatusFactory.buildStatus as status
import com.mapbox.navigation.ui.voice.R as Mapbox_R

@OptIn(ExperimentalMapboxNavigationAPI::class)
class ShowCustomStatusActivity : AppCompatActivity() {

    private lateinit var binding: MapboxActivityShowCustomStatusBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MapboxActivityShowCustomStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapView.apply {
            scalebar.enabled = false
            gestures.addOnMapClickListener(onMapClickListener)
            getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)
        }
    }

    private val statusMessages = mutableListOf(
        status(
            message = "Just text status",
            duration = 2000,
        ),
        status(
            message = "Status with text and a spinner",
            duration = 2000,
            spinner = true
        ),
        status(
            message = "Status with text and an icon",
            duration = 2000,
            icon = Mapbox_R.drawable.mapbox_ic_sound_off
        ),
        status(
            message = "Sticky status",
            duration = 0
        ),
        status(
            message = "Sticky status (without animation)",
            duration = 0,
            animated = false
        )
    )

    private fun nextStatusMessage() = statusMessages.removeFirst().also {
        // adding back to the queue
        statusMessages.add(it)
    }

    private val onMapClickListener = OnMapClickListener {
        // show next status message on map click
        binding.statusView.render(nextStatusMessage())

        false
    }
}
