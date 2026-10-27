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

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.verify.assertFalse

/**
 * Fails on a class that is both `@Entity` and `@Serializable`: a backup DTO is a separate class, so a schema change of the
 * database never changes the backup file by accident.
 *
 * Each app's `CodingConventionsTest` calls it on `Konsist.scopeFromProduction()`. Konsist has no type resolution, so
 * the annotations are matched by name.
 */
public fun KoScope.assertBackupConventions() {
    classes().assertFalse(testName = "a Room entity is never serializable into a backup") { koClass ->
        koClass.hasAnnotation { it.name == "Entity" } && koClass.hasAnnotation { it.name == "Serializable" }
    }
}
