package com.sa.posprinter.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat.getDrawableForDensity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.google.android.material.appbar.MaterialToolbar
import com.sa.posprinter.R
import com.sa.posprinter.model.PreviewItem
import com.sa.posprinter.model.PrinterData
import com.sa.posprinter.util.PrinterPreference
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
import kotlin.math.min

class PreviewActivity : AppCompatActivity() {

    private lateinit var rvItems: RecyclerView
    private lateinit var tvShopName: TextView
    private lateinit var tvCashier: TextView
    private lateinit var tvInvoiceNo: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var tvSubTotal: TextView
    private lateinit var tvDiscount: TextView
    private lateinit var tvTotalAmount: TextView
    private lateinit var tvUnpaidAmount: TextView
    private lateinit var tvPaidAmount: TextView
    private lateinit var btnPrint: Button
    private lateinit var printerPref: PrinterPreference
    private lateinit var mContext: Context
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val printerExecutor = Executors.newSingleThreadExecutor()
    private var isPrinting = false
    private var myanmarTypeface: Typeface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        mContext = this

        printerPref = PrinterPreference(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        setupToolbar()
        initMyanmarFont()
        bindViews()
        setupPreviewData()
    }

    private fun initMyanmarFont() {
        try {
            // Load Myanmar font from assets
            // Place your Myanmar font file (e.g., zawgyi.ttf, myanmar3.ttf) in app/src/main/assets/fonts/
            myanmarTypeface = Typeface.createFromAsset(assets, "fonts/zawgyi.ttf")
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Failed to load Myanmar font: ${e.message}")
            // Fallback to default font
            myanmarTypeface = Typeface.DEFAULT
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_printer -> {
                    // Handle settings click
                    val intent = Intent(this, PrinterListActivity::class.java)
                    startActivity(intent)
                    true
                }

                else -> false
            }
        }

        //setSupportActionBar(toolbar)

    }

    private fun bindViews() {
        tvShopName = findViewById(R.id.tvShopName)
        tvCashier = findViewById(R.id.tvCashier)
        tvInvoiceNo = findViewById(R.id.tvInvoiceNo)
        rvItems = findViewById(R.id.rvItems)
        tvAddress = findViewById(R.id.tvAddress)
        tvPhone = findViewById(R.id.tvPhone)
        tvDateTime = findViewById(R.id.tvDateTime)
        tvSubTotal = findViewById(R.id.tvSubTotal)
        btnPrint = findViewById(R.id.btnPrint)
        tvDiscount = findViewById(R.id.tvDiscount)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        tvUnpaidAmount = findViewById(R.id.tvUnpaidAmount)
        tvPaidAmount = findViewById(R.id.tvPaidAmount)

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
        tvShopName.text = "Dora Storee"
        tvAddress.text = "အမှတ် ၁၄၄၊ ငု၀ါလမ်း၊ လှိုင်မြို့နယ်၊ ရန်ကုန်။"
        tvPhone.text = "Phone: 09-123456789"
        tvInvoiceNo.text = "293212"
        tvCashier.text = "Dora Storee"
        tvDateTime.text = getCurrentDate()

        // Mock key-value data
        val items = listOf(
            PreviewItem("Coffee x2", "6,000"),
            PreviewItem("Tea x2", "1,000"),
            //PreviewItem("Cold Drink x2", "1,000"),
            //PreviewItem("Cake x1", "2,500"),
            //PreviewItem("Service Charge", "0")
        )


        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = PreviewAdapter(items)


        // Mock total
        tvSubTotal.text = "10,500"
        tvDiscount.text = "10,500"
        tvTotalAmount.text = "10,500"
        tvUnpaidAmount.text = "10,500"
        tvPaidAmount.text = "10,500"
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
            try {
                withContext(Dispatchers.IO) {
                    // 1️⃣ Get printer device
                    val device = adapter.getRemoteDevice(printerData.address)
                        ?: throw PrintException("Printer not found: ${printerData.name}")
                    Log.i("PreviewActivity", "device address {${device.name}}")

                    // Create Bluetooth connection
                    val connection = BluetoothConnection(device)
                    connection.connect()

                    // Create printer instance with proper settings based on paper size
                    val printer = createPrinter(connection, printerData.paperSize)
                    // Print the receipt
                    printReceipt(printer, printerData.paperSize)
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
                isPrinting = false
                btnPrint.isClickable = true
                btnPrint.text = "PRINT"

            }
        }
    }

    private fun createPrinter(
        connection: BluetoothConnection,
        paperSize: String
    ): EscPosPrinter {
        // Set printer density and width based on paper size
        return when (paperSize.lowercase(getDefault())) {
            "58mm", "58" -> EscPosPrinter(
                connection,
                203,      // Printer dots density (203 DPI for 58mm)
                48.0f,    // Paper width in mm
                32,
                EscPosCharsetEncoding("UTF-8", 26)// Max characters per line
            )
            "76mm", "76" -> EscPosPrinter(
                connection,
                203,      // Printer dots density (203 DPI)
                76.0f,    // Paper width in mm
                42 ,
                EscPosCharsetEncoding("UTF-8", 26)// Max characters per line
            )
            "80mm", "80" -> EscPosPrinter(
                connection,
                203,      // Printer dots density (203 DPI)
                74.0f,    // Paper width in mm
                48,      // Max characters per line
                EscPosCharsetEncoding("windows-1252", 16),
            )
            else -> EscPosPrinter(
                connection,
                203,
                74.0f,
                48,
                EscPosCharsetEncoding("UTF-8", 26)
            )
        }
    }

    private fun printReceipt(printer: EscPosPrinter, paperSize: String) {
        // Get data from RecyclerView
        val adapter = rvItems.adapter as PreviewAdapter
        val items = adapter.getItems()

        // Calculate line width and formatting based on paper size
        val (maxChars, itemWidth, amountWidth) = when (paperSize.lowercase(getDefault())) {
            "58mm", "58" -> Triple(32, 22, 8)
            "76mm", "76" -> Triple(42, 30, 10)
            "80mm", "80" -> Triple(48, 34, 12)
            else -> Triple(48, 34, 12)
        }

        val drawable = AppCompatResources.getDrawable(mContext, R.drawable.ic_printer)

        val printerWidthPx = getPrinterWidthInPixels(paperSize)
        val imageHex =
            PrinterTextParserImg.bitmapToHexadecimalString(printer, drawable?.toBitmap())
        // Build the receipt text
        val receiptText = buildString {


//            // Add centered image
            appendLine("[C]<img width='80'>$imageHex</img>")
            appendLine("[C]<font size='big'><b>${tvShopName.text}</b></font>")
            //appendLine("[C]${tvAddress.text}")
            val addressBitmap = createMyanmarTextBitmap(
                text = tvAddress.text.toString(),
                textSize = 18f,
                maxWidth = printerWidthPx
            )
            if (addressBitmap != null) {
                val addressImageHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, addressBitmap)
                appendLine("[C]<img>$addressImageHex</img>")
            }
            appendLine()
            appendLine("[C]<font size='big'><b>*** Receipt ***</b></font>")
            //appendLine("[C]${"=".repeat(maxChars)}")
            appendLine()

            // Store information
            appendLine("[L]Invoice No: ${tvInvoiceNo.text}")
            appendLine("[L]Cashier: ${tvCashier.text}  [R] ${tvDateTime.text} ")
            appendLine()

            //appendLine("[C]${"-".repeat(maxChars)}")
            // Column headers
            appendLine("<b>${"ITEM".padEnd(itemWidth)} ${"AMOUNT".padStart(amountWidth)}</b>")
            appendLine("[C]${"-".repeat(maxChars)}")
            appendLine()

            // Items list
            items.forEach { item ->
                val itemName = if (item.key.length > itemWidth) {
                    item.key.substring(0, itemWidth - 3) + "..."
                } else {
                    item.key.padEnd(itemWidth)
                }

                val amount = item.value.padStart(amountWidth)
                appendLine("$itemName $amount")
            }

            appendLine()
            appendLine("[L]Subtotal:   [R] ${tvSubTotal.text} ")
            appendLine("[L]Discount:   [R] ${tvDiscount.text} ")

            appendLine()
            appendLine("[L]Total Amount:   [R] ${tvTotalAmount.text} ")
            appendLine("[L]Unpaid Amount:   [R] ${tvUnpaidAmount.text} ")
            appendLine("[L]Paid Amount:   [R] ${tvPaidAmount.text} ")
           // appendLine("[C]${"-".repeat(maxChars)}")
            appendLine()

            // Total
            //appendLine("<b><font size='big'>${"TOTAL".padEnd(itemWidth)} ${tvSubTotal.text.toString().padStart(amountWidth)}</font></b>")
            //appendLine()
            //appendLine()

            // Footer
            appendLine("[C]${"*".repeat(maxChars)}")
            appendLine("[C]<b>Thank you for Shopping!</b>")
            //appendLine("[C]Please come again")
            //appendLine("[C]${"=".repeat(maxChars)}")

            // Paper cut command
            //appendLine()
            appendLine()
            append("[C]<cut />")
        }

        // Print the receipt
        printer.printFormattedText(receiptText)
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

    override fun onDestroy() {
        super.onDestroy()
        printerExecutor.shutdown()
    }


    // Create bitmap from Myanmar text
    private fun createMyanmarTextBitmap(
        text: String,
        textSize: Float,
        maxWidth: Int,
        isBold: Boolean = false
    ): Bitmap? {
        return try {
            // Create paint with Myanmar font
            val paint = Paint()
            paint.color = Color.BLACK
            paint.textSize = textSize
            paint.typeface = myanmarTypeface
            paint.isAntiAlias = true
            paint.isSubpixelText = true
            if (isBold) {
                paint.isFakeBoldText = true
            }


            // Calculate text width
            val textWidth = paint.measureText(text)
            val actualWidth = min(textWidth, maxWidth.toFloat()).toInt()

            // Calculate text height
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val height = bounds.height() + 10 // Add padding

            // Create bitmap
            val bitmap = Bitmap.createBitmap(actualWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Draw white background
            canvas.drawColor(Color.WHITE)

            // Draw text (centered horizontally)
            val xPos = (actualWidth - textWidth) / 2
            val yPos = height - 5f
            canvas.drawText(text, xPos, yPos, paint)

            // Convert to black and white for thermal printer
            convertToMonochrome(bitmap)

        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating Myanmar text bitmap: ${e.message}")
            null
        }
    }

    private fun convertToMonochrome(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val bwBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)

                // Check alpha
                val alpha = Color.alpha(pixel)
                if (alpha < 128) {
                    bwBitmap.setPixel(x, y, Color.WHITE)
                    continue
                }

                // Convert to grayscale
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()

                // Use threshold (lower = more black)
                val threshold = 200
                val color = if (luminance < threshold) Color.BLACK else Color.WHITE
                bwBitmap.setPixel(x, y, color)
            }
        }

        return bwBitmap
    }

    private fun resizeBitmapForPrinter(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // Calculate scaling factor
        val widthScale = maxWidth.toFloat() / originalWidth
        val heightScale = maxHeight.toFloat() / originalHeight
        val scaleFactor = minOf(widthScale, heightScale, 1.0f)

        val newWidth = (originalWidth * scaleFactor).toInt().coerceAtLeast(1)
        val newHeight = (originalHeight * scaleFactor).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun getPrinterWidthInPixels(paperSize: String): Int {
        return when (paperSize.lowercase(Locale.getDefault())) {
            "58mm", "58" -> 384  // 58mm at 203 DPI
            "76mm", "76" -> 512  // 76mm at 203 DPI
            "80mm", "80" -> 576  // 80mm at 203 DPI
            else -> 576
        }
    }

    private fun getMaxChars(paperSize: String): Int {
        return when (paperSize.lowercase(Locale.getDefault())) {
            "58mm", "58" -> 32
            "76mm", "76" -> 42
            "80mm", "80" -> 48
            else -> 48
        }
    }
}