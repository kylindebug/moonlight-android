package com.limelight.binding.input;

import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class KeyboardTranslatorTest {
    @Test public void standardAndKeypadEnterBothHaveACompatibleReturnMapping() {
        KeyboardTranslator translator = new KeyboardTranslator();
        assertEquals((short) 0x800D, translator.translate(KeyEvent.KEYCODE_ENTER, -1));
        assertEquals((short) 0x800D, translator.translate(KeyEvent.KEYCODE_NUMPAD_ENTER, -1));
    }

    @Test public void bothScreenshotAliasesUseWindowsSnapshotButPrintRemainsSeparate() {
        KeyboardTranslator translator = new KeyboardTranslator();
        assertEquals((short) 0x802C, translator.translate(KeyEvent.KEYCODE_SYSRQ, -1));
        assertEquals((short) 0x802C, translator.translate(KeyEvent.KEYCODE_SCREENSHOT, -1));
        assertEquals((short) 0x802A, translator.translate(KeyEvent.KEYCODE_PRINT, -1));
    }

    @Test public void leftAndRightModifierVirtualKeysRemainDistinct() {
        KeyboardTranslator translator = new KeyboardTranslator();
        int[] keys = {KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT};
        int[] codes = {0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0x5B, 0x5C};
        for (int i = 0; i < keys.length; ++i) {
            assertEquals((short) (0x8000 | codes[i]), translator.translate(keys[i], -1));
        }
    }

    @Test public void numericKeypadDigitsAndLockKeysAreUnchanged() {
        KeyboardTranslator translator = new KeyboardTranslator();
        for (int i = 0; i <= 9; ++i) {
            assertEquals((short) (0x8060 + i), translator.translate(KeyEvent.KEYCODE_NUMPAD_0 + i, -1));
        }
        assertEquals((short) 0x8090, translator.translate(KeyEvent.KEYCODE_NUM_LOCK, -1));
        assertEquals((short) 0x8014, translator.translate(KeyEvent.KEYCODE_CAPS_LOCK, -1));
        assertEquals(0, translator.translate(KeyEvent.KEYCODE_UNKNOWN, -1));
    }

    @Test public void removedDevicesNotifyTheKeyReleaseOwner() {
        AtomicInteger removed = new AtomicInteger(-1);
        KeyboardTranslator translator = new KeyboardTranslator(removed::set);
        translator.onInputDeviceRemoved(17);
        assertEquals(17, removed.get());
    }
}
