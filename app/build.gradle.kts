import io.sentry.android.gradle.extensions.InstrumentationFeature

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
    alias(libs.plugins.sentry)
}

android {
    namespace = "com.ivy.wallet"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    // ---------------------------------------------------------------------
    // Release channel
    //
    // A build is a RELEASE when its commit carries a release tag (`v*`), and a
    // BETA otherwise. libs.versions.toml always holds the LAST RELEASED version,
    // so a beta must not inherit it verbatim - that is what made every develop
    // build masquerade as the previous release. A beta instead names itself
    // after the commit it was built from:
    //
    //   release   2026.10.01                    (207)
    //   beta      2026.09.24-beta.49+a1a28556   (20600049)
    //
    //   <commit date>-beta.<commits since last release tag>+<short sha>
    //
    // Every part is a property of the COMMIT, never of the machine or the day
    // the build ran. Two builds of the same commit therefore always produce the
    // same version, which is what lets the publish step tag the commit with the
    // exact string already baked into the APK. The counter orders consecutive
    // betas and the sha keeps two branches at the same depth from colliding.
    //
    // Beta version codes live in their own numbering space, <released code> *
    // 100000 + <commits since release>, so consecutive betas upgrade over each
    // other properly. They never collide with the release lineage: `demo`
    // carries applicationIdSuffix ".debug" and so is a different app from the
    // `release` build that Google Play sees, which keeps the plain toml code.
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

    // Release tags are `v<date>-<code>`; beta tags are the beta version string
    // itself. Matching on `v*` is what stops a beta tag - which the publish step
    // writes onto this very commit - from promoting the next build to a release.
    val isRelease = git("tag", "--points-at", "HEAD", "--list", "v*") != null

    // Commits since the last RELEASE tag, so consecutive betas are ordered and
    // beta tags do not reset the counter. Falls back to the total commit count
    // before the first release tag ever exists.
    val lastReleaseTag = git("describe", "--tags", "--abbrev=0", "--match", "v*")
    val commitsSinceRelease = when (lastReleaseTag) {
        null -> git("rev-list", "--count", "HEAD")
        else -> git("rev-list", "--count", "$lastReleaseTag..HEAD")
    }?.toIntOrNull() ?: 0

    // The COMMIT's date, not today's. A version derived from the build date
    // cannot be recomputed tomorrow, which would mean the publish step could
    // never tag a commit with the string already inside its APK.
    val commitDate = git("log", "-1", "--format=%cd", "--date=format:%Y.%m.%d")

    // Uncommitted work cannot be named by a tag. Marking it here is what makes
    // the publish gate able to refuse such a build instead of shipping a version
    // that points at a commit whose contents it does not actually have.
    val isDirty = git("status", "--porcelain") != null

    // No git at all (e.g. a source archive): fall back to the released version
    // rather than inventing a beta string we cannot substantiate.
    val isBeta = !isRelease && shortSha != null && commitDate != null

    val appVersionName = when {
        isBeta -> buildString {
            append("$commitDate-beta.$commitsSinceRelease+$shortSha")
            if (isDirty) append(".dirty")
        }
        else -> baseVersionName
    }
    val appVersionCode = when {
        isBeta -> baseVersionCode * 100000 + commitsSinceRelease
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
    implementation(platform(libs.sentry.bom))
    implementation(libs.sentry.android)
    implementation(libs.sentry.android.timber)
    implementation(libs.sentry.compose.android)
    implementation(libs.keval)
    implementation(libs.bundles.opencsv)
    implementation(libs.androidx.work)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.androidx.work.testing)

    lintChecks(libs.slack.lint.compose)
}

sentry {
    org = "w0l1d"
    projectName = "mizan-android"
    authToken = System.getenv("SENTRY_AUTH_TOKEN")

    // Bytecode instrumentation - no source changes needed. OKHTTP also covers Ktor,
    // which runs on the OkHttp engine here.
    tracingInstrumentation {
        enabled = true
        features = setOf(
            InstrumentationFeature.DATABASE,
            InstrumentationFeature.FILE_IO,
            InstrumentationFeature.OKHTTP,
            InstrumentationFeature.COMPOSE,
        )
    }

    // R8 mangles `release` and `demo`; without the mapping file Sentry stack traces are
    // unreadable. Only attempt the upload when CI (or the developer) supplied a token,
    // so a plain local release build still succeeds.
    includeProguardMapping = true
    autoUploadProguardMapping = System.getenv("SENTRY_AUTH_TOKEN") != null

    // Source context needs the same token; it ships snippets of source to Sentry.
    includeSourceContext = false
}
