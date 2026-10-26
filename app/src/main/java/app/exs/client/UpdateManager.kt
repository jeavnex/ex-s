package app.exs.client

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class UpdateManager(private val activity: Activity) {
    private val executor = Executors.newSingleThreadExecutor()
    private var downloadId: Long = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
            val apk = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_NAME)
            if (!apk.exists()) {
                Toast.makeText(activity, "Не удалось скачать обновление", Toast.LENGTH_LONG).show()
                return
            }
            askToInstall(apk)
        }
    }

    fun register() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(
            activity,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unregister() {
        runCatching { activity.unregisterReceiver(receiver) }
        executor.shutdownNow()
    }

    fun check(showUpToDate: Boolean) {
        executor.execute {
            val release = runCatching { fetchLatestRelease() }.getOrNull()
            activity.runOnUiThread {
                when {
                    release == null && showUpToDate -> toast("Не удалось проверить обновления")
                    release == null -> Unit
                    compareVersions(release.version, BuildConfig.VERSION_NAME) <= 0 && showUpToDate ->
                        toast("Установлена актуальная версия ${BuildConfig.VERSION_NAME}")
                    compareVersions(release.version, BuildConfig.VERSION_NAME) > 0 -> showUpdateDialog(release)
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "EX-S/${BuildConfig.VERSION_NAME}")
        return try {
            if (connection.responseCode != 200) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("versionName")
            val downloadUrl = json.optString("apkUrl")
            if (version.isBlank() || downloadUrl.isBlank()) null else ReleaseInfo(version, downloadUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun showUpdateDialog(release: ReleaseInfo) {
        AlertDialog.Builder(activity)
            .setTitle("Доступна EX-S ${release.version}")
            .setMessage("Приложение скачает APK само. Android может попросить одно подтверждение установки.")
            .setNegativeButton("Позже", null)
            .setPositiveButton("Скачать") { _, _ -> download(release.downloadUrl) }
            .show()
    }

    private fun download(downloadUrl: String) {
        val directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        File(directory, APK_NAME).delete()
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Обновление EX-S")
            .setDescription("Загрузка новой версии")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_NAME)
            .setAllowedOverMetered(true)
        downloadId = (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        toast("Обновление загружается")
    }

    private fun askToInstall(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("Разрешите обновление EX-S")
                .setMessage("Android должен один раз разрешить EX-S устанавливать собственные обновления.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Открыть настройки") { _, _ ->
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}"),
                        ),
                    )
                }
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun toast(message: String) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()

    data class ReleaseInfo(val version: String, val downloadUrl: String)

    companion object {
        private const val RELEASE_API = "https://raw.githubusercontent.com/jeavnex/ex-s/main/getUpdate"
        private const val APK_NAME = "ex-s-universal.apk"

        fun compareVersions(left: String, right: String): Int {
            val a = left.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val b = right.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            for (index in 0 until maxOf(a.size, b.size)) {
                val delta = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
                if (delta != 0) return delta
            }
            return 0
        }
    }
}
