package com.bitchat.android.utils

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.getSystemService
import kotlin.math.sqrt

object DeviceUtils {

    /**
     * Determines if the current device is a tablet based on screen size and density.
     * Uses multiple criteria to accurately detect tablets vs phones.
     */
    fun isTablet(context: Context): Boolean {
        val windowManager = context.getSystemService<WindowManager>()
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(displayMetrics)

        // Calculate screen size in inches
        val widthInches = displayMetrics.widthPixels / displayMetrics.xdpi
        val heightInches = displayMetrics.heightPixels / displayMetrics.ydpi
        val diagonalInches = sqrt((widthInches * widthInches) + (heightInches * heightInches))

        // Check if device has tablet configuration
        val configuration = context.resources.configuration
        val isLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        val isXLargeScreen = (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE

        // A device is considered a tablet if:
        // 1. Screen diagonal is 7 inches or larger, OR
        // 2. Configuration indicates large or xlarge screen, OR
        // 3. Smallest width is 600dp or more (sw600dp)
        val smallestWidthDp = context.resources.configuration.smallestScreenWidthDp

        return diagonalInches >= 7.0 || isLargeScreen || isXLargeScreen || smallestWidthDp >= 600
    }

    /**
     * Gets the user-friendly device name for display in the mesh network.
     * Tries Bluetooth name -> System device name -> Manufacturer + Model.
     */
    fun getDeviceName(context: Context): String {
        try {
            val btAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            val btName = btAdapter?.name
            if (!btName.isNullOrBlank() && btName != "null") {
                return btName.trim()
            }
        } catch (_: Exception) {}

        try {
            val deviceName = android.provider.Settings.Global.getString(
                context.contentResolver,
                android.provider.Settings.Global.DEVICE_NAME
            )
            if (!deviceName.isNullOrBlank() && deviceName != "null") {
                return deviceName.trim()
            }
        } catch (_: Exception) {}

        try {
            val btName = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "bluetooth_name"
            )
            if (!btName.isNullOrBlank() && btName != "null") {
                return btName.trim()
            }
        } catch (_: Exception) {}

        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() 
        }
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.trim()
        } else {
            "$manufacturer $model".trim()
        }
    }
}

