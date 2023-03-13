package com.volard.trackingkind

/**
 * Defines several constants used between [BluetoothService] and the UI.
 */
internal interface Constants {
    companion object {
        // Key names received from the BluetoothService Handler
        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"

        // Message types sent from the BluetoothChatService Handler
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5
    }
}
