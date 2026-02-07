package com.sa.posprinter.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.sa.posprinter.R
import com.sa.posprinter.model.PreviewItem
import com.sa.posprinter.util.PrinterPreference
import com.github.anastaciocintra.escpos.EscPos
import com.github.anastaciocintra.escpos.EscPosConst
import com.github.anastaciocintra.escpos.Style
import com.sa.posprinter.model.PrinterData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.Locale
import java.util.Locale.getDefault
import java.util.concurrent.Executors

class PreviewActivity : AppCompatActivity() {

    private lateinit var rvItems: RecyclerView
    private lateinit var tvAddress: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var tvTotal: TextView
    private lateinit var btnPrint: Button
    private lateinit var printerPref: PrinterPreference

    // Bluetooth printing variables
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val printerExecutor = Executors.newSingleThreadExecutor()
    private var isPrinting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        printerPref = PrinterPreference(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        setupToolbar()
        bindViews()
        setupPreviewData()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    private fun bindViews() {
        rvItems = findViewById(R.id.rvItems)
        tvAddress = findViewById(R.id.tvAddress)
        tvPhone = findViewById(R.id.tvPhone)
        tvDateTime = findViewById(R.id.tvDateTime)
        tvTotal = findViewById(R.id.tvTotal)
        btnPrint = findViewById(R.id.btnPrint)

        btnPrint.setOnClickListener {
            val printer = printerPref.getPrinter()
            if (printer == null) {
                showNoPrinterDialog()
            } else {
                executePrintJob(printer)
            }
        }
    }

    private fun setupPreviewData() {
        // Mock header data
        tvAddress.text = "No.12, Main Road, Yangon"
        tvPhone.text = "Phone: 09-123456789"
        tvDateTime.text = getCurrentDate()

        // Mock key-value data
        val items = listOf(
            PreviewItem("Coffee x2", "6,000"),
            PreviewItem("tea x2", "1,000"),
            PreviewItem("cold x2", "1,000"),
            PreviewItem("Cake x1", "2,500"),
            PreviewItem("Service Charge", "0")
        )

        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = PreviewAdapter(items)

        // Mock total
        tvTotal.text = "10,500"
    }

    private fun getCurrentDate(): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        return formatter.format(Date())
    }

    private fun executePrintJob(printerData: PrinterData) {

        if (isPrinting) {
            Toast.makeText(this, "Printing in progress...", Toast.LENGTH_SHORT).show()
            return
        }

        // Bluetooth support check
        val adapter = bluetoothAdapter
        if (adapter == null) {
            showErrorDialog("Error", "Device doesn't support Bluetooth")
            return
        }

        if (!adapter.isEnabled) {
            showErrorDialog("Bluetooth Off", "Please enable Bluetooth and try again")
            return
        }

        isPrinting = true
        btnPrint.isClickable = false
        btnPrint.text = "Printing..."


        lifecycleScope.launch {
            var socket: BluetoothSocket? = null
            var os: OutputStream? = null

            try {
                withContext(Dispatchers.IO) {
                    // 1️⃣ Get printer device
                    val device = adapter.getRemoteDevice(printerData.address)
                        ?: throw PrintException("Printer not found: ${printerData.name}")
                    Log.i("PreviewActivity", "device address {${device.name}}")
                    // 2️⃣ Create RFCOMM socket
                    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    socket = device.createRfcommSocketToServiceRecord(uuid)

                    Log.i("PreviewActivity", "Printer Socket not Connected")
                    socket.connect()
                    Log.i("PreviewActivity", "Printer Socket Connected")
                    os = socket.outputStream
                        ?: throw PrintException("Failed to get printer output stream")

                    // 3️⃣ Print using EscPosCoffee
                    printReceiptWithEscPos(
                        outputStream = os,
                        paperSize = printerData.paperSize
                    )
                }
                btnPrint.isClickable = true
                // 4️⃣ Success UI
                Toast.makeText(
                    this@PreviewActivity,
                    "Receipt printed successfully",
                    Toast.LENGTH_SHORT
                ).show()


            } catch (e: SecurityException) {
                Log.e("PrintJob", "Permission error", e)

                    showErrorDialog(
                        "Permission Error",
                        "Bluetooth permission required"
                    )


            } catch (e: IOException) {
                Log.e("PrintJob", "Connection error", e)

                    showErrorDialog(
                        "Connection Failed",
                        "Cannot connect to printer: ${printerData.name}\n" +
                                "Please check if the printer is turned on and paired"
                    )

                printerPref.clearPrinter()

            } catch (e: PrintException) {
                Log.e("PrintJob", "Print error", e)

                    showErrorDialog(
                        "Print Error",
                        e.message ?: "Printing failed"
                    )


            } catch (e: Exception) {
                Log.e("PrintJob", "Unexpected error", e)

                    showErrorDialog(
                        "Print Failed",
                        "An unexpected error occurred: ${e.localizedMessage}"
                    )


            } finally {
                // 5️⃣ Cleanup
                try {
                    os?.close()
                    socket?.close()
                } catch (e: IOException) {
                    Log.e("PrintJob", "Error closing connection", e)
                }

                isPrinting = false

                btnPrint.isClickable = true
                btnPrint.text = "PRINT"

            }
        }
    }


    private fun printReceiptWithEscPos(
        outputStream: OutputStream,
        paperSize: String
    ) {
        val escPos = EscPos(outputStream)
        Log.i("PreviewActivity", "paper Size in mm $paperSize")
        val maxItemWidth = when (paperSize.lowercase()) {
            "58mm", "58" -> 24
            "76mm", "76" -> 32
            "80mm", "80" -> 36
            else -> 32
        }
        Log.i("PreviewActivity", "paper Size in inch{$maxItemWidth}")

        val boldCenter = Style()
            .setBold(true)
            .setJustification(EscPosConst.Justification.Center)

        val bigBoldCenter = Style()
            .setBold(true)
            .setFontSize(Style.FontSize._2, Style.FontSize._2)
            .setJustification(EscPosConst.Justification.Center)

        // Header
        escPos.writeLF(boldCenter, "=".repeat(48))
        escPos.writeLF(bigBoldCenter, "YOUR STORE NAME")
        escPos.writeLF(boldCenter, "=".repeat(48))
        escPos.feed(1)

        // Address
        escPos.writeLF(Style().setJustification(EscPosConst.Justification.Center), tvAddress.text.toString())
        escPos.writeLF(Style().setJustification(EscPosConst.Justification.Center), tvPhone.text.toString())
        escPos.feed(1)

        // Receipt title
        escPos.writeLF(bigBoldCenter, "RECEIPT")
        escPos.feed(1)

        // Date
        escPos.writeLF(
            Style().setJustification(EscPosConst.Justification.Center),
            "Date: ${tvDateTime.text}"
        )
        escPos.feed(1)

        escPos.writeLF("-".repeat(48))

        // Column header
        escPos.writeLF(
            Style().setBold(true),
            String.format("%-${maxItemWidth}s %10s", "ITEM", "AMOUNT")
        )
        escPos.writeLF("-".repeat(48))

        // Items
        val items = (rvItems.adapter as PreviewAdapter).getItems()

        items.forEach { item ->
            val name = if (item.key.length > maxItemWidth)
                item.key.take(maxItemWidth - 3) + "..."
            else item.key

            escPos.writeLF(
                String.format("%-${maxItemWidth}s %10s", name, item.value)
            )
        }

        escPos.feed(1)
        escPos.writeLF("-".repeat(48))

        // Total
        escPos.writeLF(
            Style()
                .setBold(true)
                .setFontSize(Style.FontSize._2, Style.FontSize._1),
            String.format("%-${maxItemWidth}s %10s", "TOTAL", tvTotal.text.toString())
        )

        escPos.feed(2)

        // Footer
        escPos.writeLF(boldCenter, "Thank you for your visit!")
        escPos.writeLF(Style().setJustification(EscPosConst.Justification.Center), "Please visit again!")
        escPos.feed(3)


        //escPos.writeLF(boldCenter, "Thank you for your visit!")
        // Cut paper
        escPos.cut(EscPos.CutMode.FULL)

        escPos.close()
    }

    /*private fun printReceiptWithLibrary(paperSize: String) {
        outputStream?.let { os ->
            // Create commander for paper size
            val commander = when (paperSize.toLowerCase()) {
                "58mm", "58" -> Commander(Commander.PaperSize.PAPER_58_MM)
                "76mm", "76" -> Commander(Commander.PaperSize.PAPER_76_MM)
                "80mm", "80" -> Commander(Commander.PaperSize.PAPER_80_MM)
                else -> Commander(Commander.PaperSize.PAPER_80_MM)
            }

            // Get data from UI
            val adapter = rvItems.adapter as PreviewAdapter
            val items = adapter.getItems()

            // Start building receipt
            commander.reset()

            // Store header with proper alignment
            commander.feed(1)
            commander.write("=".repeat(48), Alignment.CENTER, Style().setBold(true))
            commander.feed(1)
            commander.write("YOUR STORE NAME", Alignment.CENTER,
                Style().setTextSize(TextSize.DOUBLE_HEIGHT).setBold(true))
            commander.feed(1)
            commander.write("=".repeat(48), Alignment.CENTER, Style().setBold(true))
            commander.feed(1)

            // Store address (handles multi-line automatically)
            commander.write(tvAddress.text.toString(), Alignment.CENTER)
            commander.write(tvPhone.text.toString(), Alignment.CENTER)
            commander.feed(1)

            // Receipt title
            commander.write("RECEIPT", Alignment.CENTER,
                Style().setTextSize(TextSize.DOUBLE_HEIGHT_DOUBLE_WIDTH).setBold(true))
            commander.feed(1)

            // Date and time
            commander.write("Date: ${tvDateTime.text}", Alignment.CENTER)
            commander.feed(1)

            // Divider line
            commander.write("-".repeat(48), Alignment.CENTER)
            commander.feed(1)

            // Column headers with proper spacing
            val maxItemWidth = when (paperSize.toLowerCase()) {
                "58mm", "58" -> 24
                "76mm", "76" -> 32
                "80mm", "80" -> 36
                else -> 32
            }

            commander.write(
                String.format("%-${maxItemWidth}s %10s", "ITEM", "AMOUNT"),
                Alignment.LEFT,
                Style().setBold(true)
            )
            commander.feed(1)

            commander.write("-".repeat(48), Alignment.CENTER)
            commander.feed(1)

            // Items list with proper formatting
            items.forEach { item ->
                val itemName = if (item.key.length > maxItemWidth) {
                    item.key.substring(0, maxItemWidth - 3) + "..."
                } else {
                    item.key
                }

                commander.write(
                    String.format("%-${maxItemWidth}s %10s", itemName, item.value),
                    Alignment.LEFT
                )
                commander.feed(1)
            }

            commander.feed(1)

            // Total section
            commander.write("-".repeat(48), Alignment.CENTER)
            commander.feed(1)

            commander.write(
                String.format("%-${maxItemWidth}s %10s", "TOTAL", tvTotal.text.toString()),
                Alignment.LEFT,
                Style().setBold(true).setTextSize(TextSize.DOUBLE_HEIGHT)
            )
            commander.feed(2)

            // Thank you message
            commander.write("Thank you for your visit!", Alignment.CENTER,
                Style().setBold(true))
            commander.feed(1)
            commander.write("Please visit again!", Alignment.CENTER)
            commander.feed(3)

            // Paper cut
            commander.cut()

            // Get the bytes and send to printer
            val commands = commander.getBytes()
            os.write(commands)
            os.flush()

        } ?: throw PrintException("No output stream available")
    }*/

    // Alternative: Simple manual formatting without library
    private fun printReceiptManualFormat(paperSize: String) {
        outputStream?.let { os ->
            // Initialize printer
            os.write(byteArrayOf(0x1B, 0x40))

            // Get paper width
            val paperWidth = when (paperSize.lowercase(getDefault())) {
                "58mm", "58" -> 32
                "76mm", "76" -> 42
                "80mm", "80" -> 48
                else -> 48
            }

            // Get data
            val adapter = rvItems.adapter as PreviewAdapter
            val items = adapter.getItems()

            // Helper functions for manual formatting
            fun centerText(text: String): String {
                val padding = (paperWidth - text.length) / 2
                val leftPadding = " ".repeat(maxOf(0, padding))
                return leftPadding + text
            }

            fun leftRightAlign(leftText: String, rightText: String): String {
                val spaceCount = paperWidth - leftText.length - rightText.length
                return if (spaceCount > 0) {
                    leftText + " ".repeat(spaceCount) + rightText
                } else {
                    // Truncate left text if too long
                    val truncatedLeft = leftText.substring(0, paperWidth - rightText.length - 3) + "..."
                    truncatedLeft + " " + rightText
                }
            }

            // Print receipt
            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center align

            // Store info
            os.write("${"=".repeat(paperWidth)}\n".toByteArray())
            os.write("${centerText("YOUR STORE NAME")}\n".toByteArray())
            os.write("${"=".repeat(paperWidth)}\n".toByteArray())
            os.write("\n".toByteArray())

            // Address
            os.write("${centerText(tvAddress.text.toString())}\n".toByteArray())
            os.write("${centerText(tvPhone.text.toString())}\n".toByteArray())
            os.write("\n".toByteArray())

            // Receipt title
            os.write(byteArrayOf(0x1D, 0x21, 0x11)) // Double height & width
            os.write("${centerText("RECEIPT")}\n".toByteArray())
            os.write(byteArrayOf(0x1D, 0x21, 0x00)) // Normal size
            os.write("\n".toByteArray())

            // Date
            os.write("${centerText("Date: ${tvDateTime.text}")}\n".toByteArray())
            os.write("\n".toByteArray())

            // Divider
            os.write("${"-".repeat(paperWidth)}\n".toByteArray())

            // Column headers
            os.write(byteArrayOf(0x1B, 0x61, 0x00)) // Left align
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold on
            os.write("${leftRightAlign("ITEM", "AMOUNT")}\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold off

            os.write("${"-".repeat(paperWidth)}\n".toByteArray())

            // Items
            items.forEach { item ->
                os.write("${leftRightAlign(item.key, item.value)}\n".toByteArray())
            }

            os.write("\n".toByteArray())
            os.write("${"-".repeat(paperWidth)}\n".toByteArray())

            // Total
            os.write(byteArrayOf(0x1B, 0x45, 0x01)) // Bold on
            os.write("${leftRightAlign("TOTAL", tvTotal.text.toString())}\n".toByteArray())
            os.write(byteArrayOf(0x1B, 0x45, 0x00)) // Bold off

            os.write("\n".toByteArray())

            // Thank you message
            os.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center align
            os.write("${"=".repeat(paperWidth)}\n".toByteArray())
            os.write("${centerText("Thank you for your visit!")}\n".toByteArray())
            os.write("${"=".repeat(paperWidth)}\n".toByteArray())

            // Cut paper
            os.write(byteArrayOf(0x0A, 0x0A, 0x0A))
            os.write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))

            os.flush()

        } ?: throw PrintException("No output stream available")
    }

    private fun showNoPrinterDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Printer Selected")
            .setMessage("Please select a printer from the menu")
            .setPositiveButton("Select Printer") { _, _ ->
                val intent = Intent(this, PrinterListActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showErrorDialog(title: String, message: String) {

            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()

    }

    class PrintException(message: String) : Exception(message)

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_printer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_printer -> {
                val intent = Intent(this, PrinterListActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("PreviewActivity", "Error closing connections: ${e.message}")
        }
        printerExecutor.shutdown()
    }
}