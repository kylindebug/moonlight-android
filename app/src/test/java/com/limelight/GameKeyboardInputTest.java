package com.limelight;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.content.res.Configuration;
import android.widget.TextView;

import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.capture.InputCaptureProvider;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Exercises the Activity's real key dispatch without starting a stream or native library. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GameKeyboardInputTest {
    private static final byte FLAGS = MoonBridge.SS_KBE_FLAG_NON_NORMALIZED;
    private Game game;
    private NvConnection connection;

    @Before public void setUp() throws Exception {
        game = Robolectric.buildActivity(Game.class).get();
        connection = mock(NvConnection.class);
        set("conn", connection);
        set("keyboardTranslator", new KeyboardTranslator());
        set("controllerHandler", mock(ControllerHandler.class));
        set("inputCaptureProvider", mock(InputCaptureProvider.class));
    }

    private void set(String name, Object value) throws Exception {
        Field field = Game.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(game, value);
    }

    private void call(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = Game.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(game, arguments);
    }

    private KeyEvent event(int action, int key, int repeat, int meta) {
        return new KeyEvent(1, 2, action, key, repeat, meta, -1, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
    }

    private void down(int key) {
        assertTrue(game.handleKeyDown(event(KeyEvent.ACTION_DOWN, key, 0, 0)));
    }

    private void up(int key) {
        assertTrue(game.handleKeyUp(event(KeyEvent.ACTION_UP, key, 0, 0)));
    }

    @Test public void bothEnterKeysSendBalancedReturnEvents() {
        for (int key : new int[] {KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER}) {
            down(key);
            up(key);
        }
        verify(connection, times(2)).sendKeyboardInput((short) 0x800D,
                KeyboardPacket.KEY_DOWN, (byte) 0, FLAGS);
        verify(connection, times(2)).sendKeyboardInput((short) 0x800D,
                KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
        verifyNoMoreInteractions(connection);
    }

    @Test public void bothScreenshotKeysSendSnapshotWithoutImplicitModifiers() {
        for (int key : new int[] {KeyEvent.KEYCODE_SYSRQ, KeyEvent.KEYCODE_SCREENSHOT}) {
            down(key);
            up(key);
        }
        verify(connection, times(2)).sendKeyboardInput((short) 0x802C,
                KeyboardPacket.KEY_DOWN, (byte) 0, FLAGS);
        verify(connection, times(2)).sendKeyboardInput((short) 0x802C,
                KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
        verifyNoMoreInteractions(connection);
    }

    @Test public void physicalRepeatIsLeftToTheHost() {
        down(KeyEvent.KEYCODE_A);
        for (int i = 1; i <= 5; ++i) {
            assertTrue(game.handleKeyDown(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, i, 0)));
        }
        up(KeyEvent.KEYCODE_A);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_DOWN, (byte) 0, FLAGS);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
        verifyNoMoreInteractions(connection);
    }

    @Test public void releasingOneControlSidePreservesTheOtherHeldSide() {
        down(KeyEvent.KEYCODE_CTRL_LEFT);
        down(KeyEvent.KEYCODE_CTRL_RIGHT);
        up(KeyEvent.KEYCODE_CTRL_LEFT);
        down(KeyEvent.KEYCODE_A);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_DOWN,
                KeyboardPacket.MODIFIER_CTRL, FLAGS);
    }

    @Test public void focusLossReleasesHeldKeysBeforeFurtherInput() {
        down(KeyEvent.KEYCODE_CTRL_LEFT);
        down(KeyEvent.KEYCODE_A);
        game.onWindowFocusChanged(false);
        InOrder order = inOrder(connection);
        order.verify(connection).sendKeyboardInput(eq((short) 0x80A2), eq(KeyboardPacket.KEY_DOWN), anyByte(), eq(FLAGS));
        order.verify(connection).sendKeyboardInput(eq((short) 0x8041), eq(KeyboardPacket.KEY_DOWN), anyByte(), eq(FLAGS));
        order.verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
        order.verify(connection).sendKeyboardInput((short) 0x80A2, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
    }

    @Test public void altAndWinScreenshotCombinationsUseOnlyUserChosenModifiers() {
        int[] modifiers = {KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_META_RIGHT};
        byte[] masks = {KeyboardPacket.MODIFIER_ALT, KeyboardPacket.MODIFIER_META};
        for (int i = 0; i < modifiers.length; ++i) {
            down(modifiers[i]);
            down(KeyEvent.KEYCODE_SYSRQ);
            up(KeyEvent.KEYCODE_SYSRQ);
            up(modifiers[i]);
            verify(connection).sendKeyboardInput((short) 0x802C, KeyboardPacket.KEY_DOWN, masks[i], FLAGS);
            verify(connection).sendKeyboardInput((short) 0x802C, KeyboardPacket.KEY_UP, masks[i], FLAGS);
        }
    }

    @Test public void imeOnlyShiftMetaStateStillReachesTheHost() {
        assertTrue(game.handleKeyDown(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_SHIFT_ON)));
        up(KeyEvent.KEYCODE_A);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_DOWN, KeyboardPacket.MODIFIER_SHIFT, FLAGS);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
    }

    @Test public void canceledKeyUpStillReleasesTheOriginalPress() {
        down(KeyEvent.KEYCODE_A);
        KeyEvent canceled = KeyEvent.changeFlags(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, 0), KeyEvent.FLAG_CANCELED);
        assertTrue(game.handleKeyUp(canceled));
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
    }

    @Test public void layoutChangesDoNotChangeTheKeyUpWireIdentity() throws Exception {
        KeyboardTranslator translator = mock(KeyboardTranslator.class);
        when(translator.translate(KeyEvent.KEYCODE_A, -1)).thenReturn((short) 0x8051);
        when(translator.hasNormalizedMapping(KeyEvent.KEYCODE_A, -1)).thenReturn(true);
        set("keyboardTranslator", translator);
        down(KeyEvent.KEYCODE_A);
        when(translator.translate(KeyEvent.KEYCODE_A, -1)).thenReturn((short) 0x8041);
        when(translator.hasNormalizedMapping(KeyEvent.KEYCODE_A, -1)).thenReturn(false);
        up(KeyEvent.KEYCODE_A);
        verify(connection).sendKeyboardInput((short) 0x8051, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
        verify(connection).sendKeyboardInput((short) 0x8051, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
        verifyNoMoreInteractions(connection);
    }

    @Test public void ungrabReleasesHeldKeysAndDropsTheirLaterUps() throws Exception {
        down(KeyEvent.KEYCODE_A);
        call("setInputGrabState", new Class<?>[] {boolean.class}, false);
        game.handleKeyUp(event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, 0));
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
    }

    @Test public void teardownReleasesKeysAndAResumedFocusStartsClean() throws Exception {
        down(KeyEvent.KEYCODE_A);
        call("stopConnection", new Class<?>[0]);
        game.onWindowFocusChanged(true);
        up(KeyEvent.KEYCODE_A);
        down(KeyEvent.KEYCODE_A);
        up(KeyEvent.KEYCODE_A);
        verify(connection, times(2)).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_DOWN, (byte) 0, FLAGS);
        verify(connection, times(2)).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
        verifyNoMoreInteractions(connection);
    }

    @Test public void focusLossRejectsNewInputUntilFocusReturns() {
        game.onWindowFocusChanged(false);
        game.handleKeyDown(event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0));
        verifyNoInteractions(connection);
        game.onWindowFocusChanged(true);
        down(KeyEvent.KEYCODE_A);
        up(KeyEvent.KEYCODE_A);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_DOWN, (byte) 0, FLAGS);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
    }

    @Test public void rotationReleasesKeysBeforeReconfiguringTheStreamView() throws Exception {
        set("prefConfig", new PreferenceConfiguration());
        set("notificationOverlayView", mock(TextView.class));
        down(KeyEvent.KEYCODE_A);
        Configuration config = new Configuration(game.getResources().getConfiguration());
        config.orientation = Configuration.ORIENTATION_PORTRAIT;
        game.onConfigurationChanged(config);
        up(KeyEvent.KEYCODE_A);
        verify(connection).sendKeyboardInput((short) 0x8041, KeyboardPacket.KEY_UP, (byte) 0, FLAGS);
    }

    @Test public void evdevPathUsesTheSameEnterAndScreenshotMappings() {
        for (int key : new int[] {KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_SYSRQ, KeyEvent.KEYCODE_SCREENSHOT}) {
            game.keyboardEvent(true, (short) key);
            game.keyboardEvent(false, (short) key);
        }
        for (short wire : new short[] {(short) 0x800D, (short) 0x802C}) {
            verify(connection, times(2)).sendKeyboardInput(wire, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
            verify(connection, times(2)).sendKeyboardInput(wire, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
        }
        verifyNoMoreInteractions(connection);
    }
}
