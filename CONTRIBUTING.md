# Contributing to Beam Remote

Useful contributions include verified device profiles, corrected protocol timings, accessibility improvements, and reproducible bug reports.

## Development

1. Fork and clone the repository.
2. Open the project in Android Studio, install the SDK versions in the README, and let Gradle sync.
3. Make a focused change.
4. Run `./gradlew assembleDebug testDebugUnitTest lintDebug`.
5. Open a pull request describing the change and what you tested.

The app targets built-in Android IR hardware through `ConsumerIrManager`. Preserve the distinction between a signal being sent and an appliance confirming a state change. Keep power toggles transient; never retry them automatically.

## Device profiles and protocols

- Identify the exact appliance and original remote model, not only the brand.
- State whether a profile is physically verified or a candidate based on research.
- Include raw captures or a primary protocol reference when available, along with the capture format and carrier frequency.
- Document complete AC state, checksum rules, model-specific limits, and whether power is a toggle.
- Add meaningful captured-frame or golden-vector tests for encoder changes.
- Record tests using the matrix in [TESTING.md](docs/TESTING.md).

Do not label a model supported solely because an APK builds or the signal has a valid checksum. Do not submit private exported profiles, credentials, signing keys, phone serial numbers, or data taken from a third-party database without permission to redistribute it.

Contributions are provided under this repository's MIT license. Preserve upstream notices for third-party material.
