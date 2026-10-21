# Oceania

A realistic super ocean for Minecraft 26.3 on Fabric: tiny tropical islands, deep trenches,
and a houseboat.

By Meatylumberjack.

## Requirements

- Minecraft 26.3. The mod declares `~26.3`, so Fabric Loader refuses to load it on any other
  version rather than let it crash.
- Fabric Loader 0.19.5 or newer.
- Fabric API. Built against 0.162.0+26.3.
- Java 25 or newer, as 26.3 itself needs.
- Mod Menu is optional; with it the Configure screen appears under Mods > Oceania. Built
  against 21.0.0.

## Settings

With Mod Menu installed, open Mods > Oceania > Configure to switch the natural spawning of
sharks, whales, the giant squid, jellyfish and crabs on or off. The same settings live in
`config/oceania.json`; a dedicated server reads its own copy. Seagulls have no switch, because
following them is how you find land.

## Building

`./gradlew build` puts the jar in `build/libs`. The build needs a Java 25 JDK, which Gradle
locates from `gradle/gradle-daemon-jvm.properties`.

## Resource packs

`resourcepacks/Packtrix Turtle Addon` holds the turtle armour textures drawn to sit alongside
the Packtrix 64x pack. They are original art and contain no Packtrix asset.

## License

All rights reserved. See `LICENSE`. Third-party code and audio are credited in
`LICENSE-THIRD-PARTY.md`.
