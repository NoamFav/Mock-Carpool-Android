package com.example.mock_carpool

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.LatLng
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.tooling.preview.Preview
import com.google.maps.android.compose.MarkerState
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.CameraPosition

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

                Marker(
                    MarkerState(routePoints.first()),
                    title = "Start"
                )

                Marker(
                    MarkerState(routePoints.last()),
                    title = "End"
                )

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
                    searchForRoute(startLocation, endLocation, context) { routeLatLngs, distance, time ->
                        routePoints = routeLatLngs
                        routeDistance = distance
                        routeTravelTime = time
                        isRouteSearching = false
                    }
                },
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
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Get Route",
                    tint = Color.White
                )
            }
        }
    }
}

fun searchForRoute(
    start: String,
    end: String,
    context: Context,
    onRouteCalculated: (List<LatLng>, String, String) -> Unit
) {
    val requestQueue: RequestQueue = Volley.newRequestQueue(context)
    val url = "https://maps.googleapis.com/maps/api/directions/json?" +
            "origin=$start&destination=$end&key=AIzaSyCJK3XUqkqAbk0nON4PDK6CeL3uIBs2CU0"

    val jsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
        { response ->
            try {
                val routes = response.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")

                    val legs = route.getJSONArray("legs")
                    val leg = legs.getJSONObject(0)

                    // Get distance and duration
                    val distance = leg.getJSONObject("distance").getString("text")
                    val duration = leg.getJSONObject("duration").getString("text")

                    // Decode polyline to get list of LatLng points
                    val routeLatLngs = decodePolyline(overviewPolyline)

                    onRouteCalculated(routeLatLngs, distance, duration)
                } else {
                    onRouteCalculated(emptyList(), "No route found", "N/A")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onRouteCalculated(emptyList(), "Error", "Error")
            }
        },
        { error ->
            error.printStackTrace()
            onRouteCalculated(emptyList(), "Error", "Error")
        })

    requestQueue.add(jsonObjectRequest)
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