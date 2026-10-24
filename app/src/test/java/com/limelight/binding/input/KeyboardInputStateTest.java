package com.limelight.binding.input;

import android.view.KeyEvent;
import com.limelight.nvstream.input.KeyboardPacket;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class KeyboardInputStateTest {
    private KeyboardInputState.Sink sink;
    private KeyboardInputState state;
    private static final byte DOWN = KeyboardPacket.KEY_DOWN;
    private static final byte UP = KeyboardPacket.KEY_UP;
    private static final byte FLAGS = 1;

    private static short wire(int vk) { return (short) (0x8000 | vk); }

    @Before public void setUp() {
        sink = mock(KeyboardInputState.Sink.class);
        state = new KeyboardInputState(sink);
    }

    @Test public void duplicateDownDoesNotGenerateAnotherPhysicalPress() {
        state.keyDown(KeyEvent.KEYCODE_A, -1, wire(0x41), (byte) 0, FLAGS);
        state.keyDown(KeyEvent.KEYCODE_A, -1, wire(0x41), (byte) 0, FLAGS);
        assertTrue(state.keyUp(KeyEvent.KEYCODE_A, -1, (byte) 0));
        verify(sink).send(wire(0x41), DOWN, (byte) 0, FLAGS);
        verify(sink).send(wire(0x41), UP, (byte) 0, FLAGS);
        verifyNoMoreInteractions(sink);
    }

    @Test public void simultaneousEnterAliasesRemainHeldUntilBothAreReleased() {
        state.keyDown(KeyEvent.KEYCODE_ENTER, -1, wire(0x0D), (byte) 0, FLAGS);
        state.keyDown(KeyEvent.KEYCODE_NUMPAD_ENTER, -1, wire(0x0D), (byte) 0, FLAGS);
        assertTrue(state.keyUp(KeyEvent.KEYCODE_ENTER, -1, (byte) 0));
        verify(sink, never()).send(wire(0x0D), UP, (byte) 0, FLAGS);
        assertTrue(state.keyUp(KeyEvent.KEYCODE_NUMPAD_ENTER, -1, (byte) 0));
        verify(sink).send(wire(0x0D), DOWN, (byte) 0, FLAGS);
        verify(sink).send(wire(0x0D), UP, (byte) 0, FLAGS);
        verifyNoMoreInteractions(sink);
    }

    @Test public void releaseKeepsTheDownCodeAndFlagsAfterMappingChanges() {
        state.keyDown(KeyEvent.KEYCODE_A, 7, wire(0x51), (byte) 0, (byte) 0);
        state.keyDown(KeyEvent.KEYCODE_A, 7, wire(0x41), (byte) 0, FLAGS);
        state.keyUp(KeyEvent.KEYCODE_A, 7, (byte) 0);
        verify(sink).send(wire(0x51), DOWN, (byte) 0, (byte) 0);
        verify(sink).send(wire(0x51), UP, (byte) 0, (byte) 0);
        verifyNoMoreInteractions(sink);
    }

    @Test public void differentProtocolFlagsKeepSeparateBalancedWireIdentities() {
        state.keyDown(KeyEvent.KEYCODE_A, 1, wire(0x41), (byte) 0, (byte) 0);
        state.keyDown(KeyEvent.KEYCODE_A, 2, wire(0x41), (byte) 0, FLAGS);
        state.releaseAll();
        for (byte flags : new byte[] {0, FLAGS}) {
            verify(sink).send(wire(0x41), DOWN, (byte) 0, flags);
            verify(sink).send(wire(0x41), UP, (byte) 0, flags);
        }
        verifyNoMoreInteractions(sink);
    }

    @Test public void unknownAndOrphanEventsDoNotSendPhantomKeys() {
        state.keyDown(KeyEvent.KEYCODE_UNKNOWN, 1, (short) 0, (byte) 0, FLAGS);
        assertFalse(state.keyUp(KeyEvent.KEYCODE_A, 1, (byte) 0));
        state.releaseAll();
        verifyNoInteractions(sink);
    }

    @Test public void releaseAllLiftsNormalKeysBeforeModifiersWithoutShortcuts() {
        state.keyDown(KeyEvent.KEYCODE_META_RIGHT, 1, wire(0x5C), (byte) 8, FLAGS);
        state.keyDown(KeyEvent.KEYCODE_SYSRQ, 1, wire(0x2C), (byte) 8, FLAGS);
        clearInvocations(sink);
        state.releaseAll();
        InOrder order = inOrder(sink);
        order.verify(sink).send(wire(0x2C), UP, (byte) 0, FLAGS);
        order.verify(sink).send(wire(0x5C), UP, (byte) 0, FLAGS);
        order.verifyNoMoreInteractions();
    }

    @Test public void repeatedCleanupAndLateUpDoNotSendDuplicateReleases() {
        state.keyDown(KeyEvent.KEYCODE_A, -1, wire(0x41), (byte) 0, FLAGS);
        state.releaseAll();
        state.releaseAll();
        assertFalse(state.keyUp(KeyEvent.KEYCODE_A, -1, (byte) 0));
        verify(sink).send(wire(0x41), UP, (byte) 0, FLAGS);
    }

    @Test public void unpluggingADeviceDoesNotReleaseAnotherDeviceOrItsModifier() {
        state.updateModifiers(KeyEvent.KEYCODE_CTRL_LEFT, 1, true);
        state.updateModifiers(KeyEvent.KEYCODE_CTRL_LEFT, 2, true);
        state.keyDown(KeyEvent.KEYCODE_CTRL_LEFT, 1, wire(0xA2), (byte) 2, FLAGS);
        state.keyDown(KeyEvent.KEYCODE_CTRL_LEFT, 2, wire(0xA2), (byte) 2, FLAGS);
        state.keyDown(KeyEvent.KEYCODE_A, 1, wire(0x41), (byte) 2, FLAGS);
        state.releaseDevice(1);
        assertEquals(KeyboardPacket.MODIFIER_CTRL, state.getModifiers());
        verify(sink).send(wire(0x41), UP, (byte) 0, FLAGS);
        verify(sink, never()).send(wire(0xA2), UP, (byte) 0, FLAGS);
        state.releaseDevice(2);
        verify(sink).send(wire(0xA2), UP, (byte) 0, FLAGS);
        assertEquals(0, state.getModifiers());
    }

    @Test public void disabledInputRejectsLateBackgroundDowns() {
        state.keyDown(KeyEvent.KEYCODE_A, 1, wire(0x41), (byte) 0, FLAGS);
        state.setEnabled(false);
        clearInvocations(sink);
        state.keyDown(KeyEvent.KEYCODE_A, 1, wire(0x41), (byte) 0, FLAGS);
        state.updateModifiers(KeyEvent.KEYCODE_CTRL_LEFT, 1, true);
        state.setEnabled(true);
        assertEquals(0, state.getModifiers());
        assertFalse(state.keyUp(KeyEvent.KEYCODE_A, 1, (byte) 0));
        verifyNoInteractions(sink);
    }

    @Test public void reconnectBeginsWithCleanKeyOwnership() {
        for (int session = 0; session < 3; ++session) {
            state.setEnabled(true);
            state.keyDown(KeyEvent.KEYCODE_A, -1, wire(0x41), (byte) 0, FLAGS);
            state.setEnabled(false);
        }
        verify(sink, times(3)).send(wire(0x41), DOWN, (byte) 0, FLAGS);
        verify(sink, times(3)).send(wire(0x41), UP, (byte) 0, FLAGS);
        verifyNoMoreInteractions(sink);
    }

    @Test public void modifierSidesRemainIndependentForAllFourFamilies() {
        int[][] keys = {{KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, 2},
                {KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, 1},
                {KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, 4},
                {KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT, 8}};
        for (int[] pair : keys) {
            assertEquals(pair[2], state.updateModifiers(pair[0], -1, true));
            assertEquals(pair[2], state.updateModifiers(pair[1], -1, true));
            assertEquals(pair[2], state.updateModifiers(pair[0], -1, false));
            assertEquals(pair[2], state.updateModifiers(KeyEvent.KEYCODE_A, -1, true));
            assertEquals(0, state.updateModifiers(pair[1], -1, false));
        }
    }

    @Test public void allModifierFamiliesCombineAndClearTogether() {
        for (int key : new int[] {KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_META_LEFT}) {
            assertTrue(KeyboardInputState.isModifierKey(key));
            state.updateModifiers(key, 3, true);
        }
        assertEquals(15, state.getModifiers());
        assertFalse(KeyboardInputState.isModifierKey(KeyEvent.KEYCODE_NUM_LOCK));
        state.releaseAll();
        assertEquals(0, state.getModifiers());
    }
}
