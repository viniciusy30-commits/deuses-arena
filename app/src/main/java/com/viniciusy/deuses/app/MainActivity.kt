package com.viniciusy.deuses.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (28 * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#0B1020"))
        }
        val raio = TextView(this).apply {
            text = "⚡"
            textSize = 72f
            gravity = Gravity.CENTER
        }
        val titulo = TextView(this).apply {
            text = "DEUSES ARENA"
            textSize = 32f
            setTextColor(Color.parseColor("#FFD54F"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (36 * d).toInt())
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (60 * d).toInt()).apply {
            bottomMargin = (14 * d).toInt()
        }
        val jogar = Button(this).apply {
            text = "Jogar com Zeus"
            layoutParams = lp
            setOnClickListener { startActivity(Intent(this@MainActivity, GameActivity::class.java)) }
        }
        val config = Button(this).apply {
            text = "Configurações"
            layoutParams = lp
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        root.addView(raio)
        root.addView(titulo)
        root.addView(jogar)
        root.addView(config)
        setContentView(root)

        // animação suave de entrada
        listOf(raio, titulo, jogar, config).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 40f * d
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 90L).setDuration(450).start()
        }
        Novidades.mostrarSeNovo(this)
        Updater.autoCheck(this)
    }
}
