# Functional specification

This document describes observable behavior. It intentionally avoids prescribing
the structure of any earlier implementation.

## Card profiles

A profile contains a stable identifier, a user-visible label, a 16-hex-digit IDm,
an optional 20-digit printed Access Code, and optional 16-byte SPAD0 and ID-block captures. Profiles and the selected profile
survive process restarts. Deleting or reordering profiles must not select a different
profile accidentally.

The printed Access Code is display-only local metadata. Physical-card profile
capture does not infer or save it, and it does not alter HCE-F registration or
the emulated card image.

Normal mode registers the profile IDm with Android. Compatibility mode registers
`02FE001145141919` and places the same routed IDm in the first eight bytes of block
`82`; any captured trailing bytes in that block remain unchanged. The system code
is `88B4`. The HCE-F service metadata statically declares the
advertised PMm as `00F1000000014300`; supported Android NFC stacks use this value
without a native PMm hook.

## HCE-F protocol

The service validates the frame length before reading fields. It accepts standard
FeliCa Read Without Encryption (`06`) block-list elements and replies with command
`07`, the request NFCID2, two zero status bytes, a block count, and 16 bytes per
block. The read-only service code is `000B` in little-endian packet order.

Write Without Encryption (`08`) is acknowledged with response `09`; profile data is
not modified. Unsupported commands return the four-byte compatibility response
`04 11 45 14`. Malformed frames are ignored.

The default card image contains zero-filled user blocks, an all-`FF` block `0E`,
the active routed IDm in block `82`, PMm metadata in block `83`, system code in
block `85`, and fixed compatibility metadata in blocks `86` and `88`. Captured
SPAD0 and ID-block values supply their remaining data without overriding the
routed IDm prefix.

## Physical-card reading

The single reader flow enables both NFC-F and NFC-A and dispatches by detected
card technology.

For an Amusement IC, it validates the HINATA-compatible IDm, PMm, and system-code
fingerprint, then tries to read blocks `00` and `82` from service `000B` in one
command. If combined reading fails, it reads the blocks separately. SPAD0 is
decrypted and bytes 6 through 15 are displayed as the Access Code.

For any other NFC-F card, the reader captures IDm, system code, SPAD0, and ID
block using the existing combined-read and retry behavior. A profile can still
be created when only the IDm is available.

For a legacy NFC-A MIFARE Classic Aime card, it authenticates sector 0 with the
Aime key as Key B and reads the packed-decimal Access Code from block 2. Since
this card type does not supply a FeliCa IDm, one must be entered before saving.

Opening the editor does not persist anything. Profile storage changes only after
the user presses Save and all fields pass validation. Reading and profile
creation never modify the physical card.

## Android service registration

The UI persists a profile selection before activation. Activation disables the
foreground HCE-F service, registers NFCID2 and system code, then enables the service.
Registration failures report their exact stage without corrupting the stored profile
list. The selected profile is reactivated when the activity returns to the foreground.
If the NFC Binder dies while the NFC process is restarting, the UI waits and retries
for a bounded interval instead of crashing or reporting an immediate permanent failure.

## Framework hooks

The LSPosed entry point uses libxposed API 101 and is scoped only to
`com.android.nfc`. It accepts hexadecimal NFCID2 and system-code strings of the
expected lengths while rejecting reserved system codes. On Android 14 and older,
the NFC application loads `libpmm.so` only when the legacy PMm property is enabled.

The native hook replaces PMm with `00 F1 00 00 00 01 43 00`. Legacy systems hook
`nfa_dm_check_set_config`. The ST HAL path modifies only CORE_SET_CONFIG parameters
encoded as `51 08 <PMm>` or `40 12 <system-code><IDm><PMm>`.
These hooks are compatibility fallbacks for vendor stacks that ignore or replace
the standard HCE-F `t3tPmm-filter`, rather than a requirement of the card image.

## KernelSU lifecycle

The module stores user intent separately from runtime status, serializes injection,
injects at most once per observed HAL PID, and reinjects after an external HAL
restart. Enabling sets a runtime vendor property and loads the hook when needed;
disabling clears that property so subsequent configurations pass through unchanged.
The switch does not restart the HAL or NFC framework service. Commands emit
machine-readable status fields.
