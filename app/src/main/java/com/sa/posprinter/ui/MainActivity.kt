package com.sa.posprinter.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.sa.posprinter.util.WebAppInterface
import com.sa.posprinter.R

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.statusBarColor = ContextCompat.getColor(this, R.color.md_primary)

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView() {

        webView = findViewById(R.id.webView)
        WebView.setWebContentsDebuggingEnabled(true)

        // ❗ IMPORTANT: keep navigation inside WebView
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                view?.loadUrl(url)
                return true
            }
        }

        // Optional (helps for JS dialogs, popups)
        webView.webChromeClient = WebChromeClient()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true

            setSupportMultipleWindows(false)
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(WebAppInterface(this), "Android")

        webView.loadUrl("https://pos.ziigwat.com")
    }

    fun loadHTMLContent() = """
            <!DOCTYPE html>
            <html>
                <body>
                    <button onclick="handlePrint()">Print</button>
                    <script>
                        function handlePrint() {
                            if (window.Android && window.Android.openReceipt) {
                                window.Android.openReceipt('order','1');
                            }
                        }
                    </script>
                </body>
            </html>
        """
}
