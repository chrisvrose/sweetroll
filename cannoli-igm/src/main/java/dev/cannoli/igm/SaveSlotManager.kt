package dev.cannoli.igm

class SaveSlotManager {

    data class Slot(val index: Int, val label: String)

    val slots = listOf(Slot(0, "Auto")) +
        (0..9).map { Slot(it + 1, "Slot $it") }

}
