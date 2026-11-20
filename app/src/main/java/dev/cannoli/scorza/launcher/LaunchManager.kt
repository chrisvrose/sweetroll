package dev.cannoli.scorza.launcher

import android.content.Context
import android.content.pm.PackageManager
import dev.cannoli.scorza.config.AppConfig
import dev.cannoli.scorza.config.CannoliPaths
import dev.cannoli.scorza.config.EmulatorSource
import dev.cannoli.scorza.config.PlatformConfig
import dev.cannoli.scorza.libretro.SaveSlotManager
import dev.cannoli.scorza.model.App
import dev.cannoli.scorza.model.LaunchTarget
import dev.cannoli.scorza.model.Rom
import dev.cannoli.scorza.settings.SettingsRepository
import dev.cannoli.scorza.ui.screens.DialogState
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.Normalizer

class LaunchManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val platformConfig: PlatformConfig,
    private val retroArchLauncher: RetroArchLauncher,
    private val emuLauncher: EmuLauncher,
    private val apkLauncher: ApkLauncher,
    private val launchState: LaunchState,
    private val installedCoreService: InstalledCoreService? = null,
    private val gameOverrides: dev.cannoli.scorza.db.GameOverrideStore? = null,
) {
    // Per-game overrides are keyed by rom_id, which the scanner keeps stable across renames,
    // moves and auto-organize, so an override follows its game instead of being orphaned.
    private fun overrideFor(rom: Rom): dev.cannoli.scorza.config.EmulatorChoice? =
        gameOverrides?.get(rom.id)

    private var raConfigPath: String? = null

    init {
        apkLauncher.debugLog = ::debugLog
    }

    fun syncRetroArchAssets(root: File) {
        val fontDest = CannoliPaths(root).cannoliFont
        if (fontDest.exists()) return
        fontDest.parentFile?.mkdirs()
        try {
            context.assets.open("fonts/MPlus-1c-NerdFont-Bold.ttf").use { input ->
                fontDest.outputStream().use { input.copyTo(it) }
            }
        } catch (_: IOException) {}
    }

    fun syncRetroArchConfig(root: File) {
        val raDir = CannoliPaths(root).configRetroArch
        raDir.mkdirs()
        val localConfig = File(raDir, "retroarch.cfg")
        val hashFile = File(raDir, ".ra_config_hash")

        val raPackage = settings.retroArchPackage
        val sourceConfig = File("/storage/emulated/0/Android/data/$raPackage/files/retroarch.cfg")

        if (!sourceConfig.exists()) {
            if (!localConfig.exists()) {
                localConfig.writeText(buildMinimalConfig(root.absolutePath))
            }
            raConfigPath = localConfig.absolutePath
            return
        }

        val sourceBytes = try { sourceConfig.readBytes() } catch (_: IOException) {
            if (!localConfig.exists()) localConfig.writeText(buildMinimalConfig(root.absolutePath))
            raConfigPath = localConfig.absolutePath
            return
        }
        val sourceHash = sha256(sourceBytes, "$CONFIG_VERSION:${settings.raUsername}:${settings.raToken}".toByteArray())
        val storedHash = if (hashFile.exists()) try { hashFile.readText().trim() } catch (_: IOException) { "" } else ""

        if (sourceHash != storedHash || !localConfig.exists()) {
            val patched = patchRetroArchConfig(String(sourceBytes), root.absolutePath)
            localConfig.writeText(patched)
            hashFile.writeText(sourceHash)
        }

        raConfigPath = localConfig.absolutePath
    }

    private fun buildGameConfig(rom: Rom, resume: Boolean = false, slot: Int = 0): String? {
        val base = raConfigPath ?: return null
        val baseConfig = try { File(base).readText() } catch (_: IOException) { return null }
        val paths = CannoliPaths(settings.sdCardRoot)
        val romName = normalizedRomName(rom)
        val stateDir = paths.saveStateDir(rom.platformTag, romName)
        stateDir.mkdirs()
        val biosDir = paths.biosFor(rom.platformTag)
        biosDir.mkdirs()
        val raSlot = if (slot > 0) slot - 1 else 0
        val gameOverrides = buildMap {
            put("system_directory", biosDir.absolutePath)
            put("savestate_directory", stateDir.absolutePath)
            put("sort_savestates_enable", "false")
            put("sort_savestates_by_content_enable", "false")
            put("state_slot", raSlot.toString())
            // RetroArch's own auto-save is how ricotta saves on quit.
            // This lives here, not in the base config, because the base is
            // hash-gated and would not pick up a toggle until CONFIG_VERSION moved.
            put("savestate_auto_save", if (settings.alwaysSaveOnQuit) "true" else "false")
            if (resume) {
                put("savestate_auto_load", "true")
            }
        }
        val patched = applyOverrides(baseConfig, gameOverrides)
        val launchConfig = paths.raLaunchCfg
        launchConfig.writeText(patched)
        return launchConfig.absolutePath
    }

    private fun buildMinimalConfig(rootPath: String) = buildString {
        appendLine("savefile_directory = \"$rootPath/Saves\"")
        appendLine("savestate_directory = \"$rootPath/Save States\"")
        appendLine("sort_savefiles_by_content_enable = \"true\"")
        appendLine("savestate_file_compression = \"false\"")
        appendLine("config_save_on_exit = \"false\"")
        appendLine("video_font_enable = \"false\"")
        appendLine("assets_directory = \"$rootPath/Config/Assets\"")
    }

    private fun patchRetroArchConfig(source: String, rootPath: String): String {
        val raUser = settings.raUsername
        val raToken = settings.raToken
        val overrides = buildMap {
            put("savefile_directory", "$rootPath/Saves")
            put("savestate_directory", "$rootPath/Save States")
            put("screenshot_directory", "$rootPath/Media/Screenshots")
            put("recording_output_directory", "$rootPath/Media/Recordings")
            put("sort_savefiles_by_content_enable", "true")
            // RetroArch defaults this to true and would nest the core name under the content
            // directory, giving Saves/GBA/mGBA instead of the Saves/GBA the internal runner writes.
            put("sort_savefiles_enable", "false")
            put("savestate_file_compression", "false")
            put("savestate_block_format", "false")
            put("savestate_thumbnail_enable", "true")
            put("config_save_on_exit", "false")
            put("video_font_enable", "false")
            put("auto_overrides_enable", "true")

            // TODO come back to this at a later date
//            put("assets_directory", "$rootPath/Config/Assets")

            if (raUser.isNotEmpty() && raToken.isNotEmpty()) {
                put("cheevos_enable", "true")
                put("cheevos_username", raUser)
                put("cheevos_token", raToken)
            }
        }
        return applyOverrides(source, overrides)
    }

    private fun applyOverrides(source: String, overrides: Map<String, String>): String {
        val applied = mutableSetOf<String>()
        val lines = source.lines().map { line ->
            val trimmed = line.trimStart()
            val key = trimmed.substringBefore('=').trim().removePrefix("# ")
            if (key in overrides) {
                applied.add(key)
                "$key = \"${overrides[key]}\""
            } else line
        }.toMutableList()
        for ((key, value) in overrides) {
            if (key !in applied) lines.add("$key = \"$value\"")
        }
        return lines.joinToString("\n")
    }

    private fun sha256(vararg parts: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in parts) digest.update(part)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun resolveLaunchFile(rom: Rom): File {
        return rom.path
    }

    /** The stored source that applies to this ROM, game override first. Null means unset. */
    private fun sourceFor(rom: Rom): EmulatorSource? =
        overrideFor(rom)?.source ?: platformConfig.getPlatformChoice(rom.platformTag)?.source

    fun saveStateBasePath(rom: Rom): String {
        val romName = normalizedRomName(rom)
        return CannoliPaths(settings.sdCardRoot).saveStateBase(rom.platformTag, romName).absolutePath
    }

    fun slotOccupancy(rom: Rom): List<Boolean> {
        val slotManager = SaveSlotManager(saveStateBasePath(rom))
        return (0..10).map { i ->
            slotManager.slots.firstOrNull { it.index == i }
                ?.let { File(slotManager.statePath(it)).exists() } ?: false
        }
    }

    fun findMostRecentSlot(rom: Rom): Int? {
        val slotManager = SaveSlotManager(saveStateBasePath(rom))
        return slotManager.slots
            .filter { File(slotManager.statePath(it)).exists() }
            .maxByOrNull { File(slotManager.statePath(it)).lastModified() }
            ?.index
    }

    private fun hasSaveState(rom: Rom): Boolean = findMostRecentSlot(rom) != null

    fun findResumableRoms(roms: List<Rom>): Set<String> {
        val result = mutableSetOf<String>()
        for (rom in roms) {
            if (!hasSaveState(rom)) continue
            // Slots are a Cannoli and libretro concept. A standalone app manages its own saves,
            // so offering Resume for one promises something no external emulator can honour.
            if (sourceFor(rom) == EmulatorSource.Standalone) continue
        }
        return result
    }

    fun launchRom(rom: Rom): DialogState? {
        debugLog("launchRom entered: ${rom.platformTag} / ${rom.path.name} target=${rom.launchTarget::class.simpleName}")
        if (launchState.launching) return null
        launchState.launching = true
        launchState.lastLaunched = rom
        val launchFile = resolveLaunchFile(rom)
            ?: return errorAndReset(DialogState.LaunchError(context.getString(dev.cannoli.scorza.R.string.launch_error_resolve_file)))

        val gameOverride = overrideFor(rom)
        if (gameOverride?.source == EmulatorSource.Standalone && gameOverride.appPackage != null) {
            val cfg = platformConfig.getAppConfig(rom.platformTag, gameOverride.appPackage)
            return launchResultDialog(
                launchStandalone(launchFile, cfg), rom.platformTag, rom.id
            )
        }
        val overrideRomId = if (gameOverride != null) rom.id else null

        val result = when (val target = rom.launchTarget) {
            is LaunchTarget.RetroArch -> {
                val resolvedCore = gameOverride?.coreId?.ifEmpty { null }
                    ?: platformConfig.getCoreName(rom.platformTag)
                val source = pickSource(
                    gameSource = gameOverride?.source,
                    platformSource = platformConfig.getPlatformChoice(rom.platformTag)?.source,
                    // configuredCores, not installedCores: a core present only in a RetroArch the
                    // user is not using must not suppress the standalone fallback.
                    raAvailable = {
                        resolvedCore != null &&
                            installedCoreService?.configuredCores()?.any { it.value.contains(resolvedCore) } == true
                    },
                    standaloneAvailable = {
                        platformConfig.getFirstInstalledApp(rom.platformTag, context.packageManager) != null
                    },
                )
                if (source == EmulatorSource.Standalone) {
                    val cfg = platformConfig.getUserAppMapping(rom.platformTag)
                        ?.let { platformConfig.getAppConfig(rom.platformTag, it) }
                        ?: platformConfig.getFirstInstalledApp(rom.platformTag, context.packageManager)
                        ?: platformConfig.getAppPackage(rom.platformTag)?.let { platformConfig.getAppConfig(rom.platformTag, it) }
                    if (cfg != null) {
                        launchStandalone(launchFile, cfg)
                    } else {
                        LaunchResult.CoreNotInstalled("unknown")
                    }
                } else {
                    val core = resolvedCore
                    if (core != null) {
                        val raPackage = settings.retroArchPackage
                        if (!context.isPackageInstalled(raPackage)) {
                            val appName = try {
                                val info = context.packageManager.getApplicationInfo(raPackage, 0)
                                context.packageManager.getApplicationLabel(info).toString()
                            } catch (_: PackageManager.NameNotFoundException) { raPackage }
                            return errorAndReset(DialogState.MissingApp(appName, raPackage, rom.platformTag, overrideRomId))
                        }
                        // The core-install check applies to any package that can report its
                        // cores. It self-skips for one that cannot, since a missing report is
                        // not evidence of a missing core.
                        if (installedCoreService != null
                            && installedCoreService.cacheReady
                            && installedCoreService.canReport(raPackage)
                            && !installedCoreService.hasCoreInPackage(core, raPackage)) {
                            val label = InstalledCoreService.getPackageLabel(raPackage)
                            val coreName = platformConfig.getCoreDisplayName(core)
                            return errorAndReset(DialogState.MissingCore(coreName, label, rom.platformTag, overrideRomId))
                        }
                        val raConfig = "/storage/emulated/0/Android/data/$raPackage/files/retroarch.cfg"
                        retroArchLauncher.launchRetroArchIntent(launchFile, core, raConfig, raPackage)
                    } else {
                        // App-only platform (no core) that reached here has nothing installed to
                        // run it, so name the app it wants instead of a core it never had.
                        val appPkg = platformConfig.getAppPackage(rom.platformTag)
                        if (appPkg != null && !context.isPackageInstalled(appPkg)) {
                            LaunchResult.AppNotInstalled(appPkg)
                        } else {
                            LaunchResult.CoreNotInstalled("unknown")
                        }
                    }
                }
            }
            is LaunchTarget.EmuLaunch -> {
                emuLauncher.launch(launchFile, target.packageName, target.activityName, target.action)
            }
            is LaunchTarget.ApkLaunch -> {
                val pkg = if (context.isPackageInstalled(target.packageName)) {
                    target.packageName
                } else {
                    platformConfig.getFirstInstalledApp(rom.platformTag, context.packageManager)?.packageName
                        ?: target.packageName
                }
                if (launchFile.extension != "apk_launch" && launchFile.exists()) {
                    val cfg = platformConfig.getAppConfig(rom.platformTag, pkg)
                    apkLauncher.launchWithRom(pkg, launchFile, cfg)
                } else {
                    apkLauncher.launch(pkg)
                }
            }
        }

        return launchResultDialog(result, rom.platformTag)
    }

    fun launchApp(app: App): DialogState? {
        debugLog("launchApp entered: ${app.type} / ${app.packageName}")
        if (launchState.launching) return null
        launchState.launching = true
        return launchResultDialog(apkLauncher.launch(app.packageName))
    }

    fun resumeRom(rom: Rom): DialogState? = resumeRom(rom, findMostRecentSlot(rom) ?: 0)

    fun resumeRom(rom: Rom, resumeSlot: Int): DialogState? {
        debugLog("resumeRom entered: ${rom.platformTag} / ${rom.path.name} slot=$resumeSlot")
        if (launchState.launching) return null
        launchState.launching = true
        launchState.lastLaunched = rom
        val launchFile = resolveLaunchFile(rom)
        val gameOverride = overrideFor(rom)
        // Resume had no standalone branch, so a platform mapped to an uninstalled standalone app
        // fell straight through to the RetroArch path and launched the platform default core.
        // Play reported the app as missing; Resume silently ran a different emulator.
        if (sourceFor(rom) == EmulatorSource.Standalone) {
            val cfg = gameOverride?.appPackage?.let { platformConfig.getAppConfig(rom.platformTag, it) }
                ?: platformConfig.getUserAppMapping(rom.platformTag)
                    ?.let { platformConfig.getAppConfig(rom.platformTag, it) }
                ?: platformConfig.getFirstInstalledApp(rom.platformTag, context.packageManager)
            return launchResultDialog(
                if (cfg != null) launchStandalone(launchFile, cfg)
                else LaunchResult.CoreNotInstalled("unknown"),
                rom.platformTag, rom.id,
            )
        }
        val core = gameOverride?.coreId?.ifEmpty { null }
            ?: platformConfig.getCoreName(rom.platformTag)
            ?: run { launchState.launching = false; launchState.lastLaunched = null; return null }
        val raPackage = settings.retroArchPackage
        // Resume used to fire and forget: it discarded the LaunchResult and returned without
        // clearing launching, so a failed startActivity never reached onResume and every later
        // launch became a silent no-op. It now runs the same pre-checks and funnel as launchRom.
        if (!context.isPackageInstalled(raPackage)) {
            return errorAndReset(DialogState.MissingApp(
                InstalledCoreService.getPackageLabel(raPackage), raPackage,
                rom.platformTag, rom.id,
            ))
        }
        if (installedCoreService != null
            && installedCoreService.cacheReady
            && installedCoreService.canReport(raPackage)
            && !installedCoreService.hasCoreInPackage(core, raPackage)) {
            return errorAndReset(DialogState.MissingCore(
                platformConfig.getCoreDisplayName(core),
                InstalledCoreService.getPackageLabel(raPackage),
                rom.platformTag, rom.id,
            ))
        }
        val raConfig = "/storage/emulated/0/Android/data/$raPackage/files/retroarch.cfg"
        val result = retroArchLauncher.launchRetroArchIntent(launchFile, core, raConfig, raPackage)
        return launchResultDialog(result, rom.platformTag, rom.id)
    }

    private fun errorAndReset(dialog: DialogState): DialogState {
        launchState.launching = false
        launchState.lastLaunched = null
        return dialog
    }

    private fun launchResultDialog(result: LaunchResult, platformTag: String? = null, romId: Long? = null): DialogState? {
        val dialog = toLaunchDialog(result, platformTag, romId)
        if (dialog != null) launchState.launching = false
        return dialog
    }

    private fun toLaunchDialog(result: LaunchResult, platformTag: String? = null, romId: Long? = null): DialogState? {
        return when (result) {
            is LaunchResult.CoreNotInstalled -> DialogState.MissingCore(result.coreName, platformTag = platformTag, romId = romId)
            is LaunchResult.AppNotInstalled -> {
                // getApplicationLabel cannot resolve a package that is not installed, and this
                // branch only fires when it is not, so use the curated label like MissingCore does.
                val appName = InstalledCoreService.getPackageLabel(result.packageName)
                DialogState.MissingApp(appName, result.packageName, platformTag, romId)
            }
            is LaunchResult.Error -> DialogState.LaunchError(result.message)
            LaunchResult.Success -> null
        }
    }

    private val debugSink = dev.cannoli.scorza.util.LogSink(0)
    private val debugFmt = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
    private var debugSinkRoot: String? = null

    private fun debugLog(message: String) {
        if (!dev.cannoli.scorza.util.LoggingPrefs.session) return
        try {
            val root = settings.sdCardRoot
            if (root != debugSinkRoot) {
                debugSinkRoot = root
                debugSink.open(File(CannoliPaths(root).logsDir, "launch_debug.log"))
            }
            val stamp = synchronized(debugFmt) { debugFmt.format(java.util.Date()) }
            dev.cannoli.scorza.util.LogWriter.write(debugSink, "$stamp $message\n")
        } catch (_: Exception) {}
    }

    private fun normalizedRomName(rom: Rom): String =
        Normalizer.normalize(rom.path.nameWithoutExtension, Normalizer.Form.NFC)

    private fun launchStandalone(launchFile: File, cfg: AppConfig): LaunchResult =
        apkLauncher.launchWithRom(cfg.packageName, launchFile, cfg)

    companion object {
        private const val CONFIG_VERSION = 7

        // Null means no stored preference anywhere, so the caller falls through to RetroArch,
        // which is what the pre-identity code did implicitly.
        // The availability probes are lazy because a stored choice decides the answer on its
        // own, and probing the filesystem to reach a conclusion already known is wasted work.
        fun pickSource(
            gameSource: EmulatorSource?,
            platformSource: EmulatorSource?,
            raAvailable: () -> Boolean,
            standaloneAvailable: () -> Boolean,
        ): EmulatorSource? {
            val stored = gameSource ?: platformSource
            if (stored != null) return stored
            if (!raAvailable() && standaloneAvailable()) return EmulatorSource.Standalone
            return null
        }
    }
}
