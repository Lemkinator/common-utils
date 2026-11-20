/*
 * Copyright 2024-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.lemke.commonutils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_PROVIDERS
import android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
import android.content.pm.ProviderInfo
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider

/**
 * Clears FileProvider's static per-authority path-strategy cache for each non-exported, URI-granting provider the application
 * declares. [ShadowFileProvider] never reads that cache; a test on the stock provider calls this after each test, since
 * stock `getUriForFile` caches the path strategy per authority and Robolectric does not attach the provider again for the
 * next test.
 *
 * `FileProvider.attachInfo` removes the provider's authority from the cache and throws for an exported provider or one that
 * grants no URI permissions, so only matching providers are attached, once per authority of a `;`-separated
 * list. A provider a test disabled stays in the reset.
 */
public fun resetFileProviderCache() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val providers =
        context.packageManager
            .packageInfoWithProviders(context.packageName)
            .providers
            .orEmpty()
    providers
        .filter { !it.exported && it.grantUriPermissions }
        .forEach { info ->
            info.authority.split(";").forEach { authority ->
                FileProvider().attachInfo(context, ProviderInfo(info).apply { this.authority = authority })
            }
        }
}

private const val PROVIDER_FLAGS = GET_PROVIDERS or MATCH_DISABLED_COMPONENTS

private fun PackageManager.packageInfoWithProviders(packageName: String): PackageInfo =
    if (SDK_INT >= TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PROVIDER_FLAGS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, PROVIDER_FLAGS)
    }
