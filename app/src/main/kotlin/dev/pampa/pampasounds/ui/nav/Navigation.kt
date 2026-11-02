package dev.pampa.pampasounds.ui.nav

import androidx.navigation.NavHostController

/**
 * Le rotte dell'app: quattro schede, pari fra loro, e il dettaglio di una sessione che si apre sopra.
 *
 * Le pagine le muove `FluidNavHost` (apertura da destra, la pagina sotto che arretra, il back che
 * segue il dito, il passo laterale fra le schede): qui non c'e' piu' nessun movimento, solo i nomi.
 */
object Routes {
  const val LIVE = "live"
  const val SPECTRUM = "spectrum"
  const val HISTORY = "history"
  const val SETTINGS = "settings"
  const val SESSION = "session/{id}"
  const val SESSION_ID = "id"

  fun session(id: Long) = "session/$id"
}

/**
 * Passa a un'altra scheda.
 *
 * Una scheda non sta sopra l'altra: la pila resta fatta di una scheda sola, e il back da qualunque
 * scheda esce dall'app (com'era prima, con le schede dentro un `AnimatedContent`). Come prima, una
 * scheda che si lascia non ricorda dove la si era lasciata: tornandoci riparte dall'inizio.
 */
fun NavHostController.selectTab(route: String) {
  navigate(route) {
    // Via tutta la pila, grafo compreso: la scheda di partenza non e' sempre quella davanti, e un popUpTo su di
    // lei, una volta uscita dalla pila, non trova niente da togliere e le schede si accumulerebbero.
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
  }
}
