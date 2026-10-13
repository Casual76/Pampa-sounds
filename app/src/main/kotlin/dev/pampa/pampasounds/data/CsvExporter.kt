package dev.pampa.pampasounds.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Una sessione in CSV, pronta da condividere.
 *
 * Punto decimale e virgola come separatore: e' il CSV che ogni foglio di calcolo apre senza
 * chiedere niente (Fogli, Numbers, LibreOffice; Excel italiano lo importa con "Dati > Da testo").
 * I livelli sono gia' calibrati, in dB SPL: chi apre il file non deve sapere cos'e' un dBFS.
 */
class CsvExporter(private val context: Context, private val repository: SessionRepository) {

  suspend fun export(session: SessionEntity): Uri = withContext(Dispatchers.IO) {
    val samples = repository.samples(session.id)
    val zone = ZoneId.systemDefault()
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ITALY)
    val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT)
    val offset = session.offsetDb
    fun level(value: Float?) = value?.let { "%.1f".format(Locale.ROOT, it + offset) } ?: ""

    val directory = File(context.cacheDir, "exports").apply { mkdirs() }
    // Un file solo alla volta: gli export vecchi non servono a nessuno e occupano spazio.
    directory.listFiles()?.forEach { it.delete() }
    val started = Instant.ofEpochMilli(session.startedAt).atZone(zone)
    val file = File(directory, "pampa-sounds-${fileStamp.format(started)}.csv")
    file.bufferedWriter().use { out ->
      out.appendLine("secondo,ora,LAeq,LCeq,LZeq,LAFmax,LAFmin,LCFmax,LZFmax,LZpeak")
      for (sample in samples) {
        val time = started.plusSeconds(sample.second.toLong())
        out.append(sample.second.toString()).append(',')
          .append(clock.format(time)).append(',')
          .append(level(sample.eqA)).append(',')
          .append(level(sample.eqC)).append(',')
          .append(level(sample.eqZ)).append(',')
          .append(level(sample.maxA)).append(',')
          .append(level(sample.minA)).append(',')
          .append(level(sample.maxC)).append(',')
          .append(level(sample.maxZ)).append(',')
          .append(level(sample.peak))
          .appendLine()
      }
    }
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)
  }
}
