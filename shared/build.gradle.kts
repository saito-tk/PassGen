plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
}
kotlin {
    android {
        namespace = "jp.passgen.core"
        compileSdk = 37
        minSdk = 28
        withHostTestBuilder {}.configure {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "PassgenCore"
            isStatic = true
        }
    }
    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
        val jvmAndAndroidMain = create("jvmAndAndroidMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmMain.get().dependsOn(jvmAndAndroidMain)
    }
}
