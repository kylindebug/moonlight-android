# Desktop keyboard interoperability

## Implemented in 12.2-desktop.2

| Android input | Wire key | Behavior |
| --- | --- | --- |
| `KEYCODE_ENTER` | `0x800D` (`VK_RETURN`) | Normal Enter down/up |
| `KEYCODE_NUMPAD_ENTER` | `0x800D` (`VK_RETURN`) | Compatible Enter down/up on existing hosts |
| `KEYCODE_SYSRQ` | `0x802C` (`VK_SNAPSHOT`) | Print Screen down/up |
| `KEYCODE_SCREENSHOT` | `0x802C` (`VK_SNAPSHOT`) | Same screenshot semantics as SYSRQ |
| `KEYCODE_PRINT` | `0x802A` (`VK_PRINT`) | Existing distinct Print mapping preserved |

Print Screen does not inject Alt, Win or other modifiers. Users choose Alt+PrtSc or Win+PrtSc themselves.
The host's Windows settings determine whether the result is a clipboard image, a screenshot UI or a saved file.
Not seeing a new file alone is not evidence that the key failed.

The key state owner records the exact wire code and flags from DOWN and uses them for UP even if the keyboard
layout or normalization mapping changes meanwhile. It tracks modifier sides and devices independently, suppresses
duplicate DOWNs, and keeps Android aliases sharing a wire key held until their last source releases it. Android
repeat events remain suppressed for mapped keys; the host implements physical keyboard repeat.

Focus loss, input ungrab, configuration changes, device removal and stream teardown release held keys. Ordinary
keys are released before modifiers, with no synthetic shortcut. Late UPs are harmless, and background DOWNs are
rejected while forwarding is disabled. The evdev path uses the same ownership policy.

## Main Enter versus keypad Enter: capability audit

This version accepts the standard Android keypad event, but **does not preserve independent remote keypad identity**.
An IME can now send `KEYCODE_NUMPAD_ENTER` directly to this client. An IME's compatibility option should still send
ordinary `KEYCODE_ENTER` when using older clients that lack the new mapping.

The audited path is:

1. `Game.java` and `KeyboardTranslator.java` produce a Windows VK with the legacy `0x80` prefix.
2. `simplejni.c` forwards the key code, direction, modifiers and flags to `LiSendKeyboardEvent2()`.
3. `moonlight-common-c/src/Input.h` defines `NV_KEYBOARD_PACKET`. The only defined keyboard flag in the checked
   `Limelight.h` is `SS_KBE_FLAG_NON_NORMALIZED` (`0x01`); it describes layout normalization, not keypad location.
4. In the paired Sunshine source, `src/input.cpp` masks the key code with `0x00FF`. Encoding location in its high
   byte therefore cannot work with that host. `src/platform/virtualhid_input.cpp::keyboard_event()` forwards the VK
   and normalization flag, without setting a scan code.
5. libvirtualhid's `KeyboardEvent` already has a `scan_code` field. Its Windows HID backend can map extended
   scan code `E0 1C` to keypad Enter usage `0x58`; ordinary `VK_RETURN` maps to Enter usage `0x28`. That capability
   is not currently transported from this client to the host.

No reserved field or undocumented protocol flag is repurposed in this change. A future extension needs an explicit
host capability and a mutually supported packet format carrying scan code/location. It must preserve existing VK
behavior when the host lacks that capability. Main Enter and keypad Enter must then have separate held-key identities,
repeat state and cleanup on both ends. No numeric message ID or flag bit is assigned by this design note.

Before shipping such an extension, validate the Windows SendInput fallback as well as virtual HID, test both host
versions against both client versions, and observe raw Windows scan code/E0 flags (or HID usages) for DOWN and UP.

## Validation and remaining device checks

Automated tests exercise the real Activity key handlers and the state owner with a mock connection, without sending
input to a live PC. They cover both Enter keys, screenshot aliases, explicit screenshot modifiers, all four modifier
families, IME-only modifier metadata, simultaneous aliases, repeated DOWNs, canceled UPs, focus loss, rotation,
ungrab, teardown, device removal, stale UPs, and wire-identity preservation after mapping changes. Existing desktop
selection and touch long-press tests are retained.

These are local regressions, not end-to-end acceptance of the new APK. Device acceptance still needs:

- The standard keypad event and the IME's older-client compatibility option, on the actual tablet keyboard.
- Main Enter, keypad Enter and PrtSc DOWN/UP observed in a foreground Windows raw-input test application.
- Explicit left/right Ctrl, Shift, Alt and Win chords, overlapping fingers and long holds.
- Rotation, closing the IME with keys held, device unplug, disconnect and reconnect without stuck host keys.
- Actual Windows screenshot behavior, plus host NumLock/CapsLock state changes. Local IME indicators must not be
  presented as confirmed host LED state; this change introduces no LED-state feedback protocol.

Sunshine screenshot handling has only been inspected here. No host screenshot defect is asserted or patched without
device evidence, and this client change does not alter the installed host or an active stream.
