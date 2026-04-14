package com.sa.posprinter.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class utils {
    companion object{
        fun myDateTimeFormatter(_datetime: String?): String? {
            if (_datetime.isNullOrBlank()) return null

            return try {
                val inputFormat = SimpleDateFormat(
                    "y-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
                    Locale.ENGLISH
                )
                // inputFormat.timeZone = TimeZone.getTimeZone("UTC") // enable if needed

                val date = inputFormat.parse(_datetime) ?: return null

                val outputFormat = SimpleDateFormat(
                    "yyyy-MM-dd hh:mm a",
                    Locale.ENGLISH
                )

                outputFormat.format(date)

            } catch (e: Exception) {
                null
            }
        }


        fun formatAmount(value: String?): String? {
            val number = value?.toDoubleOrNull() ?: return null

            val formatter = DecimalFormat("#,###.##")
            return formatter.format(number)
        }

    }
}