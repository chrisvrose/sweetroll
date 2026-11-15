package dev.cannoli.scorza.input.runtime

import dev.cannoli.scorza.input.AnalogRole

sealed interface CanonicalEvent {
    data class Pressed(val button: dev.cannoli.igm.CanonicalButton) : CanonicalEvent
    data class Released(val button: dev.cannoli.igm.CanonicalButton) : CanonicalEvent
    data class AnalogChanged(val role: AnalogRole, val value: Float) : CanonicalEvent
}
