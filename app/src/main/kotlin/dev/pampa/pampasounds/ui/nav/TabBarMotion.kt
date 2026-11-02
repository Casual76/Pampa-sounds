package dev.pampa.pampasounds.ui.nav

/**
 * Quanto la barra delle schede e' uscita dallo schermo: 0 e' al suo posto, 1 e' fuori.
 *
 * La barra sta fuori dall'host delle pagine ed e' una sola, quindi non viaggia con nessuna pagina:
 * esce insieme a quella che si apre sopra (il dettaglio di una sessione) e rientra insieme a quella
 * che si chiude, **col dito**. Per questo segue l'avanzamento dell'host e non un interruttore: un
 * back a scorrimento a meta' strada lascia la barra a meta' strada, e un back annullato la riporta
 * dov'era.
 *
 * A riposo ([moving] falso) vale solo [restHidden], cioe' se la pagina davanti non ha la barra.
 * In movimento si va da come stava la pagina che lascia il davanti ([fromHidden]) a come sta quella
 * che lo prende ([toHidden]), di quanto l'host dice che il movimento e' fatto ([progress], 0..1).
 * Fra due schede non cambia niente: tutte e due hanno la barra.
 */
internal fun tabBarHidden(
  moving: Boolean,
  fromHidden: Boolean,
  toHidden: Boolean,
  progress: Float,
  restHidden: Boolean,
): Float {
  if (!moving) return if (restHidden) 1f else 0f
  val from = if (fromHidden) 1f else 0f
  val to = if (toHidden) 1f else 0f
  return from + (to - from) * progress.coerceIn(0f, 1f)
}
