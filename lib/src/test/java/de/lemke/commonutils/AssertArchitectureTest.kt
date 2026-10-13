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

import com.lemonappdev.konsist.core.exception.KoAssertionFailedException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain

class AssertArchitectureTest : ShouldSpec() {
    private val stateOnlyRule = "ViewModel files expose state only, without Channel or SharedFlow events"

    private fun shouldFailWith(
        rule: String,
        sources: Map<String, String>,
    ) = withSourceScope(sources) { scope, _ ->
        withClue(sources.values.joinToString("\n---\n")) {
            val error = shouldThrow<KoAssertionFailedException> { scope.assertArchitecture("demo") }
            error.message shouldContain rule
        }
    }

    private fun viewModelWith(member: String) =
        mapOf("ProbeViewModel.kt" to "package demo.ui\n\nclass ProbeViewModel : ViewModel() {\n    $member\n}\n")

    init {
        should("pass a clean app") {
            val sources =
                mapOf(
                    "Thing.kt" to "package demo.domain.model\n\ndata class Thing(val id: Int)\n",
                    "ThingRepository.kt" to
                        "package demo.data\n\nimport demo.domain.model.Thing\n\n" +
                        "class ThingRepository {\n    val all = listOf(Thing(1))\n}\n",
                    "GetThingUseCase.kt" to
                        "package demo.domain\n\nimport demo.data.ThingRepository\n\n" +
                        "class GetThingUseCase {\n    operator fun invoke() = 1\n}\n",
                    "MainViewModel.kt" to
                        """
                        package demo.ui

                        import kotlinx.coroutines.channels.BufferOverflow
                        import kotlinx.coroutines.channels.ProducerScope
                        import kotlinx.coroutines.channels.awaitClose
                        import kotlinx.coroutines.flow.MutableStateFlow
                        import kotlinx.coroutines.flow.callbackFlow

                        @HiltViewModel
                        class MainViewModel @Inject constructor() : ViewModel() {
                            /** Mirrors no Channel( or MutableSharedFlow( */
                            val state = MutableStateFlow(0)
                            val ticks = callbackFlow<Int> { awaitClose { } }
                            val label = "Channel( and MutableSharedFlow<Int>( and .shareIn("
                            val overflow = BufferOverflow.DROP_OLDEST

                            fun produce(scope: ProducerScope<Int>) = scope.trySend(0)
                        }
                        """.trimIndent(),
                )

            withSourceScope(sources) { scope, _ -> shouldNotThrowAny { scope.assertArchitecture("demo") } }
        }
        should("fail when data imports ui") {
            shouldFailWith(
                "data layer does not depend on ui",
                mapOf("Repo.kt" to "package demo.data\n\nimport demo.ui.Screen\n\nclass Repo\n"),
            )
        }
        should("fail when data imports a domain use case") {
            shouldFailWith(
                "data layer does not depend on domain use cases",
                mapOf("Repo.kt" to "package demo.data\n\nimport demo.domain.GetThingUseCase\n\nclass Repo\n"),
            )
        }
        should("fail when domain imports ui") {
            shouldFailWith(
                "domain layer does not depend on ui",
                mapOf("Logic.kt" to "package demo.domain\n\nimport demo.ui.Screen\n\nclass Logic\n"),
            )
        }
        should("ignore layers of another root package") {
            withSourceScope(mapOf("Repo.kt" to "package other.data\n\nimport other.ui.Screen\n\nclass Repo\n")) { scope, _ ->
                shouldNotThrowAny { scope.assertArchitecture("demo") }
            }
        }
        should("fail when a use case lacks operator fun invoke") {
            shouldFailWith(
                "use case classes declare operator fun invoke",
                mapOf(
                    "A.kt" to "package demo.domain\n\nclass AUseCase {\n    operator fun invoke() = 1\n}\n",
                    "B.kt" to "package demo.domain\n\nclass BUseCase {\n    fun invoke() = 1\n}\n",
                ),
            )
        }
        should("fail when a class named ViewModel extends no ViewModel") {
            shouldFailWith("classes named ViewModel extend ViewModel", mapOf("Main.kt" to "package demo.ui\n\nclass MainViewModel\n"))
        }
        should("accept a ViewModel that extends a base class in the same file") {
            val source =
                "package demo.ui\n\nabstract class BaseScreenModel : ViewModel()\n\nclass MainViewModel : BaseScreenModel()\n"

            withSourceScope(mapOf("Main.kt" to source)) { scope, _ -> shouldNotThrowAny { scope.assertArchitecture("demo") } }
        }
        should("fail on an event stream in a ViewModel behind a base class in the same file") {
            shouldFailWith(
                stateOnlyRule,
                mapOf(
                    "Screen.kt" to
                        "package demo.ui\n\nabstract class BaseScreenModel : ViewModel()\n\n" +
                        "class ScreenModel : BaseScreenModel() {\n    val events = Channel<Int>()\n}\n",
                ),
            )
        }
        should("accept a ViewModel that extends a chain of two base classes in the same file") {
            val source =
                "package demo.ui\n\nabstract class BaseModel : ViewModel()\n\nabstract class MidModel : BaseModel()\n\n" +
                    "class LeafViewModel : MidModel()\n"

            withSourceScope(mapOf("Leaf.kt" to source)) { scope, _ -> shouldNotThrowAny { scope.assertArchitecture("demo") } }
        }
        should("fail on an event stream in a ViewModel two base classes deep in the same file") {
            shouldFailWith(
                stateOnlyRule,
                mapOf(
                    "Leaf.kt" to
                        "package demo.ui\n\nabstract class BaseModel : ViewModel()\n\nabstract class MidModel : BaseModel()\n\n" +
                        "class LeafModel : MidModel() {\n    val events = Channel<Int>()\n}\n",
                ),
            )
        }
        should("allow a Channel in a chain of classes that never reaches ViewModel") {
            val source =
                "package demo.data\n\nabstract class BasePipe\n\nabstract class MidPipe : BasePipe()\n\n" +
                    "class LeafPipe : MidPipe() {\n    val events = Channel<Int>()\n}\n"

            withSourceScope(mapOf("Pipe.kt" to source)) { scope, _ -> shouldNotThrowAny { scope.assertArchitecture("demo") } }
        }
        should("fail on every Channel and SharedFlow declaration or call in a ViewModel") {
            listOf(
                "val events = kotlinx.coroutines.channels.Channel<Int>()",
                "val events = kotlinx.coroutines.flow.MutableSharedFlow<List<Int>>()",
                "val events = kotlinx.coroutines.channels.Channel<\n        Int,\n    >()",
                "val events = state.shareIn(viewModelScope, SharingStarted.Eagerly)",
                "val events = kotlinx.coroutines.channels.Channel<Int>().receiveAsFlow()",
                "val events: SharedFlow<Int>? = null",
                "val events: kotlinx.coroutines.channels.SendChannel<Int> = TODO()",
                "val events: ReceiveChannel<Int> = TODO()",
                "val events: Channel<Int> = TODO()",
                "val events: List<kotlinx.coroutines.flow.SharedFlow<Int>> = emptyList()",
                "val events: Map<String, Channel<Int>> = emptyMap()",
                "fun events(): List<ReceiveChannel<Int>> = emptyList()",
                "val events: StateFlow<Int>\n        field = kotlinx.coroutines.flow.MutableSharedFlow<Int>()",
                "val events by lazy { kotlinx.coroutines.channels.Channel<Int>() }",
                "val label = \"\" + Channel<Int>()",
                "fun events() = Channel<Int>()",
                "fun events() = state.shareIn(viewModelScope, SharingStarted.Eagerly)",
                "fun events(): SharedFlow<Int> = TODO()",
                "fun events(): kotlinx.coroutines.channels.ReceiveChannel<Int>? = null",
                "fun send() {\n        val events: SendChannel<Int> = TODO()\n    }",
                "init {\n        events = kotlinx.coroutines.flow.MutableSharedFlow<Int>()\n    }",
                "init {\n        val events: Channel<Int> = TODO()\n    }",
            ).forEach { member -> shouldFailWith(stateOnlyRule, viewModelWith(member)) }
        }
        should("fail on every event stream import in a ViewModel file") {
            listOf(
                "kotlinx.coroutines.channels.*",
                "kotlinx.coroutines.channels.Channel",
                "kotlinx.coroutines.channels.ReceiveChannel",
                "kotlinx.coroutines.channels.SendChannel",
                "kotlinx.coroutines.flow.MutableSharedFlow",
                "kotlinx.coroutines.flow.SharedFlow",
                "kotlinx.coroutines.flow.asSharedFlow",
                "kotlinx.coroutines.flow.consumeAsFlow",
                "kotlinx.coroutines.flow.receiveAsFlow",
                "kotlinx.coroutines.flow.shareIn",
            ).forEach { banned ->
                val source = "package demo.ui\n\nimport $banned\n\nclass ProbeViewModel : ViewModel()\n"
                shouldFailWith(stateOnlyRule, mapOf("ProbeViewModel.kt" to source))
            }
        }
        should("allow a Channel in a file without a ViewModel") {
            val source =
                "package demo.data\n\nimport kotlinx.coroutines.channels.Channel\n\nclass Pipe {\n    val events = Channel<Int>()\n}\n"

            withSourceScope(mapOf("Pipe.kt" to source)) { scope, _ -> shouldNotThrowAny { scope.assertArchitecture("demo") } }
        }
        should("fail when a HiltViewModel lacks an Inject constructor") {
            shouldFailWith(
                "HiltViewModel classes use Inject constructor",
                mapOf("Main.kt" to "package demo.ui\n\n@HiltViewModel\nclass MainViewModel : ViewModel()\n"),
            )
        }
        should("accept a HiltViewModel with an Inject constructor") {
            val source = "package demo.ui\n\n@HiltViewModel\nclass MainViewModel @Inject constructor() : ViewModel()\n"

            withSourceScope(mapOf("Main.kt" to source)) { scope, _ -> shouldNotThrowAny { scope.assertArchitecture("demo") } }
        }
    }
}
