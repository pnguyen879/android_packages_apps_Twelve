/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.viewmodels

import android.app.Application
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class EqualizerViewModel(application: Application) : TwelveViewModel(application) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled = _isEnabled.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets = _presets.asStateFlow()

    private val _currentPreset = MutableStateFlow(-1)
    val currentPreset = _currentPreset.asStateFlow()

    private val _bands = MutableStateFlow<List<Band>>(emptyList())
    val bands = _bands.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength = _virtualizerStrength.asStateFlow()

    data class Band(
        val index: Int,
        val centerFrequency: Int,
        val minLevel: Int,
        val maxLevel: Int,
        val level: Int
    )

    init {
        viewModelScope.launch {
            audioSessionId.filterNotNull().collectLatest { sessionId ->
                if (sessionId == 0) return@collectLatest
                try {
                    equalizer = Equalizer(0, sessionId).apply {
                        enabled = sharedPreferences.getBoolean(PREF_EQ_ENABLED, false)
                        _isEnabled.value = enabled

                        val numBands = numberOfBands.toInt()
                        val (minLevel, maxLevel) = bandLevelRange.let { it[0].toInt() to it[1].toInt() }

                        val presetsList = mutableListOf<String>()
                        for (i in 0 until numberOfPresets) {
                            presetsList.add(getPresetName(i.toShort()))
                        }
                        _presets.value = presetsList

                        val bandsList = mutableListOf<Band>()
                        for (i in 0 until numBands) {
                            val level = if (enabled) {
                                getBandLevel(i.toShort()).toInt()
                            } else {
                                sharedPreferences.getInt("${PREF_BAND_LEVEL_PREFIX}$i", 0)
                            }
                            bandsList.add(
                                Band(
                                    i,
                                    getCenterFreq(i.toShort()) / 1000,
                                    minLevel,
                                    maxLevel,
                                    level
                                )
                            )
                        }
                        _bands.value = bandsList
                        
                        _currentPreset.value = sharedPreferences.getInt(PREF_EQ_PRESET, -1)
                        if (enabled && _currentPreset.value >= 0) {
                            usePreset(_currentPreset.value.toShort())
                        }
                    }

                    bassBoost = BassBoost(0, sessionId).apply {
                        enabled = _isEnabled.value
                        setStrength(sharedPreferences.getInt(PREF_BASS_BOOST_STRENGTH, 0).toShort())
                        _bassBoostStrength.value = roundedStrength.toInt()
                    }

                    virtualizer = Virtualizer(0, sessionId).apply {
                        enabled = _isEnabled.value
                        setStrength(sharedPreferences.getInt(PREF_VIRTUALIZER_STRENGTH, 0).toShort())
                        _virtualizerStrength.value = roundedStrength.toInt()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        equalizer?.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled
        sharedPreferences.edit().putBoolean(PREF_EQ_ENABLED, enabled).apply()
    }

    fun setBandLevel(bandIndex: Int, level: Int) {
        equalizer?.setBandLevel(bandIndex.toShort(), level.toShort())
        _currentPreset.value = -1 // Custom
        val updatedBands = _bands.value.toMutableList()
        if (bandIndex in updatedBands.indices) {
            updatedBands[bandIndex] = updatedBands[bandIndex].copy(level = level)
            _bands.value = updatedBands
        }
        sharedPreferences.edit()
            .putInt(PREF_EQ_PRESET, -1)
            .putInt("${PREF_BAND_LEVEL_PREFIX}$bandIndex", level)
            .apply()
    }

    fun setPreset(presetIndex: Int) {
        if (presetIndex < 0) return
        equalizer?.usePreset(presetIndex.toShort())
        _currentPreset.value = presetIndex
        
        // Update bands
        val updatedBands = _bands.value.map {
            it.copy(level = equalizer?.getBandLevel(it.index.toShort())?.toInt() ?: it.level)
        }
        _bands.value = updatedBands

        val editor = sharedPreferences.edit()
        editor.putInt(PREF_EQ_PRESET, presetIndex)
        updatedBands.forEach {
            editor.putInt("${PREF_BAND_LEVEL_PREFIX}${it.index}", it.level)
        }
        editor.apply()
    }

    fun setBassBoostStrength(strength: Int) {
        bassBoost?.setStrength(strength.toShort())
        _bassBoostStrength.value = strength
        sharedPreferences.edit().putInt(PREF_BASS_BOOST_STRENGTH, strength).apply()
    }

    fun setVirtualizerStrength(strength: Int) {
        virtualizer?.setStrength(strength.toShort())
        _virtualizerStrength.value = strength
        sharedPreferences.edit().putInt(PREF_VIRTUALIZER_STRENGTH, strength).apply()
    }

    override fun onCleared() {
        super.onCleared()
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
    }

    companion object {
        private const val PREF_EQ_ENABLED = "equalizer_enabled"
        private const val PREF_EQ_PRESET = "equalizer_preset"
        private const val PREF_BAND_LEVEL_PREFIX = "equalizer_band_level_"
        private const val PREF_BASS_BOOST_STRENGTH = "bass_boost_strength"
        private const val PREF_VIRTUALIZER_STRENGTH = "virtualizer_strength"
    }
}
