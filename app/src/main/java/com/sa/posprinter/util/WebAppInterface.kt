package com.sa.posprinter.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val mContext: Context) {

    /** Show a toast from the web page */
    @JavascriptInterface
    fun showToast(toast: String) {
        Log.i("MainActivity","showToast")
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getAndroidVersion(): Int {
        return Build.VERSION.SDK_INT
    }
}