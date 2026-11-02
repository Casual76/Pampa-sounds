package dev.pampa.pampasounds.ui.settings

import dev.antigravity.fluidengine.ui.fluid.FluidPromptValidation

/** Quello che un fonometro di riferimento legge: sotto i 20 dB e sopra i 140 non e' un confronto sensato. */
internal val ReferenceRangeDb = 20.0..140.0

/** Il numero scritto nel campo, con la virgola o con il punto; null se non e' un numero. */
internal fun parseReference(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/**
 * Cosa pensa l'avviso di taratura di quello che e' stato scritto, perche' il tasto «Calibra» si accenda
 * solo quando ha senso: il campo vuoto non ha sbagliato niente ([FluidPromptValidation.Incomplete]), un
 * valore che non e' un numero o e' fuori misura ha sbagliato e lo dice, e senza una lettura dell'app da
 * confrontare ([appReading] nullo o NaN) non si puo' calibrare niente.
 */
internal fun calibrationValidation(text: String, appReading: Double?): FluidPromptValidation {
  if (text.isBlank()) return FluidPromptValidation.Incomplete
  val reference = parseReference(text)
    ?: return FluidPromptValidation.Invalid("Scrivi un numero, per esempio 54,3")
  if (reference !in ReferenceRangeDb) {
    return FluidPromptValidation.Invalid("Un fonometro legge fra 20 e 140 dB")
  }
  if (appReading == null || appReading.isNaN()) {
    return FluidPromptValidation.Invalid("L'app non ha ancora una lettura: aspetta un secondo")
  }
  return FluidPromptValidation.Valid
}
