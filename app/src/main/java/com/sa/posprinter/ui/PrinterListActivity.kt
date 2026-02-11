package com.sa.posprinter.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.gson.Gson
import com.sa.posprinter.R
import com.sa.posprinter.util.PrinterPreference
import java.io.IOException
import java.util.UUID

class PrinterListActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var listView: ListView
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceListAdapter

    // Bluetooth connection variables
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectThread: ConnectThread? = null
    private lateinit var printerPref : PrinterPreference


    // Common UUID for SPP (Serial Port Profile) - used by most thermal printers
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val REQUEST_CODE_BLUETOOTH = 1001
    private val TAG = "PreviewActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer_list)

        setupToolbar()

        listView = findViewById(R.id.listView)
        val btnScan = findViewById<Button>(R.id.btnScan)

        // Initialize SharedPreferences
        printerPref = PrinterPreference(this)

        // Use BluetoothManager (modern API)
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter ?: run {
            showErrorDialog("Bluetooth","Bluetooth not supported")
            finish()
            return
        }

        // Initialize adapter
        deviceAdapter = DeviceListAdapter { device ->
            printerPref.savePrinter(
                safeDeviceName(device),
                safeDeviceAddress(device),
                "58mm"
            )
            deviceAdapter.notifyDataSetChanged()
            Toast.makeText(this, "Printer data save successfully", Toast.LENGTH_SHORT).show()
        }
        listView.adapter = deviceAdapter

        if (hasBluetoothPermission()) {
            loadPairedDevices()
        } else {
            requestBluetoothPermissionWithRationale()
        }

        // Button click → check permission & load devices
//        btnScan.setOnClickListener {
//            if (hasBluetoothPermission()) {
//                loadPairedDevices()
//            } else {
//                requestBluetoothPermissionWithRationale()
//            }
//        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        //setSupportActionBar(toolbar)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }
    }

    /** Check if Bluetooth permission is granted */
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Show rationale if needed, otherwise request permission */
    private fun requestBluetoothPermissionWithRationale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) || shouldShowRequestPermissionRationale(
                    Manifest.permission.BLUETOOTH_SCAN
                )
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Bluetooth Permission Required")
                    .setMessage(
                        "This app needs Bluetooth permission to connect to receipt printers."
                    )
                    .setPositiveButton("Allow") { dialog, _ ->
                        requestPermissions(
                            permissions,
                            REQUEST_CODE_BLUETOOTH
                        )
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(
                            this,
                            "Permission denied. Cannot connect printer.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .show()
            } else {
                requestPermissions(
                    permissions,
                    REQUEST_CODE_BLUETOOTH
                )
            }
        }
    }


    /** Load paired devices safely */
    private fun loadPairedDevices() {
        try {
            val pairedDevices = bluetoothAdapter.bondedDevices
            deviceList.clear()

            // Filter for likely receipt printers (optional - can remove if you want all devices)
            pairedDevices.forEach { device ->
                Log.i("PrinterListActivity", Gson().toJson(device))
                val name = safeDeviceName(device).lowercase()
                // Common receipt printer names/keywords
                if (name.contains("printer") || name.contains("pos") ||
                    name.contains("bill") || name.contains("receipt") ||
                    name.contains("58mm") || name.contains("80mm") ||
                    name.contains("sunmi") || name.contains("rpp") ||
                    name.contains("zjiang") || name.contains("datecs")) {
                    deviceList.add(device)
                } else {
                    // Still add if user wants to try other devices
                    deviceList.add(device)
                }
            }

            if (deviceList.isNotEmpty()) {
                deviceAdapter.notifyDataSetChanged()
            } else {
                //showErrorDialog("Bluetooth","No Bluetooth devices found")
                val dialog = AlertDialog.Builder(this)
                    .setTitle("Bluetooth")
                    .setMessage("No Bluetooth devices found")
                    .setPositiveButton("Setting"
                    ) { dialog, which ->
                        startActivity( android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, which -> dialog.dismiss() }
                dialog.show()
            }
        } catch (e: SecurityException) {
            showErrorDialog("Bluetooth","Bluetooth permission denied")
        }
    }

    /** Show printer details dialog with TEST CONNECTION button */
    private fun showPrinterDetailsDialog(device: BluetoothDevice) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_printer_detail, null)

        val deviceName = dialogView.findViewById<TextView>(R.id.tvDeviceName)
        val deviceAddress = dialogView.findViewById<TextView>(R.id.tvDeviceAddress)
        val rb58mm = dialogView.findViewById<MaterialRadioButton>(R.id.rb58mm)
        val rb80mm = dialogView.findViewById<MaterialRadioButton>(R.id.rb80mm)
        val btnTest = dialogView.findViewById<MaterialButton>(R.id.btnTest)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSave)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val selectedPrinter = PrinterPreference(this).getPrinter();
        // Initially hide save button, show test button
        btnSave.visibility = View.GONE
        btnTest.visibility = View.VISIBLE
        progressBar.visibility = View.GONE

        if (selectedPrinter != null && selectedPrinter.name.isNotEmpty()) {
            // Set device info
            deviceName.text = selectedPrinter.name
            deviceAddress.text = selectedPrinter.address
            // Set default to 58mm for receipt printers

            rb58mm.isChecked = selectedPrinter.paperSize == "58mm"
            rb80mm.isChecked = selectedPrinter.paperSize == "80mm"

            //if (selectedPrinter.paperSize == "58mm") rb80mm.isChecked = true else rb80mm.isChecked = true
        } else {
            // Set device info
            deviceName.text = safeDeviceName(device)
            deviceAddress.text = safeDeviceAddress(device)
            // Set default to 58mm for receipt printers
            rb58mm.isChecked = true
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Configure Printer")
            .setNegativeButton("Cancel") { d, _ ->
                disconnectPrinter()
                d.dismiss()
            }
            .show()

        // Test connection button
        btnTest.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            btnTest.isEnabled = false

            // Test connection in background
            testPrinterConnection(device) { success ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnTest.isEnabled = true

                    if (success) {
                        // Connection successful - show save button
                        btnTest.visibility = View.GONE
                        btnSave.visibility = View.VISIBLE
                        Toast.makeText(this, "✓ Connection successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "✗ Connection failed. Please check printer is on!", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Save button (only visible after successful test)
        btnSave.setOnClickListener {
            val paperSize = if (rb80mm.isChecked) "80mm" else "58mm"

            printerPref.savePrinter(
                safeDeviceName(device),
                safeDeviceAddress(device),
                paperSize)
            deviceAdapter.notifyDataSetChanged()
            Toast.makeText(this,"Printer data save successfully", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    /** Test printer connection */
    private fun testPrinterConnection(device: BluetoothDevice, callback: (Boolean) -> Unit) {
        disconnectPrinter() // Close any existing connection

        Log.i("PrinterListActivity" , Gson().toJson(device))
        connectThread = ConnectThread(device, object : ConnectionCallback {
            override fun onConnectionSuccess(socket: BluetoothSocket) {
                Log.d(TAG, "Connection test successful")
                bluetoothSocket = socket

                // Try to send a small test command
                try {
                    val outputStream = socket.outputStream

                    // ESC/POS initialization command (harmless)
                    val initCommand = byteArrayOf(0x1B, 0x40)
                    outputStream.write(initCommand)
                    outputStream.flush()

                    // Small delay to ensure command is sent
                    Thread.sleep(100)

                    callback(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Test command failed", e)
                    callback(false)
                } finally {
                    disconnectPrinter()
                }
            }

            override fun onConnectionFailed(error: String) {
                Log.e(TAG, "Connection test failed: $error")
                callback(false)
            }
        })

        connectThread?.start()
    }


    /** Disconnect printer */
    private fun disconnectPrinter() {
        connectThread?.cancel()
        connectThread = null

        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        bluetoothSocket = null
    }

    /** Check if device is saved as selected printer */
    private fun isDeviceSelected(device: BluetoothDevice): Boolean {
        val savedAddress = printerPref.getPrinter()?.address ?: return false
        return savedAddress == safeDeviceAddress(device)
    }

    /** Safely get device name */
    private fun safeDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasBluetoothPermission()) {
                device.name ?: "Unknown Printer"
            } else {
                "Permission Required"
            }
        } catch (e: SecurityException) {
            "Permission Required"
        }
    }

    /** Safely get device address */
    private fun safeDeviceAddress(device: BluetoothDevice): String {
        return try {
            if (hasBluetoothPermission()) {
                device.address
            } else {
                "N/A"
            }
        } catch (e: SecurityException) {
            "N/A"
        }
    }

    /** Custom Adapter for ListView */
    inner class DeviceListAdapter(
        private val onItemClick: (device: BluetoothDevice) -> Unit
    ) : BaseAdapter() {

        override fun getCount(): Int = deviceList.size

        override fun getItem(position: Int): BluetoothDevice = deviceList[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_device, parent, false)

            val device = deviceList[position]

            val tvName = view.findViewById<TextView>(R.id.tvDeviceName)
            val tvAddress = view.findViewById<TextView>(R.id.tvDeviceAddress)
            val checkBox = view.findViewById<CheckBox>(R.id.cbSelected)
            val chipDetail = view.findViewById<Chip>(R.id.chipDetail)
            val printerIcon = view.findViewById<ImageView>(R.id.ivPrinterIcon)

            // Set device info
            tvName.text = safeDeviceName(device)
            tvAddress.text = safeDeviceAddress(device)

            // Check if this device is saved
            checkBox.isChecked = isDeviceSelected(device)
            checkBox.isClickable = false

            // Detail chip click
            chipDetail.setOnClickListener {
                showPrinterDetailsDialog(device)
            }

            // Whole item click
            view.setOnClickListener {
                onItemClick(device)
            }

            return view
        }
    }

    /** Handle permission result */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_BLUETOOTH && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadPairedDevices()
        } else {
            showErrorDialog("Bluetooth","Bluetooth permission denied")
        }
    }

    /** Thread for Bluetooth connection */
    inner class ConnectThread(
        private val device: BluetoothDevice,
        private val callback: ConnectionCallback
    ) : Thread() {

        override fun run() {
            try {
                // Get socket with SPP UUID
                val socket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (hasBluetoothPermission()) {
                        device.createRfcommSocketToServiceRecord(SPP_UUID)
                    } else {
                        callback.onConnectionFailed("Bluetooth permission denied")
                        return
                    }
                } else {
                    device.createRfcommSocketToServiceRecord(SPP_UUID)
                }

                // Cancel discovery to speed up connection
                bluetoothAdapter.cancelDiscovery()

                // Connect with timeout
                socket.connect()

                callback.onConnectionSuccess(socket)

            } catch (e: SecurityException) {
                callback.onConnectionFailed("Permission error: ${e.message}")
            } catch (e: IOException) {
                callback.onConnectionFailed("Connection error: ${e.message}")
            } catch (e: Exception) {
                callback.onConnectionFailed("Error: ${e.message}")
            }
        }

        fun cancel() {
            try {
                interrupt()
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling connection thread", e)
            }
        }
    }

    /** Connection callback interface */
    interface ConnectionCallback {
        fun onConnectionSuccess(socket: BluetoothSocket)
        fun onConnectionFailed(error: String)
    }

    /** Retrieve saved printer */
//    fun getSavedPrinter(): Triple<String, String, String>? {
//        val name = sharedPrefs.getString(KEY_PRINTER_NAME, null)
//        val address = sharedPrefs.getString(KEY_PRINTER_ADDRESS, null)
//        val paperSize = sharedPrefs.getString(KEY_PAPER_SIZE, "58mm") ?: "58mm"
//
//        return if (name != null && address != null) {
//            Triple(name, address, paperSize)
//        } else {
//            null
//        }
//    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.refresh_printer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                Log.i("PrinterListActivity", "refresh item is clicked")
                if(hasBluetoothPermission()){
                    loadPairedDevices()
                }else{
                    requestBluetoothPermissionWithRationale()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectPrinter()
    }

    private fun showErrorDialog(title: String, message: String) {

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()

    }
}