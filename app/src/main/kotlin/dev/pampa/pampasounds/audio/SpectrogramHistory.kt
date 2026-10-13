package dev.pampa.pampasounds.audio

import dev.pampa.pampasounds.dsp.SpectrumFrame
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp

/**
 * Gli ultimi secondi di spettro, per la cascata.
 *
 * Un anello di [columns] colonne da [rows] righe in frequenza logaritmica (la riga 0 e' la piu'
 * bassa). Una colonna per fotogramma: a 48 kHz sono 43 ms, quindi 512 colonne sono una ventina di
 * secondi. Si scrive dal thread audio e si legge dalla UI copiando: lo spettrogramma vive qui e non
 * nella schermata perche' cambiare scheda non deve cancellare quello che si e' appena sentito.
 *
 * I valori sono dBFS non pesati: pesatura, calibrazione e colori li mette chi disegna.
 */
class SpectrogramHistory(
  sampleRate: Int,
  fftSize: Int,
  val columns: Int = 512,
  val rows: Int = 160,
) {
  val minHz: Double = 20.0
  val maxHz: Double = min(20_000.0, sampleRate / 2.0)

  /** La frequenza al centro di ogni riga. */
  val rowFrequencies: DoubleArray = DoubleArray(rows) { row ->
    exp(ln(minHz) + (ln(maxHz) - ln(minHz)) * (row + 0.5) / rows)
  }

  private val binHz = sampleRate.toDouble() / fftSize
  private val lastBin = fftSize / 2

  // Per ogni riga, l'intervallo di bin che copre: nei bassi una riga e' piu' stretta di un bin e
  // si interpola, negli acuti ne copre decine e si prende il massimo (un fischio stretto deve
  // restare visibile, non annegare nella media).
  private val rowStart = IntArray(rows)
  private val rowEnd = IntArray(rows)
  private val rowExact = DoubleArray(rows)

  init {
    val ratio = exp((ln(maxHz) - ln(minHz)) / rows)
    for (row in 0 until rows) {
      val lo = rowFrequencies[row] / kotlin.math.sqrt(ratio)
      val hi = rowFrequencies[row] * kotlin.math.sqrt(ratio)
      rowStart[row] = (lo / binHz).toInt().coerceIn(1, lastBin)
      rowEnd[row] = (hi / binHz).toInt().coerceIn(rowStart[row], lastBin)
      rowExact[row] = (rowFrequencies[row] / binHz).coerceIn(1.0, lastBin - 1.0)
    }
  }

  private val lock = Any()
  private val values = FloatArray(columns * rows) { SILENT }
  private var head = 0

  /** Quante colonne sono state scritte in tutto: cambia a ogni fotogramma, la UI ci si appoggia. */
  @Volatile
  var written: Long = 0L
    private set

  fun push(frame: SpectrumFrame) {
    val line = frame.lineDb
    val column = FloatArray(rows)
    for (row in 0 until rows) {
      column[row] = if (rowEnd[row] - rowStart[row] >= 2) {
        var best = line[rowStart[row]]
        for (k in rowStart[row] + 1..rowEnd[row]) best = max(best, line[k])
        best
      } else {
        val exact = rowExact[row]
        val k = exact.toInt()
        val t = (exact - k).toFloat()
        line[k] * (1 - t) + line[min(k + 1, lastBin)] * t
      }
    }
    synchronized(lock) {
      System.arraycopy(column, 0, values, head * rows, rows)
      head = (head + 1) % columns
      written++
    }
  }

  /**
   * Copia l'anello in [target] (dimensione `columns * rows`) dalla colonna piu' vecchia alla piu'
   * recente, gia' in ordine di tempo.
   */
  fun copyChronological(target: FloatArray) = synchronized(lock) {
    val tail = (columns - head) * rows
    System.arraycopy(values, head * rows, target, 0, tail)
    System.arraycopy(values, 0, target, tail, head * rows)
  }

  companion object {
    /** Il valore delle colonne mai scritte: sotto qualsiasi scala, quindi disegnate come fondo. */
    const val SILENT: Float = -200f
  }
}
