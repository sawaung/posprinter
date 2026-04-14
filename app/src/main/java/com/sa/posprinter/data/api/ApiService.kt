package com.sa.posprinter.data.api

import com.sa.posprinter.data.model.ReceiptResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {
    @GET("api/v1/receipt/{receiptType}/{orderId}")
    suspend fun getReceipt(
        @Path("receiptType") receiptType: String,
        @Path("orderId") orderId: String): ReceiptResponse
}
