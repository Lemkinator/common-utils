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
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("commonutils.android-library")
    `maven-publish`
}

val commonUtilsPublishing = extensions.create<CommonUtilsPublishingExtension>("commonUtilsPublishing")

extensions.configure<LibraryExtension> {
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// AGP builds the release component, and the test-fixtures configurations below, after evaluation.
afterEvaluate {
    val artifact = commonUtilsPublishing.artifactId.get()
    val moduleDescription = requireNotNull(project.description) { "Set description in ${project.path}" }

    // AGP derives a testFixtures configuration's published capability from the Gradle project name, not from the
    // artifactId, so consumers doing testFixtures(libs.common.utils) can't resolve it without this.
    // Capabilities are additive, so add the correct one alongside AGP's rather than renaming the project (which
    // would also silently rename every task path).
    configurations
        .matching {
            it.name in
                setOf(
                    "releaseTestFixturesApiElements",
                    "releaseTestFixturesRuntimeElements",
                    "releaseTestFixturesVariantReleaseApiPublication",
                    "releaseTestFixturesVariantReleaseRuntimePublication",
                )
        }.configureEach {
            outgoing.capability("$group:$artifact-test-fixtures:$version")
        }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = artifact
                from(components["release"])
                pom {
                    name = artifact
                    description = moduleDescription
                    url = "https://github.com/Lemkinator/common-utils"
                    developers {
                        developer {
                            id = "Lemkinator"
                            name = "Leonard Lemke"
                            email = "leo@leonard-lemke.com"
                            url = "https://www.leonard-lemke.com"
                            timezone = "Europe/Berlin"
                        }
                    }
                    scm {
                        connection = "scm:git:git://github.com/Lemkinator/common-utils.git"
                        developerConnection = "scm:git:ssh://github.com/Lemkinator/common-utils.git"
                        url = "https://github.com/Lemkinator/common-utils"
                    }
                    issueManagement {
                        system = "GitHub Issues"
                        url = "https://github.com/Lemkinator/common-utils/issues"
                    }
                    licenses {
                        license {
                            name = "Apache-2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                            distribution = "repo"
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/lemkinator/common-utils")
                credentials {
                    username = githubProperty("ghUsername")
                    password = githubProperty("ghAccessToken")
                }
            }
        }
    }
}
