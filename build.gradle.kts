import java.util.Properties

/**
 * Note: To configure GitHub credentials, you have to do one of the following:
 * <ul>
 *      <li>Add `githubUsername` and `githubAccessToken` to Global Gradle Properties</li>
 *      <li>Set `GITHUB_USERNAME` and `GITHUB_ACCESS_TOKEN` in your environment variables</li>
 *      <li>Create a `github.properties` file in your project folder with the following content:</li>
 * </ul>
 *
 * <pre>
 *   githubUsername="YOUR_GITHUB_USERNAME"
 *   githubAccessToken="YOUR_GITHUB_ACCESS_TOKEN"
 * </pre>
 */
val githubProperties = Properties().apply {
    rootProject.file("github.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

val githubUsername: String = rootProject.findProperty("githubUsername") as String? // Global Gradle Properties
    ?: githubProperties.getProperty("githubUsername") // github.properties file
    ?: System.getenv("GITHUB_USERNAME") // Environment Variables
    ?: error("GitHub username not found")

val githubAccessToken: String = rootProject.findProperty("githubAccessToken") as String? // Global Gradle Properties
    ?: githubProperties.getProperty("githubAccessToken") // github.properties file
    ?: System.getenv("GITHUB_ACCESS_TOKEN") // Environment Variables
    ?: error("GitHub Access Token not found")

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
        classpath("com.android.tools.build:gradle:8.10.0")
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
val versionName = "0.8.11"

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