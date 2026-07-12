// Space-free project name so the produced distribution folder AND the bundled jars
// (archivesName defaults to rootProject.name) have no spaces in their ZIP entry paths.
// Spaces there trigger the JetBrains Marketplace "The plugin archive file cannot be
// extracted" verification failure (MP-7472). The visible plugin name is set separately
// via intellijPlatform.pluginConfiguration.name in build.gradle.kts.
rootProject.name = "Zeus-Thunderbolt"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
