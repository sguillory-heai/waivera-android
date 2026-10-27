package com.waivera.waivera.core.data.realtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectivityMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val online = MutableStateFlow(currentlyOnline())
    private val validNetworks = mutableSetOf<Network>()

    override val isOnline: StateFlow<Boolean> = online.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        manager?.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = synchronized(validNetworks) {
                    validNetworks += network
                    online.value = true
                }

                override fun onLost(network: Network) = synchronized(validNetworks) {
                    validNetworks -= network
                    online.value = validNetworks.isNotEmpty()
                }
            },
        )
    }

    @Suppress("ReturnCount")
    private fun currentlyOnline(): Boolean {
        val connectivity = manager ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

@Singleton
class ProcessForegroundMonitor @Inject constructor() : AppForegroundMonitor {
    private val foreground = MutableStateFlow(false)
    override val isForeground: StateFlow<Boolean> = foreground.asStateFlow()

    init {
        // ProcessLifecycleOwner must be observed on the main thread; a Handler avoids needing
        // kotlinx-coroutines-android here.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        foreground.value = true
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        foreground.value = false
                    }
                },
            )
        }
    }
}
