//
// SPDX-FileCopyrightText: 2026 The VoltageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package com.voltage.adaptivedisplay;

import static android.view.Display.COLOR_MODE_DEFAULT;
import static android.view.Display.COLOR_MODE_SRGB;
import static android.view.Display.COLOR_MODE_DISPLAY_P3;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;

/**
 * Applies color modes to the internal display and runs the "Adaptive"
 * controller: sRGB (natural) indoors, Display P3 (vivid) in bright light,
 * with a hysteresis band to prevent thrashing.
 *
 * Privileged helper for the AdaptiveDisplay app; runs in the system shared
 * UID so the hidden DMS entry point stays reachable on user builds.
 */
public class AdaptiveDisplayService extends Service {

    public static final String TAG = "AdaptiveDisplay";

    public static final String MODE_KEY = "voltage.adaptive_display.mode";
    public static final String BRIGHT_ON_KEY = "voltage.adaptive_display.brightness_on";
    public static final String BRIGHT_OFF_KEY = "voltage.adaptive_display.brightness_off";

    public static final int MODE_NATIVE = 0;
    public static final int MODE_NATURAL = 1;
    public static final int MODE_VIVID = 2;
    public static final int MODE_ADAPTIVE = 3;

    public static final String ACTION_START = "com.voltage.adaptivedisplay.START";
    public static final String ACTION_STOP = "com.voltage.adaptivedisplay.STOP";

    private static final int DEFAULT_BRIGHT_ON = 5000;   // lux to go vivid
    private static final int DEFAULT_BRIGHT_OFF = 3500;  // lux to go natural
    private static final long MIN_SWITCH_INTERVAL_MS = 2000;

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length > 0) {
                onLux(event.values[0]);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private SharedPreferences mPrefs;
    private Handler mWorker;

    private int mAppliedMode;
    private long mLastSwitchMs;
    private float mBrightOn;
    private float mBrightOff;

    @Override
    public void onCreate() {
        super.onCreate();
        mSensorManager = getSystemService(SensorManager.class);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mPrefs = getSharedPreferences("adaptive_display", Context.MODE_PRIVATE);
        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mWorker = new Handler(thread.getLooper());
        mAppliedMode = requestColorModeOr(0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopListening();
            stopSelf();
            return START_NOT_STICKY;
        }
        startListening();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    private void startListening() {
        refreshThresholds();
        if (mLightSensor != null) {
            mSensorManager.registerListener(mSensorListener, mLightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            apply(mPrefs.getInt(MODE_KEY, MODE_ADAPTIVE));
        }
    }

    private void stopListening() {
        if (mLightSensor != null) {
            mSensorManager.unregisterListener(mSensorListener);
        }
    }

    private void refreshThresholds() {
        mBrightOn = mPrefs.getFloat(BRIGHT_ON_KEY, DEFAULT_BRIGHT_ON);
        mBrightOff = mPrefs.getFloat(BRIGHT_OFF_KEY, DEFAULT_BRIGHT_OFF);
    }

    public static int getModeFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                "adaptive_display", Context.MODE_PRIVATE);
        return prefs.getInt(MODE_KEY, MODE_ADAPTIVE);
    }

    private void onLux(float lux) {
        long now = System.currentTimeMillis();
        if (now - mLastSwitchMs < MIN_SWITCH_INTERVAL_MS) {
            return;
        }
        int mode;
        if (lux >= mBrightOn) {
            mLastSwitchMs = now;
            mode = COLOR_MODE_DISPLAY_P3;
        } else if (lux <= mBrightOff) {
            mLastSwitchMs = now;
            mode = COLOR_MODE_SRGB;
        } else {
            return;
        }
        mWorker.post(() -> apply(mode));
    }

    private void apply(int mode) {
        mAppliedMode = requestColorModeOr(mode);
    }

    private int requestColorModeOr(int mode) {
        final int id = Display.DEFAULT_DISPLAY;
        // Platform-signed callers reach the hidden DMS entry point directly.
        try {
            DisplayManagerGlobal.getInstance().requestColorMode(id, mode);
        } catch (Exception e) {
            Log.e(TAG, "requestColorMode failed", e);
            return mAppliedMode;
        }
        return mode;
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                context.startService(new Intent(context, AdaptiveDisplayService.class)
                        .setAction(ACTION_START));
            }
        }
    }
}
