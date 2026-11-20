package dev.cannoli.scorza.launcher

import dev.cannoli.scorza.config.AppConfig
import dev.cannoli.scorza.config.PlatformConfig
import dev.cannoli.scorza.input.DeviceMapping
import dev.cannoli.scorza.input.runtime.ActiveMappingHolder
import dev.cannoli.scorza.model.Rom
import dev.cannoli.scorza.settings.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LaunchManagerStandalonePickTest {

    @get:Rule val tmp = TemporaryFolder()

    private val platformConfig = mockk<PlatformConfig>(relaxed = true)
    private val apkLauncher = mockk<ApkLauncher>(relaxed = true)
    private val retroArchLauncher = mockk<RetroArchLauncher>(relaxed = true)
    private val gameOverrides = mockk<dev.cannoli.scorza.db.GameOverrideStore>(relaxed = true)

    private fun rom(root: File): Rom {
        val romFile = File(root, "roms/3ds/Zelda.3ds").apply { parentFile!!.mkdirs(); writeText("x") }
        return Rom(id = 1L, path = romFile, platformTag = "3DS", displayName = "Zelda")
    }

    private fun manager(root: File): LaunchManager {
        val settings = mockk<SettingsRepository>(relaxed = true)
        every { settings.sdCardRoot } returns root.absolutePath
        every { gameOverrides.get(any()) } returns null
        every { platformConfig.getPlatformChoice("3DS") } returns
            dev.cannoli.scorza.config.EmulatorChoice(dev.cannoli.scorza.config.EmulatorSource.Standalone)
        // GC has no bundled core and no stored runner, so launch falls through to the
        // standalone app list.
        every { platformConfig.getCoreName("GC") } returns null
        every { platformConfig.getPlatformChoice("GC") } returns null
        every { apkLauncher.launchWithRom(any(), any(), any()) } returns LaunchResult.Success
        val activeMappingHolder = mockk<ActiveMappingHolder>(relaxed = true)
        every { activeMappingHolder.active } returns MutableStateFlow<DeviceMapping?>(null)
        return LaunchManager(
            context = mockk(relaxed = true),
            settings = settings,
            platformConfig = platformConfig,
            retroArchLauncher = retroArchLauncher,
            emuLauncher = mockk(relaxed = true),
            apkLauncher = apkLauncher,
            launchState = mockk(relaxed = true),
            gameOverrides = gameOverrides,
        )
    }

    private fun launchedPackage(root: File): String {
        val pkg = slot<String>()
        verify { apkLauncher.launchWithRom(capture(pkg), any(), any()) }
        return pkg.captured
    }

    @Test fun `explicit standalone pick wins over the first installed app`() {
        val root = tmp.newFolder()
        val mgr = manager(root)
        every { platformConfig.getUserAppMapping("3DS") } returns "com.picked.emu"
        every { platformConfig.getAppConfig("3DS", "com.picked.emu") } returns AppConfig("com.picked.emu")
        every { platformConfig.getFirstInstalledApp(any(), any()) } returns AppConfig("org.first.listed")

        mgr.launchRom(rom(root))

        assertEquals("com.picked.emu", launchedPackage(root))
    }

    @Test fun `without a pick launch still auto routes to the first installed app`() {
        val root = tmp.newFolder()
        val mgr = manager(root)
        every { platformConfig.getUserAppMapping("3DS") } returns null
        every { platformConfig.getFirstInstalledApp(any(), any()) } returns AppConfig("org.first.listed")

        mgr.launchRom(rom(root))

        assertEquals("org.first.listed", launchedPackage(root))
    }

    // Play reported the missing app but Resume fell through to the RetroArch path and ran the
    // platform default core, so one button launched an emulator the user had not chosen.
    @Test fun `resume routes a standalone mapping to the same launcher as play`() {
        val root = tmp.newFolder()
        val mgr = manager(root)
        every { platformConfig.getUserAppMapping("3DS") } returns "com.picked.emu"
        every { platformConfig.getAppConfig("3DS", "com.picked.emu") } returns AppConfig("com.picked.emu")

        mgr.resumeRom(rom(root), 0)

        assertEquals("com.picked.emu", launchedPackage(root))
    }

    @Test fun `resume on a standalone mapping never reaches the retroarch launcher`() {
        val root = tmp.newFolder()
        val mgr = manager(root)
        every { platformConfig.getUserAppMapping("3DS") } returns "com.picked.emu"
        every { platformConfig.getAppConfig("3DS", "com.picked.emu") } returns AppConfig("com.picked.emu")
        every { platformConfig.getCoreName("3DS") } returns "some_libretro"

        mgr.resumeRom(rom(root), 0)

        verify(exactly = 0) { retroArchLauncher.launchRetroArchIntent(any(), any(), any(), any()) }
    }
}
