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
import android.content.pm.PackageManager.GET_PROVIDERS
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider

/**
 * Clears FileProvider's static per-authority path-strategy cache for each non-exported, URI-granting provider the application
 * declares. [ShadowFileProvider] never reads that cache; a test on the stock provider calls this after each test, since
 * `getUriForFile` reads the cache before the provider is attached to the next test.
 *
 * `FileProvider.attachInfo` removes the provider's authority from the cache (`FileProvider.java:645-647`) and throws for an
 * exported provider or one that grants no URI permissions (`:632-639`), so only matching providers are attached.
 */
public fun resetFileProviderCache() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    @Suppress("DEPRECATION")
    val providers =
        context.packageManager
            .getPackageInfo(context.packageName, GET_PROVIDERS)
            .providers
            .orEmpty()
    providers
        .filter { !it.exported && it.grantUriPermissions }
        .forEach { FileProvider().attachInfo(context, it) }
}
