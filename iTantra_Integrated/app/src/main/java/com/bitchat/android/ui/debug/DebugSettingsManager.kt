package com.bitchat.android.ui.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DebugSettingsManager {
    val meshGossipEnabled = true
    val logWifiAwareLifecycle = false
    val logWifiAwareErrors = false
    val mockWifiAwareRtt = false
    val wifiAwareMockRttDelayMs: StateFlow<Int> = MutableStateFlow(0)
    val wifiAwareMockRttJitterMs: StateFlow<Int> = MutableStateFlow(0)
    val wifiAwareMockRttFailureRate: StateFlow<Double> = MutableStateFlow(0.0)
    
    fun setWifiAwareMockRttDelay(v: Int) {}
    fun setWifiAwareMockRttJitter(v: Int) {}
    fun setWifiAwareMockRttFailureRate(v: Double) {}
}
