package dev.pampa.pampasounds.dsp

import kotlin.math.log10
import kotlin.math.pow

/**
 * Un criterio di esposizione al rumore.
 *
 * Il tempo concesso a un livello si dimezza ogni [exchangeRateDb] decibel sopra [criterionDb]: a
 * 85 dB(A) NIOSH concede otto ore, a 88 quattro, a 91 due. I livelli sotto [thresholdDb] non contano.
 */
data class DoseCriterion(
  val name: String,
  val criterionDb: Double,
  val exchangeRateDb: Double,
  val thresholdDb: Double,
  val criterionHours: Double = 8.0,
) {
  /** Secondi concessi a [levelDb] prima di arrivare al 100 %. */
  fun allowedSeconds(levelDb: Double): Double =
    criterionHours * 3600.0 / 2.0.pow((levelDb - criterionDb) / exchangeRateDb)

  companion object {
    /** NIOSH REL: 85 dB(A), scambio 3 dB. Il criterio prudente, quello che conviene guardare. */
    val Niosh = DoseCriterion("NIOSH", criterionDb = 85.0, exchangeRateDb = 3.0, thresholdDb = 80.0)

    /** OSHA PEL: 90 dB(A), scambio 5 dB. Il limite di legge negli USA, molto piu' permissivo. */
    val Osha = DoseCriterion("OSHA", criterionDb = 90.0, exchangeRateDb = 5.0, thresholdDb = 80.0)
  }
}

/** La dose di rumore, in percentuale del massimo giornaliero. */
object NoiseDose {

  /** Dose accumulata stando [seconds] secondi a [levelDb] dB(A). */
  fun percentFor(levelDb: Double, seconds: Double, criterion: DoseCriterion): Double {
    if (levelDb < criterion.thresholdDb) return 0.0
    return 100.0 * seconds / criterion.allowedSeconds(levelDb)
  }

  /**
   * Dose di una registrazione a partire dall'istogramma dei suoi livelli al secondo (in dBFS).
   *
   * L'offset di calibrazione si applica qui, alla lettura: cosi' una sessione ricalibrata a meta'
   * strada ha una dose coerente con i suoi numeri, invece di una dose fatta di due tarature.
   */
  fun percent(
    secondLevels: LevelHistogram,
    offsetDb: Double,
    criterion: DoseCriterion,
    secondsPerEntry: Double = 1.0,
  ): Double {
    var dose = 0.0
    secondLevels.forEachLevel { levelDb, count ->
      dose += percentFor(levelDb + offsetDb, count * secondsPerEntry, criterion)
    }
    return dose
  }

  /** Il livello medio su otto ore (TWA) che produce [dosePercent], o null senza dose. */
  fun timeWeightedAverage(dosePercent: Double, criterion: DoseCriterion): Double? {
    if (dosePercent <= 0.0) return null
    return criterion.criterionDb + criterion.exchangeRateDb / log10(2.0) * log10(dosePercent / 100.0)
  }

  /** La dose che si raggiungerebbe in [hours] ore continuando cosi'. */
  fun projected(dosePercent: Double, elapsedSeconds: Double, hours: Double = 8.0): Double =
    if (elapsedSeconds <= 0.0) 0.0 else dosePercent * hours * 3600.0 / elapsedSeconds
}
