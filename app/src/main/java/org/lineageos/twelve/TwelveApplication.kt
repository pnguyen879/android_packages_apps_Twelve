/*
 * SPDX-FileCopyrightText: 2024-2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.MainScope
import org.lineageos.twelve.database.TwelveDatabase
import org.lineageos.twelve.repositories.MediaRepository
import org.lineageos.twelve.repositories.OutputConfigurationRepository
import org.lineageos.twelve.repositories.ProvidersRepository
import org.lineageos.twelve.repositories.ResumptionPlaylistRepository
import org.lineageos.twelve.ui.coil.ThumbnailMapper

@androidx.annotation.OptIn(UnstableApi::class)
class TwelveApplication : Application(), SingletonImageLoader.Factory {
    private val coroutineScope = MainScope()
    private val database by lazy { TwelveDatabase.get(applicationContext) }
    val providersRepository by lazy {
        ProvidersRepository(applicationContext, coroutineScope, database)
    }
    val mediaRepository by lazy {
        MediaRepository(applicationContext, coroutineScope, providersRepository, database)
    }
    val resumptionPlaylistRepository by lazy { ResumptionPlaylistRepository(database) }
    val outputConfigurationRepository by lazy { OutputConfigurationRepository() }

    override fun onCreate() {
        super.onCreate()

        // Observe dynamic colors changes
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Set default values for preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        if (!sharedPreferences.contains(PREF_FIRST_RUN)) {
            sharedPreferences.edit()
                .putBoolean(PREF_FIRST_RUN, false)
                .putBoolean(PREF_EQ_ENABLED, true)
                .putInt(PREF_EQ_PRESET, 0) // Assuming 0 is the "Normal" preset
                .apply()
        }
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(this)
        .components {
            add(ThumbnailMapper)
        }
        .build()

    companion object {
        private const val PREF_FIRST_RUN = "first_run"
        private const val PREF_EQ_ENABLED = "equalizer_enabled"
        private const val PREF_EQ_PRESET = "equalizer_preset"
    }
}
