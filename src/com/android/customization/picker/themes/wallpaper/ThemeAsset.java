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
package com.android.customization.picker.themes.wallpaper;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import com.android.wallpaper.asset.StreamableAsset;

import java.io.IOException;
import java.io.InputStream;

/**
 * Asset stored in the assets of a theme package, see {@link android.content.pm.ThemeUtils}.
 */
public final class ThemeAsset extends StreamableAsset {
    private static final String TAG = "ThemeAsset";

    private final Context mContext;
    private final String mPackageName;
    private final String mPath;

    /**
     * @param packageName the theme package
     * @param path the path of the asset in the theme package's assets
     */
    public ThemeAsset(Context context, String packageName, String path) {
        mContext = context.getApplicationContext();
        mPackageName = packageName;
        mPath = path;
    }

    @Override
    protected InputStream openInputStream() {
        try {
            return mContext.createPackageContext(mPackageName, 0).getAssets().open(mPath);
        } catch (PackageManager.NameNotFoundException | IOException e) {
            Log.w(TAG, "Unable to open " + mPath + " of " + mPackageName, e);
            return null;
        }
    }
}
