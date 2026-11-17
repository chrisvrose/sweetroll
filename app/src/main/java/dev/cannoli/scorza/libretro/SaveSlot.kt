package dev.cannoli.scorza.libretro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import dev.cannoli.core.StateSlotPaths
import dev.cannoli.igm.SaveSlotManager as SharedSlotManager

@Deprecated("Should be removed")
class SaveSlotManager(private val stateBasePath: String) {

    val slots = SharedSlotManager().slots

    fun statePath(slot: SharedSlotManager.Slot): String = StateSlotPaths.statePath(stateBasePath, slot.index)

    fun thumbnailPath(slot: SharedSlotManager.Slot): String = StateSlotPaths.thumbnailPath(stateBasePath, slot.index)

    fun loadThumbnail(slot: SharedSlotManager.Slot): Bitmap? {
        val f = File(thumbnailPath(slot))
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Exception) { null }
    }

}
