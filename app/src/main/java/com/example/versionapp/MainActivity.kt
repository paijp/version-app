package com.example.versionapp

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.versionapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvVersion.text = "Version: \${BuildConfig.VERSION_NAME}"
        binding.tvVersionCode.text = "Version Code: \${BuildConfig.VERSION_CODE}"
        binding.tvAppId.text = "App ID: \${BuildConfig.APPLICATION_ID}"

        // 起動時に1回だけバージョンチェック
        checkForUpdate()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val latest = fetchLatestRelease() ?: return@launch
            if (isUpdateAvailable(BuildConfig.VERSION_NAME, latest.first)) {
                showUpdateDialog(latest.first, latest.second)
            }
        }
    }

    private suspend fun fetchLatestRelease(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/paijp/version-app/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val tag = json.getString("tag_name")
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                apkUrl?.let { Pair(tag.trimStart('v'), it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(c.size, l.size)) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    private fun showUpdateDialog(latestVersion: String, apkUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("アップデートがあります")
            .setMessage("新しいバージョン v\$latestVersion が利用可能です。\nブラウザでダウンロードページを開きますか？")
            .setPositiveButton("開く") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                startActivity(intent)
            }
            .setNegativeButton("後で", null)
            .show()
    }
}
