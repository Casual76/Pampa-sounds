package dev.pampa.pampasounds.dsp

/**
 * Uno spettro, fermo nel tempo. Immutabile: passa dal thread audio alla UI senza copie difensive.
 *
 * Tutto in dBFS e **non pesato**: la pesatura A/C e la calibrazione sono scelte di chi guarda, e si
 * applicano alla lettura (una somma in dB per bin), cosi' cambiarle non tocca la misura.
 */
class SpectrumFrame(
  val sampleRate: Int,
  val fftSize: Int,
  /** Un valore per bin (0 … N/2). Normalizzato per i toni: un seno di RMS r legge 20·log10(r). */
  val lineDb: FloatArray,
  /** Le trenta bande a terzi d'ottava, normalizzate in energia (Parseval), in dBFS. */
  val bandDb: FloatArray,
  /** Le stesse bande in potenza lineare, per fare medie senza riconvertire. */
  val bandPower: DoubleArray,
) {
  val binHz: Double get() = sampleRate.toDouble() / fftSize
}

/**
 * Spettro a finestra scorrevole: Hann su [fftSize] campioni, un fotogramma ogni [hopSize].
 *
 * Con 8192 punti a 48 kHz la risoluzione e' 5,9 Hz e la finestra dura 170 ms; il passo di un quarto
 * di finestra da' un fotogramma ogni 43 ms, che e' il ritmo dello spettrogramma. Le due
 * normalizzazioni servono a due domande diverse: "quanto e' forte quel fischio" (un tono, picco nel
 * suo bin) e "quanta energia c'e' in quella banda" (rumore, somma su piu' bin).
 */
class SpectrumAnalyzer(
  val sampleRate: Int,
  val fftSize: Int = 8192,
  val hopSize: Int = fftSize / 4,
) {
  private val fft = Fft(fftSize)
  private val window = hannWindow(fftSize)
  private val toneScale: Double
  private val energyScale: Double

  init {
    var sum = 0.0
    var sumSquares = 0.0
    for (w in window) {
      sum += w
      sumSquares += w * w
    }
    toneScale = 2.0 / (sum * sum)
    energyScale = 2.0 / (fftSize * sumSquares)
  }

  private val history = FloatArray(fftSize)
  private var writeIndex = 0
  private var filled = 0
  private var sinceFrame = 0
  private val re = DoubleArray(fftSize)
  private val im = DoubleArray(fftSize)
  private val energy = DoubleArray(fftSize / 2 + 1)
  private val bands = BandAggregator(sampleRate, fftSize)

  val binCount: Int get() = fftSize / 2 + 1

  fun push(samples: FloatArray, count: Int, onFrame: (SpectrumFrame) -> Unit) {
    for (i in 0 until count) {
      history[writeIndex] = samples[i]
      writeIndex = if (writeIndex == fftSize - 1) 0 else writeIndex + 1
      if (filled < fftSize) filled++
      sinceFrame++
      if (filled == fftSize && sinceFrame >= hopSize) {
        sinceFrame = 0
        onFrame(analyze())
      }
    }
  }

  fun reset() {
    history.fill(0f)
    writeIndex = 0
    filled = 0
    sinceFrame = 0
  }

  private fun analyze(): SpectrumFrame {
    // Il campione piu' vecchio sta dove si scrivera' il prossimo.
    var source = writeIndex
    for (n in 0 until fftSize) {
      re[n] = history[source] * window[n]
      im[n] = 0.0
      source = if (source == fftSize - 1) 0 else source + 1
    }
    fft.transform(re, im)
    val half = fftSize / 2
    val line = FloatArray(half + 1)
    for (k in 0..half) {
      val magnitudeSquared = re[k] * re[k] + im[k] * im[k]
      // DC e Nyquist non hanno il gemello a frequenza negativa: niente fattore due.
      val edge = if (k == 0 || k == half) 0.5 else 1.0
      energy[k] = magnitudeSquared * energyScale * edge
      line[k] = powerToDb(magnitudeSquared * toneScale * edge).toFloat()
    }
    val bandPower = DoubleArray(ThirdOctave.COUNT)
    bands.aggregate(energy, bandPower)
    val bandDb = FloatArray(ThirdOctave.COUNT) { powerToDb(bandPower[it]).toFloat() }
    return SpectrumFrame(sampleRate, fftSize, line, bandDb, bandPower)
  }
}
