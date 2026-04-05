package com.sa.posprinter.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.sa.posprinter.ui.PreviewActivity

class WebAppInterface(private val mContext: Context) {

    @JavascriptInterface
    fun showToast(toast: String) {
        Log.i("MainActivity","showToast")
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun getAndroidVersion(): Int {
        return Build.VERSION.SDK_INT
    }

    @JavascriptInterface
    fun openReceipt(orderId: String) {
        Log.i("WebAppInterface", "openReceipt: $orderId")
        val intent = Intent(mContext, PreviewActivity::class.java).apply {
            putExtra(PreviewActivity.EXTRA_ORDER_ID, orderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        mContext.startActivity(intent)
    }
}