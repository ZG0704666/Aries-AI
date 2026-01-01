package com.ai.assistance.shower;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.util.Log;

import java.lang.reflect.Method;

class InputController {

    private static final String TAG = "ShowerInput";

    private final Object inputManager;
    private final Method injectInputEventMethod;
    private final Method setDisplayIdMethod;
    private int displayId;
    private boolean touchActive;
    private long touchDownTime;

    InputController() {
        try {
            Class<?> clazz = Class.forName("android.hardware.input.InputManager");
            Method getInstance = clazz.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            inputManager = getInstance.invoke(null);

            injectInputEventMethod = clazz.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
            injectInputEventMethod.setAccessible(true);

            Method m;
            try {
                m = InputEvent.class.getMethod("setDisplayId", int.class);
            } catch (NoSuchMethodException e) {
                m = null;
            }
            setDisplayIdMethod = m;
            displayId = 0;
            Main.logToFile("InputController initialized, setDisplayIdMethod=" + (setDisplayIdMethod != null), null);
        } catch (Exception e) {
            throw new RuntimeException("Init InputController failed", e);
        }
    }

    void setDisplayId(int displayId) {
        this.displayId = displayId;
        Main.logToFile("InputController.setDisplayId: " + displayId, null);
    }

    private void inject(InputEvent event) {
        try {
            int currentDisplayId = displayId;
            if (setDisplayIdMethod != null && currentDisplayId != 0) {
                try {
                    setDisplayIdMethod.invoke(event, currentDisplayId);
                } catch (Exception e) {
                    Log.e(TAG, "setDisplayId failed", e);
                    Main.logToFile("InputController.setDisplayId failed: " + e.getMessage(), e);
                }
            }

            boolean ok;
            try {
                ok = (Boolean) injectInputEventMethod.invoke(inputManager, event, 0);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SecurityException) {
                    String msg = cause.getMessage();
                    Log.e(TAG, "inject SecurityException: " + msg, cause);
                    Main.logToFile("InputController.inject SecurityException: " + msg, cause);
                } else {
                    Log.e(TAG, "inject InvocationTargetException", e);
                    Main.logToFile("InputController.inject InvocationTargetException: " + e.getMessage(), e);
                }
                return;
            }

            if (!ok) {
                Log.e(TAG, "inject returned false");
                Main.logToFile("InputController.inject returned false for event=" + event, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "inject failed", e);
            Main.logToFile("InputController.inject failed: " + e.getMessage(), e);
        }
    }

    void injectKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        down.setSource(InputDevice.SOURCE_KEYBOARD);
        inject(down);

        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        up.setSource(InputDevice.SOURCE_KEYBOARD);
        inject(up);
    }

    void injectTap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(down);

        long upTime = SystemClock.uptimeMillis();
        MotionEvent up = MotionEvent.obtain(
                now,
                upTime,
                MotionEvent.ACTION_UP,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(up);
    }

    void injectSwipe(float x1, float y1, float x2, float y2, long durationMs) {
        long start = SystemClock.uptimeMillis();
        long end = start + durationMs;
        int steps = 10;
        if (steps < 1) {
            steps = 1;
        }

        // down
        MotionEvent down = MotionEvent.obtain(
                start,
                start,
                MotionEvent.ACTION_DOWN,
                x1,
                y1,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(down);

        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x1 + (x2 - x1) * t;
            float y = y1 + (y2 - y1) * t;
            long now = start + (long) ((end - start) * t);

            MotionEvent move = MotionEvent.obtain(
                    start,
                    now,
                    MotionEvent.ACTION_MOVE,
                    x,
                    y,
                    1.0f,
                    1.0f,
                    0,
                    1.0f,
                    1.0f,
                    0,
                    0
            );
            move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            inject(move);
        }

        long upTime = end;
        MotionEvent up = MotionEvent.obtain(
                start,
                upTime,
                MotionEvent.ACTION_UP,
                x2,
                y2,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(up);
    }

    void touchDown(float x, float y) {
        long now = SystemClock.uptimeMillis();
        touchDownTime = now;
        touchActive = true;
        MotionEvent down = MotionEvent.obtain(
                touchDownTime,
                now,
                MotionEvent.ACTION_DOWN,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(down);
    }

    void touchMove(float x, float y) {
        if (!touchActive) {
            // If move is received without a prior down, start a new sequence.
            touchDown(x, y);
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent move = MotionEvent.obtain(
                touchDownTime,
                now,
                MotionEvent.ACTION_MOVE,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(move);
    }

    void touchUp(float x, float y) {
        if (!touchActive) {
            // No active touch: fall back to a simple tap at this position.
            injectTap(x, y);
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent up = MotionEvent.obtain(
                touchDownTime,
                now,
                MotionEvent.ACTION_UP,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
        );
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inject(up);
        touchActive = false;
    }
}
