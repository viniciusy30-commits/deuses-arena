package com.viniciusy.deuses.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context

object Novidades {
    class Secao(val id: String, val titulo: String, val itens: List<String>)

    fun ler(ctx: Context): List<Secao> {
        val texto = try {
            ctx.assets.open("novidades.txt").bufferedReader().readText()
        } catch (e: Exception) {
            return emptyList()
        }
        val out = ArrayList<Secao>()
        var id = ""
        var titulo = ""
        var itens = ArrayList<String>()
        fun fecha() {
            if (id.isNotEmpty()) out.add(Secao(id, titulo, itens))
            itens = ArrayList()
        }
        for (linha in texto.lines()) {
            val l = linha.trim()
            when {
                l.startsWith("#id ") -> { fecha(); id = l.removePrefix("#id ").trim(); titulo = "" }
                l.startsWith("#titulo ") -> titulo = l.removePrefix("#titulo ").trim()
                l.startsWith("- ") -> itens.add(l.removePrefix("- ").trim())
            }
        }
        fecha()
        return out
    }

    fun mostrar(act: Activity, secao: Secao) {
        val corpo = secao.itens.joinToString("\n") { "• $it" }
        AlertDialog.Builder(act)
            .setTitle("O que mudou: ${secao.titulo}")
            .setMessage(corpo)
            .setPositiveButton("Legal!", null)
            .show()
    }

    fun mostrarSeNovo(act: Activity) {
        val topo = ler(act).firstOrNull() ?: return
        val prefs = act.getSharedPreferences("novidades", Context.MODE_PRIVATE)
        if (prefs.getString("visto", "") == topo.id) return
        prefs.edit().putString("visto", topo.id).apply()
        mostrar(act, topo)
    }

    fun mostrarUltima(act: Activity) {
        val topo = ler(act).firstOrNull() ?: return
        mostrar(act, topo)
    }
}
