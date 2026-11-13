import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val allowedEdges = mapOf(
    ":app" to setOf(":core:model", ":data:energy", ":data:location", ":data:poi", ":data:navigation", ":feature:assist"),
    ":core:model" to emptySet(),
    ":data:energy" to setOf(":core:model"),
    ":data:location" to setOf(":core:model"),
    ":data:poi" to setOf(":core:model"),
    ":data:navigation" to setOf(":core:model"),
    ":feature:assist" to setOf(":core:model", ":data:energy", ":data:location", ":data:poi", ":data:navigation"),
)

val verifyArchitecture by tasks.registering {
    group = "verification"
    doLast {
        subprojects.filter { it.buildFile.exists() }.forEach { module ->
            val allowed = checkNotNull(allowedEdges[module.path]) {
                "Declare ${module.path} in allowedEdges before adding the module"
            }
            module.configurations.forEach { config ->
                config.dependencies.withType<ProjectDependency>().forEach { dependency ->
                    check(dependency.path == module.path || dependency.path in allowed) {
                        "Forbidden module dependency: ${module.path} -> ${dependency.path}"
                    }
                }
            }
        }
    }
}

val verifyAll = tasks.register("verifyAll") {
    group = "verification"
    dependsOn(verifyArchitecture, ":app:assembleDebug")
}

subprojects {
    dependencyLocking { lockAllConfigurations() }
    val module = path
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        verifyAll.configure { dependsOn("$module:test") }
    }
    listOf("com.android.application", "com.android.library").forEach { plugin ->
        pluginManager.withPlugin(plugin) {
            verifyAll.configure { dependsOn("$module:testDebugUnitTest", "$module:lintDebug") }
        }
    }
}
