/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.accessibility;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/** Device opt-in controls visibility; the page reports runtime audio support separately. */
public class MusicHapticsPreferenceController extends BasePreferenceController {
    public MusicHapticsPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportMusicHaptics)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.MUSIC_HAPTICS_ENABLED, 0) == 1
                ? R.string.music_haptics_on : R.string.music_haptics_off);
    }
}
