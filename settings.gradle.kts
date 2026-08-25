pluginManagement {
    repositories {
        google()
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

rootProject.name = "AndroidSaunaProject"

// shared: ととのい値計算ロジック・Room DB・共通モデル(Wear/Mobile両方から参照)
include(":shared")
// wear: Wear OS単体アプリ(心拍計測・セッション記録)
include(":wear")
// mobile: スマホ側アプリ(履歴閲覧・グラフ表示)
include(":mobile")
