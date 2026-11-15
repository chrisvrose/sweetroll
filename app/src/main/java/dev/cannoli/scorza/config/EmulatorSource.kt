package dev.cannoli.scorza.config

import dev.cannoli.scorza.R

enum class EmulatorSource(val displayName: String) {
    @Deprecated("Remove internal")
    Internal("Internal"),
    RetroArch("RetroArch"),
    Standalone("Standalone");

    val emptyMessageRes: Int
        get() = when (this) {
            Internal -> R.string.label_remove
            RetroArch -> R.string.value_no_cores_found
            Standalone -> R.string.value_none_installed
        }

}
