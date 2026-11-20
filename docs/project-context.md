# Project Context

This is a fork of [Cannoli](https://github.com/CannoliHQ/cannoli) with one clear goal: strip it down to a pure launcher.

**What goes:**
- The built-in libretro runner (`LibretroActivity`, `LibretroRunner`, `LibretroRenderer`, `LibretroScreen`, `retro_bridge.so`)
- RicottaArch-specific launch path and protocol (`launchRicotta`, `RicottaLaunchParams`, IGM contract)
- `EmulatorSource.Internal` and `LaunchTarget.Embedded` — the whole embedded-core concept
- RetroAchievements integration tied to the native runner

**What stays:**
- The launcher UI and navigation
- Game library, scanning, art/metadata
- Launching native Android apps (`LaunchTarget.ApkLaunch`, `LaunchTarget.EmuLaunch`)
- External emulator support — users can still point the app at RetroArch or any standalone emulator

**North star:** Cannoli upstream keeps all the original features. This fork does one thing — help you pick and launch a game — and delegates everything else to whatever emulator the user has installed.
