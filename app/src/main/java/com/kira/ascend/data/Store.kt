package com.kira.ascend.data

import android.content.Context
import com.kira.ascend.model.AppData
import com.kira.ascend.model.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * Offline-first local persistence. The whole save lives in app-private storage
 * (not readable by other apps). Writes are serialised on one thread, written to a
 * temp file first, and the previous good save is kept as a backup.
 */
class Store(private val ctx: Context) {
    private val main = File(ctx.filesDir, "ascend_save.json")
    private val backup = File(ctx.filesDir, "ascend_save.bak")
    private val tmp = File(ctx.filesDir, "ascend_save.tmp")
    private val exec = Executors.newSingleThreadExecutor()

    fun load(): AppData {
        val fromMain = readFile(main)
        if (fromMain != null) return fromMain
        val fromBackup = readFile(backup)
        if (fromBackup != null) return fromBackup
        return AppData()
    }

    private fun readFile(f: File): AppData? {
        if (!f.exists()) return null
        return try {
            Json.parse(f.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun save(json: String) {
        exec.execute {
            try {
                tmp.writeText(json)
                if (main.exists()) main.copyTo(backup, overwrite = true)
                if (!tmp.renameTo(main)) {
                    main.writeText(json)
                    tmp.delete()
                }
            } catch (e: Exception) {
                // Persistence is best-effort; the in-memory state stays valid.
            }
        }
    }

    fun wipe() {
        exec.execute {
            main.delete(); backup.delete(); tmp.delete()
        }
    }
}
