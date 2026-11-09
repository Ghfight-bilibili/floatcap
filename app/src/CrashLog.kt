package com.dsh.floatcap

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter

object CrashLog {
    fun install(ctx: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            var trace = e.toString()
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                trace = "thread: " + t.name + "\n\n" + sw.toString()
                write(ctx, "floatcap-crash.txt", trace)
            } catch (_: Throwable) {
            }
            try {
                CrashActivity.show(ctx, trace)
            } catch (_: Throwable) {
            }
            if (prev != null) prev.uncaughtException(t, e)
        }
    }

    private val uris = HashMap<String, Uri>()

    fun trace(ctx: Context, name: String, line: String) {
        try {
            val uri = uris[name] ?: (insert(ctx, name) ?: return).also { uris[name] = it }
            ctx.contentResolver.openOutputStream(uri, "wa")?.use {
                it.write((line + "\n").toByteArray())
            }
        } catch (_: Throwable) {
        }
    }

    private fun insert(ctx: Context, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DSH-Go")
        }
        return ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    fun boot(ctx: Context) {
        log(ctx, "floatcap-boot.txt", "boot " + System.currentTimeMillis())
    }

    fun log(ctx: Context, name: String, text: String) {
        try {
            write(ctx, name, text)
        } catch (_: Throwable) {
        }
    }

    private fun write(ctx: Context, name: String, text: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DSH-Go")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }
}
