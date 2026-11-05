/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.themes.wallpaper

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.android.wallpaper.asset.StreamableAsset
import java.io.IOException
import java.io.InputStream

/**
 * Asset stored in the assets of a theme package, see {@link lineageos.content.pm.ThemeUtils}.
 */
class ThemeAsset(context: Context, private val mPackageName: String, private val mPath: String) : StreamableAsset() {
    private val mContext: Context = context.applicationContext

    override fun openInputStream(): InputStream? {
        return try {
            mContext.createPackageContext(mPackageName, 0).assets.open(mPath)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Unable to open $mPath of $mPackageName", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "Unable to open $mPath of $mPackageName", e)
            null
        }
    }

    companion object {
        private const val TAG = "ThemeAsset"
    }
}
