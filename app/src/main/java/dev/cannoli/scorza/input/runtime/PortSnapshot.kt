package dev.cannoli.scorza.input.runtime

import dev.cannoli.scorza.input.AnalogRole

data class PortSnapshot(
    val pressed: Set<dev.cannoli.igm.CanonicalButton>,
    val analog: Map<AnalogRole, Float>,
)
