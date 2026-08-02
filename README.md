# AimeSimulator

AimeSimulator is an Android HCE-F card simulator. It manages local card profiles,
captures selected FeliCa Lite blocks from a physical card, and exposes the selected
profile through Android's `HostNfcFService` API.

## Capabilities

- Normal and compatibility NFCID2 routing modes.
- Read Without Encryption responses for the FeliCa Lite read-only service.
- Persistent IDm, SPAD0, and ID-block snapshots.
- Android 14 and older PMm replacement through libxposed API 101 and Dobby.
- Android 15/16 PMm replacement for the tested ST NFC HAL through a KernelSU module.
- System light/dark theme selection and Material You dynamic colors.

The Android 15/16 path is device-specific. It currently targets arm64 devices whose
HAL process name starts with `android.hardware.nfc-service`, including the ST
hyphenated and dotted variants.

## Build

Requirements:

- JDK 17
- Android SDK 34
- Android NDK with CMake 3.22.1
- Python 3

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug
python tools/package_module.py
python tools/check_artifacts.py
```

Generated files:

- `app/build/outputs/apk/debug/app-debug.apk`
- `dist/aimesim-pmm-ksu-v3.zip`

## Runtime setup

The LSPosed module uses the modern libxposed API 101 entry format and recommends
only the `com.android.nfc` scope. Android 15/16 additionally require the KernelSU
module produced by `tools/package_module.py`.

PMm state is written below `/data/adb/aimesim_pmm/`. The app reports `disabled`,
`waiting`, `injecting`, `active`, or `error` based on the module response.
The Android 15/16 module keeps its injected hook loaded and switches PMm rewriting
at runtime, so toggling the patch does not restart the NFC HAL or framework service.

See [docs/FUNCTIONAL_SPEC.md](docs/FUNCTIONAL_SPEC.md) for the behavior contract and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for dependency provenance.
