/*
 * Copyright (C) 2026 The LineageOS Project
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

package com.android.customization.picker.themes.shared.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/** A theme package and the theme components it provides. */
data class ThemePackModel(
    val packageName: String,
    val name: String,
    val author: String?,
    val icon: Drawable?,
    /** The components of android.content.pm.ThemeUtils the theme provides. */
    val components: List<String>,
)

/** What a theme package looks like, shown before it is applied. */
data class ThemePackPreviewModel(
    val wallpaper: Bitmap? = null,
    val lockWallpaper: Bitmap? = null,
    /** A few app icons as the theme's icon pack draws them. */
    val icons: List<Drawable> = emptyList(),
)
