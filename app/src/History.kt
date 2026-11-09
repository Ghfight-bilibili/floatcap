package com.dsh.floatcap

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object History {
    private val items = ArrayList<String>()
    private var started = 0L

    fun begin() {
        items.clear()
        started = System.currentTimeMillis()
    }

    fun add(line: String) {
        if (line.isBlank()) return
        synchronized(items) {
            items.add(line)
            if (items.size > 4000) items.removeAt(0)
        }
    }

    fun snapshot(): List<String> = synchronized(items) { items.toList() }

    fun count(): Int = synchronized(items) { items.size }

    fun clear() = synchronized(items) { items.clear() }

    fun export(ctx: Context): String? {
        val list = snapshot()
        if (list.isEmpty()) return null
        val fmt = SimpleDateFormat("MM-dd HH-mm", Locale.getDefault())
        val name = "浮字字幕-" + fmt.format(Date(started.takeIf { it > 0 } ?: System.currentTimeMillis())) + ".txt"
        val body = buildString {
            append("# 浮字字幕记录\n\n")
            for (l in list) append(l).append('\n')
        }
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DSH-Go")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) null else {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
                name
            }
        } catch (t: Throwable) {
            null
        }
    }

}
