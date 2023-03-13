package com.volard.trackingkind

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


/**
 * Tools for connection to remote device, initializing
 * listening for incoming connections and exchanging data via RFCOMM protocol
 * and Bluetooth API
 */
class BluetoothService internal constructor(private val mHandler: Handler) {
    // Member fields
    private val mAdapter: BluetoothAdapter
    private var mSecureAcceptThread: AcceptThread? = null
    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null

    /**
     * Return the current connection state.
     */
    @get:Synchronized
    var state: Int
        private set
    private var mNewState: Int

    /**
     * Returns human readable state description
     * @param stateId id of the state
     * @return state's description
     */
    @Synchronized
    fun getStateDescription(stateId: Int): String {
        return try {
            stateDescriptions[stateId]
        } catch (ex: IndexOutOfBoundsException) {
            "Unknown state under index $stateId"
        }
    }

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param handler A Handler to send messages back to the UI Activity
     */
    init {
        mAdapter = BluetoothAdapter.getDefaultAdapter()
        state = STATE_NONE
        mNewState = state
    }

    /**
     * Update UI title according to the current state of the chat connection
     */
    @Synchronized
    private fun updateUserInterfaceTitle() {
        state = state
        Log.i(
            TAG, "Update state: " + getStateDescription(mNewState) + " -> " + getStateDescription(
                state
            )
        )
        mNewState = state

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget()
    }

    /**
     * Start AcceptThread to begin a session in listening (server) mode.
     * Called by the Activity onResume()
     */
    @Synchronized
    fun startListening() {
        Log.i(TAG, "Start listening")

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = AcceptThread(true)
            mSecureAcceptThread!!.start()
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = AcceptThread(false)
            mInsecureAcceptThread!!.start()
        }

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    @Synchronized
    fun connect(device: BluetoothDevice, secure: Boolean) {
        Log.i(TAG, "Connecting to: $device")

        // Cancel any thread attempting to make a connection
        if (state == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device, secure)
        mConnectThread!!.start()

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost() {
        Log.i(TAG, "Connection lost")
        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(Constants.TOAST, "Device connection was lost")
        msg.data = bundle
        mHandler.sendMessage(msg)
        state = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        startListening()
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed() {
        Log.i(TAG, "Connection failed")

        // Send a failure message back to the Activity
        val msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(Constants.TOAST, "Unable to connect device")
        msg.data = bundle
        mHandler.sendMessage(msg)
        state = STATE_NONE
        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        startListening()
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread @SuppressLint("MissingPermission") constructor(
        private val mmDevice: BluetoothDevice,
        secure: Boolean
    ) :
        Thread() {
        private val mmSocket: BluetoothSocket?
        private val mSocketType: String

        init {
            var tmp: BluetoothSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"


            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = if (secure) {
                    mmDevice.createRfcommSocketToServiceRecord(
                        UUID_SECURE
                    )
                } else {
                    mmDevice.createInsecureRfcommSocketToServiceRecord(
                        UUID_INSECURE
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e)
            }
            mmSocket = tmp
            state = STATE_CONNECTING
        }

        @SuppressLint("MissingPermission")
        override fun run() {
            Log.i(
                TAG,
                "BEGIN mConnectThread SocketType:$mSocketType"
            )

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket!!.connect()
            } catch (connectException: IOException) {
                // Unable to connect; close the socket and return.
                Log.e(TAG, "Unable to connect via defined socket")
                try {
                    mmSocket!!.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "Could not close the failed client socket", closeException)
                }
                connectionFailed()
                return
            }
            Log.i(TAG, "Connection attempt succeeded")

            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothService) { mConnectThread = null }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType)
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the successful client socket", e)
            }
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice, socketType: String) {
        Log.i(TAG, "Connected, Socket Type:$socketType")

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket, socketType)
        mConnectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(Constants.DEVICE_NAME, device.name)
        msg.data = bundle
        mHandler.sendMessage(msg)

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * Stop all threads
     */
    @Synchronized
    fun stop() {
        Log.i(TAG, "Stop bluetooth service")
        if (mConnectThread != null) {
            mConnectThread!!.cancel()
            mConnectThread = null
        }
        if (mConnectedThread != null) {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }
        state = STATE_NONE

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    inner class AcceptThread @SuppressLint("MissingPermission") constructor(secure: Boolean) :
        Thread() {
        private val mmServerSocket // local Server socket
                : BluetoothServerSocket?
        private val mSocketType: String

        init {
            var tmp: BluetoothServerSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            // Create a new listening server socket
            try {
                tmp = if (secure) {
                    mAdapter.listenUsingRfcommWithServiceRecord(
                        NAME_SECURE,
                        UUID.fromString("953ef910-3861-4b53-bdd2-8db5ba9fcc72")
                    )
                } else {
                    mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        NAME_INSECURE,
                        UUID.fromString("953ef910-3861-4b53-bdd2-8db5ba9fcc72")
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e)
            }
            mmServerSocket = tmp
            state = STATE_LISTEN
        }

        override fun run() {
            Log.i(TAG, "AcceptThread run: AcceptThread Running...")
            var socket: BluetoothSocket? // local socket

            // Keep listening until exception occurs or a lSocket is returned.
            while (state != STATE_CONNECTED) {
                socket = try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    mmServerSocket!!.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "AcceptThread run: Socket's accept() method failed: " + e.message)
                    break
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@BluetoothService) {
                        when (state) {
                            STATE_LISTEN, STATE_CONNECTING ->                                 // Situation normal. Start the connected thread.
                                connected(
                                    socket, socket.remoteDevice,
                                    mSocketType
                                )
                            STATE_NONE, STATE_CONNECTED ->                                 // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(
                                        TAG,
                                        "Could not close unwanted socket: " + e.message
                                    )
                                }
                        }
                    }
                }
            }
            Log.i(TAG, "AcceptThread run: End AcceptThread.")
        }

        /**
         * Closes the connect socket and causes the thread to finish
         */
        fun cancel() {
            Log.i(TAG, "AcceptThread cancel: Canceling AcceptThread.")
            try {
                mmServerSocket!!.close()
            } catch (e: IOException) {
                Log.e(
                    TAG, "AcceptThread cancel: Could not close the connect socket" +
                            e.message
                )
            }
        }
    }

    /**
     * Write to the ConnectedThread in an un-synchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray?) {
        // Create temporary object
        var r: ConnectedThread?

        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (state != STATE_CONNECTED) return
            r = mConnectedThread
        }

        // Perform the write un-synchronized
        r!!.write(out)
    }

    /**
     * Common Thread for data exchanging
     */
    private inner class ConnectedThread(socket: BluetoothSocket?, socketType: String) :
        Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        private val mmSocket: BluetoothSocket?
        private var buffer // buffer store for the stream
                : ByteArray

        /**
         * Initialize streams to manage out/in data
         * @param socket connected socket to get/push data
         */
        init {
            Log.i(
                TAG,
                "create ConnectedThread: $socketType"
            )
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the input and output streams
            // using temp objects because member streams are final
            try {
                tmpIn = socket!!.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created" + e.message)
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
            state = STATE_CONNECTED
        }

        override fun run() {
            Log.i(TAG, "ConnectedThread run: Connected thread running...")
            buffer = ByteArray(1024)
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (state == STATE_CONNECTED) {
                Log.i(TAG, "ConnectedThread run: Connected thread is listening...")
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream!!.read(buffer)

                    // Send the obtained bytes to the UI activity.
                    val readMsg = mHandler.obtainMessage(
                        Constants.MESSAGE_READ, numBytes, -1,
                        buffer
                    )
                    readMsg.sendToTarget()
                } catch (e: IOException) {
                    Log.e(TAG, "ConnectedThread run: Input stream was disconnected: " + e.message)
                    connectionLost()
                    break
                }
            }
        }

        /**
         * Send data to remote device
         * @param bytes sending data
         */
        fun write(bytes: ByteArray?) {
            try {
                mmOutStream!!.write(bytes)

                // Share the sent message with the UI activity.
                val writtenMsg = mHandler.obtainMessage(
                    Constants.MESSAGE_WRITE, -1, -1, buffer
                )
                writtenMsg.sendToTarget()
                Log.i(TAG, "ConnectedThread write: Wrote to remote device")
            } catch (e: IOException) {
                Log.e(TAG, "ConnectedThread write: Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = mHandler.obtainMessage(Constants.MESSAGE_TOAST)
                val bundle = Bundle()
                bundle.putString(
                    "toast",
                    "Couldn't send data to the other device"
                )
                writeErrorMsg.data = bundle
                mHandler.sendMessage(writeErrorMsg)
            }
        }

        /**
         * Shut down connection
         */
        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket: " + e.message)
            }
        }
    }

    companion object {
        // Debugging
        const val TAG = "BLUETOOTH_SERVICE"

        // Unique UUID for this application
        private val UUID_INSECURE = UUID.fromString("953ef910-3861-4b53-bdd2-8db5ba9fcc72")
        private val UUID_SECURE = UUID.fromString("434ca063-3a16-465f-a8eb-ab59d75fdd8e")

        // Name for the SDP record when creating server socket
        private const val NAME_SECURE = "BluetoothConnectionSecure"
        private const val NAME_INSECURE = "BluetoothConnectionInsecure"

        // Constants that indicate the current connection state
        const val STATE_NONE = 0 // we're doing nothing
        const val STATE_LISTEN = 1 // now listening for incoming connections
        const val STATE_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED = 3 // now connected to a remote device
        private val stateDescriptions: ArrayList<String> = object : ArrayList<String?>() {
            init {
                add("Doing nothing")
                add("Listening")
                add("Connecting")
                add("Connected")
            }
        }
    }
}