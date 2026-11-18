package dev.cannoli.scorza.input

import dev.cannoli.scorza.config.EmulatorSource
import dev.cannoli.scorza.ui.screens.CoreAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorMappingDownloadableTest {

    @Test fun `a RetroArch row is unknown when RetroArch cannot report`() {
        assertEquals(
            CoreAvailability.UNKNOWN,
            EmulatorMappingBuilder.currentRowAvailability(EmulatorSource.RetroArch, raCannotReport = true),
        )
    }

    @Test fun `a RetroArch row is unavailable when RetroArch can report`() {
        assertEquals(
            CoreAvailability.UNAVAILABLE,
            EmulatorMappingBuilder.currentRowAvailability(EmulatorSource.RetroArch, raCannotReport = false),
        )
    }

    @Test fun `Internal and Standalone rows are unavailable regardless of RetroArch reporting`() {
        assertEquals(
            CoreAvailability.UNAVAILABLE,
            EmulatorMappingBuilder.currentRowAvailability(EmulatorSource.Standalone, raCannotReport = true),
        )
    }
}
