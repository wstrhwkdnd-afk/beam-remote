# Profile interchange, version 1

Export a device from its remote screen, then import that JSON through Devices. Imports are validated completely before being added, and receive a new UUID to avoid overwriting profiles.

Example: a usable LG28 AC candidate (verify on hardware):

```json
{
  "schemaVersion": 1,
  "name": "Bedroom AC",
  "brand": "LG",
  "model": "6711A20083V",
  "kind": "AC",
  "protocol": "LG28",
  "temperature": 24,
  "mode": 0,
  "fan": 5,
  "commands": {}
}
```

`kind` is `TV` or `AC`. `protocol` is `RAW`, `LG28`, `LG2`, `WHIRLPOOL3A`, or `WHIRLPOOL91`; all AC encoders require kind AC. `id` is optional on import. `name` and `brand` allow 1–60 characters, `model` 1–100. Non-empty strings are required. Changing metadata never changes a protocol.

LG modes: 0 cool, 1 dry, 2 fan, 3 auto, 4 heat. Fan values: 0 lowest, 1 low, 2 medium, 4 high, 5 auto. Temperature is 16–30. These fields represent the desired settings only; no “actual power” value is persisted. All profiles keep these fields internally, even if unused by RAW.

For RAW, `commands` maps visible button labels to signal objects:

```json
{
  "Power": {
    "frequency": 38000,
    "pattern": [9000, 4500, 560]
  }
}
```

The short pattern above demonstrates syntax only; it is **not a complete appliance command**. Supply an actual full capture. Units are hertz and microseconds. The first element is an emitting mark, the second a space, and so on. Discard the receiver's leading idle gap. Do not confuse frequency (38000 Hz) with pulse duration (for example 560 µs).

Validation rejects fractional numbers, non-positive durations, carriers outside 1,000–1,000,000 Hz, more than 4,096 durations, fewer than 3 durations, duration totals at or above 2,000,000 µs, unknown protocols, incompatible device types, and unsupported schema versions. A syntactically valid frame is not proof it controls an appliance. At transmission time, the app also checks reported hardware carrier ranges.

Limits: 512 KB per transferred JSON file, 64 commands with names of 1–60 characters per profile, and 50 profiles. This is not a Pronto, Flipper or LIRC importer. Convert those formats to carrier + mark/space microseconds with their documented tooling before import. Raw code learning requires external receiver hardware.

## Whirlpool profiles

Use `WHIRLPOOL3A` for DG11J1-3A/04 or `WHIRLPOOL91` for DG11J1-91. Temperature bounds are 18–32°C and 16–30°C respectively. Shared mode values are 0 Cool, 1 Dry, 2 Fan, 3 basic 6th Sense/Auto, 4 Heat. Auto encodes 23°C and automatic fan. Fan values are 5 Auto, 1 Low, 2 Medium, 4 High; 0 Lowest is rejected.

Optional `acCommand` records the last changed setting: 2 temperature (default), 3 sleep, 4 Turbo, 6 mode, 7 swing, 17 fan, 23 basic 6th Sense. Power command 1 is generated transiently and is rejected here. Each ordinary button action carries complete state with the power-toggle bit clear; Power carries the state with the toggle bit set. These categories do not send until the user taps a control.

Optional boolean fields `swing`, `sleep` and `turbo` default to false when absent. They must be JSON booleans. Auto forces 23°C/Auto fan and clears sleep/Turbo in the transmitted frame. Turbo forces high fan and the appropriate temperature extreme; sleep forces low fan when Turbo is off. The UI updates those dependent values together. Older basic profiles still load unchanged. Use v1.2.0+ for these fields and commands; older builds may reject new command categories or ignore extra flags.

## Library-backed saved devices (v1.3.0)

`protocol: "LIBRARY"` requires kind AC and fields `libraryId` (64 lowercase SHA-256 hexadecimal digits), `libraryEntry` (profile JSON path), `libraryModeCode` (C/H/W/D/A/AI/F) and `libraryFanCode` (source digit or A). Temperature permits 0–100 for source units, including Fahrenheit candidates. A saved device references rather than embeds its library. Import the same library on a receiving phone before using its saved-device JSON. Older app versions do not accept LIBRARY profiles. See [PROFILE_LIBRARY.md](PROFILE_LIBRARY.md).
