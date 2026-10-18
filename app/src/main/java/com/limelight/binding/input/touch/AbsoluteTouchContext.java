package com.limelight.binding.input.touch;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewConfiguration;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;

public class AbsoluteTouchContext implements TouchContext {
    private int lastTouchDownX = 0;
    private int lastTouchDownY = 0;
    private long lastTouchDownTime = 0;
    private int lastTouchUpX = 0;
    private int lastTouchUpY = 0;
    private long lastTouchUpTime = 0;
    private int lastTouchLocationX = 0;
    private int lastTouchLocationY = 0;
    private boolean cancelled;
    private boolean confirmedLongPress;
    private boolean confirmedTap;
    private boolean leftButtonDown;
    private boolean rightButtonDown;
    private final int touchSlop;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (cancelled || confirmedTap || confirmedLongPress) {
                return;
            }
            // A desktop long press is exclusively a right click. Sending a left
            // click first can activate a taskbar window and change the target.
            confirmedLongPress = true;
            updatePosition(lastTouchDownX, lastTouchDownY);
            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
            rightButtonDown = true;
            // Complete the click while the finger is still held, so Windows can
            // show its context menu without waiting for the finger to lift.
            handler.postDelayed(rightButtonUpRunnable, 50);
        }
    };

    private final NvConnection conn;
    private final int actionIndex;
    private final View targetView;
    private final Handler handler;

    private final Runnable leftButtonUpRunnable = new Runnable() {
        @Override
        public void run() {
            releaseLeftButton();
        }
    };
    private final Runnable rightButtonUpRunnable = new Runnable() {
        @Override
        public void run() {
            releaseRightButton();
        }
    };

    private static final int SCROLL_SPEED_FACTOR = 3;

    private static final int LONG_PRESS_TIME_THRESHOLD = 650;

    private static final int DOUBLE_TAP_TIME_THRESHOLD = 250;
    private static final int DOUBLE_TAP_DISTANCE_THRESHOLD = 60;

    public AbsoluteTouchContext(NvConnection conn, int actionIndex, View view)
    {
        this.conn = conn;
        this.actionIndex = actionIndex;
        this.targetView = view;
        this.handler = new Handler(Looper.getMainLooper());
        this.touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    @Override
    public int getActionIndex()
    {
        return actionIndex;
    }

    @Override
    public boolean touchDownEvent(int eventX, int eventY, long eventTime, boolean isNewFinger)
    {
        if (!isNewFinger) {
            // We don't handle finger transitions for absolute mode
            return true;
        }

        if (actionIndex == 0) {
            handler.removeCallbacks(leftButtonUpRunnable);
            handler.removeCallbacks(rightButtonUpRunnable);
            releaseLeftButton();
            releaseRightButton();
        }

        lastTouchLocationX = lastTouchDownX = eventX;
        lastTouchLocationY = lastTouchDownY = eventY;
        lastTouchDownTime = eventTime;
        cancelled = confirmedTap = confirmedLongPress = false;

        if (actionIndex == 0) {
            // Start the timers
            startLongPressTimer();
        }

        return true;
    }

    private boolean distanceExceeds(int deltaX, int deltaY, double limit) {
        return Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2)) > limit;
    }

    private void updatePosition(int eventX, int eventY) {
        // We may get values slightly outside our view region on ACTION_HOVER_ENTER and ACTION_HOVER_EXIT.
        // Normalize these to the view size. We can't just drop them because we won't always get an event
        // right at the boundary of the view, so dropping them would result in our cursor never really
        // reaching the sides of the screen.
        eventX = Math.min(Math.max(eventX, 0), targetView.getWidth());
        eventY = Math.min(Math.max(eventY, 0), targetView.getHeight());

        conn.sendMousePosition((short)eventX, (short)eventY, (short)targetView.getWidth(), (short)targetView.getHeight());
    }

    @Override
    public void touchUpEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return;
        }

        if (actionIndex == 0) {
            // Cancel the timers
            cancelLongPressTimer();

            // Raise the mouse buttons that we currently have down
            if (confirmedLongPress) {
                handler.removeCallbacks(rightButtonUpRunnable);
                releaseRightButton();
            }
            else if (confirmedTap) {
                releaseLeftButton();
            }
            else {
                // A stationary short tap becomes a left click only on release.
                tapConfirmed();

                // Release the left mouse button in 100ms to allow for apps that use polling
                // to detect mouse button presses.
                handler.removeCallbacks(leftButtonUpRunnable);
                handler.postDelayed(leftButtonUpRunnable, 100);
            }
        }

        lastTouchLocationX = lastTouchUpX = eventX;
        lastTouchLocationY = lastTouchUpY = eventY;
        lastTouchUpTime = eventTime;
    }

    private void startLongPressTimer() {
        cancelLongPressTimer();
        handler.postDelayed(longPressRunnable, LONG_PRESS_TIME_THRESHOLD);
    }

    private void cancelLongPressTimer() {
        handler.removeCallbacks(longPressRunnable);
    }

    private void releaseLeftButton() {
        if (leftButtonDown) {
            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
            leftButtonDown = false;
        }
    }

    private void releaseRightButton() {
        if (rightButtonDown) {
            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
            rightButtonDown = false;
        }
    }

    private void tapConfirmed() {
        if (confirmedTap || confirmedLongPress) {
            return;
        }

        confirmedTap = true;

        // Left button down at original position
        if (lastTouchDownTime - lastTouchUpTime > DOUBLE_TAP_TIME_THRESHOLD ||
                distanceExceeds(lastTouchDownX - lastTouchUpX, lastTouchDownY - lastTouchUpY, DOUBLE_TAP_DISTANCE_THRESHOLD)) {
            // Don't reposition for finger down events within the deadzone. This makes double-clicking easier.
            updatePosition(lastTouchDownX, lastTouchDownY);
        }
        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        leftButtonDown = true;
    }

    @Override
    public boolean touchMoveEvent(int eventX, int eventY, long eventTime)
    {
        if (cancelled) {
            return true;
        }

        if (actionIndex == 0) {
            if (confirmedLongPress) {
                return true;
            }
            // Movement beyond Android's density-aware slop starts a left drag.
            // Once dragging starts, it must never turn into a right click.
            if (confirmedTap || distanceExceeds(eventX - lastTouchDownX, eventY - lastTouchDownY, touchSlop)) {
                cancelLongPressTimer();
                tapConfirmed();
                updatePosition(eventX, eventY);
            }
        }
        else if (actionIndex == 1) {
            conn.sendMouseHighResScroll((short)((eventY - lastTouchLocationY) * SCROLL_SPEED_FACTOR));
        }

        lastTouchLocationX = eventX;
        lastTouchLocationY = eventY;

        return true;
    }

    @Override
    public void cancelTouch() {
        cancelled = true;

        // Cancel the timers
        cancelLongPressTimer();
        handler.removeCallbacks(leftButtonUpRunnable);
        handler.removeCallbacks(rightButtonUpRunnable);

        // Raise the mouse buttons
        releaseRightButton();
        releaseLeftButton();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setPointerCount(int pointerCount) {
        if (actionIndex == 0 && pointerCount > 1) {
            cancelTouch();
        }
    }
}
