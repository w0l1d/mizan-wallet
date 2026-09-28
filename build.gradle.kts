// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Run with:
    // ./gradlew detekt // Simple report in the console
    // ./gradlew detektFormat // To check with enabled auto-correction
    id("ivy.detekt")
    id("com.jraska.module.graph.assertion")

    alias(libs.plugins.gradleWrapperUpgrade)

    alias(libs.plugins.koverPlugin)

    // Run with:
    // ./gradlew dependencyUpdates -Drevision=release
    alias(libs.plugins.dependencyUpdates)

    // Run with:
    // ./gradlew dependencyCheckAnalyze
    alias(libs.plugins.owaspDependencyCheck)

    // Applied in :app - declared here so the plugin lands on the build classpath once.
    alias(libs.plugins.sentry) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
    kover {
        reports {
            filters {
                excludes {
                    classes(
                        "*Activity",
                        "*Activity\$*",
                        "*.BuildConfig",
                        "dagger.hilt.*",
                        "hilt_aggregated_deps.*",
                        "*.Hilt_*"
                    )
                    annotatedBy("@Composable")
                }
            }
        }
    }
}

wrapperUpgrade {
    gradle {
        create("ivyWallet") {
            repo.set("Ivy-Apps/ivy-wallet")
            baseBranch.set("main")
        }
    }
}
