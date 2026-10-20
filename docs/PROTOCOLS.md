# Protocols and code provenance

These encoders were written for this project. No Arduino or ESP8266 library is linked or copied into the app. References describe wire formats and candidate command values; they do not certify compatibility with the user's hardware.

| Family | Wire format | Scope |
| --- | --- | --- |
| Samsung TV | 38 kHz, 4.5 ms / 4.5 ms header, 32 bits LSB first, address bytes 07/07, command and complement | BN59-01180A-family candidate commands |
| LG TV | Standard 8-bit-address NEC at 38 kHz, address 04, complemented address and command, LSB first | Common LG TV code candidate, not AC |
| LG28 AC | 38 kHz; 8.5 ms / 4.25 ms header; 28 bits MSB first | Basic LG 6711A20083V-style state |
| LG2 AC | 38 kHz; 3.2 ms / 9.9 ms header; 480 µs marks; 28 bits MSB first | Basic AKB75215403-style state |
| Whirlpool AC | 38 kHz; 21 bytes, LSB first; sections of 6/8/7 bytes with two inter-section gaps | DG11J1-3A/04 and DG11J1-91 basic-state candidates |
| Raw | Explicit carrier + alternating positive mark/space microseconds | Any complete captured signal within API bounds |

Samsung command values are documented by the [Arduino-IRremote Samsung example](https://github.com/Arduino-IRremote/Arduino-IRremote/blob/master/examples/ReceiveOneAndSendMultiple/ReceiveOneAndSendMultiple.ino). Its [Samsung protocol implementation](https://github.com/Arduino-IRremote/Arduino-IRremote/blob/master/src/ir_Samsung.hpp) describes bit order and timings.

The [LG wire-format reference](https://github.com/crankyoldgit/IRremoteESP8266/blob/master/src/ir_LG.cpp) and [LG state layout](https://github.com/crankyoldgit/IRremoteESP8266/blob/master/src/ir_LG.h) document the AC frame variants. The AC command field contains mode, temperature offset by 15°C, and fan; the low nibble checksums the four command nibbles. Off uses `0x88C0051`. Golden vectors include cool/24/auto `0x880095E` and heat/24/auto `0x8804952`.

The LG TV candidate uses common NEC commands; compare against captures before assuming compatibility. Samsung power has legacy MSB wire representation `E0E040BF`; LG TV power is `20DF10EF`. Raw integer hex values found online may use reversed bit order, different byte order, or a different protocol; do not paste hex into the raw-duration editor.

## Repeats, state, and extending coverage

Built-in buttons emit one complete frame per accepted tap. No press-and-hold stream or automatic repeat is generated. The transmitter keeps at least 150 ms between accepted sends after the API returns. If a model needs repeats, add a custom raw command containing the frames and intervening spaces, keeping the total below two seconds. Duty cycle cannot be selected through ConsumerIrManager.

For another TV code set, create a catalog candidate with explicit brand and remote-model metadata, populate raw commands using an encoder, and add captured-vector tests. For another AC family, implement an independent full-state encoder with documented fields, checksum, valid state ranges, and protocol/model-specific controls. Do not reuse LG state bytes for other brands.

For ACs without an implemented encoder, add scenes such as “Cool 24°C Auto” and “Off”. Each scene must contain the entire captured message, including required repeated frames. The UI intentionally does not show arbitrary temperature controls for a raw scene profile.

Licensing: the Gradle wrapper and launcher scripts are official Gradle distribution assets under Apache License 2.0; see the license headers and [Gradle license](https://github.com/gradle/gradle/blob/master/LICENSE). Protocol research references above retain their authors' licenses; no claim of ownership is made over those references. Review source/data licensing before importing or redistributing a third-party code database.

## Whirlpool implementation

References: [Whirlpool state layout and model families](https://github.com/crankyoldgit/IRremoteESP8266/blob/master/src/ir_Whirlpool.h), [transmission and checksum behavior](https://github.com/crankyoldgit/IRremoteESP8266/blob/master/src/ir_Whirlpool.cpp), and [upstream captured-frame tests](https://github.com/crankyoldgit/IRremoteESP8266/blob/master/test/ir_Whirlpool_test.cpp). The Java implementation is independent; no upstream library is linked.

The encoder constructs a 21-byte message, translates shared profile mode/fan values, applies model-specific temperature offsets, sets the model marker, and calculates two XOR checksums. It produces a 343-duration three-section waveform, with no repeated power frame. The upstream reference treats 38 kHz as an inferred carrier; physical verification remains necessary.

Power is a one-shot toggle bit, never a saved desired on/off state. Other button actions clear that bit. The command categories are temperature (2), sleep (3), Turbo/Super (4), mode (6), swing (7), fan (17) and basic 6th Sense (23 / 0x17); Power temporarily uses category 1. Note that fan's decimal 17 differs from 6th Sense's hexadecimal 0x17.

Basic 6th Sense uses wire Auto mode 1, 23°C, Auto fan and no sleep/Turbo. Sleep sets byte 2 bit 3 and low fan. Vertical swing uses both byte 2 bit 7 and byte 8 bit 6. Turbo uses byte 5 bits 4 and 7, high fan and minimum cooling/maximum heating temperature. Power clears Turbo. Frames retain both XOR checksums and model offsets. Timers are disabled, clock is 00:00, and the default display-light flag is unchanged.

DG11J1-3A/04 spans 18–32°C; DG11J1-91 spans 16–30°C. Heat requires heating hardware. These new controls follow documented candidate-family fields; they have not been confirmed on DG11J1-35. Upstream also distinguishes basic Auto/sleep from unsupported advanced 6th Sense and sleep programs. No iFeel, AroundU, Silent, PowerSave or proprietary sensor programs are implemented. Each button sends one complete frame; no automatic repeat or retry is added.
