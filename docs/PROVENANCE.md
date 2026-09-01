# Source provenance boundary

This document records a focused source-comparison audit. It is an engineering
record, not a legal opinion, and it does not grant permission to copy, modify,
or redistribute any material.

## Audit scope

The comparison was performed on 2026-09-01 against these immutable revisions:

- Project HINATA / `hinata_go` commit
  [`c56d8badc3a720e0ba9e2f721f3f73111f2f6d97`](https://github.com/Project-HINATA/hinata_go/tree/c56d8badc3a720e0ba9e2f721f3f73111f2f6d97)
- `UmiSlat/AICEmu` commit
  [`5a1ecc28e98541e8226e1e92b07bd2747e607bb3`](https://github.com/UmiSlat/AICEmu/tree/5a1ecc28e98541e8226e1e92b07bd2747e607bb3)

Neither revision contains a license for the repository as a whole. License
files inside vendored dependency subdirectories do not license the surrounding
application source.

## Observed correspondence

| AimeSimulator path | Observed relationship | Current boundary |
| --- | --- | --- |
| `app/src/main/java/io/github/umislat/aimesimulator/nfc/AicAccessCodeCodec.kt` | The decoded 9 x 256-byte substitution table is byte-for-byte identical to `hinata_go/lib/utils/spad0.dart`. Both byte sequences have SHA-256 `5CD26727D9961A2CD6F70651B5BC8493A5C3CF1C5B4376BEDE302FC407F367B6`. The table keys, inverse-table construction, round count, table progression, and 15-byte rotation also correspond. | Unresolved source-material boundary. Do not describe this file as merely informed by protocol facts. |
| `app/src/test/java/io/github/umislat/aimesimulator/nfc/AicAccessCodeCodecTest.kt` | The tests identify the HINATA behavior and exercise the corresponding SPAD0 transform and AIC fingerprint. | Tied to the unresolved codec feature, although the test structure itself was not found verbatim in HINATA. |
| `app/src/main/java/io/github/umislat/aimesimulator/nfc/PhysicalCardReader.kt` | The AIC candidate values and decision predicate correspond to HINATA: IDm prefix `01 2E`, PMm `00 F1 00 00 00 01 43 00`, System Code `88B4` or an absent/zero code, service `000B`, and SPAD0 Block `00`. | Recorded as interoperability and protocol-identification facts. This audit found no large copied data table in this file, but it does not make a legal determination about individual constants. |
| `app/src/main/java/io/github/umislat/aimesimulator/nfc/CardImage.kt` | The block set and fixed values correspond to `AICEmu/app/src/main/assets/felica_template.json`, including Blocks `83`, `85`, and `88`. The template was introduced by AICEmu commit `5a1ecc28`. | Unresolved template-data boundary. Its history is separate from the later HINATA physical-card reader work. |

The Android HCE service, UI, storage, root integration, native hooks, build
scripts, and other protocol code were outside this focused comparison. Their
absence from the table is not a declaration that every line has been fully
audited for provenance.

## Protocol facts versus source expression

Identifiers needed for interoperability, such as NFC command values, service
codes, block numbers, IDm/PMm fingerprints, and System Codes, are recorded
separately from source expression. The exact 2304-byte SPAD0 table and the fixed
card-image template require additional review because the present project data
matches material in unlicensed upstream revisions, rather than only using a
small set of protocol identifiers.

Changing formatting, language, or encoding does not by itself resolve that
provenance issue. A clean-room rewrite of control flow also would not establish
the origin or redistribution status of table data that remains byte-identical.

## Required resolution before a project-wide license

Before adding a root license that could be read as covering the whole source
tree, choose and document one of these paths for each unresolved item:

1. Obtain explicit permission or a compatible license from the relevant rights
   holder and preserve the required attribution and license text.
2. Replace the material using an adequately documented public specification or
   a compatible-licensed source, keeping evidence of the independent source.
3. Remove the affected feature or data from the redistributable tree, then
   license only the remaining project-owned work.
4. Use carefully scoped file-level licensing and explicit exclusions after an
   appropriate legal review.

After those boundaries are resolved, the project can add a root license for
material its contributors are entitled to license while retaining this record
and `THIRD_PARTY_NOTICES.md` for third-party material.
