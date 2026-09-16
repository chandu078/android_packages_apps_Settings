/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.settings.accessibility;

import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaPlayer;
import android.media.audiofx.HapticGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

/** Music haptics controls. Full mix is generated locally from the selected media session. */
@SearchIndexable
public class MusicHapticsSettings extends DashboardFragment {
    private SwitchPreferenceCompat mEnabled;
    private SwitchPreferenceCompat mGames;
    private ListPreference mIntensity;
    private Preference mSample;
    private AudioManager mAudio;
    private AudioFocusRequest mFocus;
    private MediaPlayer mPlayer;
    private boolean mSupported;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.ACCESSIBILITY_VIBRATION;
    }

    @Override
    protected String getLogTag() {
        return "MusicHapticsSettings";
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.music_haptics_settings;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAudio = requireContext().getSystemService(AudioManager.class);
        mEnabled = findPreference("music_haptics_enabled");
        mGames = findPreference("music_haptics_include_games");
        mIntensity = findPreference("music_haptics_intensity");
        mSample = findPreference("music_haptics_sample");
        mEnabled.setOnPreferenceChangeListener((preference, value) -> {
            boolean enabled = (Boolean) value;
            if (enabled && !mSupported) return false;
            boolean saved = Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.MUSIC_HAPTICS_ENABLED, enabled ? 1 : 0);
            if (saved) {
                if (!enabled) stopSample();
                setControlsEnabled(enabled);
                mEnabled.setEnabled(mSupported || enabled);
            }
            return saved;
        });
        mGames.setOnPreferenceChangeListener((preference, value) -> Settings.Secure.putInt(
                getContentResolver(), Settings.Secure.MUSIC_HAPTICS_INCLUDE_GAMES,
                (Boolean) value ? 1 : 0));
        mIntensity.setOnPreferenceChangeListener((preference, value) -> {
            int intensity;
            try {
                intensity = Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return false;
            }
            if (intensity < 1 || intensity > 3) return false;
            return Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.MUSIC_HAPTICS_INTENSITY, intensity);
        });
        mSample.setOnPreferenceClickListener(preference -> {
            if (mPlayer == null) playSample(); else stopSample();
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ContentResolver resolver = getContentResolver();
        boolean enabled = Settings.Secure.getInt(resolver,
                Settings.Secure.MUSIC_HAPTICS_ENABLED, 0) == 1;
        mEnabled.setChecked(enabled);
        mGames.setChecked(Settings.Secure.getInt(resolver,
                Settings.Secure.MUSIC_HAPTICS_INCLUDE_GAMES, 0) == 1);
        mIntensity.setValue(Integer.toString(Math.max(1, Math.min(3,
                Settings.Secure.getInt(resolver, Settings.Secure.MUSIC_HAPTICS_INTENSITY, 2)))));
        Vibrator vibrator = requireContext().getSystemService(Vibrator.class);
        boolean supported = false;
        try {
            supported = requireContext().getResources().getBoolean(
                    com.android.internal.R.bool.config_supportMusicHaptics)
                    && vibrator != null && vibrator.hasExternalControl()
                    && HapticGenerator.isAvailable()
                    && AudioSystem.getParameters("lineage_music_haptics")
                            .startsWith("lineage_music_haptics=");
        } catch (RuntimeException e) {
            // The audio service can temporarily be unavailable during restart.
        }
        mSupported = supported;
        mEnabled.setEnabled(supported || enabled);
        findPreference("music_haptics_description").setSummary(supported
                ? R.string.music_haptics_description : R.string.music_haptics_unavailable);
        setControlsEnabled(enabled && supported);
    }

    @Override
    public void onPause() {
        stopSample();
        super.onPause();
    }

    private void setControlsEnabled(boolean enabled) {
        mGames.setEnabled(enabled);
        mIntensity.setEnabled(enabled);
        mSample.setEnabled(enabled);
    }

    private void playSample() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        mFocus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0) stopSample();
                }).build();
        if (mAudio.requestAudioFocus(mFocus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stopSample();
            showSampleError();
            return;
        }
        try {
            // Deliberately use a normal media player: this exercises automatic system integration.
            mPlayer = MediaPlayer.create(requireContext(), R.raw.music_haptics_sample, attributes,
                    mAudio.generateAudioSessionId());
            if (mPlayer == null) throw new IllegalStateException("Unable to create sample player");
            mPlayer.setOnCompletionListener(player -> stopSample());
            mPlayer.setOnErrorListener((player, what, extra) -> {
                stopSample();
                showSampleError();
                return true;
            });
            mPlayer.start();
            mSample.setTitle(R.string.music_haptics_stop_sample);
        } catch (RuntimeException e) {
            stopSample();
            showSampleError();
        }
    }

    private void showSampleError() {
        Toast.makeText(requireContext(), R.string.music_haptics_sample_error,
                Toast.LENGTH_SHORT).show();
    }

    private void stopSample() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        if (mFocus != null) {
            mAudio.abandonAudioFocusRequest(mFocus);
            mFocus = null;
        }
        if (mSample != null) mSample.setTitle(R.string.music_haptics_play_sample);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.music_haptics_settings) {
                @Override
                protected boolean isPageSearchEnabled(android.content.Context context) {
                    return context.getResources().getBoolean(
                            com.android.internal.R.bool.config_supportMusicHaptics);
                }
            };
}
