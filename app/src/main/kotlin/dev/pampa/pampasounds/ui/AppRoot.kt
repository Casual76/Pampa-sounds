package dev.pampa.pampasounds.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
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
import dev.antigravity.fluidengine.ui.theme.FluidRouteMotionHost
import dev.antigravity.fluidengine.ui.theme.fluidTouchOriginTracker
import dev.antigravity.fluidengine.ui.theme.rememberFluidTouchOrigin
import dev.pampa.pampasounds.AppContainer
import dev.pampa.pampasounds.ui.history.HistoryScreen
import dev.pampa.pampasounds.ui.live.LiveScreen
import dev.pampa.pampasounds.ui.nav.Routes
import dev.pampa.pampasounds.ui.nav.SoundsNavHost
import dev.pampa.pampasounds.ui.nav.writeExpandOrigin
import dev.pampa.pampasounds.ui.session.SessionScreen
import dev.pampa.pampasounds.ui.settings.SettingsScreen
import dev.pampa.pampasounds.ui.spectrum.SpectrumScreen
import kotlin.math.roundToInt

enum class SoundsTab(val label: String, val icon: ImageVector) {
  Live("Misura", Icons.Rounded.Speed),
  Spectrum("Spettro", Icons.Rounded.GraphicEq),
  History("Storico", Icons.Rounded.History),
  Settings("Impostazioni", Icons.Rounded.Settings),
  ;

  val item: FluidTabItem get() = FluidTabItem(route = name, label = label, icon = icon)
}

/**
 * Il guscio: quattro schede sotto una barra flottante di vetro, e il dettaglio di una sessione
 * che si apre sopra. Barra, notifiche e modali sono fratelli del contenuto e rifrangono la stessa
 * pagina (`chromeController.activeBackdrop`): lo stesso schema della galleria dell'engine.
 */
@Composable
fun AppRoot(container: AppContainer) {
  val nav = rememberNavController()
  val touchOrigin = rememberFluidTouchOrigin()
  var tab by rememberSaveable { mutableStateOf(SoundsTab.Live) }
  val backStack by nav.currentBackStackEntryAsState()
  val onMain = backStack?.destination?.route?.let { it == Routes.MAIN } ?: true

  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val chromeController = rememberFluidChromeController()
  val scrollToTop = remember { FluidScrollToTopBus() }
  val modalHost = rememberFluidGlassModalHostState()
  val notificationHost = rememberFluidNotificationHostState()
  val fallbackBackdrop = rememberGlassBackdrop()
  val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop
  val tabBarScroll = rememberFluidFloatingTabBarScrollConnection()
  val meter = rememberMeterController(container)

  val openSession: (Long) -> Unit = { id ->
    nav.navigate(Routes.session(id))
    nav.currentBackStackEntry?.savedStateHandle?.writeExpandOrigin(touchOrigin.origin)
  }

  CompositionLocalProvider(
    LocalFluidGlassModalHostState provides modalHost,
    LocalFluidNotificationHostState provides notificationHost,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .fluidTouchOriginTracker(touchOrigin),
    ) {
      ProvideFluidChrome(
        controller = chromeController,
        bottomInset = if (onMain) FluidFloatingTabBarDefaults.ContentInset else 0.dp,
        scrollToTop = scrollToTop,
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(tabBarScroll)
            .fluidGlassModalObscured(),
        ) {
          SoundsNavHost(controller = nav) {
            composable(Routes.MAIN) {
              FluidRouteMotionHost(this@composable) {
                MainTabs(
                  tab = tab,
                  container = container,
                  meter = meter,
                  bottomInset = bottomInset,
                  onOpenSession = openSession,
                  onOpenTab = { tab = it },
                )
              }
            }
            composable(
              route = Routes.SESSION,
              arguments = listOf(navArgument(Routes.SESSION_ID) { type = NavType.LongType }),
            ) { entry ->
              FluidRouteMotionHost(this@composable) {
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
      }

      if (onMain) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
              horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
              vertical = FluidFloatingTabBarDefaults.BottomMargin,
            ),
        ) {
          FluidFloatingTabBar(
            items = SoundsTab.entries.map { it.item },
            selectedRoute = tab.name,
            onSelect = { item -> tab = SoundsTab.valueOf(item.route) },
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

/**
 * Le schede sono pari: nessuna sta dentro l'altra, quindi niente si espande. Scorrono di lato
 * insieme nel verso che la barra implica — quella che arriva opaca dal primo fotogramma, quella che
 * se ne va che cede il passo — come fra le schede di Pampa Notes.
 */
@Composable
private fun MainTabs(
  tab: SoundsTab,
  container: AppContainer,
  meter: MeterController,
  bottomInset: Dp,
  onOpenSession: (Long) -> Unit,
  onOpenTab: (SoundsTab) -> Unit,
) {
  AnimatedContent(
    targetState = tab,
    transitionSpec = {
      val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
      val slide = tween<IntOffset>(FluidMotion.DurationPeer, easing = FluidMotion.EaseEmphasized)
      slideInHorizontally(slide) { (it * FluidMotion.PeerSlideFraction * direction).roundToInt() } togetherWith
        (fadeOut(tween(FluidMotion.DurationPeerFadeOut, easing = FluidMotion.EaseIn)) +
          slideOutHorizontally(slide) { (-it * FluidMotion.PeerSlideFraction * direction).roundToInt() })
    },
    label = "tabs",
  ) { current ->
    when (current) {
      SoundsTab.Live -> LiveScreen(container, meter, bottomInset, onOpenTab)
      SoundsTab.Spectrum -> SpectrumScreen(container, meter, bottomInset)
      SoundsTab.History -> HistoryScreen(container, meter, bottomInset, onOpenSession)
      SoundsTab.Settings -> SettingsScreen(container, bottomInset)
    }
  }
}
