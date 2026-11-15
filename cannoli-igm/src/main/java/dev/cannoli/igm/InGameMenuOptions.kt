package dev.cannoli.igm

enum class IgmMenuAction { RESUME, SAVE_STATE, LOAD_STATE, ACHIEVEMENTS, GUIDE, CHEATS, SETTINGS, SWITCH_DISC, REASSIGN, RESET, QUIT }

class InGameMenuOptions(
    hasDiscs: Boolean,
    val discLabel: String,
    hasAchievements: Boolean = false,
    val hasGuides: Boolean = false,
    val hasCheats: Boolean = false,
    val cheatsLabel: String = "Cheats",
    hasReassign: Boolean = false,
    quitLabel: String = "Quit",
) {
    val actions: List<IgmMenuAction> = buildList {
        add(IgmMenuAction.RESUME)
        add(IgmMenuAction.SAVE_STATE)
        add(IgmMenuAction.LOAD_STATE)
        if (hasGuides) add(IgmMenuAction.GUIDE)
        if (hasCheats) add(IgmMenuAction.CHEATS)
        if (hasAchievements) add(IgmMenuAction.ACHIEVEMENTS)
        add(IgmMenuAction.SETTINGS)
        if (hasDiscs) add(IgmMenuAction.SWITCH_DISC)
        if (hasReassign) add(IgmMenuAction.REASSIGN)
        add(IgmMenuAction.RESET)
        add(IgmMenuAction.QUIT)
    }

    val options: List<String> = actions.map { action ->
        when (action) {
            IgmMenuAction.RESUME -> "Resume"
            IgmMenuAction.SAVE_STATE -> "Save State"
            IgmMenuAction.LOAD_STATE -> "Load State"
            IgmMenuAction.ACHIEVEMENTS -> "Achievements"
            IgmMenuAction.GUIDE -> "Guide"
            IgmMenuAction.CHEATS -> cheatsLabel
            IgmMenuAction.SETTINGS -> "Settings"
            IgmMenuAction.SWITCH_DISC -> discLabel
            IgmMenuAction.REASSIGN -> "Reassign Players"
            IgmMenuAction.RESET -> "Reset"
            IgmMenuAction.QUIT -> quitLabel
        }
    }

    val achievementsIndex get() = actions.indexOf(IgmMenuAction.ACHIEVEMENTS)
    val cheatsIndex get() = actions.indexOf(IgmMenuAction.CHEATS)
    val resetIndex get() = actions.indexOf(IgmMenuAction.RESET)
    val quitIndex get() = actions.indexOf(IgmMenuAction.QUIT)

    fun actionAt(index: Int): IgmMenuAction? = actions.getOrNull(index)
}
