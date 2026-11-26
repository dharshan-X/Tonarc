import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val appVersionName = (project.findProperty("APP_VERSION_NAME") as? String)
    ?.trim()
    ?.removePrefix("v")
    ?.substringBefore("-")
    ?.takeIf { Regex("""\d+\.\d+\.\d+""").matches(it) }
    ?: "0.1.0"

val syncSharedPureKotlin = tasks.register<Sync>("syncSharedPureKotlin") {
    from(rootProject.file("app/src/main/java")) {
        include(
            "com/quietrays/tonarc/data/model/Lyrics.kt",
            "com/quietrays/tonarc/data/network/lyrics/LrcLibApiService.kt",
            "com/quietrays/tonarc/data/network/lyrics/LrcLibResponse.kt",
            "com/quietrays/tonarc/data/network/youtube/InnertubeModels.kt",
            "com/quietrays/tonarc/data/network/youtube/InnertubeParser.kt",
            "com/quietrays/tonarc/data/network/youtube/NewPipeDownloader.kt",
            "com/quietrays/tonarc/utils/TtmlLyricsParser.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared/kotlin"))
}

sourceSets {
    main {
        kotlin.srcDir(syncSharedPureKotlin)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.foundation)
    implementation(compose.animation)
    implementation(compose.ui)
    implementation(compose.runtime)

    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.org.json)
    implementation(libs.newpipe.extractor)

    implementation(libs.jaudiotagger)
    implementation(libs.vorbisjava.core)

    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junitplatformlauncher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.quietrays.tonarc.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        jvmArgs(
            "-Dapple.awt.application.name=Tonarc",
            "-Dawt.useSystemAAFontSettings=on",
            "-Dswing.aatext=true",
            "-Xmx1024m"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Tonarc"
            packageVersion = appVersionName
            description = "Privacy-first high-resolution music player and YouTube Music streaming client"
            vendor = "QuietRays"
            copyright = "GPL-3.0"
            licenseFile.set(rootProject.file("LICENSE"))

            modules(
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.naming",
                "java.net.http",
                "java.sql",
                "java.xml",
                "jdk.crypto.ec",
                "jdk.unsupported"
            )

            linux {
                iconFile.set(rootProject.file("assets/icon.png"))
                packageName = "tonarc"
                debMaintainer = "dharshan-X"
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo;Audio;Player;Music;"
            }
        }
    }
}
