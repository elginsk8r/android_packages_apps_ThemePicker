/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.iconpack.shared.model

import android.graphics.drawable.Drawable

/** An icon pack: a theme package that ships one, or a launcher icon pack. */
data class IconPackModel(val packageName: String, val name: String, val icon: Drawable?)
