package dev.pampa.pampasounds.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FFT complessa radix-2, in place, senza allocazioni dopo la costruzione.
 *
 * Twiddle e permutazione a bit invertiti si calcolano una volta sola: il thread audio la chiama
 * una ventina di volte al secondo e non deve mai chiedere memoria al garbage collector.
 */
class Fft(val size: Int) {

  init {
    require(size >= 2 && size and (size - 1) == 0) { "La dimensione della FFT deve essere una potenza di due: $size" }
  }

  private val bits = Integer.numberOfTrailingZeros(size)
  private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
  private val sinTable = DoubleArray(size / 2) { sin(2.0 * PI * it / size) }
  private val reversed = IntArray(size) { Integer.reverse(it) ushr (32 - bits) }

  /** Trasformata diretta (esponente negativo) di `re + i·im`, sovrascritta sugli stessi array. */
  fun transform(re: DoubleArray, im: DoubleArray) {
    require(re.size == size && im.size == size)
    for (i in 0 until size) {
      val j = reversed[i]
      if (j > i) {
        val tr = re[i]; re[i] = re[j]; re[j] = tr
        val ti = im[i]; im[i] = im[j]; im[j] = ti
      }
    }
    var half = 1
    while (half < size) {
      val step = size / (half * 2)
      var start = 0
      while (start < size) {
        var k = 0
        for (j in start until start + half) {
          val l = j + half
          val c = cosTable[k]
          val s = sinTable[k]
          // (c - i·s)·(re + i·im)
          val tr = re[l] * c + im[l] * s
          val ti = im[l] * c - re[l] * s
          re[l] = re[j] - tr
          im[l] = im[j] - ti
          re[j] += tr
          im[j] += ti
          k += step
        }
        start += half * 2
      }
      half *= 2
    }
  }
}

/**
 * Finestra di Hann *periodica* (denominatore N, non N-1): quella giusta per l'analisi spettrale con
 * sovrapposizione, perche' le copie sfasate di un quarto sommano a una costante.
 */
fun hannWindow(size: Int): DoubleArray = DoubleArray(size) { 0.5 - 0.5 * cos(2.0 * PI * it / size) }
