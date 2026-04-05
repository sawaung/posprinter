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
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.sa.posprinter.data.api.ApiClient
import com.sa.posprinter.data.model.ReceiptResponse
import com.google.android.material.appbar.MaterialToolbar
import coil.load
import coil.imageLoader
import coil.request.ImageRequest
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
import androidx.core.graphics.createBitmap

class PreviewActivity : AppCompatActivity() {
    private lateinit var mainLayout: LinearLayout
    private lateinit var errorLayout: LinearLayout
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
    private lateinit var tvError: TextView
    private lateinit var btnTryAgain: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var ivLogo: ImageView
    private lateinit var printerPref: PrinterPreference
    private lateinit var mContext: Context
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val printerExecutor = Executors.newSingleThreadExecutor()
    private var isPrinting = false
    private var myanmarTypeface: Typeface? = null
    private var myanmarTypefaceBold: Typeface? = null
    private var languageType: String = "mm" // "en" or "mm", will be set from deep link
    private var orderId: String = "1" // will be set from deep link

    private var shopLogoBitmap: Bitmap? = null

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)
        mContext = this

        printerPref = PrinterPreference(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        setupToolbar()
        initMyanmarFont()
        bindViews()
        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: "1"
        fetchReceiptData()
    }

    private fun initMyanmarFont() {
        try {
            myanmarTypeface = Typeface.createFromAsset(assets, "fonts/NotoSansMyanmar-Regular.ttf")
            myanmarTypefaceBold = Typeface.createFromAsset(assets, "fonts/NotoSansMyanmar-Bold.ttf")
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Failed to load Myanmar font: ${e.message}")
            myanmarTypeface = Typeface.DEFAULT
            myanmarTypefaceBold = Typeface.DEFAULT_BOLD
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
        mainLayout = findViewById(R.id.mainLayout)
        errorLayout = findViewById(R.id.errorLayout)
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
        tvError = findViewById(R.id.txtError)
        btnTryAgain = findViewById(R.id.btnTryAgain)
        progressBar = findViewById(R.id.progressBar)
        ivLogo = findViewById(R.id.ivLogo)

        btnPrint.setOnClickListener {
            val printer = printerPref.getPrinter()
            if (printer == null) {
                showNoPrinterDialog()
            } else {
                executePrintJob(printer)
            }
        }

        btnTryAgain.setOnClickListener {
            fetchReceiptData()
        }
    }

    private fun fetchReceiptData() {

        lifecycleScope.launch {
            try {
                mainLayout.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
                errorLayout.visibility = View.GONE

                val response = withContext(Dispatchers.IO) {
                    ApiClient.service.getReceipt(orderId)
                }
                mainLayout.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                populatePreviewData(response)
            } catch (e: Exception) {
                Log.e("PreviewActivity", "Failed to fetch receipt: ${e.message}")
                errorLayout.visibility = View.VISIBLE
                tvError.text = "Failed to load receipt data!"
                //Toast.makeText(this@PreviewActivity, "Failed to load receipt data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populatePreviewData(response: ReceiptResponse) {
        val header = response.data.receipt.header
        val body = response.data.receipt.body
        val footer = response.data.receipt.footer
        languageType = response.data.locale

        // Display logo using Coil
        ivLogo.load(header.shopLogo)

        // Load logo as bitmap for printing
        lifecycleScope.launch {
            shopLogoBitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(mContext)
                    .data(header.shopLogo)
                    .allowHardware(false) // required for bitmap access
                    .build()
                imageLoader.execute(request).drawable?.toBitmap()
            }
        }
        tvShopName.text = header.shopName
        tvAddress.text = header.shopAddress
        tvPhone.text = header.shopPhone
        tvInvoiceNo.text = header.voucherNo
        tvCashier.text = header.shopName
        tvDateTime.text = header.voucherDate

        val items = body.map { PreviewItem("${it.itemName} x${it.itemQuantity.toDouble().toInt()}", it.itemLineAmount) }
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = PreviewAdapter(items)

        tvSubTotal.text = footer.subtotal
        tvDiscount.text = footer.discountTotal
        tvTotalAmount.text = footer.grandTotal
        tvUnpaidAmount.text = footer.unpaidAmount
        tvPaidAmount.text = footer.paidAmount
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
                    // 1️⃣ Get PRINTER DEVICE
                    val device = adapter.getRemoteDevice(printerData.address)
                        ?: throw PrintException("Printer not found: ${printerData.name}")
                    Log.i("PreviewActivity", "device address {${device.name}}")

                    // Create BLUETOOTH CONNECTION
                    val connection = BluetoothConnection(device)
                    connection.connect()

                    // Create ESC POS PRINTER INSTANCE with proper settings based on paper size
                    val printer = createPrinter(connection, printerData.paperSize)
                    // Print the receipt
                    printReceipt(printer, printerData.paperSize)
//                    btnPrint.isClickable = true
//                    // 4️⃣ Success UI
//                    Toast.makeText(
//                        this@PreviewActivity,
//                        "Receipt printed successfully",
//                        Toast.LENGTH_SHORT
//                    ).show()
                }

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

    private fun textToImageHex(printer: EscPosPrinter, text: String, textSize: Float = 18f, isBold: Boolean = false): String? {
        val printerWidthPx = getPrinterWidthInPixels(printerPref.getPrinter()?.paperSize ?: "58mm")
        val bitmap = createMyanmarTextBitmap(text, textSize, printerWidthPx, isBold) ?: return null
        return PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
    }

    private fun createItemRowBitmap(itemName: String, amount: String, maxWidth: Int, textSize: Float = 18f): Bitmap? {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.BLACK
            paint.textSize = textSize
            paint.typeface = myanmarTypeface

            val height = (textSize * 1.5f).toInt().coerceAtLeast(30)
            val bitmap = createBitmap(maxWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Draw item name on left
            canvas.drawText(itemName, 10f, height - 10f, paint)

            // Draw amount on right
            val amountWidth = paint.measureText(amount)
            canvas.drawText(amount, maxWidth - amountWidth - 10f, height - 10f, paint)

            convertToMonochrome(bitmap)
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating item row bitmap: ${e.message}")
            null
        }
    }

    private fun printReceipt(printer: EscPosPrinter, paperSize: String) {
        val adapter = rvItems.adapter as PreviewAdapter
        val items = adapter.getItems()

        val (maxChars, itemWidth, amountWidth) = when (paperSize.lowercase(getDefault())) {
            "58mm", "58" -> Triple(32, 22, 8)
            "76mm", "76" -> Triple(42, 30, 10)
            "80mm", "80" -> Triple(48, 34, 12)
            else -> Triple(48, 34, 12)
        }

        val printerWidthPx = getPrinterWidthInPixels(paperSize)

        val receiptText = buildString {
            // Logo image - only print if available
            shopLogoBitmap?.let { logo ->
                val logoBitmap = Bitmap.createScaledBitmap(logo, 150, 150, true)
                val imageHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, logoBitmap)
                appendLine("[C]<img>$imageHex</img>")
                appendLine()
            }


                // Myanmar mode - all text as bitmap
//                textToImageHex(printer, tvShopName.text.toString(), 26f, isBold = true)?.let {
//                    appendLine("[C]<img>$it</img>")
//                }
//                textToImageHex(printer, tvAddress.text.toString(),26f)?.let {
//                    appendLine("[C]<img>$it</img>")
//                }
//                appendLine()
//                textToImageHex(printer, "*** Receipt ***", 26f,isBold = true)?.let {
//                    appendLine("[C]<img>$it</img>")
//                }

//                appendLine()
//                textToImageHex(printer, "Invoice No: ${tvInvoiceNo.text}",26f)?.let {
//                    appendLine("[L]<img>$it</img>")
//                }
                
                // Calculate spacing between cashier and datetime
                val cashierText = "Cashier: ${tvCashier.text}"
                val dateText = tvDateTime.text.toString()
                val totalLength = cashierText.length + dateText.length
                val spacesNeeded = maxChars - totalLength
                val spacing = " ".repeat(spacesNeeded.coerceAtLeast(2))
                
                textToImageHex(printer, "$cashierText$spacing$dateText",26f)?.let {
                    appendLine("[L]<img>$it</img>")
                }

                createLeftRightTextBitmap(cashierText, dateText, printerWidthPx, 24f, leftPadding = 0f)?.let { bitmap ->
                    val headerHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                    appendLine("[L]<img>$headerHex</img>")
                }
//                appendLine()
                //ITEM===============================AMOUNT //28
                //ITEM                               AMOUNT
                createLeftRightTextBitmap("ITEM", "AMOUNT", printerWidthPx, 24f, leftPadding = 0f)?.let { bitmap ->
                    val headerHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                    appendLine("[L]<img>$headerHex</img>")
                }
//                appendLine("[C]${"-".repeat(maxChars)}")
//                appendLine()
//
//                items.forEach { item ->
//                    createItemRowBitmap(item.key, item.value, printerWidthPx)?.let { bitmap ->
//                        val itemHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
//                        appendLine("[L]<img>$itemHex</img>")
//                    }
//                }
//
//                appendLine()
//                textToImageHex(printer, "Subtotal:   ${tvSubTotal.text}")?.let {
//                    appendLine("[L]<img>$it</img>")
//                }
//                textToImageHex(printer, "Discount:   ${tvDiscount.text}")?.let {
//                    appendLine("[L]<img>$it</img>")
//                }
//                appendLine()
//                textToImageHex(printer, "Total Amount:   ${tvTotalAmount.text}")?.let {
//                    appendLine("[L]<img>$it</img>")
//                }
//                textToImageHex(printer, "Unpaid Amount:   ${tvUnpaidAmount.text}")?.let {
//                    appendLine("[L]<img>$it</img>")
//                }
//                textToImageHex(printer, "Paid Amount:   ${tvPaidAmount.text}")?.let {
//                    appendLine("[L]<img>$it</img>")
//                }
//                appendLine()
//                appendLine("[C]${"*".repeat(maxChars)}")
//                textToImageHex(printer, "Thank you for Shopping!")?.let {
//                    appendLine("[C]<img>$it</img>")
//                }

            /* ** For English Language
            else {
                // English mode - text only
                appendLine("[C]<b>${tvShopName.text}</b>")  // Normal size (closest to 14sp)
                appendLine("[C]${tvAddress.text}")
                appendLine()
                appendLine("[C]<b>*** Receipt ***</b>")
                appendLine()
                appendLine("[L]Invoice No: ${tvInvoiceNo.text}")
                appendLine("[L]Cashier: ${tvCashier.text}  [R] ${tvDateTime.text}")
                appendLine()
                appendLine("<b>${"ITEM".padEnd(itemWidth)} ${"AMOUNT".padStart(amountWidth)}</b>")
                appendLine("[C]${"-".repeat(maxChars)}")
                appendLine()

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
                appendLine("[L]Subtotal:   [R] ${tvSubTotal.text}")
                appendLine("[L]Discount:   [R] ${tvDiscount.text}")
                appendLine()
                appendLine("[L]Total Amount:   [R] ${tvTotalAmount.text}")
                appendLine("[L]Unpaid Amount:   [R] ${tvUnpaidAmount.text}")
                appendLine("[L]Paid Amount:   [R] ${tvPaidAmount.text}")
                appendLine()
                appendLine("[C]${"*".repeat(maxChars)}")
                appendLine("[C]<b>Thank you for Shopping!</b>")
            }*/
            appendLine()
        }

        printer.printFormattedText(receiptText)
    }

    private fun createLeftRightTextBitmap(leftText: String, rightText: String, maxWidth: Int, textSize: Float = 18f, leftPadding: Float = 10f): Bitmap? {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.BLACK
            paint.textSize = textSize
            paint.typeface = myanmarTypeface

            val height = (textSize * 1.5f).toInt().coerceAtLeast(30)
            val bitmap = createBitmap(maxWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            canvas.drawText(leftText, leftPadding, height - 10f, paint)
            val rightWidth = paint.measureText(rightText)
            canvas.drawText(rightText, maxWidth - rightWidth - 10f, height - 10f, paint)

            convertToMonochrome(bitmap)
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating left-right text bitmap: ${e.message}")
            null
        }
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


    // Create bitmap from text with custom font
    private fun createMyanmarTextBitmap(
        text: String,
        textSize: Float,
        maxWidth: Int,
        isBold: Boolean = false
    ): Bitmap? {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.BLACK
            paint.textSize = textSize
            paint.typeface = if (isBold) myanmarTypefaceBold else myanmarTypeface

            // Measure text properly
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val textWidth = paint.measureText(text).toInt()
            val textHeight = bounds.height()

            val width = min(textWidth, maxWidth).coerceAtLeast(1)
            val height = (textHeight + 20).coerceAtLeast(1)

            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Draw text at baseline
            val x = if (textWidth > maxWidth) 0f else (width - textWidth) / 2f
            val y = height - 10f
            canvas.drawText(text, x, y, paint)

            convertToMonochrome(bitmap)
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating Myanmar text bitmap: ${e.message}")
            null
        }
    }

    private fun convertToMonochrome(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
            pixels[i] = if (gray < 128) Color.BLACK else Color.WHITE
        }

        val result = createBitmap(width, height)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
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