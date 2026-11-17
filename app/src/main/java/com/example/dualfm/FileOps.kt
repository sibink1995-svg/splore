package com.example.dualfm

import java.io.File
import java.text.DateFormat
import java.util.Date

object FileOps {

    fun list(dir: File, showHidden: Boolean): List<File> =
        (dir.listFiles() ?: emptyArray<File>())
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))

    private fun unique(destDir: File, name: String): File {
        var t = File(destDir, name)
        if (!t.exists()) return t
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (t.exists()) {
            t = File(destDir, "$base ($i)$ext")
            i++
        }
        return t
    }

    fun copy(src: File, destDir: File): Boolean = try {
        val target = unique(destDir, src.name)
        if (src.isDirectory) {
            val sep = File.separator
            if ((destDir.canonicalPath + sep).startsWith(src.canonicalPath + sep)) false
            else src.copyRecursively(target, overwrite = false)
        } else {
            src.copyTo(target, overwrite = false)
            true
        }
    } catch (e: Exception) {
        false
    }

    fun move(src: File, destDir: File): Boolean {
        if (src.parentFile?.canonicalPath == destDir.canonicalPath) return true
        val target = unique(destDir, src.name)
        if (src.renameTo(target)) return true
        return copy(src, destDir) && src.deleteRecursively()
    }

    fun delete(f: File): Boolean = f.deleteRecursively()

    fun sizeOf(f: File, depth: Int = 0): Long {
        if (!f.isDirectory) return f.length()
        if (depth > 40) return 0L
        var total = 0L
        f.listFiles()?.forEach { total += sizeOf(it, depth + 1) }
        return total
    }

    fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return String.format("%.1f %s", v, units[i])
    }

    fun fmtDate(ms: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))
}
