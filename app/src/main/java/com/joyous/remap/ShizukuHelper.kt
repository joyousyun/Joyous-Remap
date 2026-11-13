package com.joyous.remap

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/** Ejecuta comandos de shell sin PC a través de la app Shizuku. */
object ShizukuHelper {
    const val PKG = "moe.shizuku.privileged.api"
    private const val REQ = 4711

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun granted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    fun request() {
        try {
            Shizuku.requestPermission(REQ)
        } catch (e: Throwable) {
        }
    }

    /** Devuelve (código de salida, salida de texto). */
    fun run(cmd: String): Pair<Int, String> {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() +
            p.errorStream.bufferedReader().readText()
        return p.waitFor() to out.trim()
    }
}
