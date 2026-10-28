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
package de.lemke.commonutils.backup.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject

/** The verdict of [BackupSection.validate]. */
public sealed interface EntryCheck {
    public data object Valid : EntryCheck

    public data class Invalid(val reason: String) : EntryCheck
}

/**
 * One kind of app data in a backup file, stored as `sections/<id>.json`.
 *
 * Entries are `@Serializable` DTOs, separate from database entities. Replace is generic: it calls [clear] and
 * [insert]. An app that can merge implements [MergeableSection] instead.
 */
public interface BackupSection<E : Any> {
    public val id: String

    /** The current section format version. */
    public val version: Int

    /** The oldest version [upgrade] still maps to [version]. */
    public val minReadableVersion: Int
    public val serializer: KSerializer<E>

    /**
     * One step: maps an entry of [fromVersion] to an entry of `fromVersion + 1`. A step that cannot map an entry throws;
     * the reader skips and counts that entry.
     */
    public fun upgrade(
        fromVersion: Int,
        entry: JsonObject,
    ): JsonObject

    public fun validate(entry: E): EntryCheck

    /** Runs inside the read transaction. */
    public suspend fun exportEntries(): List<E>

    /** Replace only. Runs inside the write transaction. */
    public suspend fun clear()

    /** Runs inside the write transaction. */
    public suspend fun insert(entries: List<E>)
}

/** A section with merge rules. Merge never deletes a local entry. */
public interface MergeableSection<E : Any, K : Any> : BackupSection<E> {
    public fun key(entry: E): K

    /** The entry that stays after a clash. Build it with [newerWins] or [higherRankWins] so every device picks the same one. */
    public fun resolve(
        local: E,
        incoming: E,
    ): E

    /** Runs inside the write transaction. */
    public suspend fun localEntries(keys: Set<K>): Map<K, E>

    /** Runs inside the write transaction. */
    public suspend fun replace(
        local: E,
        merged: E,
    )
}
