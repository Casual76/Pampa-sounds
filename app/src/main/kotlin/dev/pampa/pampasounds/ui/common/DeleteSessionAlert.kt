package dev.pampa.pampasounds.ui.common

import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction

/**
 * La conferma prima di eliminare una sessione, la stessa dallo Storico e dal dettaglio: un avviso
 * centrato a due azioni, quella che distrugge in rosso. [message] dice di quale sessione si parla e che
 * i dati non tornano; [onConfirm] non chiude l'avviso, che lo possiede chi lo mostra.
 */
@Composable
fun DeleteSessionAlert(
  message: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  FluidAlert(
    onDismissRequest = onDismiss,
    title = "Eliminare la sessione?",
    message = message,
    actions = listOf(
      FluidAlertAction("Annulla", onClick = onDismiss),
      FluidAlertAction("Elimina", onClick = onConfirm, emphasis = FluidAlertAction.Emphasis.Destructive),
    ),
  )
}
