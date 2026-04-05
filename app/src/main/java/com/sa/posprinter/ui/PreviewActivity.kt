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
import com.sa.posprinter.databinding.ActivityMainBinding
import com.sa.posprinter.databinding.ActivityPreviewBinding
import com.sa.posprinter.util.utils
import com.sa.posprinter.util.utils.Companion.formatAmount
import com.sa.posprinter.util.utils.Companion.myDateTimeFormatter

class PreviewActivity : AppCompatActivity() {
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
    private var receiptNote: String = ""

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    private lateinit var binding: ActivityPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mContext = this

        printerPref = PrinterPreference(this)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        initMyanmarFont()
        setClickEvent()
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


    private fun setClickEvent() {

        binding.btnPrint.setOnClickListener {
            val printer = printerPref.getPrinter()
            if (printer == null) {
                showNoPrinterDialog()
            } else {
                executePrintJob(printer)
            }
        }

        binding.btnTryAgain.setOnClickListener {
            fetchReceiptData()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
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
    }

    private fun fetchReceiptData() {

        lifecycleScope.launch {
            try {
                binding.mainLayout.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                binding.errorLayout.visibility = View.GONE

                val response = withContext(Dispatchers.IO) {
                    ApiClient.service.getReceipt(orderId)
                }
                binding.mainLayout.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                populatePreviewData(response)
            } catch (e: Exception) {
                Log.e("PreviewActivity", "Failed to fetch receipt: ${e.message}")
                binding.errorLayout.visibility = View.VISIBLE
                binding.tvError.text = "Failed to load receipt data!"
                //Toast.makeText(this@PreviewActivity, "Failed to load receipt data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populatePreviewData(response: ReceiptResponse) {
        val header = response.data.receipt.header
        val body = response.data.receipt.body
        val footer = response.data.receipt.footer
        languageType = response.data.locale
        val currencySymbol = response.data.currency.symbol

        binding.ivLogo.load(header.shopLogo)

        lifecycleScope.launch {
            shopLogoBitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(mContext)
                    .data(header.shopLogo)
                    .allowHardware(false)
                    .build()
                imageLoader.execute(request).drawable?.toBitmap()
            }
        }

        binding.tvShopName.text = header.shopName
        binding.tvAddress.text = header.shopAddress
        binding.tvPhone.text = header.shopPhone
        binding.tvInvoiceNo.text = header.voucherNo
        binding.tvCashier.text = header.cashierName
        binding.tvDateTime.text = myDateTimeFormatter(header.voucherDate)

        val items = body.map {
            PreviewItem(
                "${it.itemQuantity.toDouble().toInt()} x ${it.itemName}",
                "${formatAmount(it.itemLineAmount)} $currencySymbol"
            )
        }
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = PreviewAdapter(items)

        binding.tvSubTotal.text = "${formatAmount(footer.subtotal)} $currencySymbol"
        binding.tvDiscount.text = "${formatAmount(footer.discountTotal)} $currencySymbol"
        binding.tvTotalAmount.text = "${formatAmount(footer.grandTotal)} $currencySymbol"
        binding.tvTax.text = "${formatAmount(footer.taxTotal)} $currencySymbol"
        binding.tvUnpaidAmount.text = "${formatAmount(footer.unpaidAmount)} $currencySymbol"
        binding.tvPaidAmount.text = "${formatAmount(footer.paidAmount)} $currencySymbol"
        binding.tvRefundAmount.text = "${formatAmount(footer.refundAmount)} $currencySymbol"
        receiptNote = footer.receiptNote
    }

    private fun setupPreviewData() {
        // Mock header data
        binding.tvShopName.text = "Dora Storee"
        binding.tvAddress.text = "အမှတ် ၁၄၄၊ ငု၀ါလမ်း၊ လှိုင်မြို့နယ်၊ ရန်ကုန်။"
        binding.tvPhone.text = "Phone: 09-123456789"
        binding.tvInvoiceNo.text = "293212"
        binding.tvCashier.text = "Dora Storee"
        binding.tvDateTime.text = getCurrentDate()

        // Mock key-value data
        val items = listOf(
            PreviewItem("Coffee x2", "6,000"),
            PreviewItem("Tea x2", "1,000"),
            //PreviewItem("Cold Drink x2", "1,000"),
            //PreviewItem("Cake x1", "2,500"),
            //PreviewItem("Service Charge", "0")
        )


        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = PreviewAdapter(items)


        // Mock total
        binding.tvSubTotal.text = "10,500"
        binding.tvDiscount.text = "10,500"
        binding.tvTotalAmount.text = "10,500"
        binding.tvUnpaidAmount.text = "10,500"
        binding.tvPaidAmount.text = "10,500"
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
        binding.btnPrint.isClickable = false
        binding.btnPrint.text = "Printing..."

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
                binding.btnPrint.isClickable = true
                binding.btnPrint.text = "PRINT"

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

    // Item row: left=Myanmar font, right=Default font
    private fun createItemRowBitmap(itemName: String, amount: String, maxWidth: Int, textSize: Float = 18f): Bitmap? {
        return try {
            val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                this.textSize = textSize
                typeface = myanmarTypeface
            }
            val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                this.textSize = textSize
                typeface = Typeface.DEFAULT
            }

            val height = (textSize * 1.5f).toInt().coerceAtLeast(30)
            val bitmap = createBitmap(maxWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            canvas.drawText(itemName, 0f, height - 10f, leftPaint)
            val amountWidth = rightPaint.measureText(amount)
            canvas.drawText(amount, maxWidth - amountWidth - 4f, height - 10f, rightPaint)

            convertToMonochrome(bitmap)
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating item row bitmap: ${e.message}")
            null
        }
    }

    private fun printReceipt(printer: EscPosPrinter, paperSize: String) {
        val adapter = binding.rvItems.adapter as PreviewAdapter
        val items = adapter.getItems()

        val (maxChars, width, height) = when (paperSize.lowercase(getDefault())) {
            "58mm", "58" -> Triple(32, 22, 8)
            "76mm", "76" -> Triple(42, 30, 10)
            "80mm", "80" -> Triple(48, 34, 12)
            else -> Triple(48, 34, 12)
        }

        val printerWidthPx = getPrinterWidthInPixels(paperSize)

        val receiptText = buildString {

            // Logo - only if available
            shopLogoBitmap?.let { logo ->
                val logoBitmap = Bitmap.createScaledBitmap(logo, 150, 150, true)
                val imageHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, logoBitmap)
                appendLine("[C]<img>$imageHex</img>")
            }

            // Shop name - Myanmar font bitmap
            textToImageHex(printer, binding.tvShopName.text.toString(), 24f, isBold = true)?.let {
                appendLine("[C]<img>$it</img>")
            }

            // Shop address - Myanmar font bitmap
            textToImageHex(printer, binding.tvAddress.text.toString(), 24f)?.let {
                appendLine("[C]<img>$it</img>")
            }

            // Shop phone - plain text
            appendLine("[C]${binding.tvPhone.text}")
            appendLine("[C]${"-".repeat(maxChars)}")

            // Voucher No
            appendLine("[L]Voucher No: ${binding.tvInvoiceNo.text}")

            // Cashier (left) and DateTime (right) on same row
            createLeftRightTextBitmap(
                "Cashier: ${binding.tvCashier.text}",
                binding.tvDateTime.text.toString(),
                printerWidthPx, 24f,0f
            )?.let {
                val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, it)
                appendLine("[L]<img>$hex</img>")
            }
            appendLine("[C]${"-".repeat(maxChars)}")

            // Item header
            appendLine("[L]<b>ITEM[R]AMOUNT</b>")
            appendLine("[C]${"-".repeat(maxChars)}")

            // Items - Myanmar font for name, Default for amount
            items.forEach { item ->
                createItemRowBitmap(item.key, item.value, printerWidthPx, 24f)?.let { bitmap ->
                    val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                    appendLine("[L]<img>$hex</img>")
                }
            }

            appendLine("[C]${"-".repeat(maxChars)}")

            // Totals - plain text
            appendLine("[L]Subtotal[R]${binding.tvSubTotal.text}")
            appendLine("[L]Discount[R]${binding.tvDiscount.text}")
            appendLine("[L]Tax[R]${binding.tvTax.text}")
            appendLine("[C]${"-".repeat(maxChars)}")
            appendLine("[L]<b>Total[R]${binding.tvTotalAmount.text}</b>")
            appendLine("[C]${"-".repeat(maxChars)}")
            appendLine("[L]Unpaid Amount[R]${binding.tvUnpaidAmount.text}")
            appendLine("[L]Paid Amount[R]${binding.tvPaidAmount.text}")
            appendLine("[L]Refund Amount[R]${binding.tvRefundAmount.text}")
            appendLine("[C]${"-".repeat(maxChars)}")

            // Footer note
            appendLine("[C]$receiptNote")
            appendLine()
        }

        val receiptText2 = buildString {
            createLeftRightTextBitmap(
                "Cashier: ${binding.tvCashier.text}",
                binding.tvDateTime.text.toString(),
                printerWidthPx, 24f,0f
            )?.let {
                val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, it)
                appendLine("[L]<img>$hex</img>")
            }
            items.forEach { item ->
                createItemRowBitmap(item.key, item.value, printerWidthPx, 24f)?.let { bitmap ->
                    val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                    appendLine("[L]<img>$hex</img>")
                }
            }
            appendLine("[L]Refund Amount[R]${binding.tvRefundAmount.text}")
            appendLine("[C]${"-".repeat(maxChars)}")
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