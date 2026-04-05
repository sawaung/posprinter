package com.sa.posprinter.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class utils {
    companion object{
        fun myDateTimeFormatter(_datetime:String):String{
            try {
                val inputFormat = SimpleDateFormat("y-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
                //inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(_datetime)
                val outputFormat = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.ENGLISH)
                return outputFormat.format(date!!)
            }catch (e:Exception){
                return _datetime
            }
        }

        fun formatAmount(value: String): String {
            val number = value.toDoubleOrNull() ?: return "0"

            val formatter = DecimalFormat("#,###")
            return formatter.format(number)
        }
    }
}