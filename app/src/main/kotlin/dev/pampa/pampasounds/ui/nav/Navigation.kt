package dev.pampa.pampasounds.ui.nav

import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination

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
 * scheda esce dall'app (com'era prima, con le schede dentro un `AnimatedContent`). Ogni scheda
 * conserva dove la si era lasciata (la posizione della lista, la fascia scelta): e' lo stato che
 * `navigation` salva quando la scheda esce e rimette quando rientra.
 */
fun NavHostController.selectTab(route: String) {
  navigate(route) {
    popUpTo(graph.findStartDestination().id) {
      inclusive = true
      saveState = true
    }
    launchSingleTop = true
    restoreState = true
  }
}
