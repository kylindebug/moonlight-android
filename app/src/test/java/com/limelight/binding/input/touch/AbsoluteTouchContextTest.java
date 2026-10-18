package com.limelight.binding.input.touch;

import android.os.Looper;
import android.view.View;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import java.time.Duration;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class AbsoluteTouchContextTest {
    private NvConnection conn;
    private AbsoluteTouchContext touch;
    private static final byte LEFT = MouseButtonPacket.BUTTON_LEFT;
    private static final byte RIGHT = MouseButtonPacket.BUTTON_RIGHT;

    @Before public void setUp() {
        conn = mock(NvConnection.class);
        View view = new View(RuntimeEnvironment.getApplication());
        view.layout(0, 0, 1920, 1080);
        touch = new AbsoluteTouchContext(conn, 0, view);
    }
    private void waitMs(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }
    @Test public void longPressCompletesRightClickWithoutAnyLeftClickWhileFingerHeld() {
        touch.touchDownEvent(350, 1050, 1000, true);
        waitMs(649);
        verifyNoInteractions(conn);
        waitMs(51);
        InOrder order = inOrder(conn);
        order.verify(conn).sendMousePosition((short)350, (short)1050, (short)1920, (short)1080);
        order.verify(conn).sendMouseButtonDown(RIGHT);
        order.verify(conn).sendMouseButtonUp(RIGHT);
        touch.touchUpEvent(350, 1050, 1800);
        waitMs(500);
        verify(conn, never()).sendMouseButtonDown(LEFT);
        verify(conn, times(1)).sendMouseButtonUp(RIGHT);
    }
    @Test public void shortTapClicksLeftOnlyOnRelease() {
        touch.touchDownEvent(100, 100, 1000, true);
        waitMs(200);
        verifyNoInteractions(conn);
        touch.touchUpEvent(100, 100, 1200);
        waitMs(100);
        verify(conn).sendMouseButtonDown(LEFT);
        verify(conn).sendMouseButtonUp(LEFT);
        verify(conn, never()).sendMouseButtonDown(RIGHT);
    }
    @Test public void movementBeginsDragAndNeverChangesIntoRightClick() {
        touch.touchDownEvent(100, 100, 1000, true);
        touch.touchMoveEvent(250, 200, 1100);
        waitMs(1000);
        touch.touchUpEvent(350, 200, 2100);
        verify(conn).sendMouseButtonDown(LEFT);
        verify(conn).sendMouseButtonUp(LEFT);
        verify(conn, never()).sendMouseButtonDown(RIGHT);
    }
    @Test public void smallFingerJitterStillProducesOnlyRightClick() {
        touch.touchDownEvent(100, 100, 1000, true);
        touch.touchMoveEvent(101, 101, 1100);
        waitMs(700);
        verify(conn).sendMouseButtonDown(RIGHT);
        verify(conn, never()).sendMouseButtonDown(LEFT);
    }
    @Test public void cancelledTouchCannotFireDelayedClicks() {
        touch.touchDownEvent(100, 100, 1000, true);
        waitMs(300);
        touch.cancelTouch();
        waitMs(1000);
        verifyNoInteractions(conn);
    }
    @Test public void secondFingerCancelsPendingLongPress() {
        touch.touchDownEvent(100, 100, 1000, true);
        touch.setPointerCount(2);
        waitMs(1000);
        verifyNoInteractions(conn);
    }
    @Test public void cancellationReleasesRightButtonExactlyOnce() {
        touch.touchDownEvent(100, 100, 1000, true);
        waitMs(650);
        touch.cancelTouch();
        waitMs(100);
        verify(conn, times(1)).sendMouseButtonDown(RIGHT);
        verify(conn, times(1)).sendMouseButtonUp(RIGHT);
    }
    @Test public void secondTapReleasesPendingLeftUpBeforePressingAgain() {
        touch.touchDownEvent(100, 100, 1000, true);
        touch.touchUpEvent(100, 100, 1030);
        touch.touchDownEvent(100, 100, 1070, true);
        touch.touchUpEvent(100, 100, 1100);
        waitMs(100);
        InOrder order = inOrder(conn);
        order.verify(conn).sendMouseButtonDown(LEFT);
        order.verify(conn).sendMouseButtonUp(LEFT);
        order.verify(conn).sendMouseButtonDown(LEFT);
        order.verify(conn).sendMouseButtonUp(LEFT);
        verify(conn, never()).sendMouseButtonDown(RIGHT);
    }
}
