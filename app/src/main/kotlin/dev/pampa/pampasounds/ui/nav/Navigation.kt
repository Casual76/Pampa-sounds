package dev.pampa.pampasounds.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.theme.FluidRouteMotion
import dev.antigravity.fluidengine.ui.theme.MotionOrigin

object Routes {
  /** Le quattro schede, sotto la barra. */
  const val MAIN = "main"
  const val SESSION = "session/{id}"
  const val SESSION_ID = "id"

  fun session(id: Long) = "session/$id"
}

private const val OriginXKey = "route-motion:origin-x"
private const val OriginYKey = "route-motion:origin-y"

/** Registra da dove e' stata aperta la voce: la destinazione ci cresce sopra, e ci torna. */
fun SavedStateHandle.writeExpandOrigin(origin: MotionOrigin) {
  this[OriginXKey] = origin.fractionX
  this[OriginYKey] = origin.fractionY
}

private fun SavedStateHandle.readExpandOrigin(): MotionOrigin {
  val x = get<Float>(OriginXKey) ?: return MotionOrigin.Center
  val y = get<Float>(OriginYKey) ?: return MotionOrigin.Center
  if (!x.isFinite() || !y.isFinite()) return MotionOrigin.Center
  return MotionOrigin(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * Il `NavHost` con il movimento delle app Pampa (lo stesso di Pampa Notes, ridotto a quello che
 * serve qui): la sessione cresce dal punto toccato mentre l'elenco arretra appena sotto di lei, e
 * il ritorno e' lo stesso movimento rovesciato — e' quello che permette al back predittivo di
 * scorrerlo col dito. Tutto a durata finita: solo una curva finita si puo' scorrere a mano.
 *
 * Le schede non passano di qui: sono pari, e scorrono di lato dentro la rotta principale.
 */
@Composable
fun SoundsNavHost(
  controller: NavHostController,
  modifier: Modifier = Modifier,
  builder: NavGraphBuilder.() -> Unit,
) {
  NavHost(
    navController = controller,
    startDestination = Routes.MAIN,
    modifier = modifier,
    enterTransition = { expandEnter(targetState.savedStateHandle.readExpandOrigin(), isPop = false) },
    exitTransition = { expandExit(targetState.savedStateHandle.readExpandOrigin(), isPop = false) },
    popEnterTransition = { expandEnter(initialState.savedStateHandle.readExpandOrigin(), isPop = true) },
    popExitTransition = { expandExit(initialState.savedStateHandle.readExpandOrigin(), isPop = true) },
    builder = builder,
  )
}

private fun expandSpec() = tween<Float>(durationMillis = FluidMotion.DurationExpand, easing = FluidMotion.EaseEmphasized)

private fun collapseSpec() = tween<Float>(durationMillis = FluidMotion.DurationCollapse, easing = FluidMotion.EaseEmphasized)

private fun expandEnter(origin: MotionOrigin, isPop: Boolean): EnterTransition = if (isPop) {
  // Tornando indietro la pagina sotto viene scoperta: rientra da poco troppo vicina, che e'
  // esattamente dove l'andata l'aveva lasciata.
  scaleIn(collapseSpec(), initialScale = FluidRouteMotion.ExpandParentScale, transformOrigin = origin.toTransformOrigin())
} else {
  scaleIn(expandSpec(), initialScale = FluidRouteMotion.ExpandInitialScale, transformOrigin = origin.toTransformOrigin())
}

private fun expandExit(origin: MotionOrigin, isPop: Boolean): ExitTransition = if (isPop) {
  // La pagina congedata resta solida mentre si stringe e sparisce solo alla fine.
  fadeOut(tween(durationMillis = 170, delayMillis = 90, easing = FluidMotion.EaseIn)) +
    scaleOut(collapseSpec(), targetScale = FluidRouteMotion.ExpandInitialScale, transformOrigin = origin.toTransformOrigin())
} else {
  fadeOut(tween(durationMillis = FluidMotion.DurationRouteFadeOut, easing = FluidMotion.EaseIn)) +
    scaleOut(expandSpec(), targetScale = FluidRouteMotion.ExpandParentScale, transformOrigin = origin.toTransformOrigin())
}
