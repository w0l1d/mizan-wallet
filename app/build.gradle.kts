import java.time.LocalDate

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.android")
    org.jetbrains.kotlin.plugin.compose
    id("dagger.hilt.android.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.ivy.wallet"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    // ---------------------------------------------------------------------
    // Release channel
    //
    // A build is a RELEASE when its commit carries a git tag, and a BETA
    // otherwise. libs.versions.toml always holds the LAST RELEASED version,
    // so a beta must not inherit it verbatim - that is what made every
    // develop build masquerade as the previous release. Instead a beta
    // advertises the release it is heading toward:
    //
    //   release   2026.10.01                   (207)   "Mizan"
    //   beta      2026.09.24-beta.49+a1a2855   (207)   "Mizan Beta"
    //
    //   <build date>-beta.<commits since last release tag>+<short sha>
    //
    // The counter orders consecutive betas; the sha pins the exact commit.
    // Beta version code is last released code + 1 - the code the next
    // release will carry - so a beta always outranks the release it
    // supersedes, and the eventual release installs cleanly over it.
    // ---------------------------------------------------------------------

    // Returns trimmed stdout, or null when git is unavailable or says nothing.
    fun git(vararg args: String): String? = try {
        providers.exec { commandLine("git", *args) }
            .standardOutput.asText.get().trim().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    val baseVersionName = libs.versions.version.name.get()
    val baseVersionCode = libs.versions.version.code.get().toInt()

    val shortSha = git("rev-parse", "--short", "HEAD")

    // `git tag --points-at HEAD` exits 0 either way; empty output = untagged.
    val isRelease = git("tag", "--points-at", "HEAD") != null

    // Commits since the last release tag, so consecutive betas are ordered.
    // Falls back to total commit count before the first tag ever exists.
    val lastReleaseTag = git("describe", "--tags", "--abbrev=0")
    val commitsSinceRelease = when (lastReleaseTag) {
        null -> git("rev-list", "--count", "HEAD")
        else -> git("rev-list", "--count", "$lastReleaseTag..HEAD")
    }?.toIntOrNull() ?: 0

    // Read through providers.exec so the configuration cache re-evaluates the
    // date each build instead of freezing an earlier day's value into a
    // cached entry. LocalDate covers platforms without a `date` binary.
    val buildDate = try {
        providers.exec { commandLine("date", "+%Y.%m.%d") }
            .standardOutput.asText.get().trim()
    } catch (_: Exception) {
        LocalDate.now().toString().replace('-', '.')
    }

    // No git at all (e.g. a source archive): fall back to the released
    // version rather than inventing a beta string we cannot substantiate.
    val isBeta = !isRelease && shortSha != null

    val appVersionName = when {
        isBeta -> "$buildDate-beta.$commitsSinceRelease+$shortSha"
        else -> baseVersionName
    }
    val appVersionCode = when {
        isBeta -> baseVersionCode + 1
        else -> baseVersionCode
    }
    // Launcher label, so a beta is obvious on the device without opening
    // Settings. Applied to `demo` only: `release` feeds Google Play, which
    // must never be relabelled by an accidental untagged build.
    val demoAppName = if (isBeta) "Mizan Beta" else "Mizan"

    defaultConfig {
        applicationId = "dev.w0l1d.mizan"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.compile.sdk.get().toInt()

        versionName = appVersionName
        versionCode = appVersionCode
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.jks")
            storePassword = "IVY7834!DEbug"
            keyAlias = "debug"
            keyPassword = "IVY7834!DEbug"
        }

        create("release") {
            storeFile = file("../sign.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            isDebuggable = false
            isDefault = false

            signingConfig = signingConfigs.getByName("release")

            resValue("string", "app_name", "Mizan")
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            isDebuggable = true
            isDefault = true

            signingConfig = signingConfigs.getByName("debug")

            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Mizan Debug")
        }

        create("demo") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            matchingFallbacks.add("release")

            isDebuggable = false
            isDefault = false

            signingConfig = signingConfigs.getByName("debug")

            applicationIdSuffix = ".debug"
            resValue("string", "app_name", demoAppName)
        }
    }

    val javaVersion = libs.versions.jvm.target.get()
    kotlinOptions {
        jvmTarget = javaVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
        targetCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += "ComposeViewModelInjection"
        checkDependencies = true
        abortOnError = false
        checkReleaseBuilds = false
        htmlReport = true
        htmlOutput = file("${project.rootDir}/build/reports/lint/lint.html")
        xmlReport = true
        xmlOutput = file("${project.rootDir}/build/reports/lint/lint.xml")
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(projects.feature.attributions)
    implementation(projects.feature.balance)
    implementation(projects.feature.budgets)
    implementation(projects.feature.categories)
    implementation(projects.feature.contributors)
    implementation(projects.feature.disclaimer)
    implementation(projects.feature.editTransaction)
    implementation(projects.feature.exchangeRates)
    implementation(projects.feature.features)
    implementation(projects.feature.home)
    implementation(projects.feature.importData)
    implementation(projects.feature.loans)
    implementation(projects.feature.main)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.piechart)
    implementation(projects.feature.plannedPayments)
    implementation(projects.feature.poll.impl)
    implementation(projects.feature.poll.public)
    implementation(projects.feature.releases)
    implementation(projects.feature.reports)
    implementation(projects.feature.search)
    implementation(projects.feature.settings)
    implementation(projects.feature.transactions)
    implementation(projects.feature.poll.impl)
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)
    implementation(projects.temp.legacyCode)
    implementation(projects.temp.oldDesign)
    implementation(projects.widget.addTransaction)
    implementation(projects.widget.balance)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.kotlin.android)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.activity)
    implementation(libs.bundles.google)
    implementation(libs.bundles.firebase)
    implementation(libs.datastore)
    implementation(libs.androidx.security)
    implementation(libs.androidx.biometrics)

    implementation(libs.bundles.hilt)
    implementation(libs.material)
    ksp(libs.hilt.compiler)

    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(libs.timber)
    implementation(libs.keval)
    implementation(libs.bundles.opencsv)
    implementation(libs.androidx.work)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.androidx.work.testing)

    lintChecks(libs.slack.lint.compose)
}
