package com.sa.posprinter.data.model

import com.google.gson.annotations.SerializedName

data class ReceiptResponse(
    val status: String,
    val message: String,
    val data: ReceiptData
)

data class ReceiptData(
    val locale: String,
    val currency: Currency,
    val receipt: Receipt
)

data class Currency(
    val name: String,
    val code: String,
    val symbol: String,
    @SerializedName("decimal_places") val decimalPlaces: Int,
    @SerializedName("rounding_mode") val roundingMode: String
)

data class Receipt(
    val header: ReceiptHeader,
    val body: List<ReceiptItem>,
    val footer: ReceiptFooter
)

data class ReceiptHeader(
    @SerializedName("voucher_id") val voucherId: Int,
    @SerializedName("voucher_no") val voucherNo: String,
    @SerializedName("voucher_date") val voucherDate: String,
    @SerializedName("shop_logo") val shopLogo: String,
    @SerializedName("shop_name") val shopName: String,
    @SerializedName("shop_address") val shopAddress: String,
    @SerializedName("shop_phone") val shopPhone: String,
    @SerializedName("cashier_name") val cashierName: String = "Cashier: 01"
)

data class ReceiptItem(
    @SerializedName("item_quantity") val itemQuantity: String,
    @SerializedName("item_name") val itemName: String,
    @SerializedName("item_line_amount") val itemLineAmount: String
)

data class ReceiptFooter(
    val subtotal: String,
    @SerializedName("discount_total") val discountTotal: String,
    @SerializedName("tax_total") val taxTotal: String,
    @SerializedName("grand_total") val grandTotal: String,
    @SerializedName("unpaid_amount") val unpaidAmount: String,
    @SerializedName("paid_amount") val paidAmount: String,
    @SerializedName("refund_amount") val refundAmount: String,
    @SerializedName("receipt_note") val receiptNote: String
)
