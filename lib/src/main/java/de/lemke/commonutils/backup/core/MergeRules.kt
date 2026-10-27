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

/**
 * A rule for [MergeableSection.resolve]: the entry with the larger [stamp] wins. On an equal stamp, the entry greater by
 * [order] wins; if [order] reports both equal, the local entry stays. The result does not depend on which side is local.
 */
public fun <E : Any, T : Comparable<T>> newerWins(
    stamp: (E) -> T,
    order: Comparator<in E>,
): (E, E) -> E =
    { local, incoming ->
        val byStamp = stamp(local).compareTo(stamp(incoming))
        winner(local, incoming, if (byStamp != 0) byStamp else order.compare(local, incoming))
    }

/**
 * A rule for [MergeableSection.resolve]: the entry with the higher [rank] wins, then the larger [stamp], then the entry
 * greater by [order]; if [order] reports both equal, the local entry stays. The result does not depend on which side is local.
 */
public fun <E : Any, R : Comparable<R>, T : Comparable<T>> higherRankWins(
    rank: (E) -> R,
    stamp: (E) -> T,
    order: Comparator<in E>,
): (E, E) -> E =
    { local, incoming ->
        val byRank = rank(local).compareTo(rank(incoming))
        val byStamp = stamp(local).compareTo(stamp(incoming))
        winner(
            local,
            incoming,
            if (byRank != 0) {
                byRank
            } else if (byStamp != 0) {
                byStamp
            } else {
                order.compare(local, incoming)
            },
        )
    }

/** Orders entries by their encoded JSON text, the last tie-break of [newerWins] and [higherRankWins]. */
public fun <E : Any> encodedOrder(serializer: KSerializer<E>): Comparator<E> = compareBy { backupJson.encodeToString(serializer, it) }

private fun <E> winner(
    local: E,
    incoming: E,
    localComparedToIncoming: Int,
): E = if (localComparedToIncoming < 0) incoming else local
