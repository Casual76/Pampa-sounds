package dev.pampa.pampasounds.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

// Le quattro frequenze dei poli della norma IEC 61672-1 (Hz).
private const val F1 = 20.598997
private const val F2 = 107.65265
private const val F3 = 737.86223
private const val F4 = 12194.217

/**
 * Le pesature in frequenza di un fonometro.
 *
 * - **A** imita l'orecchio a volume moderato: taglia i bassi (−19 dB a 100 Hz) e un po' gli acuti.
 *   E' quella delle leggi sul rumore e della dose di esposizione.
 * - **C** e' quasi piatta fra 31 Hz e 8 kHz: dice quanta energia c'e' nei bassi che la A nasconde.
 * - **Z** e' "zero", nessuna pesatura: il microfono e basta.
 */
enum class FrequencyWeighting {
  A,
  C,
  Z,
  ;

  /** Guadagno analitico (dB) alla frequenza data, dalla definizione della norma. */
  fun gainDb(frequencyHz: Double): Double = when (this) {
    A -> aWeightingDb(frequencyHz)
    C -> cWeightingDb(frequencyHz)
    Z -> 0.0
  }
}

/** Curva A di IEC 61672-1, normalizzata a 0 dB a 1 kHz. */
fun aWeightingDb(frequencyHz: Double): Double {
  if (frequencyHz <= 0.0) return SILENCE_DBFS
  val f2 = frequencyHz * frequencyHz
  val ra = (F4 * F4 * f2 * f2) /
    ((f2 + F1 * F1) * sqrt((f2 + F2 * F2) * (f2 + F3 * F3)) * (f2 + F4 * F4))
  return 20.0 * log10(ra) + 2.0
}

/** Curva C di IEC 61672-1, normalizzata a 0 dB a 1 kHz. */
fun cWeightingDb(frequencyHz: Double): Double {
  if (frequencyHz <= 0.0) return SILENCE_DBFS
  val f2 = frequencyHz * frequencyHz
  val rc = (F4 * F4 * f2) / ((f2 + F1 * F1) * (f2 + F4 * F4))
  return 20.0 * log10(rc) + 0.06
}

/** Un biquad in forma diretta II trasposta: lo stato in doppia precisione, mai in float. */
private class Biquad(
  private val b0: Double,
  private val b1: Double,
  private val b2: Double,
  private val a1: Double,
  private val a2: Double,
) {
  private var z1 = 0.0
  private var z2 = 0.0

  fun process(x: Double): Double {
    val y = b0 * x + z1
    z1 = b1 * x - a1 * y + z2
    z2 = b2 * x - a2 * y
    return y
  }

  fun reset() {
    z1 = 0.0
    z2 = 0.0
  }

  /** |H(e^jω)| */
  fun magnitude(omega: Double): Double {
    val c1 = cos(omega); val s1 = sin(omega)
    val c2 = cos(2 * omega); val s2 = sin(2 * omega)
    val nr = b0 + b1 * c1 + b2 * c2
    val ni = -(b1 * s1 + b2 * s2)
    val dr = 1.0 + a1 * c1 + a2 * c2
    val di = -(a1 * s1 + a2 * s2)
    return sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
  }

  companion object {
    /** Il biquad con due zeri e due poli reali: (1 − z₁z⁻¹)(1 − z₂z⁻¹) / (1 − p₁z⁻¹)(1 − p₂z⁻¹). */
    fun fromRoots(z1: Double, z2: Double, p1: Double, p2: Double) = Biquad(
      b0 = 1.0,
      b1 = -(z1 + z2),
      b2 = z1 * z2,
      a1 = -(p1 + p2),
      a2 = p1 * p2,
    )
  }
}

/**
 * La pesatura A o C applicata nel tempo, campione per campione.
 *
 * Serve nel tempo e non solo sullo spettro perche' le costanti Fast/Slow e il picco si misurano sul
 * segnale pesato. Il filtro analogico della norma passa nel discreto con la trasformata bilineare:
 * ogni polo reale in s = −ω diventa z = (2fs − ω)/(2fs + ω), gli zeri nell'origine diventano z = 1, e
 * i poli in eccesso portano zeri in z = −1. Il guadagno si fissa misurando il filtro digitale a
 * 1 kHz, cosi' lo zero della scala e' esatto per costruzione.
 *
 * Il prezzo della bilineare e' la compressione delle frequenze vicino a Nyquist: a 48 kHz la curva
 * resta entro mezzo decibel dalla norma fino a ~5 kHz e scende di circa un decibel in piu' a 10 kHz,
 * ben dentro le tolleranze di classe 2 — e comunque sotto la risposta di un microfono da telefono.
 */
class WeightingFilter private constructor(
  private val sections: Array<Biquad>,
  private val sampleRate: Int,
) {
  private var gain = 1.0

  fun process(x: Double): Double {
    var y = x * gain
    for (section in sections) y = section.process(y)
    return y
  }

  fun reset() {
    for (section in sections) section.reset()
  }

  /** Risposta del filtro digitale (dB) alla frequenza data. */
  fun responseDb(frequencyHz: Double): Double {
    val omega = 2.0 * PI * frequencyHz / sampleRate
    var magnitude = gain
    for (section in sections) magnitude *= section.magnitude(omega)
    return 20.0 * log10(magnitude)
  }

  companion object {
    /** Il filtro per [weighting], o `null` per la Z, che non filtra niente. */
    fun create(weighting: FrequencyWeighting, sampleRate: Int): WeightingFilter? {
      val twoFs = 2.0 * sampleRate
      fun pole(frequency: Double): Double {
        val omega = 2.0 * PI * frequency
        return (twoFs - omega) / (twoFs + omega)
      }
      val p1 = pole(F1)
      val p4 = pole(F4)
      val sections = when (weighting) {
        FrequencyWeighting.A -> arrayOf(
          Biquad.fromRoots(1.0, 1.0, p1, p1),
          Biquad.fromRoots(1.0, 1.0, pole(F2), pole(F3)),
          Biquad.fromRoots(-1.0, -1.0, p4, p4),
        )
        FrequencyWeighting.C -> arrayOf(
          Biquad.fromRoots(1.0, 1.0, p1, p1),
          Biquad.fromRoots(-1.0, -1.0, p4, p4),
        )
        FrequencyWeighting.Z -> return null
      }
      val filter = WeightingFilter(sections, sampleRate)
      // Normalizzazione: a 1 kHz il filtro digitale deve dare esattamente quello che da' la norma.
      val target = weighting.gainDb(1000.0)
      val actual = filter.responseDb(1000.0)
      filter.gain = Math.pow(10.0, (target - actual) / 20.0)
      return filter
    }
  }
}
