import java.util.Properties

/**
 * Converts a camelCase or mixedCase string to ENV_VAR_STYLE (uppercase with underscores).
 * Example: githubAccessToken -> GITHUB_ACCESS_TOKEN
 */
fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

/**
 * Note: To configure GitHub credentials, you have to generate an access token with at least `read:packages` scope at
 * https://github.com/settings/tokens/new and then add it to any of the following:
 *
 * - Add `ghUsername` and `ghAccessToken` to Global Gradle Properties
 * - Set `GH_USERNAME` and `GH_ACCESS_TOKEN` in your environment variables or
 * - Create a `github.properties` file in your project folder with the following content:
 *      ghUsername=&lt;YOUR_GITHUB_USERNAME&gt;
 *      ghAccessToken=&lt;YOUR_GITHUB_ACCESS_TOKEN&gt;
 */
fun getProperty(key: String): String =
    Properties().apply { rootProject.file("github.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }.getProperty(key)
        ?: rootProject.findProperty(key)?.toString()
        ?: System.getenv(key.toEnvVarStyle())
        ?: throw GradleException("Property $key not found")

val githubUsername = getProperty("ghUsername")
val githubAccessToken = getProperty("ghAccessToken")

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
        classpath("com.android.tools.build:gradle:8.10.1")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/tribalfs/sesl-androidx") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven("https://maven.pkg.github.com/tribalfs/sesl-material-components-android") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven("https://maven.pkg.github.com/tribalfs/oneui-design") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
    }
}

val groupId = "io.github.lemkinator"
val artifact = "common-utils"
val versionName = "0.8.25"

subprojects {
    afterEvaluate {
        if (!project.plugins.hasPlugin("maven-publish")) {
            return@afterEvaluate
        }
        group = groupId
        version = versionName
        println("Evaluated $group:$artifact:$version")
        project.extensions.configure<PublishingExtension>("publishing") {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifactId = artifact
                    afterEvaluate {
                        from(components["release"])
                    }
                    pom {
                        name = artifact
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
                        issueManagement{
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
                        username = githubUsername
                        password = githubAccessToken
                    }
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}