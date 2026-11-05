/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.themes.ui.binder

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.common.ui.view.SingleRowListItemSpacing
import com.android.customization.picker.themes.shared.model.ThemePackModel
import com.android.customization.picker.themes.shared.model.ThemePackPreviewModel
import com.android.themepicker.R
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.STYLES
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.option.ui.adapter.OptionItemAdapter2
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import lineageos.content.pm.ThemeUtils

object StyleFloatingSheetBinder {

    fun bind(
        view: View,
        optionsViewModel: ThemePickerCustomizationOptionsViewModel,
        colorUpdateViewModel: ColorUpdateViewModel,
        lifecycleOwner: LifecycleOwner,
        backgroundDispatcher: CoroutineDispatcher,
    ) {
        val viewModel = optionsViewModel.stylePickerViewModel
        val isFloatingSheetActive = { optionsViewModel.selectedOption.value == STYLES }

        val floatingSheetContainer =
            view.requireViewById<ViewGroup>(R.id.floating_sheet_content_container)
        ColorUpdateBinder.bind(
            setColor = { color ->
                DrawableCompat.setTint(
                    DrawableCompat.wrap(floatingSheetContainer.background),
                    color,
                )
            },
            color = colorUpdateViewModel.colorSurfaceBright,
            shouldAnimate = isFloatingSheetActive,
            lifecycleOwner = lifecycleOwner,
        )

        val wallpaper = view.requireViewById<ImageView>(R.id.style_preview_wallpaper)
        val lockWallpaper =
            view.requireViewById<ImageView>(R.id.style_preview_lock_wallpaper)
        val icons = view.requireViewById<LinearLayout>(R.id.style_preview_icons)
        val description = view.requireViewById<TextView>(R.id.style_preview_description)

        val optionListAdapter =
            createOptionItemAdapter(
                colorUpdateViewModel = colorUpdateViewModel,
                shouldAnimateColor = isFloatingSheetActive,
                lifecycleOwner = lifecycleOwner,
                backgroundDispatcher = backgroundDispatcher,
            )
        val optionList =
            view.requireViewById<RecyclerView>(R.id.style_options).also {
                it.initOptionList(view.context, optionListAdapter)
            }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.themePackOptionListItems.collect { options ->
                        optionListAdapter.setItems(options) {
                            val indexToFocus =
                                options.indexOfFirst { it.isSelected.value }.coerceAtLeast(0)
                            (optionList.layoutManager as LinearLayoutManager).scrollToPosition(
                                indexToFocus
                            )
                        }
                    }
                }

                launch {
                    viewModel.previewingThemePackPreview.collect { (themePack, preview) ->
                        bindPreview(
                            themePack,
                            preview,
                            wallpaper,
                            lockWallpaper,
                            icons,
                            description,
                        )
                    }
                }
            }
        }
    }

    private fun bindPreview(
        themePack: ThemePackModel?,
        preview: ThemePackPreviewModel?,
        wallpaper: ImageView,
        lockWallpaper: ImageView,
        icons: LinearLayout,
        description: TextView,
    ) {
        val context = description.context
        wallpaper.setImageBitmap(preview?.wallpaper)
        lockWallpaper.setImageBitmap(preview?.lockWallpaper)

        icons.removeAllViews()
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.theme_preview_icon_size)
        val iconSpacing = context.resources.getDimensionPixelSize(R.dimen.theme_preview_spacing)
        preview?.icons?.forEach { icon ->
            icons.addView(
                ImageView(context).apply { setImageDrawable(icon) },
                LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginStart = iconSpacing / 2
                    marginEnd = iconSpacing / 2
                },
            )
        }

        description.text =
            if (themePack == null) {
                context.getString(R.string.styles_default_description)
            } else {
                val name =
                    themePack.author?.let {
                        context.getString(R.string.styles_name_author, themePack.name, it)
                    } ?: themePack.name
                val components =
                    themePack.components.joinToString { context.getString(componentLabel(it)) }
                context.getString(R.string.styles_preview_description, name, components)
            }
    }

    @StringRes
    private fun componentLabel(component: String): Int =
        when (component) {
            ThemeUtils.MODIFIES_STATUS_BAR -> R.string.theme_component_status_bar
            ThemeUtils.MODIFIES_NAVIGATION_BAR -> R.string.theme_component_navigation_bar
            ThemeUtils.MODIFIES_ICONS -> R.string.theme_component_icons
            ThemeUtils.MODIFIES_FONTS -> R.string.theme_component_fonts
            ThemeUtils.MODIFIES_LAUNCHER -> R.string.theme_component_wallpaper
            ThemeUtils.MODIFIES_LOCKSCREEN -> R.string.theme_component_lockscreen
            ThemeUtils.MODIFIES_RINGTONES -> R.string.theme_component_ringtone
            ThemeUtils.MODIFIES_NOTIFICATIONS -> R.string.theme_component_notification
            ThemeUtils.MODIFIES_ALARMS -> R.string.theme_component_alarm
            ThemeUtils.MODIFIES_BOOT_ANIM -> R.string.theme_component_boot_animation
            else -> R.string.theme_component_overlays
        }

    private fun createOptionItemAdapter(
        colorUpdateViewModel: ColorUpdateViewModel,
        shouldAnimateColor: () -> Boolean,
        lifecycleOwner: LifecycleOwner,
        backgroundDispatcher: CoroutineDispatcher,
    ): OptionItemAdapter2<Drawable> =
        OptionItemAdapter2(
            layoutResourceId = R.layout.theme_option,
            lifecycleOwner = lifecycleOwner,
            backgroundDispatcher = backgroundDispatcher,
            bindPayload = { view: View, icon: Drawable ->
                val imageView = view.findViewById(R.id.foreground) as? ImageView
                imageView?.setImageDrawable(icon)
                return@OptionItemAdapter2 null
            },
            colorUpdateViewModel = WeakReference(colorUpdateViewModel),
            shouldAnimateColor = shouldAnimateColor,
        )

    private fun RecyclerView.initOptionList(
        context: Context,
        adapter: OptionItemAdapter2<Drawable>,
    ) {
        apply {
            this.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            addItemDecoration(
                SingleRowListItemSpacing(
                    edgeItemSpacePx =
                        context.resources.getDimensionPixelSize(
                            R.dimen.floating_sheet_content_horizontal_padding
                        ),
                    itemHorizontalSpacePx =
                        context.resources.getDimensionPixelSize(
                            R.dimen.floating_sheet_grid_list_item_horizontal_space
                        ),
                )
            )
            this.adapter = adapter
        }
    }
}
