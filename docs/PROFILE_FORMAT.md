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

Use `WHIRLPOOL3A` for DG11J1-3A/04 or `WHIRLPOOL91` for DG11J1-91. Temperature bounds are 18–32°C and 16–30°C respectively. Shared mode values are 0 Cool, 1 Dry, 2 Fan, 4 Heat; 3 Auto/6th Sense is intentionally rejected. Fan values are 5 Auto, 1 Low, 2 Medium, 4 High; 0 Lowest is rejected.

Optional `acCommand` records the last changed setting: 2 temperature (default), 6 mode, 17 fan. Power command 1 is generated transiently and is rejected here. Each Apply carries full basic state with the power-toggle bit clear; Power on/off carries the same state with the toggle bit set. Existing version-1 LG/RAW profiles lacking `acCommand` still load unchanged. Earlier app builds will reject the new Whirlpool protocol strings; install v1.1.0 or newer before importing these profiles.
