package dev.cannoli.scorza.model

sealed interface LaunchTarget {
    /**
     * Launching retroarch
     */
    data object RetroArch : LaunchTarget

    /**
     * Launching a custom emulator
     */
    data class EmuLaunch(
        val packageName: String,
        val activityName: String,
        val action: String = "android.intent.action.VIEW"
    ) : LaunchTarget

    data class ApkLaunch(
        val packageName: String
    ) : LaunchTarget

    @Deprecated("Pending removal")
    data class Embedded(
        val corePath: String
    ) : LaunchTarget
}
