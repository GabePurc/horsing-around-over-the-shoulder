# Horsing Around: Over the Shoulder

Fabric client mod for Minecraft 26.3 (Java 25, Mojang names): a Red Dead Redemption 2 style over-the-shoulder
third-person camera. Optional add-on to Horsing Around (separate repo, checked out next to this one as
`../Horsing Around`), but it must keep working on its own.

- Camera: `ShoulderCamera` (framing and smoothing) and `mixin/CameraMixin` (placement). The camera always looks exactly
  where the player looks and orbits the head; never twist it per frame.
- Aiming: `ShoulderAim` finds what is under the centre crosshair; `MinecraftMixin` (targeting) and `LocalPlayerMixin`
  / `MultiPlayerGameModeMixin` (rotation sent to the server, which aims projectiles) point at it.
- Settings: `config/ShoulderConfig` (JSON in the config folder) and a vanilla-style screen reachable from Mod Menu
  (optional dependency; never required at runtime).
- Horsing Around integration: only through `dev.horsingaround.client.api.RideCameraApi`, guarded by
  `isModLoaded("horsingaround")` in `HorsingAroundCompat`. `src/horsingaroundApi` holds a compile-only copy of that
  class; keep its signatures identical to the real one in the Horsing Around repo and never package it.

Build: `./gradlew build` (jar in `build/libs/`). Play-test: `./gradlew runClient` (loads Mod Menu, and Horsing Around if
it has been built in `../Horsing Around` or `../horsing-around`). Builds must work on any machine: never commit
machine-specific paths. Gradle picks JDK 25 through `gradle/gradle-daemon-jvm.properties` (downloads one if needed).
Release readiness (Modrinth, compatibility with other mods) is planned in the Horsing Around repo's
`development_plan.md`, Release.

Verify camera changes with `./gradlew runClientGameTest` (`src/gametest/.../ShoulderCamTest.java`): writes
`build/run/clientGameTest/shoulder-camera-report.txt` and screenshots. The riding checks run only when Horsing Around
is built next door.

Code rules: no per-frame allocation beyond what vanilla already does; keep the add-on independent of Horsing Around
internals.
