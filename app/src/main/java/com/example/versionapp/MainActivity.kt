package com.example.versionapp

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.versionapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var downloadReceiver: BroadcastReceiver? = null
    private val logLines = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvVersion.text = "Version: ${BuildConfig.VERSION_NAME}"
        binding.tvVersionCode.text = "Version Code: ${BuildConfig.VERSION_CODE}"
        binding.tvAppId.text = "App ID: ${BuildConfig.APPLICATION_ID}"

        log("アプリ起動: v${BuildConfig.VERSION_NAME}")
        log("バージョンチェック開始...")
        checkForUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let { unregisterReceiver(it) }
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time] $message"
        logLines.add(line)
        // 最新20行のみ表示
        binding.tvLog.text = logLines.takeLast(20).joinToString("\n")
        binding.svLog.post { binding.svLog.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            val latest = fetchLatestRelease()
            if (latest == null) {
                log("⚠️ バージョン情報の取得に失敗しました")
                return@launch
            }
            log("最新バージョン: v${latest.first}")
            if (isUpdateAvailable(BuildConfig.VERSION_NAME, latest.first)) {
                log("✅ アップデートあり → ダイアログを表示")
                showUpdateDialog(latest.first, latest.second)
            } else {
                log("✅ 最新バージョンです")
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
            .setMessage("新しいバージョン v$latestVersion が利用可能です。\nダウンロードしてインストールしますか？")
            .setPositiveButton("インストール") { _, _ ->
                checkInstallPermissionAndDownload(apkUrl)
            }
            .setNegativeButton("後で", null)
            .show()
    }

    private fun checkInstallPermissionAndDownload(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()) {
            log("⚠️ インストール許可が必要です")
            AlertDialog.Builder(this)
                .setTitle("インストール許可が必要です")
                .setMessage("このアプリからのインストールを許可してください。\n設定画面を開きますか？")
                .setPositiveButton("設定を開く") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
                    pendingApkUrl = apkUrl
                }
                .setNegativeButton("キャンセル", null)
                .show()
        } else {
            downloadAndInstall(apkUrl)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            pendingApkUrl?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    packageManager.canRequestPackageInstalls()) {
                    downloadAndInstall(it)
                }
                pendingApkUrl = null
            }
        }
    }

    private fun downloadAndInstall(apkUrl: String) {
        log("⬇️ ダウンロード開始...")
        val fileName = "update.apk"
        val destFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("アップデートをダウンロード中")
            setDescription("新しいバージョンをダウンロードしています...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    log("✅ ダウンロード完了 → インストール開始")
                    installApk(destFile)
                    unregisterReceiver(this)
                    downloadReceiver = null
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    companion object {
        private const val REQUEST_INSTALL_PERMISSION = 1001
        private var pendingApkUrl: String? = null
    }
}
