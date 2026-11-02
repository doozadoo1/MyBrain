plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        withHostTest {}
    }

    // Desktop (Windows/Linux/macOS) target shared by all KMP libraries.
    jvm()
}
