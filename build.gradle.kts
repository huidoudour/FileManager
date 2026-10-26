plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.test) apply false
}

subprojects {
    layout.buildDirectory.value(
        rootProject.layout.buildDirectory.get().dir(project.name)
    )
}
