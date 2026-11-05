/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.iconpack.ui.viewmodel

import android.content.Context
import android.graphics.drawable.Drawable
import com.android.customization.picker.iconpack.domain.interactor.IconPackInteractor
import com.android.customization.picker.iconpack.domain.interactor.IconPackInteractor.Companion.DEFAULT_ICON_PACK_KEY
import com.android.customization.picker.iconpack.shared.model.IconPackModel
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/** Picks an icon pack to apply, modeled on the grid picker. */
class IconPackPickerViewModel
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val interactor: IconPackInteractor,
    @Assisted private val viewModelScope: CoroutineScope,
) {
    private val defaultIcon: Drawable = context.packageManager.defaultActivityIcon

    private val iconPacks: Flow<List<IconPackModel>> =
        interactor.iconPacks.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), 1)

    val isIconPackCustomizationAvailable: Flow<Boolean> =
        iconPacks
            .map { interactor.isAvailable && it.isNotEmpty() }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    // The key of the currently applied icon pack
    private val selectedIconPackKey: Flow<String> =
        interactor.selectedIconPackKey.shareIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            replay = 1,
        )

    // The currently applied icon pack
    val selectedIconPack: Flow<OptionItemViewModel2<Drawable>> =
        combine(iconPacks, selectedIconPackKey) { iconPacks, selectedKey ->
                toOptionItemViewModel(iconPacks.find { it.packageName == selectedKey })
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    private val overridingIconPackKey = MutableStateFlow<String?>(null)
    // If the overriding key is null, use the currently applied icon pack
    val previewingIconPackKey: Flow<String> =
        combine(overridingIconPackKey, selectedIconPackKey) { overridingKey, selectedKey ->
                overridingKey ?: selectedKey
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    /** The icon pack being previewed, or null for the apps' own icons. */
    val previewingIconPack: Flow<IconPackModel?> =
        combine(previewingIconPackKey, iconPacks) { key, iconPacks ->
            iconPacks.find { it.packageName == key }
        }

    /**
     * The icon pack being previewed, or null for the apps' own icons, and a few app icons as it
     * draws them once they have been loaded.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val previewingIconPackPreview: Flow<Pair<IconPackModel?, List<Drawable>?>> =
        previewingIconPack
            .distinctUntilChanged()
            .transformLatest { iconPack ->
                emit(iconPack to null)
                if (iconPack != null) {
                    emit(iconPack to interactor.getPreviewIcons(iconPack.packageName))
                }
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    val iconPackOptionListItems: Flow<List<OptionItemViewModel2<Drawable>>> =
        iconPacks
            .map { iconPacks ->
                listOf(toOptionItemViewModel(null)) + iconPacks.map { toOptionItemViewModel(it) }
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.Lazily, replay = 1)

    val onApply: Flow<(suspend () -> Unit)?> =
        combine(overridingIconPackKey, selectedIconPackKey) { overridingKey, selectedKey ->
            if (overridingKey != null && overridingKey != selectedKey) {
                { interactor.applyIconPack(overridingKey) }
            } else {
                null
            }
        }

    fun resetPreview() {
        overridingIconPackKey.value = null
    }

    /** @param iconPack the icon pack, or null for the apps' own icons */
    private fun toOptionItemViewModel(iconPack: IconPackModel?): OptionItemViewModel2<Drawable> {
        val key = iconPack?.packageName ?: DEFAULT_ICON_PACK_KEY
        val isSelected =
            previewingIconPackKey
                .map { it == key }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Lazily,
                    initialValue = false,
                )
        return OptionItemViewModel2(
            key = MutableStateFlow(key),
            payload = iconPack?.icon ?: defaultIcon,
            text =
                iconPack?.let { Text.Loaded(it.name) } ?: Text.Resource(R.string.icon_pack_default),
            isSelected = isSelected,
            onClicked =
                isSelected.map {
                    if (!it) {
                        { overridingIconPackKey.value = key }
                    } else {
                        null
                    }
                },
        )
    }

    @ViewModelScoped
    @AssistedFactory
    interface Factory {
        fun create(viewModelScope: CoroutineScope): IconPackPickerViewModel
    }
}
