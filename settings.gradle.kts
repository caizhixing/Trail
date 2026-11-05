pluginManagement {
    // 离线缓存无全部 plugin marker，直接映射到本机已缓存的实现工件
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application" -> useModule("com.android.tools.build:gradle:9.5.0")
                "com.android.library" -> useModule("com.android.tools.build:gradle:9.5.0")
                "org.jetbrains.kotlin.android" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
                "org.jetbrains.kotlin.jvm" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
                "org.jetbrains.kotlin.plugin.compose" -> useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.10")
            }
        }
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Trail"

include(":trail-api")
include(":trail-spi")
include(":trail-debug")
include(":trail-noop")
include(":trail-okhttp")
include(":sample-app")
