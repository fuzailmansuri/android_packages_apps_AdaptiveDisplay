//
// SPDX-FileCopyrightText: 2026 The VoltageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package com.voltage.adaptivedisplay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Display;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/**
 * Simple settings surface for the adaptive display controller.
 * Selects a mode (panel native / sRGB natural / Display P3 vivid /
 * adaptive lux-based), and edits the hysteresis thresholds.
 */
public class AdaptiveDisplayActivity extends Activity {

    private static final String ACTION_APPLY =
            "com.voltage.adaptivedisplay.APPLY";

    private static final int[] MODE_IDS = new int[] {
            AdaptiveDisplayService.MODE_NATIVE,
            AdaptiveDisplayService.MODE_NATURAL,
            AdaptiveDisplayService.MODE_VIVID,
            AdaptiveDisplayService.MODE_ADAPTIVE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        final LinearLayout root = (LinearLayout) findViewById(R.id.root);
        final TextView luxView = (TextView) findViewById(R.id.lux);
        final RadioGroup group = (RadioGroup) findViewById(R.id.modes);
        final EditText brightOn = (EditText) findViewById(R.id.bright_on);
        final EditText brightOff = (EditText) findViewById(R.id.bright_off);
        final LinearLayout adaptiveRow =
                (LinearLayout) findViewById(R.id.adaptive_row);

        final int selected = AdaptiveDisplayService.getModeFromPrefs(this);
        for (int i = 0; i < MODE_IDS.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(MODE_IDS[i]);
            rb.setText(MODE_IDS[i] == AdaptiveDisplayService.MODE_ADAPTIVE
                    ? getString(R.string.mode_adaptive)
                    : (MODE_IDS[i] == AdaptiveDisplayService.MODE_NATURAL
                        ? getString(R.string.mode_natural)
                        : (MODE_IDS[i] == AdaptiveDisplayService.MODE_VIVID
                            ? getString(R.string.mode_vivid)
                            : getString(R.string.mode_native))));
            if (selected == MODE_IDS[i]) {
                rb.setChecked(true);
            }
            group.addView(rb);
        }
        group.setOnCheckedChangeListener((g, id) -> {
            SharedPreferences.Editor ed = getSharedPreferences(
                    "adaptive_display", MODE_PRIVATE).edit();
            ed.putInt(AdaptiveDisplayService.MODE_KEY, id);
            ed.apply();
            adaptiveRow.setVisibility(
                    id == AdaptiveDisplayService.MODE_ADAPTIVE
                            ? LinearLayout.VISIBLE : LinearLayout.GONE);
            startAdaptiveIfNeeded();
        });
        adaptiveRow.setVisibility(
                selected == AdaptiveDisplayService.MODE_ADAPTIVE
                        ? LinearLayout.VISIBLE : LinearLayout.GONE);

        SharedPreferences prefs = getSharedPreferences(
                "adaptive_display", MODE_PRIVATE);
        brightOn.setText(String.valueOf(prefs.getFloat(
                AdaptiveDisplayService.BRIGHT_ON_KEY, 5000)));
        brightOff.setText(String.valueOf(prefs.getFloat(
                AdaptiveDisplayService.BRIGHT_OFF_KEY, 3500)));

        brightOn.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveThreshold(brightOn,
                    AdaptiveDisplayService.BRIGHT_ON_KEY, 5000f);
        });
        brightOff.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveThreshold(brightOff,
                    AdaptiveDisplayService.BRIGHT_OFF_KEY, 3500f);
        });

        // Live lux readout for calibration.
        final SensorManager sm = getSystemService(SensorManager.class);
        final Sensor light = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (light != null) {
            sm.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event.values.length > 0) {
                        luxView.setText(getString(
                                R.string.current_lux_fmt, event.values[0]));
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor s, int a) {
                }
            }, light, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void saveThreshold(EditText field, String key, float def) {
        try {
            float value = Float.parseFloat(field.getText().toString());
            getSharedPreferences("adaptive_display", MODE_PRIVATE)
                    .edit()
                    .putFloat(key, value)
                    .apply();
        } catch (NumberFormatException ignore) {
        }
        startAdaptiveIfNeeded();
    }

    private void startAdaptiveIfNeeded() {
        startService(new Intent(this, AdaptiveDisplayService.class));
        sendBroadcast(new Intent(ACTION_APPLY));
    }
}
