package com.akash.moneytracker.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes exports to the cache folder and hands them to Android's share sheet. */
object Exporter {
    private fun exportsDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    fun newFile(context: Context, name: String): File = File(exportsDir(context), name)

    /**
     * Shared files (backups, CSVs, PDFs) are copies of private financial data, so they don't linger in the app's
     * cache: anything older than [maxAgeMillis] is deleted. Called when the app starts.
     */
    fun cleanup(context: Context, maxAgeMillis: Long = 10 * 60_000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        exportsDir(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    /** Frames a chart image on the app background with a footer, saves it as a PNG and shares it. */
    suspend fun sharePng(context: Context, chart: Bitmap, fileName: String, footer: String, background: Int) {
        val file = withContext(Dispatchers.Default) {
            // Compose captures into a hardware bitmap, which can't be drawn onto a software canvas.
            val chart = chart.copy(Bitmap.Config.ARGB_8888, false)
            val pad = 48
            val footerHeight = 64
            val framed = Bitmap.createBitmap(chart.width + pad * 2, chart.height + pad * 2 + footerHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(framed)
            canvas.drawColor(background)
            canvas.drawBitmap(chart, pad.toFloat(), pad.toFloat(), null)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6E6E73")
                textSize = 26f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(footer, framed.width / 2f, framed.height - pad / 2f - 4f, paint)
            newFile(context, "$fileName.png").also { f ->
                f.outputStream().use { framed.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        share(context, file, "image/png", "Share chart")
    }

    fun share(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
