package com.sa.posprinter.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.sa.posprinter.data.api.ApiClient
import com.sa.posprinter.data.model.ReceiptResponse
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
import java.util.Locale.getDefault
import kotlin.math.min
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import com.sa.posprinter.databinding.ActivityPreviewBinding
import com.sa.posprinter.util.PrintException
import com.sa.posprinter.util.ReceiptBitmapGenerator
import com.sa.posprinter.util.showIfNotEmpty
import com.sa.posprinter.util.showIfValueNotNull
import com.sa.posprinter.util.utils.Companion.formatAmount
import com.sa.posprinter.util.utils.Companion.myDateTimeFormatter
import com.sa.posprinter.util.utils.Companion.getPrinterWidthInPixels

class PreviewActivity : AppCompatActivity() {
    private lateinit var printerPref: PrinterPreference
    private lateinit var mContext: Context
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var isPrinting = false
    private var myanmarTypeface: Typeface? = null
    private var myanmarTypefaceBold: Typeface? = null
    private var languageType: String = "mm"
    private var orderId: String = "1"
    private var receiptStatus: String = "order"

    private var shopLogoBitmap: Bitmap? = null
    private var receiptNote: String = ""
    private val bitmapGenerator by lazy {
        ReceiptBitmapGenerator(
            myanmarTypeface,
            myanmarTypefaceBold
        )
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_RECEIPT_TYPE = "extra_order_type"
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
        receiptStatus = intent.getStringExtra(EXTRA_RECEIPT_TYPE) ?: "order"
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
                    ApiClient.service.getReceipt(receiptStatus,orderId)
                }
                binding.mainLayout.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                populatePreviewData(response)
            } catch (e: Exception) {
                Log.e("PreviewActivity", "Failed to fetch receipt: ${e.message},{$receiptStatus} / {$orderId}")
                binding.errorLayout.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                binding.tvError.text = resources.getString(R.string.fail_message)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populatePreviewData(response: ReceiptResponse) {
        val header = response.data.receipt.header
        val body = response.data.receipt.body
        val footer = response.data.receipt.footer
        languageType = response.data.locale?.takeIf { it.isNotBlank() } ?: "en"
        val currencySymbol = response.data.currency.symbol

        // Shop Logo
        if (header.shopLogo.isNullOrEmpty()) {
            binding.ivLogo.visibility = View.GONE
        } else {
            binding.ivLogo.visibility = View.VISIBLE
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
        }

        binding.tvShopName.showIfNotEmpty(header.shopName)
        binding.tvAddress.showIfNotEmpty(header.shopAddress)
        binding.tvPhone.showIfNotEmpty(header.shopPhone)
        binding.tvInvoiceNo.text = header.voucherNo ?: ""
        binding.tvCashier.text = header.cashierName ?: ""
        binding.tvDateTime.showIfNotEmpty(myDateTimeFormatter(header.voucherDate))

        // Hide layout containers
        binding.layoutInvoiceNo.showIfValueNotNull(header.voucherNo)
        binding.layoutCashier.showIfValueNotNull(header.cashierName)

        // Show/hide separators based on content
        val hasHeaderContent = binding.ivLogo.isVisible ||
                (binding.tvShopName.isVisible && binding.tvShopName.text.isNotEmpty()) ||
                (binding.tvAddress.isVisible && binding.tvAddress.text.isNotEmpty()) ||
                (binding.tvPhone.isVisible && binding.tvPhone.text.isNotEmpty())

        binding.separatorHeader.visibility = if (hasHeaderContent) View.VISIBLE else View.GONE

        val hasInfoContent = (binding.layoutInvoiceNo.isVisible && binding.tvInvoiceNo.text.isNotEmpty()) ||
                (binding.layoutCashier.isVisible && binding.tvCashier.text.isNotEmpty()) ||
                (binding.tvDateTime.isVisible && binding.tvDateTime.text.isNotEmpty())

        binding.separatorInfo.visibility = if (hasInfoContent) View.VISIBLE else View.GONE

        val items = body.map {
            val qtyText = it.itemQuantity
                ?.toDoubleOrNull()
                ?.toInt()
                ?.let { qty -> "$qty x " }
                ?: ""

            PreviewItem(
                "$qtyText${it.itemName.orEmpty()}",
                "${formatAmount(it.itemLineAmount)}"
            )
        }
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = PreviewAdapter(items)

        binding.tvSubTotal.text = "${formatAmount(footer.subtotal)}"
        binding.tvDiscount.text = "${formatAmount(footer.discountTotal)}"
        binding.tvTotalAmount.text = "${formatAmount(footer.grandTotal)}"
        binding.tvTax.text = "${formatAmount(footer.taxTotal)}"
        binding.tvUnpaidAmount.text = "${formatAmount(footer.unpaidAmount)}"
        binding.tvPaidAmount.text = "${formatAmount(footer.paidAmount)}"
        binding.tvRefundAmount.text = "${formatAmount(footer.refundAmount)}"

        binding.layoutSubtotal.showIfValueNotNull(footer.subtotal)
        binding.layoutDiscount.showIfValueNotNull(footer.discountTotal)
        binding.layoutTax.showIfValueNotNull(footer.taxTotal)
        binding.layoutTotal.showIfValueNotNull(footer.grandTotal)
        binding.layoutUnpaid.showIfValueNotNull(footer.unpaidAmount)
        binding.layoutPaid.showIfValueNotNull(footer.paidAmount)
        binding.layoutRefund.showIfValueNotNull(footer.refundAmount)

        // Show separators for totals section
        val hasTotals = binding.layoutSubtotal.isVisible ||
                binding.layoutDiscount.isVisible ||
                binding.layoutTax.isVisible

        binding.separatorBeforeTotals.visibility = if (hasTotals && items.isNotEmpty()) View.VISIBLE else View.GONE
        binding.separatorBeforeTotal.visibility = if (binding.layoutTotal.isVisible) View.VISIBLE else View.GONE

        receiptNote = footer.receiptNote?.takeIf { it.isNotBlank() } ?: "Thanks For Shopping!!!"
        binding.separatorBeforeNote.visibility = if (receiptNote.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvNote.text = receiptNote
    }

    private fun executePrintJob(printerData: PrinterData) {
        if (isPrinting) {
            Toast.makeText(this, resources.getString(R.string.printing_progress), Toast.LENGTH_SHORT).show()
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
        binding.btnPrint.text = resources.getString(R.string.printing)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 1️⃣ Get PRINTER DEVICE by Address saved in shared preference
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
                binding.btnPrint.text = resources.getString(R.string.print).uppercase()

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

    // Converts Myanmar text into a bitmap (based on printer width, textSize, bold),
    private fun textToImageHex(printer: EscPosPrinter, text: String, textSize: Float = 18f, isBold: Boolean = false): String? {
        val printerWidthPx = getPrinterWidthInPixels(printerPref.getPrinter()?.paperSize ?: "58mm")
        val bitmap = bitmapGenerator.createMyanmarTextBitmap(text, textSize, printerWidthPx, isBold) ?: return null
        return PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
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

        //convert paper size to width in pixel
        val printerWidthPx = getPrinterWidthInPixels(paperSize)

        val receiptText = buildString {

            // Logo - only if visible
            if (binding.ivLogo.isVisible) {
                shopLogoBitmap?.let { logo ->
                    val logoBitmap = Bitmap.createScaledBitmap(logo, 150, 150, true)
                    val imageHex = PrinterTextParserImg.bitmapToHexadecimalString(printer, logoBitmap)
                    appendLine("[C]<img>$imageHex</img>")
                }
            }

            // Shop name
            if (binding.tvShopName.isVisible && binding.tvShopName.text.isNotEmpty()) {
                textToImageHex(printer, binding.tvShopName.text.toString(), 24f, isBold = true)?.let {
                    appendLine("[C]<img>$it</img>")
                }
            }

            // Shop address
            if (binding.tvAddress.isVisible && binding.tvAddress.text.isNotEmpty()) {
                textToImageHex(printer, binding.tvAddress.text.toString(), 24f)?.let {
                    appendLine("[C]<img>$it</img>")
                }
            }

            // Shop phone
            if (binding.tvPhone.isVisible && binding.tvPhone.text.isNotEmpty()) {
                appendLine("[C]${binding.tvPhone.text}")
            }

            // Only add separator if there's any header content
            if (binding.ivLogo.isVisible ||
                (binding.tvShopName.isVisible && binding.tvShopName.text.isNotEmpty()) ||
                (binding.tvAddress.isVisible && binding.tvAddress.text.isNotEmpty()) ||
                (binding.tvPhone.isVisible && binding.tvPhone.text.isNotEmpty())) {
                appendLine("[C]${"-".repeat(maxChars)}")
            }

            // Voucher No
            if (binding.layoutInvoiceNo.isVisible && binding.tvInvoiceNo.text.isNotEmpty()) {
                appendLine("[L]Voucher No: ${binding.tvInvoiceNo.text}")
            }

            // Cashier and DateTime on same row
            val showCashier = binding.layoutCashier.isVisible && binding.tvCashier.text.isNotEmpty()
            val showDateTime = binding.tvDateTime.isVisible && binding.tvDateTime.text.isNotEmpty()

            if (showCashier || showDateTime) {
                when {
                    showCashier && showDateTime -> {
                        bitmapGenerator.createLeftRightTextBitmap(
                            "Cashier: ${binding.tvCashier.text}",
                            binding.tvDateTime.text.toString(),
                            printerWidthPx, 24f, 0f
                        )?.let {
                            val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, it)
                            appendLine("[L]<img>$hex</img>")
                        }
                    }
                    showCashier -> {
                        appendLine("[L]Cashier: ${binding.tvCashier.text}")
                    }
                    showDateTime -> {
                        appendLine("[R]${binding.tvDateTime.text}")
                    }
                }
            }

            // Add separator if any voucher/cashier/date content exists
            if ((binding.layoutInvoiceNo.isVisible && binding.tvInvoiceNo.text.isNotEmpty()) ||
                showCashier || showDateTime) {
                appendLine("[C]${"-".repeat(maxChars)}")
            }

            // Items list
            items.forEach { item ->
                bitmapGenerator.createItemRowBitmap(item.key, item.value, printerWidthPx, 24f)?.let { bitmap ->
                    val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                    appendLine("[L]<img>$hex</img>")
                }
            }

            if (items.isNotEmpty()) {
                appendLine("[C]${"-".repeat(maxChars)}")
            }

            // Subtotal
            if (binding.layoutSubtotal.isVisible && binding.tvSubTotal.text.isNotEmpty()) {
                appendLine("[L]Subtotal[R]${binding.tvSubTotal.text}")
            }

            // Discount
            if (binding.layoutDiscount.isVisible && binding.tvDiscount.text.isNotEmpty()) {
                appendLine("[L]Discount[R]${binding.tvDiscount.text}")
            }

            // Tax
            if (binding.layoutTax.isVisible && binding.tvTax.text.isNotEmpty()) {
                appendLine("[L]Tax[R]${binding.tvTax.text}")
            }

            // Add separator if any of subtotal/discount/tax is visible
            if (binding.layoutSubtotal.isVisible || binding.layoutDiscount.isVisible || binding.layoutTax.isVisible) {
                appendLine("[C]${"-".repeat(maxChars)}")
            }

            // Total
            if (binding.layoutTotal.isVisible && binding.tvTotalAmount.text.isNotEmpty()) {
                appendLine("[L]<b>Total[R]${binding.tvTotalAmount.text}</b>")
            }

            // Unpaid Amount
            if (binding.layoutUnpaid.isVisible && binding.tvUnpaidAmount.text.isNotEmpty()) {
                appendLine("[L]Unpaid Amount[R]${binding.tvUnpaidAmount.text}")
            }

            // Paid Amount
            if (binding.layoutPaid.isVisible && binding.tvPaidAmount.text.isNotEmpty()) {
                appendLine("[L]Paid Amount[R]${binding.tvPaidAmount.text}")
            }

            // Refund Amount
            if (binding.layoutRefund.isVisible && binding.tvRefundAmount.text.isNotEmpty()) {
                appendLine("[L]Refund Amount[R]${binding.tvRefundAmount.text}")
            }

            // Add separator if any payment fields are visible
            if (binding.layoutUnpaid.isVisible || binding.layoutPaid.isVisible || binding.layoutRefund.isVisible) {
                appendLine("[C]${"-".repeat(maxChars)}")
            }

            // Footer note
            if (receiptNote.isNotEmpty()) {
                appendLine("[C]$receiptNote")
            }
            appendLine(" ")
            appendLine(" ")
        }

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

    override fun onDestroy() {
        super.onDestroy()
    }

}