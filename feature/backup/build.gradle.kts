plugins {
    id("ivy.feature")
}

android {
    namespace = "com.ivy.backup"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.data.model)
    implementation(projects.shared.domain)
    implementation(projects.shared.ui.core)
    implementation(projects.shared.ui.navigation)

    implementation(libs.androidx.work)

    testImplementation(projects.shared.ui.testing)
    testImplementation(libs.androidx.work.testing)
}
