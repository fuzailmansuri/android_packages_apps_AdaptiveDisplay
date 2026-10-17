//
// SPDX-FileCopyrightText: 2026 The VoltageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package com.voltage.adaptivedisplay;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class AdaptiveDisplayTile extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        int mode = AdaptiveDisplayService.getModeFromPrefs(this);
        boolean active = mode == AdaptiveDisplayService.MODE_ADAPTIVE;
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(active ? getString(R.string.tile_active)
                : getString(AdaptiveDisplayService.MODE_NATURAL == mode
                        ? R.string.mode_natural
                        : AdaptiveDisplayService.MODE_VIVID == mode
                                ? R.string.mode_vivid
                                : R.string.mode_native));
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        int mode = AdaptiveDisplayService.getModeFromPrefs(this);
        int next = mode == AdaptiveDisplayService.MODE_ADAPTIVE
                ? AdaptiveDisplayService.MODE_NATURAL
                : AdaptiveDisplayService.MODE_ADAPTIVE;
        getSharedPreferences("adaptive_display", MODE_PRIVATE)
                .edit()
                .putInt(AdaptiveDisplayService.MODE_KEY, next)
                .apply();
        startService(new Intent(this, AdaptiveDisplayService.class));
        onStartListening();
    }
}
