package com.viniciusy.deuses.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (24 * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#0B1020"))
        }
        val titulo = TextView(this).apply {
            text = "Configurações"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val versao = TextView(this).apply {
            text = "Versão ${packageManager.getPackageInfo(packageName, 0).versionName}"
            textSize = 15f
            setTextColor(Color.parseColor("#9FB3D9"))
            setPadding(0, (8 * d).toInt(), 0, (28 * d).toInt())
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * d).toInt()).apply {
            bottomMargin = (14 * d).toInt()
        }
        val btnUpd = Button(this).apply {
            text = "Verificar atualização"
            layoutParams = lp
            setOnClickListener { Updater.check(this@SettingsActivity, manual = true) }
        }
        val btnNov = Button(this).apply {
            text = "O que mudou"
            layoutParams = lp
            setOnClickListener { Novidades.mostrarUltima(this@SettingsActivity) }
        }
        val btnVoltar = Button(this).apply {
            text = "Voltar"
            layoutParams = lp
            setOnClickListener { finish() }
        }
        root.addView(titulo)
        root.addView(versao)
        root.addView(btnUpd)
        root.addView(btnNov)
        root.addView(btnVoltar)
        setContentView(root)
    }
}
