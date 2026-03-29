/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.twelve.fragments

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.twelve.R
import org.lineageos.twelve.ext.getViewProperty
import org.lineageos.twelve.viewmodels.EqualizerViewModel

class EqualizerBottomSheetDialogFragment : TwelveBottomSheetDialogFragment(
    R.layout.fragment_equalizer_bottom_sheet_dialog
) {
    // View models
    private val viewModel by activityViewModels<EqualizerViewModel>()

    // Views
    private val equalizerMasterSwitch by getViewProperty<MaterialSwitch>(R.id.equalizerMasterSwitch)
    private val presetsAutoCompleteTextView by getViewProperty<AutoCompleteTextView>(R.id.presetsAutoCompleteTextView)
    private val bandsLinearLayout by getViewProperty<LinearLayout>(R.id.bandsLinearLayout)
    private val bassBoostSlider by getViewProperty<Slider>(R.id.bassBoostSlider)
    private val virtualizerSlider by getViewProperty<Slider>(R.id.virtualizerSlider)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        equalizerMasterSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(isChecked)
        }

        presetsAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            viewModel.setPreset(position)
        }

        bassBoostSlider.addOnChangeListener { _, value, _ ->
            viewModel.setBassBoostStrength(value.toInt())
        }

        virtualizerSlider.addOnChangeListener { _, value, _ ->
            viewModel.setVirtualizerStrength(value.toInt())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isEnabled.collectLatest { isEnabled ->
                        equalizerMasterSwitch.isChecked = isEnabled
                        presetsAutoCompleteTextView.isEnabled = isEnabled
                        bandsLinearLayout.isEnabled = isEnabled
                        bassBoostSlider.isEnabled = isEnabled
                        virtualizerSlider.isEnabled = isEnabled
                        
                        // Recursively enable/disable children of bandsLinearLayout
                        for (i in 0 until bandsLinearLayout.childCount) {
                            val child = bandsLinearLayout.getChildAt(i)
                            child.isEnabled = isEnabled
                            if (child is ViewGroup) {
                                for (j in 0 until child.childCount) {
                                    child.getChildAt(j).isEnabled = isEnabled
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.presets.collectLatest { presets ->
                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            presets
                        )
                        presetsAutoCompleteTextView.setAdapter(adapter)
                    }
                }

                launch {
                    viewModel.currentPreset.collectLatest { presetIndex ->
                        if (presetIndex >= 0) {
                            val presets = viewModel.presets.value
                            if (presetIndex < presets.size) {
                                presetsAutoCompleteTextView.setText(presets[presetIndex], false)
                            }
                        } else {
                            presetsAutoCompleteTextView.setText(getString(R.string.preset_custom), false)
                        }
                    }
                }

                launch {
                    viewModel.bands.collectLatest { bands ->
                        if (bands.isNotEmpty()) {
                            if (bandsLinearLayout.childCount == 0) {
                                setupBands(bands)
                            } else {
                                updateBands(bands)
                            }
                        }
                    }
                }

                launch {
                    viewModel.bassBoostStrength.collectLatest { strength ->
                        bassBoostSlider.value = strength.toFloat()
                    }
                }

                launch {
                    viewModel.virtualizerStrength.collectLatest { strength ->
                        virtualizerSlider.value = strength.toFloat()
                    }
                }
            }
        }
    }

    private fun setupBands(bands: List<EqualizerViewModel.Band>) {
        bandsLinearLayout.removeAllViews()
        for (band in bands) {
            val bandView = layoutInflater.inflate(R.layout.equalizer_band, bandsLinearLayout, false)
            val slider = bandView.findViewById<Slider>(R.id.bandSlider)
            val label = bandView.findViewById<TextView>(R.id.bandLabel)

            slider.valueFrom = band.minLevel.toFloat()
            slider.valueTo = band.maxLevel.toFloat()
            slider.value = band.level.toFloat()
            
            label.text = if (band.centerFrequency < 1000) {
                "${band.centerFrequency} Hz"
            } else {
                "${band.centerFrequency / 1000} kHz"
            }

            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    viewModel.setBandLevel(band.index, value.toInt())
                }
            }

            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            bandsLinearLayout.addView(bandView, params)
        }
    }

    private fun updateBands(bands: List<EqualizerViewModel.Band>) {
        for (i in 0 until bandsLinearLayout.childCount) {
            val bandView = bandsLinearLayout.getChildAt(i)
            val slider = bandView.findViewById<Slider>(R.id.bandSlider)
            if (i < bands.size) {
                slider.value = bands[i].level.toFloat()
            }
        }
    }
}
