package dev.pampa.pampasounds.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.ui.fluid.FluidChromeController
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.FluidPeerMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.ui.history.HistoryScreen
import dev.pampa.pampasounds.ui.live.LiveScreen
import dev.pampa.pampasounds.ui.nav.Routes
import dev.pampa.pampasounds.ui.nav.selectTab
import dev.pampa.pampasounds.ui.nav.tabBarHidden
import dev.pampa.pampasounds.ui.session.SessionScreen
import dev.pampa.pampasounds.ui.settings.SettingsScreen
import dev.pampa.pampasounds.ui.spectrum.SpectrumScreen
import kotlin.math.roundToInt

enum class SoundsTab(val route: String, val label: String, val icon: ImageVector) {
  Live(Routes.LIVE, "Misura", Icons.Rounded.Speed),
  Spectrum(Routes.SPECTRUM, "Spettro", Icons.Rounded.GraphicEq),
  History(Routes.HISTORY, "Storico", Icons.Rounded.History),
  Settings(Routes.SETTINGS, "Impostazioni", Icons.Rounded.Settings),
  ;

  val item: FluidTabItem get() = FluidTabItem(route = route, label = label, icon = icon)

  companion object {
    fun fromRoute(route: String?): SoundsTab? = entries.firstOrNull { it.route == route }
  }
}

/**
 * Il guscio: quattro schede sotto una barra flottante di vetro, e il dettaglio di una sessione
 * che si apre sopra. Le schede e il dettaglio sono pagine di `FluidNavHost`: le schede fanno il passo
 * laterale (`peer`), il dettaglio si apre e si chiude col dito. Barra, notifiche e modali sono fratelli
 * dell'host e rifrangono la stessa pagina (`chromeController.activeBackdrop`): lo stesso schema della
 * galleria dell'engine.
 */
@Composable
fun AppRoot(container: AppContainer) {
  val nav = rememberNavController()
  val pages = rememberFluidPageHostState()
  val backStack by nav.currentBackStackEntryAsState()
  val route = backStack?.destination?.route
  // Prima che navigation abbia una rotta e' la prima scheda; il dettaglio di una sessione non ne ha.
  val tab = if (route == null) SoundsTab.Live else SoundsTab.fromRoute(route)
  val onMain = tab != null
  // L'ultima scheda davanti: mentre il dettaglio si apre e la barra se ne va, la barra resta com'era e non salta a Misura.
  val lastTab = remember { LastTab() }
  if (tab != null) lastTab.value = tab

  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val chromeController = rememberFluidChromeController()
  val scrollToTop = remember { FluidScrollToTopBus() }
  val modalHost = rememberFluidGlassModalHostState()
  val notificationHost = rememberFluidNotificationHostState()
  val fallbackBackdrop = rememberGlassBackdrop()
  val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop
  val tabBarScroll = rememberFluidFloatingTabBarScrollConnection()
  val meter = rememberMeterController(container)

  val openSession = remember(nav) { { id: Long -> nav.navigate(Routes.session(id)) } }
  val openTab = remember(nav) { { target: SoundsTab -> nav.selectTab(target.route) } }
  // L'ordine delle schede: fra due schede la pagina fa il passo laterale nel verso che la barra implica.
  val peer = remember {
    FluidPeerMove<NavBackStackEntry> { from, to ->
      val before = SoundsTab.fromRoute(from.destination.route)
      val after = SoundsTab.fromRoute(to.destination.route)
      if (before == null || after == null) 0 else after.ordinal - before.ordinal
    }
  }

  CompositionLocalProvider(
    LocalFluidGlassModalHostState provides modalHost,
    LocalFluidNotificationHostState provides notificationHost,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      ProvideFluidChrome(controller = chromeController, bottomInset = 0.dp, scrollToTop = scrollToTop) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(tabBarScroll)
            .fluidGlassModalObscured(),
        ) {
          FluidNavHost(
            navController = nav,
            startDestination = Routes.LIVE,
            modifier = Modifier.fillMaxSize(),
            peer = peer,
            state = pages,
          ) {
            composable(Routes.LIVE) {
              TabPage(chromeController, scrollToTop) { LiveScreen(container, meter, bottomInset, openTab) }
            }
            composable(Routes.SPECTRUM) {
              TabPage(chromeController, scrollToTop) { SpectrumScreen(container, meter, bottomInset) }
            }
            composable(Routes.HISTORY) {
              TabPage(chromeController, scrollToTop) { HistoryScreen(container, meter, bottomInset, openSession) }
            }
            composable(Routes.SETTINGS) {
              TabPage(chromeController, scrollToTop) { SettingsScreen(container, bottomInset) }
            }
            // La sessione cresce dal punto toccato: era il carattere dell'apertura prima della 3.0, e `Expand`
            // lo conserva (opaca, da 0,91 a 1, con la pagina sotto ferma).
            composable(
              route = Routes.SESSION,
              arguments = listOf(navArgument(Routes.SESSION_ID) { type = NavType.LongType }),
              transition = FluidPageTransition.Expand,
            ) { entry ->
              SessionScreen(
                container = container,
                sessionId = entry.arguments?.getLong(Routes.SESSION_ID) ?: 0L,
                bottomInset = bottomInset,
                onBack = { nav.popBackStack() },
              )
            }
          }
        }
      }

      // La barra c'e' finche' una pagina con la barra e' davanti, e mentre una pagina senza (il dettaglio)
      // si apre o si chiude: li' esce e rientra insieme a lei, sotto il dito.
      if (onMain || pages.isMoving) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .slideOffBottom { tabBarHidden(pages, restHidden = !onMain) }
            .navigationBarsPadding()
            .padding(
              horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
              vertical = FluidFloatingTabBarDefaults.BottomMargin,
            ),
        ) {
          FluidFloatingTabBar(
            items = SoundsTab.entries.map { it.item },
            selectedRoute = lastTab.value.route,
            onSelect = { item -> SoundsTab.fromRoute(item.route)?.let(openTab) },
            onReselect = { scrollToTop.request() },
            scrollConnection = tabBarScroll,
            backdrop = backdrop,
          )
        }
      }

      // Sopra la barra: un pop-up sotto cui passa una capsula di navigazione non e' un modale.
      FluidGlassModalHost(state = modalHost, backdrop = backdrop)
      FluidNotificationHost(
        state = notificationHost,
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

/** Un valore ricordato fuori dallo stato di Compose: si scrive durante la composizione senza riscriverla. */
private class LastTab {
  var value: SoundsTab = SoundsTab.Live
}

/** Una scheda: la lista lascia in fondo lo spazio della barra, che la copre. Il dettaglio non ne lascia. */
@Composable
private fun TabPage(
  chromeController: FluidChromeController,
  scrollToTop: FluidScrollToTopBus,
  content: @Composable () -> Unit,
) {
  ProvideFluidChrome(
    controller = chromeController,
    bottomInset = FluidFloatingTabBarDefaults.ContentInset,
    scrollToTop = scrollToTop,
    content = content,
  )
}

/**
 * Sposta in basso, fuori dallo schermo, di [hidden] volte la propria altezza: 0 e' al suo posto, 1 e' via.
 * E' un offset di posizione, letto quando si posa: il vetro della barra sa dov'e' davvero, e non c'e' mai un
 * layer traslato sopra una lastra che rifrange.
 */
private fun Modifier.slideOffBottom(hidden: () -> Float): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints)
  layout(placeable.width, placeable.height) {
    placeable.place(0, (placeable.height * hidden().coerceIn(0f, 1f)).roundToInt())
  }
}

private fun tabBarHidden(pages: FluidPageHostState, restHidden: Boolean): Float = tabBarHidden(
  moving = pages.isMoving,
  fromHidden = pages.from.isSession(),
  toHidden = pages.to.isSession(),
  progress = pages.progress,
  restHidden = restHidden,
)

private fun Any?.isSession(): Boolean = (this as? NavBackStackEntry)?.destination?.route == Routes.SESSION
