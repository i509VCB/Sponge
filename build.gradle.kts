import java.security.MessageDigest
import java.util.Locale

plugins {
    id("org.spongepowered.gradle.vanilla")
    `maven-publish`
    `java-library`
    eclipse
    id("org.cadixdev.licenser") version "0.5.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    // id("org.spongepowered.mixin")
    id("fabric-loom") version "0.5-SNAPSHOT" apply false // FIXME: Horrid hack
}

val apiProject = project.project("SpongeAPI")
val commonProject = project
val minecraftVersion: String by project
val recommendedVersion: String by project

val asmVersion: String by project
val modlauncherVersion: String by project
val mixinVersion: String by project
val pluginSpiVersion: String by project
val guavaVersion: String by project
val junitVersion: String by project

minecraft {
    version(minecraftVersion)
    injectRepositories().set(false)
    project.sourceSets["main"].resources
            .filter { it.name.endsWith(".accesswidener") }
            .files
            .forEach {
                accessWidener(it)
                parent?.minecraft?.accessWidener(it)
            }
}

tasks {
    jar {
        manifest {
            attributes(mapOf(
                    "Specification-Title" to "Sponge",
                    "Specification-Vendor" to "SpongePowered",
                    "Specification-Version" to apiProject.version,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                    "Implementation-Vendor" to "SpongePowered"
//                            "Implementation-Timestamp" to Instant.now().format("yyyy-MM-dd'T'HH:mm:ssZ")
            ))
        }
    }
    val mixinsJar by registering(Jar::class) {
        archiveClassifier.set("mixins")
        manifest {
            attributes(mapOf(
                    "Specification-Title" to "Sponge",
                    "Specification-Vendor" to "SpongePowered",
                    "Specification-Version" to apiProject.version,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                    "Implementation-Vendor" to "SpongePowered"
//                            "Implementation-Timestamp" to Instant.now().format("yyyy-MM-dd'T'HH:mm:ssZ")
            ))
        }
        from(mixins.map { it.output })
    }
    val accessorsJar by registering(Jar::class) {
        archiveClassifier.set("accessors")
        manifest {
            attributes(mapOf(
                    "Specification-Title" to "Sponge",
                    "Specification-Vendor" to "SpongePowered",
                    "Specification-Version" to apiProject.version,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                    "Implementation-Vendor" to "SpongePowered"
//                            "Implementation-Timestamp" to Instant.now().format("yyyy-MM-dd'T'HH:mm:ssZ")
            ))
        }
        from(accessors.map { it.output })
    }
    val launchJar by registering(Jar::class) {
        archiveClassifier.set("launch")
        manifest {
            attributes(mapOf(
                    "Specification-Title" to "Sponge",
                    "Specification-Vendor" to "SpongePowered",
                    "Specification-Version" to apiProject.version,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                    "Implementation-Vendor" to "SpongePowered"
//                            "Implementation-Timestamp" to Instant.now().format("yyyy-MM-dd'T'HH:mm:ssZ")
            ))
        }
        from(launch.map { it.output })
    }
    val applaunchJar by registering(Jar::class) {
        archiveClassifier.set("applaunch")
        manifest {
            attributes(mapOf(
                    "Specification-Title" to "Sponge",
                    "Specification-Vendor" to "SpongePowered",
                    "Specification-Version" to apiProject.version,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                    "Implementation-Vendor" to "SpongePowered"
//                            "Implementation-Timestamp" to Instant.now().format("yyyy-MM-dd'T'HH:mm:ssZ")
            ))
        }
        from(applaunch.map { it.output })
    }

    test {
        useJUnitPlatform()
    }

}

version = generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion)

// Configurations
val minecraftConfig by configurations.named("minecraft")
val minecraftClasspathConfig by configurations.named("minecraftClasspath")

val applaunchConfig by configurations.register("applaunch")

val launchConfig by configurations.register("launch") {
    extendsFrom(minecraftConfig)
    extendsFrom(minecraftClasspathConfig)
    extendsFrom(applaunchConfig)
}
val accessorsConfig by configurations.register("accessors") {
    extendsFrom(minecraftConfig)
    extendsFrom(launchConfig)
    extendsFrom(minecraftClasspathConfig)
}
val mixinsConfig by configurations.register("mixins") {
    extendsFrom(applaunchConfig)
    extendsFrom(launchConfig)
    extendsFrom(minecraftConfig)
    extendsFrom(minecraftClasspathConfig)
}

// create the sourcesets
val main by sourceSets

val applaunch by sourceSets.registering {
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = this, targetSource = main, implProject = project, dependencyConfigName = main.implementationConfigurationName)
    project.dependencies {
        mixinsConfig(this@registering.output)
    }
}
val launch by sourceSets.registering {
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = applaunch.get(), targetSource = this, implProject = project, dependencyConfigName = this.implementationConfigurationName)
    project.dependencies {
        mixinsConfig(this@registering.output)
    }
    project.dependencies {
        implementation(this@registering.output)
    }

}

val accessors by sourceSets.registering {
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = launch.get(), targetSource = this, implProject = project, dependencyConfigName = this.implementationConfigurationName)
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = this, targetSource = main, implProject = project, dependencyConfigName = main.implementationConfigurationName)
}
val mixins by sourceSets.registering {
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = launch.get(), targetSource = this, implProject = project, dependencyConfigName = this.implementationConfigurationName)
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = applaunch.get(), targetSource = this, implProject = project, dependencyConfigName = this.implementationConfigurationName)
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = accessors.get(), targetSource = this, implProject = project, dependencyConfigName = this.implementationConfigurationName)
    applyNamedDependencyOnOutput(originProject = project, sourceAdding = main, targetSource = this, implProject = project, dependencyConfigName = this.implementationConfigurationName)
}

repositories {
    maven {
        name = "sponge v2"
        setUrl("https://repo-new.spongepowered.org/repository/maven-public/")
    }
    maven("https://libraries.minecraft.net") {
        name = "mojang"
        mavenContent { releasesOnly() }
    }
}

dependencies {
    // api
    api(project(":SpongeAPI"))

    // Database stuffs... likely needs to be looked at
    implementation("com.zaxxer:HikariCP:2.6.3")
    implementation("org.mariadb.jdbc:mariadb-java-client:2.0.3")
    implementation("com.h2database:h2:1.4.196")
    implementation("org.xerial:sqlite-jdbc:3.20.0")
    implementation("com.google.inject:guice:4.1.0")
    implementation("javax.inject:javax.inject:1")

    // ASM - required for generating event listeners
    implementation("org.ow2.asm:asm-util:$asmVersion")
    implementation("org.ow2.asm:asm-tree:$asmVersion")

    // Implementation-only Adventure
    implementation(platform("net.kyori:adventure-bom:4.4.0"))
    implementation("net.kyori:adventure-serializer-configurate4")

    // annotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")
    // annotationProcessor("org.apache.logging.log4j:log4j-core:2.11.2")

    // Launch Dependencies - Needed to bootstrap the engine(s)
    launchConfig(project(":SpongeAPI"))
    launchConfig("org.spongepowered:plugin-spi:$pluginSpiVersion")
    launchConfig("org.spongepowered:mixin:$mixinVersion")
    launchConfig("org.checkerframework:checker-qual:3.4.1")
    launchConfig("com.google.guava:guava:$guavaVersion") {
        exclude(group = "com.google.code.findbugs", module = "jsr305") // We don't want to use jsr305, use checkerframework
        exclude(group = "org.checkerframework", module = "checker-qual") // We use our own version
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
    }
    launchConfig("com.google.inject:guice:4.1.0")
    launchConfig("javax.inject:javax.inject:1")
    launchConfig("com.google.code.gson:gson:2.8.0")
    launchConfig("org.ow2.asm:asm-tree:$asmVersion")
    launchConfig("org.ow2.asm:asm-util:$asmVersion")
    add(launch.get().implementationConfigurationName, launchConfig)

    // Applaunch -- initialization that needs to occur without game access
    applaunchConfig("org.spongepowered:plugin-spi:$pluginSpiVersion")
    applaunchConfig("org.apache.logging.log4j:log4j-api:2.11.2")
    applaunchConfig("com.google.guava:guava:$guavaVersion")
    applaunchConfig(platform("org.spongepowered:configurate-bom:4.0.0"))
    applaunchConfig("org.spongepowered:configurate-core") {
        exclude(group = "org.checkerframework", module = "checker-qual") // We use our own version
    }
    applaunchConfig("org.spongepowered:configurate-hocon") {
        exclude(group = "org.spongepowered", module = "configurate-core")
        exclude(group = "org.checkerframework", module = "checker-qual") // We use our own version
    }
    applaunchConfig("org.spongepowered:configurate-jackson") {
        exclude(group = "org.spongepowered", module = "configurate-core")
        exclude(group = "org.checkerframework", module = "checker-qual") // We use our own version
    }
    applaunchConfig("org.apache.logging.log4j:log4j-core:2.11.2")
    add(applaunch.get().implementationConfigurationName, applaunchConfig)

    // Annotation Processor
    // "accessorsAnnotationProcessor"("org.spongepowered:mixin:$mixinVersion:processor")
    // "mixinsAnnotationProcessor"("org.spongepowered:mixin:$mixinVersion:processor")
    // "accessorsAnnotationProcessor"("org.apache.logging.log4j:log4j-core:2.11.2")
    // "mixinsAnnotationProcessor"("org.apache.logging.log4j:log4j-core:2.11.2")
    mixinsConfig(sourceSets.named("main").map { it.output })
    add(accessors.get().implementationConfigurationName, accessorsConfig)
    add(mixins.get().implementationConfigurationName, mixinsConfig)
    add(mixins.get().implementationConfigurationName, project(":SpongeAPI"))

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}
/*val extraSrgs = file("extra.srgs")
mixin {
    add("mixins", "sponge.mixins.refmap.json")
    add("accessors", "sponge.accessors.refmap.json")
}*/
fun debug(logger: Logger, messsage: String) {
    println(message = messsage)
    if (System.getProperty("sponge.gradleDebug", "false")!!.toBoolean()) {
        logger.lifecycle(messsage)
    }
}
fun applyNamedDependencyOnOutput(originProject: Project, sourceAdding: SourceSet, targetSource: SourceSet, implProject: Project, dependencyConfigName: String) {
    debug(implProject.logger, "[${implProject.name}] Adding ${originProject.path}(${sourceAdding.name}) to ${implProject.path}(${targetSource.name}).$dependencyConfigName")
    implProject.dependencies.add(dependencyConfigName, sourceAdding.output)
}

fun generateImplementationVersionString(apiVersion: String, minecraftVersion: String, implRecommendedVersion: String, addedVersionInfo: String? = null): String {
    val apiSplit = apiVersion.replace("-SNAPSHOT", "").split(".")
    val minor = if (apiSplit.size > 1) apiSplit[1] else (if (apiSplit.size > 0) apiSplit.last() else "-1")
    val apiReleaseVersion = "${apiSplit[0]}.$minor"
    return listOfNotNull(minecraftVersion, addedVersionInfo, "$apiReleaseVersion.$implRecommendedVersion").joinToString("-")
}
fun generatePlatformBuildVersionString(apiVersion: String, minecraftVersion: String, implRecommendedVersion: String, addedVersionInfo: String? = null): String {
    val isRelease = !implRecommendedVersion.endsWith("-SNAPSHOT")
    println("Detected Implementation Version $implRecommendedVersion as ${if (isRelease) "Release" else "Snapshot"}")
    val apiSplit = apiVersion.replace("-SNAPSHOT", "").split(".")
    val minor = if (apiSplit.size > 1) apiSplit[1] else (if (apiSplit.size > 0) apiSplit.last() else "-1")
    val apiReleaseVersion = "${apiSplit[0]}.$minor"
    val buildNumber = Integer.parseInt(System.getenv("BUILD_NUMBER") ?: "0")
    val implVersionAsReleaseCandidateOrRecommended: String = if (isRelease) {
        "$apiReleaseVersion.$implRecommendedVersion"
    } else {
        "$apiReleaseVersion.${implRecommendedVersion.replace("-SNAPSHOT", "")}-RC$buildNumber"
    }
    return listOfNotNull(minecraftVersion, addedVersionInfo, implVersionAsReleaseCandidateOrRecommended).joinToString("-")
}

val organization: String by project
val projectUrl: String by project
license {
    (this as ExtensionAware).extra.apply {
        this["name"] = "Sponge"
        this["organization"] = organization
        this["url"] = projectUrl
    }
    header = apiProject.file("HEADER.txt")

    include("**/*.java")
    newLine = false
}

allprojects {

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    base {
        archivesBaseName = name.toLowerCase(Locale.ENGLISH)
    }

    tasks {
        withType(JavaCompile::class).configureEach {
            options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
            options.encoding = "UTF-8"
            if (JavaVersion.current().isJava10Compatible) {
                options.release.set(8)
            }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    tasks.withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    repositories {
        maven {
            name = "sponge v2"
            setUrl("https://repo-new.spongepowered.org/repository/maven-public/")
        }
        maven("https://libraries.minecraft.net") {
            name = "mojang"
            mavenContent { releasesOnly() }
        }
    }
    val spongeSnapshotRepo: String? by project
    val spongeReleaseRepo: String? by project
    tasks {

        withType<PublishToMavenRepository>().configureEach {
            onlyIf {
                (repository == publishing.repositories["GitHubPackages"] &&
                        !(rootProject.version as String).endsWith("-SNAPSHOT")) ||
                        (!spongeSnapshotRepo.isNullOrBlank()
                                && !spongeReleaseRepo.isNullOrBlank()
                                && repository == publishing.repositories["spongeRepo"]
                                && publication == publishing.publications["sponge"])

            }
        }
    }
    sourceSets.configureEach {

        if (project.name == "SpongeAPI" && "main" == this.name) {
            return@configureEach;
        }
        val sourceSet = this
        val sourceJarName: String = if ("main".equals(this.name)) "sourceJar" else "${this.name}SourceJar"
        tasks.register(sourceJarName, Jar::class.java) {
            group = "build"
            val classifier = if ("main".equals(sourceSet.name)) "sources" else "${sourceSet.name}sources"
            archiveClassifier.set(classifier)
            from(sourceSet.allJava)
        }
    }
    afterEvaluate {
        publishing {
            repositories {
                maven {
                    name = "GitHubPackages"
                    this.url = uri("https://maven.pkg.github.com/SpongePowered/${rootProject.name}")
                    credentials {
                        username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
                        password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                    }
                }
                // Set by the build server
                maven {
                    name = "spongeRepo"
                    val repoUrl = if ((version as String).endsWith("-SNAPSHOT")) spongeSnapshotRepo else spongeReleaseRepo
                    repoUrl?.apply {
                        url = uri(this)
                    }
                    val spongeUsername: String? by project
                    val spongePassword: String? by project
                    credentials {
                        username = spongeUsername ?: ""
                        password = spongePassword ?: ""
                    }
                }
            }
        }
    }
}
tasks {
    val jar by existing
    val sourceJar by existing
    val mixinsJar by existing
    val accessorsJar by existing
    val launchJar by existing
    val applaunchJar by existing
    shadowJar {
        mergeServiceFiles()
        val generateImplementationVersionString = generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion)

        archiveClassifier.set("dev")
        manifest {
            attributes(mapOf(
                    "Access-Widener" to "common.accesswidener",
                    "Multi-Release" to true,
                    "Specification-Title" to "Sponge",
                    "Specification-Vendor" to "SpongePowered",
                    "Specification-Version" to apiProject.version,
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to generateImplementationVersionString,
                    "Implementation-Vendor" to "SpongePowered"
            ))
        }
        from(jar)
        from(sourceJar)
        from(mixinsJar)
        from(accessorsJar)
        from(launchJar)
        from(applaunchJar)
        dependencies {
            include(project(":"))
        }
    }
}
publishing {
    publications {
        register("sponge", MavenPublication::class) {
            from(components["java"])

            artifact(tasks["shadowJar"])
            artifact(tasks["sourceJar"])
            artifact(tasks["mixinsJar"])
            artifact(tasks["accessorsJar"])
            artifact(tasks["launchJar"])
            artifact(tasks["applaunchJar"])
            artifact(tasks["applaunchSourceJar"])
            artifact(tasks["launchSourceJar"])
            artifact(tasks["mixinsSourceJar"])
            artifact(tasks["accessorsSourceJar"])
            pom {
                artifactId = project.name.toLowerCase()
                this.name.set(project.name)
                this.description.set(project.description)
                this.url.set(projectUrl)

                licenses {
                    license {
                        this.name.set("MIT")
                        this.url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/SpongePowered/Sponge.git")
                    developerConnection.set("scm:git:ssh://github.com/SpongePowered/Sponge.git")
                    this.url.set(projectUrl)
                }
            }
        }
    }
}
data class ProjectDep(val group: String, val module: String, val version: String)


val testplugins: Project? = subprojects.find { "testplugins".equals(it.name) }
if (testplugins != null) {
    project("testplugins") {
        apply {
            plugin("java-library")
            plugin("idea")
            plugin("eclipse")
            plugin("org.cadixdev.licenser")
        }

        dependencies {
            implementation(rootProject.project(":SpongeAPI"))
            annotationProcessor(rootProject.project(":SpongeAPI"))
        }

        tasks.jar {
            manifest {
                attributes("Loader" to "java_plain")
            }
        }
        license {
            (this as ExtensionAware).extra.apply {
                this["name"] = "Sponge"
                this["organization"] = organization
                this["url"] = projectUrl
            }
            header = apiProject.file("HEADER.txt")

            include("**/*.java")
            newLine = false
        }
    }
}

project("SpongeVanilla") {
    val vanillaProject = this
    apply {
        plugin("org.spongepowered.gradle.vanilla")
        // plugin("org.spongepowered.mixin")
        plugin("java-library")
        plugin("maven-publish")
        // plugin("idea")
        // plugin("eclipse")
        plugin("org.cadixdev.licenser")
        plugin("com.github.johnrengelman.shadow")
    }

    description = "The SpongeAPI implementation for Vanilla Minecraft"
    version = generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion)
    println("SpongeVanilla Version $version")

    val vanillaMinecraftConfig by configurations.named("minecraft")
    val vanillaMinecraftClasspathConfig by configurations.named("minecraftClasspath")
    val vanillaAppLaunchConfig by configurations.register("applaunch") {
    }
    val vanillaInstallerConfig by configurations.register("installer") {
    }

    val vanillaInstaller by sourceSets.register("installer") {
    }

    val vanillaMain by sourceSets.named("main") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
    }
    val vanillaLaunch by sourceSets.register("launch") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = this, targetSource = vanillaMain, implProject = vanillaProject, dependencyConfigName = vanillaMain.implementationConfigurationName)
    }
    val vanillaMixins by sourceSets.register("mixins") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = mixins.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = vanillaMain, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = vanillaLaunch, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
    }
    val vanillaAppLaunch by sourceSets.register("applaunch") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = vanillaLaunch, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = vanillaInstaller, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = this, targetSource = vanillaLaunch, implProject = vanillaProject, dependencyConfigName = vanillaLaunch.implementationConfigurationName)
        // runtime dependencies - literally add the rest of the project, because we want to launch the game
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = vanillaMixins, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = vanillaLaunch, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = mixins.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = vanillaProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = vanillaProject, sourceAdding = vanillaMain, targetSource = this, implProject = vanillaProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
    }
    val vanillaMixinsImplementation by configurations.named(vanillaMixins.implementationConfigurationName) {
        extendsFrom(vanillaMinecraftConfig)
        extendsFrom(vanillaMinecraftClasspathConfig)
    }
    val vanillaMixinsAnnotationProcessor by configurations.named(vanillaMixins.annotationProcessorConfigurationName)
    val vanillaInstallerImplementation by configurations.named(vanillaInstaller.implementationConfigurationName)
    val vanillaAppLaunchImplementation by configurations.named(vanillaAppLaunch.implementationConfigurationName) {
        extendsFrom(launchConfig)
    }
    val vanillaAppLaunchRuntime by configurations.named(vanillaAppLaunch.runtimeOnlyConfigurationName)

    minecraft {
        version(minecraftVersion)
        injectRepositories().set(false)
        runs {
            server {
                workingDirectory().set(vanillaProject.file("run/"))
                args("nogui", "--launchTarget", "sponge_server_dev")
                mainClass().set("org.spongepowered.vanilla.applaunch.Main")
                classpath().from(vanillaAppLaunch.runtimeClasspath, vanillaAppLaunch.output)
                // ideaModule("${rootProject.name}.${project.name}.applaunch")
            }

            client {
                workingDirectory().set(vanillaProject.file("run/"))
                args("--launchTarget", "sponge_client_dev")
                mainClass().set("org.spongepowered.vanilla.applaunch.Main")
                classpath().from(vanillaAppLaunch.runtimeClasspath, vanillaAppLaunch.output)
                // ideaModule("${rootProject.name}.${project.name}.applaunch")
            }
        }
        commonProject.sourceSets["main"].resources
                .filter { it.name.endsWith(".accesswidener") }
                .files
                .forEach {
                    accessWidener(it)
                }

        vanillaProject.sourceSets["main"].resources
                .filter { it.name.endsWith(".accesswidener") }
                .files
                .forEach { accessWidener(it) }
    }

    dependencies {
        api(launch.map { it.output })
        implementation(accessors.map { it.output })
        implementation(project(commonProject.path)) {
            // exclude(group = "net.minecraft", module = minecraftDep)
        }
        // annotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")

        vanillaMixinsImplementation(project(commonProject.path))
        add(vanillaLaunch.implementationConfigurationName, project(":SpongeAPI"))
        add(vanillaLaunch.implementationConfigurationName, vanillaAppLaunchConfig)
        add(vanillaLaunch.implementationConfigurationName, vanillaMinecraftConfig)
        add(vanillaLaunch.implementationConfigurationName, vanillaMinecraftClasspathConfig)

        vanillaInstallerConfig("com.google.code.gson:gson:2.8.0")
        vanillaInstallerConfig("org.spongepowered:configurate-hocon:4.0.0")
        vanillaInstallerConfig("org.spongepowered:configurate-core:4.0.0")
        vanillaInstallerConfig("net.sf.jopt-simple:jopt-simple:5.0.3")
        vanillaInstallerConfig("org.apache.logging.log4j:log4j-api:2.11.2")
        vanillaInstallerConfig("org.apache.logging.log4j:log4j-core:2.11.2")
        // Override ASM versions, and explicitly declare dependencies so ASM is excluded from the manifest.
        val asmExclusions = sequenceOf("-commons", "-tree", "-analysis", "")
                .map { "asm$it" }
                .onEach {
            vanillaInstallerConfig("org.ow2.asm:$it:$asmVersion")
        }.toSet()
        vanillaInstallerConfig("org.cadixdev:atlas:0.2.0") {
            asmExclusions.forEach { exclude(group = "org.ow2.asm", module = it) } // Use our own ASM version
        }
        vanillaInstallerConfig("org.cadixdev:lorenz-asm:0.5.6") {
            asmExclusions.forEach { exclude(group = "org.ow2.asm", module = it) } // Use our own ASM version
        }
        vanillaInstallerConfig("org.cadixdev:lorenz-io-proguard:0.5.6")
        vanillaInstallerImplementation(vanillaInstallerConfig)

        vanillaAppLaunchConfig(project(":SpongeAPI"))
        vanillaAppLaunchConfig(platform("net.kyori:adventure-bom:4.4.0"))
        vanillaAppLaunchConfig("net.kyori:adventure-serializer-configurate4")
        vanillaAppLaunchConfig("org.spongepowered:mixin:$mixinVersion")
        vanillaAppLaunchConfig("org.ow2.asm:asm-util:$asmVersion")
        vanillaAppLaunchConfig("org.ow2.asm:asm-tree:$asmVersion")
        vanillaAppLaunchConfig("com.google.guava:guava:$guavaVersion")
        vanillaAppLaunchConfig("org.spongepowered:plugin-spi:$pluginSpiVersion")
        vanillaAppLaunchConfig("javax.inject:javax.inject:1")
        vanillaAppLaunchConfig("org.apache.logging.log4j:log4j-api:2.11.2")
        vanillaAppLaunchConfig("org.apache.logging.log4j:log4j-core:2.11.2")
        vanillaAppLaunchConfig("com.zaxxer:HikariCP:2.6.3")
        vanillaAppLaunchConfig("org.apache.logging.log4j:log4j-slf4j-impl:2.11.2")
        vanillaAppLaunchConfig(platform("org.spongepowered:configurate-bom:4.0.0"))
        vanillaAppLaunchConfig("org.spongepowered:configurate-core") {
            exclude(group = "org.checkerframework", module = "checker-qual")
        }
        vanillaAppLaunchConfig("org.spongepowered:configurate-hocon") {
            exclude(group = "org.spongepowered", module = "configurate-core")
            exclude(group = "org.checkerframework", module = "checker-qual")
        }
        vanillaAppLaunchConfig("org.spongepowered:configurate-jackson") {
            exclude(group = "org.spongepowered", module = "configurate-core")
            exclude(group = "org.checkerframework", module = "checker-qual")
        }

        // Launch Dependencies - Needed to bootstrap the engine(s)
        // The ModLauncher compatibility launch layer
        vanillaAppLaunchConfig("cpw.mods:modlauncher:$modlauncherVersion") {
            exclude(group = "org.apache.logging.log4j")
        }
        vanillaAppLaunchConfig("org.ow2.asm:asm-commons:$asmVersion")
        vanillaAppLaunchConfig("cpw.mods:grossjava9hacks:1.1.+") {
            exclude(group = "org.apache.logging.log4j")
        }
        vanillaAppLaunchConfig("net.fabricmc:access-widener:1.0.0") {
            exclude(group = "org.apache.logging.log4j")
        }
        vanillaAppLaunchImplementation(vanillaAppLaunchConfig)
        vanillaMixinsImplementation(vanillaAppLaunchConfig)
        vanillaMixinsImplementation(vanillaMinecraftConfig)
        vanillaMixinsImplementation(vanillaMinecraftClasspathConfig)

        // Annotation Processor
        // vanillaMixinsAnnotationProcessor(vanillaAppLaunchImplementation)
        // vanillaMixinsAnnotationProcessor("org.apache.logging.log4j:log4j-core:2.11.2")
        // vanillaMixinsAnnotationProcessor("org.spongepowered:mixin:$mixinVersion")

        testplugins?.apply {
            vanillaAppLaunchRuntime(project(testplugins.path)) {
                exclude(group = "org.spongepowered")
            }
        }
    }

    /*mixin {
        add(vanillaMixins, "spongevanilla.mixins.refmap.json")
    } */

    tasks {
        jar {
            manifest {
                attributes(mapOf(
                        "Specification-Title" to "SpongeVanilla",
                        "Specification-Vendor" to "SpongePowered",
                        "Specification-Version" to apiProject.version,
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                        "Implementation-Vendor" to "SpongePowered"
                ))
            }
        }
        val vanillaInstallerJar by registering(Jar::class) {
            archiveClassifier.set("installer")
            manifest {
                attributes(mapOf(
                        "Specification-Title" to "Sponge",
                        "Specification-Vendor" to "SpongePowered",
                        "Specification-Version" to apiProject.version,
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                        "Implementation-Vendor" to "SpongePowered"
                ))
            }
            from(vanillaInstaller.output)
        }
        val vanillaAppLaunchJar by registering(Jar::class) {
            archiveClassifier.set("applaunch")
            manifest {
                attributes(mapOf(
                        "Specification-Title" to "Sponge",
                        "Specification-Vendor" to "SpongePowered",
                        "Specification-Version" to apiProject.version,
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                        "Implementation-Vendor" to "SpongePowered"
                ))
            }
            from(vanillaAppLaunch.output)
        }
        val vanillaLaunchJar by registering(Jar::class) {
            archiveClassifier.set("launch")
            manifest {
                attributes(mapOf(
                        "Specification-Title" to "Sponge",
                        "Specification-Vendor" to "SpongePowered",
                        "Specification-Version" to apiProject.version,
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                        "Implementation-Vendor" to "SpongePowered"
                ))
            }
            from(vanillaLaunch.output)
        }
        val vanillaMixinsJar by registering(Jar::class) {
            archiveClassifier.set("mixins")
            manifest {
                attributes(mapOf(
                        "Specification-Title" to "Sponge",
                        "Specification-Vendor" to "SpongePowered",
                        "Specification-Version" to apiProject.version,
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion),
                        "Implementation-Vendor" to "SpongePowered"
                ))
            }
            from(vanillaMixins.output)
        }

        val installerResources = vanillaProject.layout.buildDirectory.dir("generated/resources/installer")
        vanillaInstaller.resources.srcDir(installerResources)
        val emitDependencies by registering(OutputDependenciesToJson::class) {
            group = "sponge"
            // everything in applaunch
            configuration.set(vanillaAppLaunchConfig)
            // except what we're providing through the installer
            excludeConfiguration.set(vanillaInstallerConfig)
            // for accesstransformers
            allowedClassifiers.add("service")

            outputFile.set(installerResources.map { it.file("libraries.json") })
        }
        named(vanillaInstaller.processResourcesTaskName).configure {
            dependsOn(emitDependencies)
        }

        shadowJar {
            mergeServiceFiles()
            val generateImplementationVersionString = generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion)

            archiveClassifier.set("universal")
            manifest {
                attributes(mapOf(
                        "Access-Widener" to "common.accesswidener",
                        "Main-Class" to "org.spongepowered.vanilla.installer.InstallerMain",
                        "Launch-Target" to "sponge_server_prod",
                        "Multi-Release" to true,
                        "Specification-Title" to "SpongeVanilla",
                        "Specification-Vendor" to "SpongePowered",
                        "Specification-Version" to apiProject.version,
                        "Implementation-Title" to project.name,
                        "Implementation-Version" to generateImplementationVersionString,
                        "Implementation-Vendor" to "SpongePowered"
                ))
            }
            from(commonProject.tasks.jar)
            from(commonProject.tasks.named("mixinsJar"))
            from(commonProject.tasks.named("accessorsJar"))
            from(commonProject.tasks.named("launchJar"))
            from(commonProject.tasks.named("applaunchJar"))
            from(jar)
            from(vanillaInstallerJar)
            from(vanillaAppLaunchJar)
            from(vanillaLaunchJar)
            from(vanillaMixinsJar)
            from(vanillaInstallerConfig)
            dependencies {
                include(project(":"))
                include(project(":SpongeAPI"))
            }
        }
        assemble {
            dependsOn(shadowJar)
        }

        /*reobf {
            create("vanillaAppLaunchJar")
            create("vanillaLaunchJar")
            create("vanillaMixinsJar")
            create("shadowJar")
        }*/
    }

    license {
        (this as ExtensionAware).extra.apply {
            this["name"] = "Sponge"
            this["organization"] = organization
            this["url"] = projectUrl
        }
        header = apiProject.file("HEADER.txt")

        include("**/*.java")
        newLine = false
    }

    val shadowJar by tasks.existing
    val vanillaInstallerJar by tasks.existing
    val vanillaAppLaunchJar by tasks.existing
    val vanillaLaunchJar by tasks.existing
    val vanillaMixinsJar by tasks.existing

    publishing {
        publications {
            register("sponge", MavenPublication::class) {

                artifact(shadowJar.get())
                artifact(vanillaInstallerJar.get())
                artifact(vanillaAppLaunchJar.get())
                artifact(vanillaLaunchJar.get())
                artifact(vanillaMixinsJar.get())
                artifact(tasks["applaunchSourceJar"])
                artifact(tasks["launchSourceJar"])
                artifact(tasks["mixinsSourceJar"])
                pom {
                    artifactId = project.name.toLowerCase()
                    this.name.set(project.name)
                    this.description.set(project.description)
                    this.url.set(projectUrl)

                    licenses {
                        license {
                            this.name.set("MIT")
                            this.url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/SpongePowered/Sponge.git")
                        developerConnection.set("scm:git:ssh://github.com/SpongePowered/Sponge.git")
                        this.url.set(projectUrl)
                    }
                }
            }
        }
    }
}

project("Spunbric") {
    val fabricProject = this

    apply {
        // TODO: Add new plugin for net.fabricmc.gradle.loom
        plugin("fabric-loom")
        plugin("java-library")
        plugin("maven-publish")
        plugin("org.cadixdev.licenser")
        plugin("com.github.johnrengelman.shadow")
    }

    repositories {
        maven(url = "https://maven.fabricmc.net/") {
            name = "Fabric"
        }
    }

    description = "The unofficial SpongeAPI implementation for the Fabric Mod Loader"
    version = generateImplementationVersionString(apiProject.version as String, minecraftVersion, recommendedVersion)
    println("Spunbric Version $version")

    val fabricMinecraftConfig by configurations.named("minecraft")

    val fabricAppLaunchConfig by configurations.register("applaunch") {
    }

    val fabricMain by sourceSets.named("main") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
    }

    val fabricLaunch by sourceSets.register("launch") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = this, targetSource = fabricMain, implProject = fabricProject, dependencyConfigName = fabricMain.implementationConfigurationName)
    }

    val fabricMixins by sourceSets.register("mixins") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = mixins.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = fabricMain, targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = fabricLaunch, targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
    }

    val fabricAppLaunch by sourceSets.register("applaunch") {
        // implementation (compile) dependencies
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = fabricLaunch, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName)
        //applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = vanillaInstaller, targetSource = this, implProject = fabricProject, dependencyConfigName = this.implementationConfigurationName) // TODO: Installer?
        applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = this, targetSource = fabricLaunch, implProject = fabricProject, dependencyConfigName = fabricLaunch.implementationConfigurationName)
        // runtime dependencies - literally add the rest of the project, because we want to launch the game
        applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = fabricMixins, targetSource = this, implProject = fabricProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = fabricLaunch, targetSource = this, implProject = fabricProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = mixins.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = fabricProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = fabricProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
        applyNamedDependencyOnOutput(originProject = fabricProject, sourceAdding = fabricMain, targetSource = this, implProject = fabricProject, dependencyConfigName = this.runtimeClasspathConfigurationName)
    }

    val fabricMixinsImplementation by configurations.named(fabricMixins.implementationConfigurationName) {
        extendsFrom(fabricMinecraftConfig)
        //extendsFrom(vanillaMinecraftClasspathConfig)
    }

    val fabricMixinsAnnotationProcessor by configurations.named(fabricMixins.annotationProcessorConfigurationName)

    val fabricAppLaunchImplementation by configurations.named(fabricAppLaunch.implementationConfigurationName) {
        extendsFrom(launchConfig)
    }

    val fabricAppLaunchRuntime by configurations.named(fabricAppLaunch.runtimeOnlyConfigurationName)

    println(extensions.findByName("loom"))

    configure<net.fabricmc.loom.LoomGradleExtension> {
        // TODO: Refactor loom to support multiple aw files
        commonProject.sourceSets["main"].resources
                .filter { it.name.endsWith(".accesswidener") }
                .files
                .forEach {
                    accessWidener(it)
                }
    }

    val LOOM_MIXIN_AP_ARGS = setOf("outRefMapFile", "defaultObfuscationEnv", "outMapFileNamedIntermediary", "inMapFileNamedIntermediary")

    fun applyMixinSourceSets(project: Project, base: SourceSet) {
        // Launch
        // can see entire project
        fabricLaunch.apply {
            compileClasspath += base.compileClasspath
            runtimeClasspath += base.runtimeClasspath
            project.dependencies.add(implementationConfigurationName, base.output)
        }

        // Mixin
        // can see entire project
        fabricMixins.apply {
            compileClasspath += base.compileClasspath
            runtimeClasspath += base.runtimeClasspath
            project.dependencies.add(implementationConfigurationName, base.output)
        }

        // TODO: Provide a nicer way to do this in Loom
        val refmapArgName = "-AoutRefMapFile"
        project.afterEvaluate {
            // No accessors?
            // configure refmap
//            tasks.named<JavaCompile>(accessor.get().compileJavaTaskName).configure { c ->
//                c.options.compilerArgs.removeIf { arg -> arg.startsWith(refmapArgName) }
//                c.options.compilerArgs.add("$refmapArgName=${c.destinationDir}/${project.name.toLowerCase()}-${accessor.get().name}-refmap.json")
//            }
            // And remove Mixin AP parameters from the main source set
            tasks.named<JavaCompile>(base.compileJavaTaskName).configure {
                options.compilerArgs.removeIf { arg -> LOOM_MIXIN_AP_ARGS.any { mixin -> arg.contains(mixin) } }
            }
        }

        // Make sure Mixin AP doesn't run on the base source set
        project.configurations.named(base.annotationProcessorConfigurationName).configure {
            exclude(mapOf("group" to "net.fabricmc", "module" to "fabric-mixin-compile-extensions"))
            exclude(mapOf("group" to "net.fabricmc", "module" to "sponge-mixin"))
        }
    }

    applyMixinSourceSets(fabricProject, fabricMain)

    dependencies {
        // Specify required platform dependencies for Spunbric
        "minecraft"("com.mojang:minecraft:$minecraftVersion")

        val loomExtension = fabricProject.extensions.findByName("loom") as net.fabricmc.loom.LoomGradleExtension

        "mappings"(loomExtension.officialMojangMappings())
        // Fabric loader
        "modImplementation"("net.fabricmc:fabric-loader:0.11.1")
        // Fabric API
        // Sponge requires Fabric API as we reimplement where needed to preserve compatibility with most mods
        "modImplementation"("net.fabricmc.fabric-api:fabric-api:0.30.0+1.16")

        api(launch.map { it.output })
        implementation(accessors.map { it.output })
        implementation(project(commonProject.path)) {
            exclude(group = "net.minecraft")
            // TODO: Exclude sponge's mixin?
        }
        // TODO: AP needed?
        // annotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")

        fabricMixinsImplementation(project(commonProject.path))
        add(fabricLaunch.implementationConfigurationName, project(":SpongeAPI"))
        add(fabricLaunch.implementationConfigurationName, fabricAppLaunchConfig)
        add(fabricLaunch.implementationConfigurationName, fabricMinecraftConfig)
        //add(fabricLaunch.implementationConfigurationName, vanillaMinecraftClasspathConfig)

        fabricAppLaunchConfig(project(":SpongeAPI"))
        fabricAppLaunchConfig(platform("net.kyori:adventure-bom:4.4.0")) // TODO: Verify platform plugins work fine with loom
        fabricAppLaunchConfig("net.kyori:adventure-serializer-configurate4")
        fabricAppLaunchConfig("org.spongepowered:mixin:$mixinVersion")
        fabricAppLaunchConfig("org.ow2.asm:asm-util:$asmVersion")
        fabricAppLaunchConfig("org.ow2.asm:asm-tree:$asmVersion")
        fabricAppLaunchConfig("com.google.guava:guava:$guavaVersion")
        fabricAppLaunchConfig("org.spongepowered:plugin-spi:$pluginSpiVersion")
        fabricAppLaunchConfig("javax.inject:javax.inject:1")
        fabricAppLaunchConfig("org.apache.logging.log4j:log4j-api:2.11.2")
        fabricAppLaunchConfig("org.apache.logging.log4j:log4j-core:2.11.2")
        fabricAppLaunchConfig("com.zaxxer:HikariCP:2.6.3")
        fabricAppLaunchConfig("org.apache.logging.log4j:log4j-slf4j-impl:2.11.2")
        fabricAppLaunchConfig(platform("org.spongepowered:configurate-bom:4.0.0"))
        fabricAppLaunchConfig("org.spongepowered:configurate-core") {
            exclude(group = "org.checkerframework", module = "checker-qual")
        }
        fabricAppLaunchConfig("org.spongepowered:configurate-hocon") {
            exclude(group = "org.spongepowered", module = "configurate-core")
            exclude(group = "org.checkerframework", module = "checker-qual")
        }
        fabricAppLaunchConfig("org.spongepowered:configurate-jackson") {
            exclude(group = "org.spongepowered", module = "configurate-core")
            exclude(group = "org.checkerframework", module = "checker-qual")
        }

        // Fabric's Launch dependencies are just loader

        fabricAppLaunchImplementation(fabricAppLaunchConfig)
        fabricMixinsImplementation(fabricAppLaunchConfig)
        fabricMixinsImplementation(fabricMinecraftConfig)
        //fabricMixinsImplementation(vanillaMinecraftClasspathConfig)

        testplugins?.apply {
            fabricAppLaunchRuntime(project(testplugins.path)) {
                exclude(group = "org.spongepowered")
            }
        }
    }
}

abstract class OutputDependenciesToJson: DefaultTask() {

    companion object {
        val GSON: com.google.gson.Gson = com.google.gson.GsonBuilder().also {
            it.setPrettyPrinting()
        }.create()
    }

    /**
     * A single dependency
     */
    data class DependencyDescriptor(val group: String, val module: String, val version: String, val md5: String)

    /**
     * A manifest containing a list of dependencies.
     *
     * At runtime, transitive dependencies won't be traversed, so this needs to
     * include direct + transitive depends.
     */
    data class DependencyManifest(val dependencies: List<DependencyDescriptor>)

    /**
     * Configuration to gather depenency artifacts from
     */
    @get:org.gradle.api.tasks.Input
    abstract val configuration: Property<Configuration>

    /**
     * Excludes configuration, to remove certain entries from dependencies and transitive dependencies of [configuration]
     */
    @get:org.gradle.api.tasks.Input
    abstract val excludeConfiguration: Property<Configuration>

    /**
     * Classifiers to include in the dependency manifest. The empty string identifies no classifier.
     */
    @get:org.gradle.api.tasks.Input
    abstract val allowedClassifiers: SetProperty<String>

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        allowedClassifiers.add("")
    }

    @org.gradle.api.tasks.TaskAction
    fun generateDependenciesJson() {
        val foundConfig = if (this.excludeConfiguration.isPresent) {
            val config = project.configurations.detachedConfiguration(*this.configuration.get().allDependencies.toTypedArray())
            val excludes = this.excludeConfiguration.get()
            excludes.allDependencies.forEach {
                config.exclude(group = it.group, module = it.name)
            }
            config
        } else {
            this.configuration.get()
        }

        val manifest = foundConfig.resolvedConfiguration.firstLevelModuleDependencies.asSequence()
                .flatMap { it.allModuleArtifacts.asSequence() }
                // only jars with the allowed classifiers
                .filter { it.extension == "jar" && allowedClassifiers.get().contains(it.classifier ?: "") }
                .filter { it.moduleVersion.id.name != "SpongeAPI" }
                .distinct()
                .map { dependency ->

                    val ident = dependency.moduleVersion.id
                    val version = (dependency.id.componentIdentifier as? ModuleComponentIdentifier)?.version ?: ident.version
                    // Get file input stream for reading the file content
                    val md5hash = dependency.file.inputStream().use {
                        val hasher = MessageDigest.getInstance("MD5")
                        val buf = ByteArray(4096)
                        var read: Int = it.read(buf)
                        while (read != -1) {
                            hasher.update(buf, 0, read)
                            read = it.read(buf)
                        }

                        hasher.digest().joinToString("") { "%02x".format(it) }
                    }

                    // create descriptor
                    DependencyDescriptor(
                            group = ident.group,
                            module = ident.name,
                            version = version,
                            md5 = md5hash
                    )
                }.toList().run {
                    DependencyManifest(this)
                }

        println("Writing to ${outputFile.get().asFile}")
        this.outputFile.get().asFile.bufferedWriter(Charsets.UTF_8).use {
            GSON.toJson(manifest, it)
        }
    }

}
/*
val spongeForge: Project? = subprojects.find { "SpongeForge".equals(it.name) }
if (spongeForge != null) {
    project("SpongeForge") {
        val forgeProject = this
        apply {
            plugin("net.minecraftforge.gradle")
            plugin("org.spongepowered.mixin")
            plugin("java-library")
            plugin("maven-publish")
            plugin("idea")
            plugin("eclipse")
            plugin("org.cadixdev.licenser")
            plugin("com.github.johnrengelman.shadow")
        }

        val forgeDep: String by project
        val forgeOrg: String by project
        val forgeVersion: String by project

        description = "The SpongeAPI implementation for MinecraftForge"
        version = generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion, forgeVersion)

        val forgeMinecraftConfig by configurations.named("minecraft")
        val forgeLaunchConfig by configurations.register("launcher") {
            extendsFrom(forgeMinecraftConfig)
        }

        val forgeMain by sourceSets.named("main") {
            // implementation (compile) dependencies
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
        }
        val forgeLaunch by sourceSets.register("launch") {
            // implementation (compile) dependencies
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = forgeProject, sourceAdding = this, targetSource = forgeMain, implProject = forgeProject, dependencyConfigName = forgeMain.implementationConfigurationName)
        }
        val forgeAccessors by sourceSets.register("accessors") {
            // implementation (compile) dependencies
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = forgeProject, sourceAdding = this, targetSource = forgeMain, implProject = forgeProject, dependencyConfigName = forgeMain.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = forgeProject, sourceAdding = forgeLaunch, targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
        }
        val forgeMixins by sourceSets.register("mixins") {
            // implementation (compile) dependencies
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = mixins.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = accessors.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = forgeProject, sourceAdding = forgeAccessors, targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = launch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = applaunch.get(), targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = commonProject, sourceAdding = main, targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = forgeProject, sourceAdding = forgeMain, targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
            applyNamedDependencyOnOutput(originProject = forgeProject, sourceAdding = forgeLaunch, targetSource = this, implProject = forgeProject, dependencyConfigName = this.implementationConfigurationName)
        }

        val forgeAccessorsAnnotationProcessor by configurations.named(forgeAccessors.annotationProcessorConfigurationName)
        val forgeAccessorsImplementation by configurations.named(forgeAccessors.implementationConfigurationName)
        val forgeMixinsImplementation by configurations.named(forgeMixins.implementationConfigurationName) {
            extendsFrom(forgeMinecraftConfig)
        }
        val forgeMixinsAnnotationProcessor by configurations.named(forgeMixins.annotationProcessorConfigurationName)

        configure<net.minecraftforge.gradle.userdev.UserDevExtension> {
            mappings(mappingsChannel, mappingsVersion)
            runs {
                create("server") {
                    workingDirectory(forgeProject.file("./run"))
                    args.addAll(listOf("nogui", "--launchTarget", "sponge_server_dev"))
                    main = "org.spongepowered.forge.modlauncher.Main"
                }

                create("client") {
                    environment("target", "client")
                    workingDirectory(forgeProject.file("./run"))
                    args.addAll(listOf("--launchTarget", "sponge_client_dev", "--version", "1.15.2", "--accessToken", "0"))
                    main = "org.spongepowered.forge.modlauncher.Main"
                }
            }
            commonProject.sourceSets["main"].resources
                    .filter { it.name.endsWith("_at.cfg") }
                    .files
                    .forEach {
                        accessTransformer(it)
                    }

            forgeProject.sourceSets["main"].resources
                    .filter { it.name.endsWith("_at.cfg") }
                    .files
                    .forEach { accessTransformer(it) }
        }

        dependencies {
            minecraft("$forgeOrg:$forgeDep:$minecraftVersion-$forgeVersion")

            api(launch.get().output)
            implementation(accessors.get().output)
            implementation(project(commonProject.path)) {
                exclude(group = "net.minecraft", module = minecraftDep)
            }
            forgeMixinsImplementation(project(commonProject.path)) {
                exclude(group = "net.minecraft", module = minecraftDep)
            }
            annotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")
            add(forgeLaunch.implementationConfigurationName, project(":SpongeAPI"))

            forgeLaunchConfig("org.spongepowered:mixin:$mixinVersion")
            forgeLaunchConfig("org.ow2.asm:asm-util:$asmVersion")
            forgeLaunchConfig("org.ow2.asm:asm-tree:$asmVersion")
            forgeLaunchConfig("org.spongepowered:plugin-spi:$pluginSpiVersion")
            forgeLaunchConfig("javax.inject:javax.inject:1")
            forgeLaunchConfig("org.apache.logging.log4j:log4j-api:2.11.2")
            forgeLaunchConfig("org.apache.logging.log4j:log4j-core:2.11.2")
            runtime("com.zaxxer:HikariCP:2.6.3")
            runtime("org.apache.logging.log4j:log4j-slf4j-impl:2.11.2")

            // Launch Dependencies - Needed to bootstrap the engine(s)
            // The ModLauncher compatibility launch layer
            forgeMixinsImplementation(forgeLaunchConfig)
            forgeAccessorsImplementation(forgeLaunchConfig)

            // Annotation Processor
            forgeAccessorsAnnotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")
            forgeMixinsAnnotationProcessor("org.spongepowered:mixin:$mixinVersion:processor")
            forgeAccessorsAnnotationProcessor("org.apache.logging.log4j:log4j-core:2.11.2")
            forgeMixinsAnnotationProcessor("org.apache.logging.log4j:log4j-core:2.11.2")

            testplugins?.apply {
                add(forgeLaunch.runtimeConfigurationName, project(testplugins.path)) {
                    exclude(group = "org.spongepowered")
                }
            }
        }

        mixin {
            add(forgeMixins, "spongeforge.mixins.refmap.json")
            add(forgeAccessors, "spongeforge.accessors.refmap.json")
        }

        tasks {
            jar {
                manifest {
                    attributes(mapOf(
                            "Specification-Title" to "SpongeForge",
                            "Specification-Vendor" to "SpongePowered",
                            "Specification-Version" to apiProject.version,
                            "Implementation-Title" to project.name,
                            "Implementation-Version" to generatePlatformBuildVersionString(apiProject.version as String, minecraftVersion, recommendedVersion, forgeVersion),
                            "Implementation-Vendor" to "SpongePowered"
                    ))
                }
            }
            reobf {

            }

        }

        license {
            (this as ExtensionAware).extra.apply {
                this["name"] = "Sponge"
                this["organization"] = organization
                this["url"] = projectUrl
            }
            header = apiProject.file("HEADER.txt")

            // include("**<slash>*.java")
            newLine = false
        }
    }
}*/
