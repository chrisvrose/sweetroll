package dev.cannoli.scorza.input

import dev.cannoli.igm.CanonicalButton

data class DeviceMapping(
    val id: String,
    val displayName: String,
    val match: DeviceMatchRule,
    val bindings: Map<dev.cannoli.igm.CanonicalButton, List<InputBinding>>,
    val menuConfirm: dev.cannoli.igm.CanonicalButton = dev.cannoli.igm.CanonicalButton.BTN_EAST,
    val menuBack: dev.cannoli.igm.CanonicalButton = CanonicalButton.BTN_SOUTH,
    val glyphStyle: GlyphStyle = GlyphStyle.PLUMBER,
    val excludeFromGameplay: Boolean = false,
    val defaultControllerTypeId: Int? = null,
    val source: MappingSource,
    val userEdited: Boolean = false,
)
