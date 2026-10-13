package dev.pampa.pampasounds.dsp

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Le bande a terzi d'ottava da 25 Hz a 20 kHz (IEC 61260, base 2).
 *
 * Trenta bande, la griglia standard dell'acustica degli ambienti: un ronzio di rete cade nella 50,
 * la voce fra 250 e 4 k, il sibilo di un trasformatore nelle ultime.
 */
object ThirdOctave {
  const val COUNT = 30

  /** Centri esatti: 1000 · 2^(n/3), n = −16 … 13. */
  val centers: DoubleArray = DoubleArray(COUNT) { 1000.0 * 2.0.pow((it - 16) / 3.0) }

  /** I nomi nominali con cui le bande si scrivono ovunque. */
  val labels: List<String> = listOf(
    "25", "31,5", "40", "50", "63", "80", "100", "125", "160", "200",
    "250", "315", "400", "500", "630", "800", "1k", "1,25k", "1,6k", "2k",
    "2,5k", "3,15k", "4k", "5k", "6,3k", "8k", "10k", "12,5k", "16k", "20k",
  )

  private val halfBand = 2.0.pow(1.0 / 6.0)

  fun lowerEdge(index: Int): Double = centers[index] / halfBand
  fun upperEdge(index: Int): Double = centers[index] * halfBand
}

/**
 * Raccoglie la potenza di uno spettro FFT nelle bande a terzi d'ottava.
 *
 * Ogni bin copre [k − ½, k + ½]·Δf e cede a ogni banda la frazione di larghezza che le cade dentro.
 * Nei bassi una banda e' piu' stretta di un bin (a 48 kHz con 8192 punti un bin e' 5,9 Hz, la banda
 * dei 25 Hz e' larga 5,8): li' la banda e' una stima, e lo si sa — un microfono da telefono sotto
 * gli 80 Hz sente comunque poco.
 */
class BandAggregator(sampleRate: Int, fftSize: Int) {
  private val binHz = sampleRate.toDouble() / fftSize
  private val nyquistBin = fftSize / 2
  private val bins: Array<IntArray>
  private val weights: Array<DoubleArray>

  init {
    val binLists = Array(ThirdOctave.COUNT) { ArrayList<Int>() }
    val weightLists = Array(ThirdOctave.COUNT) { ArrayList<Double>() }
    for (band in 0 until ThirdOctave.COUNT) {
      val lo = ThirdOctave.lowerEdge(band)
      val hi = ThirdOctave.upperEdge(band)
      val first = max(1, (lo / binHz - 0.5).toInt())
      val last = min(nyquistBin - 1, (hi / binHz + 0.5).toInt() + 1)
      for (k in first..last) {
        val binLo = (k - 0.5) * binHz
        val binHi = (k + 0.5) * binHz
        val overlap = min(hi, binHi) - max(lo, binLo)
        if (overlap > 0) {
          binLists[band].add(k)
          weightLists[band].add(overlap / binHz)
        }
      }
    }
    bins = Array(ThirdOctave.COUNT) { binLists[it].toIntArray() }
    weights = Array(ThirdOctave.COUNT) { weightLists[it].toDoubleArray() }
  }

  /** [energyPower]: potenza per bin normalizzata con Parseval. Scrive in [out] la potenza di banda. */
  fun aggregate(energyPower: DoubleArray, out: DoubleArray) {
    for (band in 0 until ThirdOctave.COUNT) {
      var sum = 0.0
      val b = bins[band]
      val w = weights[band]
      for (i in b.indices) sum += energyPower[b[i]] * w[i]
      out[band] = sum
    }
  }
}
