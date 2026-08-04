# Third-party notices

This project depends on third-party components through their published package
coordinates. No prebuilt application or library copied from an earlier simulator
repository is checked in.

## Packaged and build dependencies

- **Dobby** (`io.github.vvb2060.ndk:dobby:1.2`) — Apache License 2.0.
  Source: <https://github.com/jmpews/Dobby>
- **libxposed API 101** (`io.github.libxposed:api:101.0.0`) — Apache License 2.0.
  It is a compile-only interface and is not packaged into the APK.
- **AndroidX**, **Material Components for Android**, and the Android Gradle Plugin —
  Apache License 2.0.
- **Kotlin** — Apache License 2.0.
- **JUnit 4** is used only for tests under the Eclipse Public License 1.0.

Each component remains governed by its own license. This notice does not grant a
license to project-owned source code.

## Protocol implementation reference

- **Project HINATA / hinata_go**, analyzed at commit
  `c56d8badc3a720e0ba9e2f721f3f73111f2f6d97`.
  Source: <https://github.com/Project-HINATA/hinata_go>

  The Amusement IC fingerprint, FeliCa service/block layout, and SPAD0
  substitution-table and round behavior were used as interoperability
  references for the physical-card reader.

  No license file was present in the analyzed repository revision. This notice
  records provenance and does not assert that referenced source code has been
  relicensed by this project. Redistribution rights for material derived from
  that implementation should be verified before publishing release source or
  binaries.
