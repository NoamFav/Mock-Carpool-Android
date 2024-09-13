package com.example.mock_carpool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarpoolApp()
        }
    }
}

@Preview
@Composable
fun CarpoolAppPreview() {
    MainActivity()
}

@Composable
fun CarpoolApp() {
    var startLocation by remember { mutableStateOf("") }
    var endLocation by remember { mutableStateOf("") }
    var routeDistance by remember { mutableStateOf("") }
    var routeTravelTime by remember { mutableStateOf("") }
    var isRouteSearching by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.7749, -122.4194), 10f)
    }

    // Get the current context
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = PaddingValues(16.dp)
        ) {
            // Draw the route polyline using decoded route points
            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    color = Color.Blue,
                    width = 5f
                )

                Marker(MarkerState(routePoints.first()), title = "Start")

                Marker(MarkerState(routePoints.last()), title = "End")

                // Move the camera to see the entire route
                cameraPositionState.position = CameraPosition.builder()
                    .target(routePoints.first())
                    .zoom(10f)
                    .build()
            }
        }

        // Display route info
        if (routeDistance.isNotEmpty() && routeTravelTime.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(Color.DarkGray.copy(alpha = 0.8f))
                    .padding(16.dp)
            ) {
                Text("Distance: $routeDistance", fontSize = 20.sp, color = Color.White)
                Text("Travel Time: $routeTravelTime", fontSize = 18.sp, color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            // Start Location Text Field
            TextField(
                value = startLocation,
                onValueChange = { startLocation = it },
                label = { Text("Start location") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // End Location Text Field
            TextField(
                value = endLocation,
                onValueChange = { endLocation = it },
                label = { Text("End location") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Get Route Button
            Button(
                onClick = {
                    isRouteSearching = true
                    // Call the search function, passing the context
                    searchForRoute(startLocation, endLocation, Volley.newRequestQueue(context)) { result ->
                        when (result) {
                            is RouteResult.Success -> {
                                routePoints = result.route
                                routeDistance = result.distance
                                routeTravelTime = result.duration
                            }
                            is RouteResult.Failure -> {
                                routeDistance = ""
                                routeTravelTime = ""
                                routePoints = emptyList()
                            }
                        }
                        isRouteSearching = false
                    }
                },
                enabled = !isRouteSearching,
                modifier = Modifier
                    .size(70.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = if (isRouteSearching) listOf(Color.Gray, Color.Gray)
                            else listOf(Color.Blue, Color.Magenta)
                        ),
                        shape = CircleShape
                    ),
                contentPadding = PaddingValues(),
                colors = ButtonDefaults.buttonColors()
            ) {
                if (isRouteSearching) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Get Route",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

sealed class RouteResult {
    data class Success(val route: List<LatLng>, val distance: String, val duration: String) : RouteResult()
    data class Failure(val message: String) : RouteResult()
}

fun searchForRoute(
    start: String,
    end: String,
    requestQueue: RequestQueue, // Injected dependency
    onRouteCalculated: (RouteResult) -> Unit
) {
    val url = buildDirectionsUrl(start, end, "AIzaSyCJK3XUqkqAbk0nON4PDK6CeL3uIBs2CU0")

    val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
        { response ->
            try {
                response.getJSONArray("routes").optJSONObject(0)?.let { route ->
                    val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
                    val leg = route.getJSONArray("legs").getJSONObject(0)
                    val distance = leg.getJSONObject("distance").getString("text")
                    val duration = leg.getJSONObject("duration").getString("text")
                    val routeLatLngs = decodePolyline(overviewPolyline)
                    onRouteCalculated(RouteResult.Success(routeLatLngs, distance, duration))
                } ?: onRouteCalculated(RouteResult.Failure("No route found"))
            } catch (e: Exception) {
                onRouteCalculated(RouteResult.Failure("Error parsing response: ${e.message}"))
            }
        },
        { error ->
            onRouteCalculated(RouteResult.Failure("Network Error: ${error.message}"))
        })

    requestQueue.add(jsonObjectRequest)
}

private fun buildDirectionsUrl(start: String, end: String, apiKey: String): String {
    return "https://maps.googleapis.com/maps/api/directions/json?" +
            "origin=$start&destination=$end&key=$apiKey"
}

// Function to decode the polyline from encoded format
fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1f shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val latLng = LatLng(
            lat.toDouble() / 1E5,
            lng.toDouble() / 1E5
        )
        poly.add(latLng)
    }

    return poly
}