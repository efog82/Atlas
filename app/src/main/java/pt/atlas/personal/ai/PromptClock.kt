package pt.atlas.personal.ai

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// A clock attached to each persisted user turn stays identical on subsequent renders.
// Putting now() in the system prefix would invalidate almost the entire KV cache every turn.
fun historyTurn(role: String, content: String, createdAt: Long, zone: ZoneId = ZoneId.systemDefault()): Turn {
    if (role != "user") return Turn(role, content)
    val time = Instant.ofEpochMilli(createdAt).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    return Turn(role, "$content\n[Data/hora local deste pedido: $time]")
}
