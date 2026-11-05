/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wallpaper.customization.ui.binder

import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.Toolbar
import androidx.core.animation.Animator
import androidx.core.animation.ValueAnimator
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.themepicker.R as ThemePickerR
import com.android.wallpaper.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.APP_ICONS
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.COLORS
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.GRID
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.ICON_PACKS
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption.CLOCK
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption.SHORTCUTS
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder
import com.android.wallpaper.picker.customization.ui.binder.DefaultToolbarBinder
import com.android.wallpaper.picker.customization.ui.binder.ToolbarBinder
import com.android.wallpaper.picker.customization.ui.view.ApplyButton
import com.android.wallpaper.picker.customization.ui.view.ApplyButton.ApplyButtonState.APPLY_BUTTON_DISABLED
import com.android.wallpaper.picker.customization.ui.view.ApplyButton.ApplyButtonState.APPLY_BUTTON_ENABLED
import com.android.wallpaper.picker.customization.ui.view.ApplyButton.ApplyButtonState.APPLY_BUTTON_IN_PROGRESS
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationOptionsViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Singleton
class ThemePickerToolbarBinder
@Inject
constructor(
    private val defaultToolbarBinder: DefaultToolbarBinder,
    private val baseFlags: BaseFlags,
) : ToolbarBinder {

    override fun bind(
        toolbarContainer: LinearLayout,
        viewModel: CustomizationOptionsViewModel,
        colorUpdateViewModel: ColorUpdateViewModel,
        lifecycleOwner: LifecycleOwner,
        onNavBack: () -> Unit,
    ) {
        defaultToolbarBinder.bind(
            toolbarContainer,
            viewModel,
            colorUpdateViewModel,
            lifecycleOwner,
            onNavBack,
        )

        val toolbar: Toolbar = toolbarContainer.requireViewById(R.id.toolbar)
        val applyButton: ApplyButton = toolbarContainer.requireViewById(R.id.apply_button)

        if (viewModel !is ThemePickerCustomizationOptionsViewModel) {
            throw IllegalArgumentException(
                "viewModel $viewModel is not a ThemePickerCustomizationOptionsViewModel."
            )
        }

        ColorUpdateBinder.bind(
            setColor = { color -> applyButton.setApplyButtonBackgroundColor(color) },
            color = colorUpdateViewModel.colorPrimary,
            shouldAnimate = { true },
            lifecycleOwner = lifecycleOwner,
        )

        ColorUpdateBinder.bind(
            setColor = { color ->
                applyButton.setApplyButtonTextColor(color)
                applyButton.setIndicatorColor(color)
            },
            color =
                combine(
                    viewModel.applyButtonState,
                    colorUpdateViewModel.colorOnPrimary,
                    colorUpdateViewModel.colorOnSurface,
                ) { state, onPrimary, onSurface ->
                    when (state) {
                        APPLY_BUTTON_ENABLED -> onPrimary
                        APPLY_BUTTON_DISABLED ->
                            ColorUtils.setAlphaComponent(onSurface, 97) // 97 for 38% transparent
                        APPLY_BUTTON_IN_PROGRESS -> onPrimary
                    }
                },
            shouldAnimate = { false },
            lifecycleOwner = lifecycleOwner,
        )

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.onApplyButtonClicked.collect { onApplyButtonClicked ->
                        applyButton.setOnClickListener { onApplyButtonClicked?.invoke(onNavBack) }
                    }
                }

                launch { viewModel.isApplyButtonVisible.collect { applyButton.isInvisible = !it } }

                launch {
                    viewModel.applyButtonState.collect { applyButton.setApplyButtonState(it) }
                }

                launch {
                    viewModel.selectedOption.collect {
                        val stringResId =
                            when (it) {
                                COLORS -> ThemePickerR.string.system_colors_title
                                GRID -> ThemePickerR.string.grid_layout
                                CLOCK -> ThemePickerR.string.clock_title
                                SHORTCUTS ->
                                    ThemePickerR.string.keyguard_quick_affordance_section_title
                                APP_ICONS -> ThemePickerR.string.app_icons_title
                                ICON_PACKS -> ThemePickerR.string.icon_pack_title
                                else -> R.string.app_name
                            }
                        toolbar.title = toolbar.resources.getString(stringResId)
                    }
                }

                if (baseFlags.isColorPickerUpdateEnabled()) {
                    launch {
                        var valueAnimator: ValueAnimator? = null
                        var isInitialValue = true
                        viewModel.shouldShowToolbar.collect { shouldShowToolbar ->
                            toolbarContainer.doOnLayout {
                                valueAnimator?.cancel()
                                val initialHeight = toolbarContainer.measuredHeight
                                val targetHeight =
                                    if (shouldShowToolbar) {
                                        toolbarContainer.measure(
                                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                                        )
                                        toolbarContainer.measuredHeight
                                    } else {
                                        0
                                    }
                                val setFinalHeight = {
                                    if (shouldShowToolbar) {
                                        toolbarContainer.layoutParams.height =
                                            LayoutParams.WRAP_CONTENT
                                    } else {
                                        toolbarContainer.layoutParams.height = 0
                                    }
                                }

                                // Skip animation for the first emitted value to avoid jank on start
                                if (isInitialValue) {
                                    setFinalHeight()
                                    isInitialValue = false
                                } else if (initialHeight != targetHeight) {
                                    valueAnimator =
                                        ValueAnimator.ofInt(initialHeight, targetHeight).apply {
                                            addUpdateListener { animation ->
                                                toolbarContainer.layoutParams.height =
                                                    (animation as ValueAnimator).animatedValue
                                                        as Int
                                            }
                                            addListener(
                                                object : Animator.AnimatorListener {
                                                    override fun onAnimationStart(
                                                        animation: Animator
                                                    ) {}

                                                    override fun onAnimationEnd(
                                                        animation: Animator
                                                    ) {
                                                        setFinalHeight()
                                                    }

                                                    override fun onAnimationCancel(
                                                        animation: Animator
                                                    ) {
                                                        setFinalHeight()
                                                    }

                                                    override fun onAnimationRepeat(
                                                        animation: Animator
                                                    ) {}
                                                }
                                            )
                                            duration = ANIMATION_DURATION
                                            start()
                                        }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ANIMATION_DURATION = 200L
    }
}
