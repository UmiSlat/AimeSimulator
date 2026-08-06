# HINATA FeliCa diagnostics

This local WebHID tool separates FeliCa discovery, targeted System Code polling,
and Aime service reads. It does not send write, firmware, or configuration
commands to the reader.

## Run

1. Close HINATA Client, maimai, and any other application using the reader.
2. Start a localhost server from the repository root:

   ```powershell
   python -m http.server 8765 --bind 127.0.0.1 --directory tools/hinata-diagnostics
   ```

   On Windows, you can also double-click `start-hinata-diagnostics.bat` in this
   directory. Keep its console window open while using the page.

3. Open <http://127.0.0.1:8765/> in desktop Chrome.
4. Select the HINATA USB device and run the full diagnostic while the phone is
   unlocked and the AimeSimulator HCE-F service is active.
5. Export the JSON report after the run.

WebHID requires a secure context. `localhost` and `127.0.0.1` are treated as
secure contexts by Chrome; opening `index.html` directly is not supported.

## Result meaning

| Result | Meaning |
| --- | --- |
| `FFFF` responds | The phone is visible to wildcard 212 kbps FeliCa polling. |
| `88B4` responds | The phone is discoverable under the Aime System Code. |
| `4000` responds, `88B4` does not | Android generic HCE-F works, but this does not match an Aime cabinet poll. |
| Blocks `00`, `82`, and `85` are read | Polling advanced to service `000B`; block `85` exposes the card-image System Code. |
| Poll responds but block reads fail | System Code routing works; the HCE service command path does not. |

The complete run uses PN532 `InListPassiveTarget` at 212 kbps with Request Code
`01`, then reads blocks `00`, `82`, and `85` from service `000B` for each discovered
target. Raw HINATA `E2`/PN532 frames are retained in the exported report.

## Local maimai limitation

The local game launch script injects `mai2hook.dll` into `amdaemon.exe` and the
hook loads `hinata.dll` through the segatools AimeIO interface. That path can
validate game-side card handling, but it does not reproduce the original Sega
reader's RF polling or System Code filter.

## Test

```powershell
node --test tools/hinata-diagnostics/protocol.test.mjs
```
