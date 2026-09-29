package com.nishant.playersystem

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject
import java.time.LocalDate

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var pendingExport: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.parseColor("#03050D"))

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#03050D"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(this), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (e: Exception) { }
                return true
            }
        }
        root.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Keep the page clear of the status bar, navigation bar, cutout and keyboard (Android 15 draws edge to edge).
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
                )
                v.setPadding(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

        setContentView(root)
        web.loadUrl("file:///android_asset/index.html")
        Reminders.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        Logic.rollover(this)
        web.evaluateJavascript("window.__onResume && window.__onResume()", null)
    }

    override fun onPause() {
        super.onPause()
        SystemWidget.refresh(this)
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
    }

    fun startExport(json: String) {
        pendingExport = json
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "player-system-backup-${LocalDate.now()}.json")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_EXPORT)
        } catch (e: Exception) { callJs("window.__exported && window.__exported(false)") }
    }

    fun startImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_IMPORT)
        } catch (e: Exception) { }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        when (requestCode) {
            REQ_EXPORT -> {
                val json = pendingExport
                pendingExport = null
                var ok = false
                if (resultCode == RESULT_OK && uri != null && json != null) {
                    try {
                        contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ok = true
                    } catch (e: Exception) { }
                }
                callJs("window.__exported && window.__exported($ok)")
            }
            REQ_IMPORT -> {
                if (resultCode == RESULT_OK && uri != null) {
                    try {
                        val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                        callJs("window.__imported && window.__imported(${JSONObject.quote(text)})")
                    } catch (e: Exception) { }
                }
            }
        }
    }

    private fun callJs(js: String) {
        web.post { web.evaluateJavascript(js, null) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.__back ? window.__back() : false") { result ->
            if (result != "true") finish()
        }
    }

    companion object {
        private const val REQ_NOTIFY = 5
        private const val REQ_EXPORT = 11
        private const val REQ_IMPORT = 12
    }
}
