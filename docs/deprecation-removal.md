# Deprecation Removal Notes

## Track 1: Embedded/Internal launch path ✅ COMPLETE

All committed:
- Native builds (`libretro_bridge.cpp`, `ra_integration.c`, `native_audio.cpp`, etc.)
- `LibretroActivity`, `LibretroRenderer`, `LibretroRunner`, `LibretroScreen`
- `RetroAchievementsManager`, `OverrideManager`, `SaveIdentityMigrator`
- All `libretro/settings/`, `libretro/shader/`, GLSL shader assets
- `LaunchArgs`, `LaunchTarget.Embedded`, `EmulatorSource.Internal`
- `findEmbeddedCore`, `getEmbeddedCorePath`, `launchEmbedded`, `extractBundledCores`
- v1 cores migration (`migrateV1`, `legacyChoice`, `fromRunnerLabel`)
- `CanonicalButton.kt` app alias — callers migrated to `dev.cannoli.igm.CanonicalButton` directly
- IGM: `CannoliIGM`, `IGMController`, `IGMScreen`, `IGMSettings*`, `InGameMenu`, `InGameMenuOptions`
- IGM: `EmulatorBridge`, `IgmInputTranslator`, `IgmGameInfo`, `IGMHostConfig`
- IGM: all cheat code (`CheatManager`, `CheatSession`, `CheatsScreen`, `ChtParser`, `CheatEntry`, `CheatFile`)
- IGM: `GenericIgmSettings`, `IgmSettingsProvider`, `ProviderSettingsController`
- IGM: `RaIgmSettingsProvider`, `RaOptionCatalog`, `RaOptionStrings`, `RaSetting`, `RaSettingsHost`, `RaValueCycler`
- `RaHasher`, `RaConsoles`, `RaPreloadEligibility`

---

## Track 2: RicottaArch launch path + RA offline ⬅️ NEXT

### Still to remove

**`LaunchManager.kt`**:
- `isRicotta` branches in `launchRom` (line 308), `resumeRom` (line 404), `findResumableRoms` (line 235) — replace with `launchRetroArchIntent` unconditionally
- `buildRicottaIgm()` (line 489) and its call sites
- `IgmColors`, `IgmDisplaySettings`, `CanonicalButton` imports (used only by `buildRicottaIgm`)
- ⚠️ **Open question:** once `isRicotta` is gone from `findResumableRoms`, resume works for no one. Decide: does resume work with stock RetroArch, or does the feature go away?

**`RetroArchLauncher.kt`**:
- `launchRicotta()` — `@Deprecated("TODO Remove")`
- `RicottaIgm` data class
- `ProtocolVerdict` sealed interface, `checkProtocol`, `readInstalledProtocol`, `isRicotta`
- `IgmColors`, `IgmDisplaySettings`, `IgmInputMapping`, `RicottaLaunchParams`, `RICOTTA_PROTOCOL_VERSION` imports

**`launcher/IgmInputMappingFactory.kt`** — delete entirely, only used by `buildRicottaIgm`

**`EmulatorMappingBuilder.kt`**:
- `isRicotta` call (line 357) and its comment (line 233)

**`InstalledCoreService.kt`**:
- Ricotta package labels and filter

**`SettingsRepository.kt`**:
- `DEFAULT_RA_PACKAGE = "dev.cannoli.ricotta.aarch64"` — change to stock RetroArch

**`SettingsViewModel.kt`**:
- Ricotta package filter in RA package picker

**`CoreDownloadService.kt`**:
- `ACTION_DOWNLOAD/RESULT` using `dev.cannoli.ricotta` package strings

**RA offline** (`ra/` directory — entire package, all dead):
- `RaPreloadController`, `RaPreloadEngine`, `RaOfflineStore`, `RaOfflinePreloader`, `RaOfflineLookup`, `RaConnectClient`, `RaSetMetadata`
- Unblock first: remove `raPreloadController` from `DialogInputHandler` + `InputRouter`, remove `RaOfflineStore` refs from `DialogInputHandler` + `InputRouter`, remove `RaOfflinePlatform` + RA screens from `AppNavGraph`
- `CannoliPaths.configRaOffline`
- `DialogState.RAPreloadProgress`, `DialogState.RAPreloadResult`
- `DialogOverlay` RA preload rendering
- `BootInitializer` RA game IDs file check

**`cannoli-igm/`**:
- `RicottaLaunchParams.kt`
- `IgmColors` (in `IgmDisplaySettings.kt`), `IgmDisplaySettings.kt`, `IgmInputMapping.kt`
- `IGMHostConfig.kt`

---

## Track 3.5: Delfino (GameCube/Wii fork)

Same pattern as Ricotta — a Cannoli fork of Dolphin (`dev.cannoli.delfino`) with a structured IGM launch protocol.

**`DelfinoLaunchParams.kt`** in `cannoli-igm` — carries `IgmColors`, `IgmDisplaySettings`, `IgmInputMapping`. Goes once those types are removed.

**`DelfinoLauncher.kt`** — builds and fires the structured intent. Would become a plain `EmuLaunch` or `ApkLaunch` target, or be removed entirely.

**Decision needed:** keep structured Delfino launch (save states, guide viewer for GC games) or flatten to a generic external emulator like stock RetroArch?

---

## Track 3: Save slots UI

**`SaveStatePickerScreen.kt`** — `@Deprecated("Remove")`, still wired in `AppNavGraph`. Replaced by `SaveSlotsScreen`. Migrate the `AppNavGraph` callsite then delete.

**`SaveSlot.kt` / `SaveSlotManager`** — `@Deprecated("Should be removed")`. Thin wrapper around `cannoli-igm`'s `SharedSlotManager`. Can go once `SaveStatePickerScreen` is removed and remaining callers switch to `SharedSlotManager` directly.
