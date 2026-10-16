package com.viniciusy.deuses.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object Updater {
    const val REPO = "viniciusy30-commits/deuses-arena"
    private const val PREFS = "updater"
    private const val KEY_LAST = "lastCheck"
    private const val SIX_HOURS = 6L * 60 * 60 * 1000
    private val main = Handler(Looper.getMainLooper())

    private class Info(val version: Long, val name: String, val url: String)

    fun installedCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(pi)
    }

    fun autoCheck(act: Activity) {
        val prefs = act.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST, 0L) < SIX_HOURS) return
        prefs.edit().putLong(KEY_LAST, now).apply()
        check(act, manual = false)
    }

    fun check(act: Activity, manual: Boolean) {
        if (manual) toast(act, "Procurando atualização...")
        thread {
            try {
                val info = fetchLatest()
                val installed = installedCode(act)
                main.post {
                    if (act.isFinishing) return@post
                    if (info != null && info.version > installed) {
                        showUpdateDialog(act, info)
                    } else if (manual) {
                        toast(act, "Você já está na versão mais nova.")
                    }
                }
            } catch (e: Exception) {
                if (manual) main.post { toast(act, "Sem internet ou GitHub indisponível.") }
            }
        }
    }

    private fun fetchLatest(): Info? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) return null
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name").removePrefix("v")
            val v = tag.toLongOrNull() ?: return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Info(v, a.getString("name"), a.getString("browser_download_url"))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun showUpdateDialog(act: Activity, info: Info) {
        AlertDialog.Builder(act)
            .setTitle("Nova versão disponível")
            .setMessage("Versão ${info.version} pronta para instalar.")
            .setPositiveButton("Atualizar") { _, _ -> startDownload(act, info) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun startDownload(act: Activity, info: Info) {
        val pad = (20 * act.resources.displayMetrics.density).toInt()
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val label = TextView(act).apply { text = "Baixando... 0%"; gravity = Gravity.CENTER }
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        box.addView(label)
        box.addView(bar)
        val dlg = AlertDialog.Builder(act).setTitle("Atualizando").setView(box).setCancelable(false).show()

        thread {
            try {
                val dir = File(act.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, info.name)
                val c = URL(info.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 15000
                c.instanceFollowRedirects = true
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(16384)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    main.post { bar.progress = pct; label.text = "Baixando... $pct%" }
                                }
                            }
                        }
                    }
                }
                c.disconnect()
                main.post { dlg.dismiss(); install(act, out) }
            } catch (e: Exception) {
                main.post { dlg.dismiss(); toast(act, "Falha no download. Tente de novo.") }
            }
        }
    }

    private fun install(act: Activity, apk: File) {
        if (!act.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(act)
                .setTitle("Permissão necessária")
                .setMessage("Para instalar a atualização, permita \"Instalar apps desconhecidos\" para o Deuses Arena. Depois volte e toque em Verificar atualização.")
                .setPositiveButton("Abrir configuração") { _, _ ->
                    val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}"))
                    act.startActivity(i)
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        act.startActivity(i)
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
