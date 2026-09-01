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

## Interoperability source material

- **Project HINATA / hinata_go**, analyzed at commit
  `c56d8badc3a720e0ba9e2f721f3f73111f2f6d97`.
  Source: <https://github.com/Project-HINATA/hinata_go>

  The Amusement IC fingerprint and FeliCa service/block behavior were used as
  interoperability references for the physical-card reader. The 2304 decoded
  substitution-table bytes in `AicAccessCodeCodec.kt` are an exact match for
  `lib/utils/spad0.dart` at this revision, and the implemented round behavior
  corresponds to that source.

  No license file was present in the analyzed repository revision. This notice
  records provenance and does not assert that referenced source code or table
  data has been relicensed by this project.

- **UmiSlat/AICEmu**, template introduced at commit
  `5a1ecc28e98541e8226e1e92b07bd2747e607bb3`.
  Source: <https://github.com/UmiSlat/AICEmu/blob/5a1ecc28e98541e8226e1e92b07bd2747e607bb3/app/src/main/assets/felica_template.json>

  The block layout and fixed values used by `CardImage.kt` correspond to this
  FeliCa template. No repository-level license was present in the analyzed
  revision.

The file-by-file evidence and unresolved licensing boundary are documented in
[`docs/PROVENANCE.md`](docs/PROVENANCE.md). Redistribution rights for the
identified material should be resolved before publishing source or binaries
under a project-wide license.
