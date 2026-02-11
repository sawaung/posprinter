package com.sa.posprinter.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sa.posprinter.util.WebAppInterface
import com.sa.posprinter.R

class MainActivity : AppCompatActivity() {

    //create button
    lateinit var webView: WebView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //setupWebView()

    }

    @SuppressLint("SetJavaScriptEnabled")
    fun setupWebView(){
        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(WebAppInterface(this), "Android")
        //add a button to webView
        webView.loadDataWithBaseURL(
            null,
            loadHTMLContent(),
            "text/html",
            "UTF-8",
            null
        );
    }

    fun loadHTMLContent() = """
            <!DOCTYPE html>
            <html>
                <body>
                    <button onclick="handleSubmit()">Submit Form</button>
                    <script>
                        function handleSubmit() {
                            if (window.Android && window.Android.showToast) {
                                 window.Android.showToast('Form submitted successfully!');
                            }
                        }
                    </script>
                </body>
            </html>
        """

}