package com.nova.anime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView

    private val pickImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPicked(uri)
    }
    private val pickPlay = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .setData(uri)
                    .putExtra("title", nameOf(uri))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF07020F.toInt()) }
        web = WebView(this).apply {
            setBackgroundColor(0xFF07020F.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
            }
            addJavascriptInterface(NovaBridge(), "NOVA")
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(6, 8, 6, 8); setBackgroundColor(0xFF120922.toInt())
        }
        bar.addView(action("▶ Play File") { pickPlay.launch(arrayOf("video/*", "application/octet-stream")) })
        bar.addView(action("＋ Import") { pickImport.launch(arrayOf("video/*", "application/octet-stream")) })
        bar.addView(action("My Videos") { startActivity(Intent(this, LibraryActivity::class.java)) })
        bar.addView(action("Reels") { startActivity(Intent(this, ReelsActivity::class.java)) })
        bar.addView(action("Storage") { showStorage() })
        root.addView(bar)
        setContentView(root)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    private fun action(label: String, click: () -> Unit) = Button(this).apply {
        text = label; textSize = 10f; isAllCaps = false; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF54218A.toInt()); setOnClickListener { click() }
        setPadding(2, 0, 2, 0)
        layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(3, 0, 3, 0) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun nameOf(uri: Uri): String =
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "video"

    private fun importPicked(uri: Uri) {
        val name = nameOf(uri)
        Toast.makeText(this, "কপি হচ্ছে… বড় ফাইলে সময় লাগতে পারে", Toast.LENGTH_LONG).show()
        Thread {
            try {
                val item = VideoStore.importVideo(applicationContext, uri, name)
                runOnUiThread { Toast.makeText(this, "NOVA-তে কপি হয়েছে: ${item.name}", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showStorage() {
        val mb = VideoStore.bytes(this) / (1024.0 * 1024.0)
        Toast.makeText(this, "NOVA Videos: %.1f MB · %d files".format(mb, VideoStore.all(this).size), Toast.LENGTH_LONG).show()
    }

    inner class NovaBridge {
        @JavascriptInterface fun openLibrary() { runOnUiThread { startActivity(Intent(this@MainActivity, LibraryActivity::class.java)) } }
        @JavascriptInterface fun openReels() { runOnUiThread { startActivity(Intent(this@MainActivity, ReelsActivity::class.java)) } }
        @JavascriptInterface fun storageBytes(): Long = VideoStore.bytes(this@MainActivity)
    }
}
