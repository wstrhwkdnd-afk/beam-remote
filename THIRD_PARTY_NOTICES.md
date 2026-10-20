# Third-party notices

Beam Remote's original application code and documentation are provided under the [MIT License](LICENSE).

## Gradle wrapper

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` are official Gradle wrapper assets from Gradle 9.8.1. Their original notices are preserved. They are licensed under the Apache License, Version 2.0; see [the bundled license](docs/licenses/Gradle-Apache-2.0.txt), [the bundled notices](docs/licenses/Gradle-NOTICE.txt), and [Gradle's source](https://github.com/gradle/gradle/tree/v9.8.1).

The pinned Gradle distribution is downloaded at build time and retains its own licensing and notices. Gradle, Android build tooling, the Android SDK, and the JDK are separate tools and are not relicensed by this project's MIT license.

## Test dependencies

JUnit 4.13.2 and JSON-java 20240303 are downloaded for local tests and are not packaged as application runtime dependencies. See their upstream notices: [JUnit](https://github.com/junit-team/junit4/blob/r4.13.2/LICENSE-junit.txt) and [JSON-java](https://github.com/stleary/JSON-java/blob/20240303/LICENSE).

## Infrared protocol research

The Java encoders in this project were implemented independently. Research references and command provenance are listed in [PROTOCOLS.md](docs/PROTOCOLS.md), including Arduino-IRremote and IRremoteESP8266. Those projects retain their own licenses. No Arduino/ESP8266 library or third-party universal remote database is bundled.

Brand and model names identify protocol candidates and compatibility research. This project is not affiliated with or endorsed by Samsung, LG, Whirlpool, Xiaomi, Google, or Gradle.
