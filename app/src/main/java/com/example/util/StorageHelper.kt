package com.example.util

import android.content.Context
import android.os.Environment
import java.io.File

object StorageHelper {

    fun getBeamDropDir(context: Context): File {
        // Use external app-specific files directory under Downloads/BeamDrop for direct accessible storage
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadsDir, "BeamDrop")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getUniqueTargetFile(context: Context, originalFileName: String): File {
        val dir = getBeamDropDir(context)
        var target = File(dir, originalFileName)
        if (!target.exists()) {
            return target
        }

        val dotIndex = originalFileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalFileName.substring(0, dotIndex) else originalFileName
        val extension = if (dotIndex > 0) originalFileName.substring(dotIndex) else ""

        var counter = 1
        while (target.exists()) {
            target = File(dir, "${baseName}_($counter)$extension")
            counter++
        }
        return target
    }

    fun getTotalStorageUsed(context: Context): Long {
        val dir = getBeamDropDir(context)
        return calculateDirSize(dir)
    }

    private fun calculateDirSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { child ->
            size += calculateDirSize(child)
        }
        return size
    }
}
