package com.limelight.binding.input;

import android.view.KeyEvent;

import com.limelight.nvstream.input.KeyboardPacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tracks the exact wire identity of held keys for one streaming Activity. */
public final class KeyboardInputState {
    public interface Sink {
        void send(short keyCode, byte direction, byte modifiers, byte flags);
    }

    private static final class WireKey {
        final short code;
        final byte flags;

        WireKey(short code, byte flags) {
            this.code = code;
            this.flags = flags;
        }

        boolean matches(WireKey other) {
            return code == other.code && flags == other.flags;
        }

        boolean isModifier() {
            int vk = code & 0xFF;
            return (vk >= 0xA0 && vk <= 0xA5) || vk == 0x5B || vk == 0x5C;
        }
    }

    private final Sink sink;
    private final Map<Long, WireKey> pressed = new LinkedHashMap<>();
    private final Set<Long> modifiers = new HashSet<>();
    private boolean enabled = true;

    public KeyboardInputState(Sink sink) {
        this.sink = sink;
    }

    private static long identity(int androidKeyCode, int deviceId) {
        return ((long) deviceId << 32) | (androidKeyCode & 0xFFFFFFFFL);
    }

    private static byte modifierMask(int androidKeyCode) {
        switch (androidKeyCode) {
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return KeyboardPacket.MODIFIER_CTRL;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return KeyboardPacket.MODIFIER_SHIFT;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return KeyboardPacket.MODIFIER_ALT;
            case KeyEvent.KEYCODE_META_LEFT:
            case KeyEvent.KEYCODE_META_RIGHT:
                return KeyboardPacket.MODIFIER_META;
            default:
                return 0;
        }
    }

    public static boolean isModifierKey(int androidKeyCode) {
        return modifierMask(androidKeyCode) != 0;
    }

    /** Preserve each physical modifier side and input device independently. */
    public synchronized byte updateModifiers(int androidKeyCode, int deviceId, boolean down) {
        if (modifierMask(androidKeyCode) != 0) {
            long id = identity(androidKeyCode, deviceId);
            if (down) {
                modifiers.add(id);
            } else {
                modifiers.remove(id);
            }
        }
        return getModifiers();
    }

    public synchronized byte getModifiers() {
        byte result = 0;
        for (long id : modifiers) {
            result |= modifierMask((int) id);
        }
        return result;
    }

    private boolean hasWireKey(WireKey key) {
        for (WireKey held : pressed.values()) {
            if (key.matches(held)) {
                return true;
            }
        }
        return false;
    }

    /** Send one DOWN; Android repeats remain the host's responsibility. */
    public synchronized void keyDown(int androidKeyCode, int deviceId, short code,
                                     byte modifierState, byte flags) {
        long id = identity(androidKeyCode, deviceId);
        if (!enabled || code == 0 || pressed.containsKey(id)) {
            return;
        }
        WireKey key = new WireKey(code, flags);
        boolean alreadyHeld = hasWireKey(key);
        pressed.put(id, key);
        // Two Android keys may share a compatible VK (notably both Enter keys).
        // Release that VK only after the last source lets go.
        if (!alreadyHeld) {
            sink.send(code, KeyboardPacket.KEY_DOWN, modifierState, flags);
        }
    }

    /** Release the original DOWN identity, even after layout or device mapping changes. */
    public synchronized boolean keyUp(int androidKeyCode, int deviceId, byte modifierState) {
        WireKey key = pressed.remove(identity(androidKeyCode, deviceId));
        if (key == null) {
            return false;
        }
        if (!hasWireKey(key)) {
            sink.send(key.code, KeyboardPacket.KEY_UP, modifierState, key.flags);
        }
        return true;
    }

    private void release(List<Long> ids) {
        List<WireKey> released = new ArrayList<>();
        for (long id : ids) {
            WireKey key = pressed.remove(id);
            if (key != null && !hasWireKey(key)) {
                released.add(key);
            }
        }
        // Lift ordinary keys before modifiers. Never synthesize a shortcut here.
        released.sort((a, b) -> Boolean.compare(a.isModifier(), b.isModifier()));
        for (WireKey key : released) {
            sink.send(key.code, KeyboardPacket.KEY_UP, (byte) 0, key.flags);
        }
    }

    /** Clear only an unplugged device, preserving keys held by another device. */
    public synchronized void releaseDevice(int deviceId) {
        List<Long> ids = new ArrayList<>();
        for (long id : pressed.keySet()) {
            if ((int) (id >> 32) == deviceId) {
                ids.add(id);
            }
        }
        modifiers.removeIf(id -> (int) (id >> 32) == deviceId);
        release(ids);
    }

    /** Release all held wire keys on focus loss, ungrab, rotation or stream teardown. */
    public synchronized void releaseAll() {
        modifiers.clear();
        release(new ArrayList<>(pressed.keySet()));
    }

    /** Also reject late background input after focus loss or connection shutdown. */
    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            releaseAll();
            this.enabled = enabled;
        }
    }
}
