package com.sa.posprinter.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sa.posprinter.model.PrinterData

class PrinterPreference(context: Context) {
    val sharedPref: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    companion object{
        const val PREFS_NAME = "printer_prefs"
        const val KEY_PRINTER_NAME = "printer_name"
        const val KEY_PRINTER_ADDRESS = "printer_address"
        const val KEY_PAPER_SIZE = "paper_size"
        const val KEY_IS_TESTED = "printer_tested"
    }

    fun savePrinter(deviceName: String,
                    deviceAddress: String,
                    paperSize: String){
        sharedPref.edit {
            putString(KEY_PRINTER_NAME, deviceName)
                .putString(KEY_PRINTER_ADDRESS, deviceAddress)
                .putString(KEY_PAPER_SIZE, paperSize)
                .putBoolean(KEY_IS_TESTED, true)
        }
    }

    fun getPrinter(): PrinterData? {
        val name = sharedPref.getString(KEY_PRINTER_NAME, null)
        val address = sharedPref.getString(KEY_PRINTER_ADDRESS, null)

        if (name == null || address == null) return null

        return PrinterData(
            name = name,
            address = address,
            paperSize = sharedPref.getString(KEY_PAPER_SIZE, "58mm") ?: "58mm",
            isTested = sharedPref.getBoolean(KEY_IS_TESTED, false)
        )
    }

    fun clearPrinter() {
        sharedPref.edit { clear() }
    }
}