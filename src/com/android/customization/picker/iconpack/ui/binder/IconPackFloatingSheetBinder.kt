/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.iconpack.ui.binder

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.common.ui.view.SingleRowListItemSpacing
import com.android.customization.picker.iconpack.shared.model.IconPackModel
import com.android.themepicker.R
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.ICON_PACKS
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.option.ui.adapter.OptionItemAdapter2
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

object IconPackFloatingSheetBinder {

    fun bind(
        view: View,
        optionsViewModel: ThemePickerCustomizationOptionsViewModel,
        colorUpdateViewModel: ColorUpdateViewModel,
        lifecycleOwner: LifecycleOwner,
        backgroundDispatcher: CoroutineDispatcher,
    ) {
        val viewModel = optionsViewModel.iconPackPickerViewModel
        val isFloatingSheetActive = { optionsViewModel.selectedOption.value == ICON_PACKS }

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

        val icons = view.requireViewById<LinearLayout>(R.id.icon_pack_preview_icons)
        val description = view.requireViewById<TextView>(R.id.icon_pack_preview_description)

        val optionListAdapter =
            createOptionItemAdapter(
                colorUpdateViewModel = colorUpdateViewModel,
                shouldAnimateColor = isFloatingSheetActive,
                lifecycleOwner = lifecycleOwner,
                backgroundDispatcher = backgroundDispatcher,
            )
        val optionList =
            view.requireViewById<RecyclerView>(R.id.icon_pack_options).also {
                it.initOptionList(view.context, optionListAdapter)
            }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.iconPackOptionListItems.collect { options ->
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
                    viewModel.previewingIconPackPreview.collect { (iconPack, previewIcons) ->
                        bindPreview(iconPack, previewIcons, icons, description)
                    }
                }
            }
        }
    }

    private fun bindPreview(
        iconPack: IconPackModel?,
        previewIcons: List<Drawable>?,
        icons: LinearLayout,
        description: TextView,
    ) {
        val context = description.context
        icons.removeAllViews()
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.theme_preview_icon_size)
        val iconSpacing = context.resources.getDimensionPixelSize(R.dimen.theme_preview_spacing)
        previewIcons?.forEach { icon ->
            icons.addView(
                ImageView(context).apply { setImageDrawable(icon) },
                LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginStart = iconSpacing / 2
                    marginEnd = iconSpacing / 2
                },
            )
        }

        description.text =
            when {
                iconPack == null -> context.getString(R.string.icon_pack_default_description)
                previewIcons != null && previewIcons.isEmpty() ->
                    context.getString(R.string.icon_pack_restyled_description, iconPack.name)
                else -> iconPack.name
            }
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
