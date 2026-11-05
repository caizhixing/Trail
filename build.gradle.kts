buildscript {
    configurations.classpath {
        resolutionStrategy {
            // 离线缓存无 kotlin-reflect 2.4.10（AGP 深层传递依赖），钉到本机已缓存的 2.4.0（仅构建期 classpath）
            force("org.jetbrains.kotlin:kotlin-reflect:2.2.10")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            // 离线缓存无 kotlin-stdlib 2.2.20（传递依赖默认钉版），统一钉到与 AGP 9.5 内置 KGP 一致的 2.2.10
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
        }
    }
}
