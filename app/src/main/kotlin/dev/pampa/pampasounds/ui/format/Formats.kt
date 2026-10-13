package dev.pampa.pampasounds.ui.format

import dev.pampa.pampasounds.dsp.CaptureSource
import dev.pampa.pampasounds.dsp.FrequencyWeighting
import dev.pampa.pampasounds.dsp.MusicalNote
import dev.pampa.pampasounds.dsp.TimeWeighting
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Italian: Locale = Locale.ITALY

/** "62,4" — una cifra decimale, virgola italiana, trattino quando il dato non c'e'. */
fun formatDb(value: Double?, decimals: Int = 1): String {
  if (value == null || value.isNaN() || value.isInfinite()) return "—"
  return String.format(Italian, "%.${decimals}f", value)
}

fun FrequencyWeighting.unitLabel(): String = "dB(${name})"

fun FrequencyWeighting.shortLabel(): String = name

fun TimeWeighting.label(): String = when (this) {
  TimeWeighting.Fast -> "Fast"
  TimeWeighting.Slow -> "Slow"
}

/** "12:04", oppure "1:02:03" oltre l'ora. */
fun formatDuration(totalSeconds: Long): String {
  val s = totalSeconds.coerceAtLeast(0)
  val hours = s / 3600
  val minutes = (s % 3600) / 60
  val seconds = s % 60
  return if (hours > 0) String.format(Italian, "%d:%02d:%02d", hours, minutes, seconds)
  else String.format(Italian, "%d:%02d", minutes, seconds)
}

/** "2 h 14 min", "14 min", "38 s": per le righe dell'elenco, dove l'orologio sarebbe rumore. */
fun formatDurationWords(totalSeconds: Double): String {
  val s = totalSeconds.roundToInt()
  return when {
    s >= 3600 -> "${s / 3600} h ${(s % 3600) / 60} min"
    s >= 60 -> "${s / 60} min"
    else -> "$s s"
  }
}

/** "440 Hz", "1,25 kHz". */
fun formatFrequency(hz: Double): String = when {
  hz >= 10_000 -> String.format(Italian, "%.1f kHz", hz / 1000)
  hz >= 1000 -> String.format(Italian, "%.2f kHz", hz / 1000)
  else -> String.format(Italian, "%.0f Hz", hz)
}

private val NoteNames = listOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")

/** "La4 +3 cent" */
fun MusicalNote.label(): String {
  val cents = when {
    cents > 0 -> " +$cents cent"
    cents < 0 -> " −${-cents} cent"
    else -> ""
  }
  return "${NoteNames[pitchClass]}$octave$cents"
}

fun formatPercent(value: Double): String = when {
  value.isNaN() -> "—"
  value < 1 && value > 0 -> String.format(Italian, "%.1f %%", value)
  else -> String.format(Italian, "%.0f %%", value)
}

private val DayFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Italian)
private val ShortDayFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Italian)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Italian)
private val ClockFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Italian)

/** "oggi, 11:42" · "ieri, 23:10" · "lunedì 6 ottobre, 09:15" · "3 mar 2025, 09:15" */
fun formatSessionStart(epochMillis: Long, today: LocalDate = LocalDate.now()): String {
  val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  val day = time.toLocalDate()
  val dayLabel = when {
    day == today -> "oggi"
    day == today.minusDays(1) -> "ieri"
    day.year == today.year -> DayFormatter.format(time)
    else -> ShortDayFormatter.format(time)
  }
  return "$dayLabel, ${TimeFormatter.format(time)}"
}

fun formatClock(epochMillis: Long): String =
  ClockFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatHourMinute(epochMillis: Long): String =
  TimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun CaptureSource.label(): String = when (this) {
  CaptureSource.Unprocessed -> "Microfono senza elaborazioni"
  CaptureSource.VoiceRecognition -> "Percorso riconoscimento vocale"
  CaptureSource.Mic -> "Microfono standard"
}

/** Un livello tipico con cui confrontare la lettura. */
data class ReferenceLevel(val db: Int, val label: String, val example: String)

/** I livelli di riferimento, in dB(A). Valori indicativi: ogni ambiente fa storia a se'. */
val ReferenceLevels: List<ReferenceLevel> = listOf(
  ReferenceLevel(20, "Quasi silenzio", "fruscio di foglie, studio di registrazione"),
  ReferenceLevel(30, "Molto tranquillo", "sussurro, camera da letto di notte"),
  ReferenceLevel(40, "Tranquillo", "biblioteca, frigorifero"),
  ReferenceLevel(50, "Moderato", "ufficio silenzioso, pioggia leggera"),
  ReferenceLevel(60, "Conversazione", "voce normale a un metro, ristorante"),
  ReferenceLevel(70, "Rumoroso", "aspirapolvere, traffico cittadino"),
  ReferenceLevel(80, "Molto rumoroso", "traffico intenso, sveglia vicina"),
  ReferenceLevel(85, "Rischio per l'udito", "dopo 8 ore: tagliaerba, metropolitana"),
  ReferenceLevel(100, "Pericoloso", "concerto, discoteca: danni in pochi minuti"),
  ReferenceLevel(120, "Soglia del dolore", "sirena da vicino, motore a reazione"),
)

/** Il riferimento piu' vicino sotto [db]: "come una conversazione". */
fun referenceFor(db: Double): ReferenceLevel {
  if (db.isNaN()) return ReferenceLevels.first()
  return ReferenceLevels.lastOrNull { db >= it.db - 5 } ?: ReferenceLevels.first()
}
