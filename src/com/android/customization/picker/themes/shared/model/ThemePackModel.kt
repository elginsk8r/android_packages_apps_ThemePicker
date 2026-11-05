/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.themes.shared.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/** A theme package and the theme components it provides. */
data class ThemePackModel(
    val packageName: String,
    val name: String,
    val author: String?,
    val icon: Drawable?,
    /** The components of lineageos.content.pm.ThemeUtils the theme provides. */
    val components: List<String>,
)

/** What a theme package looks like, shown before it is applied. */
data class ThemePackPreviewModel(
    val wallpaper: Bitmap? = null,
    val lockWallpaper: Bitmap? = null,
    /** A few app icons as the theme's icon pack draws them. */
    val icons: List<Drawable> = emptyList(),
)
