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
package de.lemke.commonutils.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.android.material.transition.MaterialSharedAxis
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.util.withContext
import de.lemke.commonutils.R
import dev.oneuiproject.oneui.design.R as designR

/** Pre-built open-source licenses fragment powered by AboutLibraries. */
class CommonUtilsLibsFragment : TransitionFragmentSharedAxis(axis = MaterialSharedAxis.Y) {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setContent { LibsContent() }
        }

    @Suppress("ktlint:standard:function-naming", "FunctionNaming")
    @Composable
    private fun LibsContent() {
        LibsContent(Libs.Builder().withContext(requireContext()).build())
    }
}

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
@Composable
private fun LibsContent(libs: Libs) {
    MaterialTheme(
        if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = Color(colorResource(id = R.color.primary_color_themed).value),
                secondary = Color(colorResource(id = R.color.commonutils_secondary_text_icon_color).value),
                background = Color(colorResource(id = designR.color.oui_des_round_and_bgcolor).value),
            )
        } else {
            lightColorScheme(
                primary = Color(colorResource(id = R.color.primary_color_themed).value),
                secondary = Color(colorResource(id = R.color.commonutils_secondary_text_icon_color).value),
                background = Color(colorResource(id = designR.color.oui_des_round_and_bgcolor).value),
            )
        },
    ) {
        Scaffold { padding ->
            Column(modifier = Modifier.padding(padding)) {
                LibrariesContainer(
                    libs,
                    modifier = Modifier.fillMaxSize(),
                    showDescription = true,
                    showFundingBadges = true,
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
@Preview
@Composable
private fun LibsContentPreview() {
    val libs =
        Libs
            .Builder()
            .withJson(
                """
                {
                  "libraries": [
                    {
                      "uniqueId": "sample",
                      "name": "Sample Library",
                      "licenses": ["apache_2_0"]
                    }
                  ],
                  "licenses": {
                    "apache_2_0": {
                      "name": "Apache-2.0"
                    }
                  }
                }
                """.trimIndent(),
            ).build()
    LibsContent(libs)
}
