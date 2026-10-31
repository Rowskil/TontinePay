package com.example.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        // Emit initial status
        val initialCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val initialConnected = initialCapabilities != null &&
                (initialCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        initialCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        initialCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        trySend(initialConnected)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
        }
    }.distinctUntilChanged()
}
