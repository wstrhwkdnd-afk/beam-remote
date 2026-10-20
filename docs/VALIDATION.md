# Delivery validation — 18 October 2026

## Build

- `assembleDebug`: passed; installable debug APK generated.
- `testDebugUnitTest`: **15 tests passed**, 0 failures, 0 errors.
- `lintDebug`: passed, **0 errors and 6 warnings**.
- Toolchain: Gradle 9.8.1, Android Gradle Plugin 9.4.1, Android Studio bundled OpenJDK 25.0.3, compile SDK 37, target SDK 36, minimum SDK 23.
- Remaining lint warnings: target SDK is below the installed latest SDK; a newer test-only JSON dependency exists; Android backup-rule guidance; and three localization warnings in this English-only MVP. Deprecated platform calls are used for API-23-compatible document-picker and inset handling. No lint errors were suppressed.

## Emulator checks completed (v1.0.0 baseline)

Android API 37 ARM64 emulator, reduced to 540×960 at density 160 to keep the host responsive:

- App installed and cold-launched successfully.
- Empty device list and preview default displayed correctly.
- Added a Samsung TV profile through the native dialog.
- Power produced a 38 kHz, 67-duration preview, explicitly reporting nothing transmitted.
- Added an LG28 AC profile through the native dialog.
- Changed desired temperature from 24°C to 25°C and applied it in preview.
- AC preview reported 38 kHz and 59 durations without transmitting.
- Force-stopped and relaunched: both profiles and selected AC's 25°C setting survived.
- Installed the final APK as an update; the saved 25°C setting remained intact.
- Disabled Preview on the emulator and applied an AC state: the app reported “No accessible IR emitter”, with no false success. Restored Preview afterward.
- Inspected the TV layout from an emulator screenshot: controls, explanatory text, and bottom navigation were readable and separated from system bars.

The emulator initially showed Android launcher/System UI stalls under the host's resource constraints; reducing display size, disabling animations and keeping it awake allowed the checks to complete. These were system-process stalls, not a reported Beam Remote exception.

## Not certified by these checks

Physical IR transmission, OEM driver timing/frequency behavior, appliance compatibility, range, repeated-frame requirements, and AC model-specific responses require the physical-phone checklist in TESTING.md. API 23–36 runtime behavior, screen-reader traversal, all font scales, slow/cloud document providers, and full Android file-picker import/export flows were not exercised end to end. Profile encoding/import validation and serialization were tested on the JVM. Presets are candidates, not a guaranteed universal database.

## Whirlpool v1.1.0 update

`assembleDebug`, `testDebugUnitTest`, and `lintDebug` passed for the updated APK. Six new Whirlpool tests cover golden 24°C frames for both models, transient toggle semantics, 960 combinations of model/temperature/mode/fan/power with waveform decoding and checksums, unsupported settings, model-specific profile round-trips, and backward-compatible legacy imports. The complete suite now has 15 passing tests. Version code is 2 for installation over v1.0.0.

The new Whirlpool screens were compiled and lint-checked but were not rerun on an emulator in this update. The emulator observations above are the original MVP baseline, not Whirlpool hardware certification. Physical Whirlpool IR reception and appliance behavior remain untested; follow the Whirlpool acceptance checks in TESTING.md.

## v1.2.0 remote controls — 20 October 2026

Debug build and Android lint passed; all 18 JVM tests passed. Added checks cover explicit wire fields/checksums for basic 6th Sense, sleep, swing and Turbo, settings interactions, profile persistence with strict boolean validation, legacy defaults and bounded waveforms. These remain candidate commands requiring physical verification.

The AC screen now uses a remote-shaped panel and explicit one-tap buttons. It does not transmit on rendering, profile opening, import or restore. Busy-state checks precede desired-setting mutation. Whirlpool power stays a transient single toggle. A user reported basic AC operation with the 3A/04 profile and DG11J1-35 remote family, but did not enumerate individual working functions.

The API37 emulator was started in a visible window. Initial system/launcher stalls were resolved by restarting with host graphics. Verified in Preview: the Whirlpool screen opens without sending; one TEMP+ tap changes 24°C to 25°C and previews a 38kHz/343-duration command; 6th Sense selects 23°C/Auto fan and disables manual temperature/fan controls; Swing and Turbo update their flags, with Turbo selecting 18°C/High fan and clearing Sleep. Saved legacy profiles remained available after update. The final remote screenshot was inspected. No physical reception or full accessibility certification is implied.
