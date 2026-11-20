package dev.cannoli.scorza.config

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformConfigResetTest {
    private fun config(): PlatformConfig {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return PlatformConfig(File(ctx.cacheDir, "reset-root").apply { mkdirs() }, ctx.assets)
    }

    // No bundled cores and no installed apps in this fixture, so the default resolves to
    // nothing and the platform is left unmapped.
    @Test fun `reset with no resolvable default leaves the platform unmapped`() {
        val pc = config()
        pc.setPlatformChoice("NES", EmulatorChoice(EmulatorSource.RetroArch, "nestopia_libretro"))
        assertTrue(pc.hasUserMapping("NES"))
        pc.resetPlatformToDefault("NES")
        assertFalse(pc.hasUserMapping("NES"))
    }
}
