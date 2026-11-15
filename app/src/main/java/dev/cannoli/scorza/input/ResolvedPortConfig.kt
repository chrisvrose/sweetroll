package dev.cannoli.scorza.input

import dev.cannoli.igm.CanonicalButton

data class ResolvedPortConfig(
    val port: Int,
    val mappingId: String?,
    val resolvedBindings: Map<CanonicalButton, List<InputBinding>>,
    val controllerTypeId: Int,
)
