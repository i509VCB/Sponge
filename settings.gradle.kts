rootProject.name = "Sponge"

include("SpongeAPI")
include(":SpongeVanilla")
project(":SpongeVanilla").projectDir = file("vanilla")
pluginManagement {
    repositories {
        maven("https://repo-new.spongepowered.org/repository/maven-public")
    }

}
val testPlugins = file("testplugins.settings.gradle.kts")
if (testPlugins.exists()) {
    apply(from = testPlugins)
} else {
    testPlugins.writeText("// Uncomment to enable client module for debugging\n//include(\":testplugins\")\n")
}
val testPluginsEnabledInCi: String = startParameter.projectProperties.getOrDefault("enableTestPlugins", "false")
if (testPluginsEnabledInCi.toBoolean()) {
    include(":testplugins")
}

val spongeForge = file("spongeforge.settings.gradle.kts")
if (spongeForge.exists()) {
    apply(from = spongeForge)
} else {
    spongeForge.writeText("// Uncomment to enable SpongeForge module.\n" +
            "// By default only Sponge and SpongeVanilla are made available\n" +
            "//include(\":SpongeForge\")\n" +
            "//project(\":SpongeForge\").projectDir = file(\"forge\")\n")
}
val spongeForgeEnabledInCi: String = startParameter.projectProperties.getOrDefault("enableSpongeForge", "false")
if (spongeForgeEnabledInCi.toBoolean()) {
    include(":SpongeForge")
    project(":SpongeForge").projectDir = file("forge")
}

val spunbric = file("spunbric.settings.gradle.kts")

if (spunbric.exists()) {
    apply(from = spunbric)
} else {
    spunbric.writeText("// Uncomment to enable Spunbric module.\n" +
            "// By default only Sponge and SpongeVanilla are made available\n" +
            "//include(\":Spunbric\")\n" +
            "//project(\":Spunbric\").projectDir = file(\"fabric\")\n")
}
