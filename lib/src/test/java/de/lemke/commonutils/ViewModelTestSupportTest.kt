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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.isActive

private class RecordingViewModel : ViewModel() {
    var cleared = 0

    override fun onCleared() {
        cleared++
    }
}

class ViewModelTestSupportTest : ShouldSpec(
    {
        should("call onCleared once") {
            val viewModel = RecordingViewModel()

            viewModel.clearForTest()

            viewModel.cleared shouldBe 1
        }

        should("cancel the view model scope") {
            val viewModel = RecordingViewModel()
            viewModel.viewModelScope.isActive shouldBe true

            viewModel.clearForTest()

            viewModel.viewModelScope.isActive shouldBe false
        }

        should("clear an anonymous view model") {
            var cleared = false
            val viewModel =
                object : ViewModel() {
                    override fun onCleared() {
                        cleared = true
                    }
                }

            viewModel.clearForTest()

            cleared shouldBe true
        }
    },
)
