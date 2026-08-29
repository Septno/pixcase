plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.example.pixcase"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.pixcase"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // 单元测试注解:用 JUnit4 + Robolectric 兼容 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // 默认 locale:首次启动按系统语言,但保留英文 strings 作为 fallback。
        resourceConfigurations += listOf("en", "zh-rCN")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // 阶段 0 暂用 debug 签名,正式发布再换
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        allWarningsAsErrors = true
        freeCompilerArgs += listOf(
            // 关闭 -Xexplicit-api:此规则为库项目设计(App 项目用太严),
            // 但与 allWarningsAsErrors = true 冲突,即便 warning 级别也会变 error。
            // 这里直接不启用,顶层声明保留 Kotlin 默认 visibility。
            "-Xjsr305=strict",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }

    // 把 lint-baseline 放在模块根,跑一次 ./gradlew lint 后自动生成
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
        // 阶段 0 暂时禁用的检查(理由见 lint-baseline.xml 注释):
        // - SelectedPhotoAccess:阶段 1+ 再适配 Android 14 部分照片访问
        // - GradleDependency:KSP 2.2.21-2.0.5 与 Kotlin 2.0.21 不兼容,固定 2.0.21-1.0.28
        // - ObsoleteSdkInt:lint 误报 mipmap-anydpi-v26,实际是 adaptive icon 必要的版本限定
        // - RemoveWorkManagerInitializer:on-demand 初始化已在 manifest 处理
        // - AndroidGradlePluginVersion:计划文档工具链约定 AGP 8.7+,lint 提示"有更新版本
        //   可用"是软警告,与项目版本范围策略冲突;warningsAsErrors=true 会把它升 error
        disable += setOf(
            "MissingTranslation",
            "SelectedPhotoAccess",
            "GradleDependency",
            "ObsoleteSdkInt",
            "RemoveWorkManagerInitializer",
            "AndroidGradlePluginVersion"
        )
    }
}

// 依赖版本警告:引入 alpha/beta 版本时报 warning,避免意外。
// requested.version 是 String?,所以用 ?. 安全调用,避免 nullable receiver 编译错误。
configurations.all {
    resolutionStrategy.eachDependency {
        val v = requested.version
        if (v != null && (v.contains("alpha") || v.contains("beta"))) {
            logger.warn("⚠️ 引入非稳定版依赖: ${requested.group}:${requested.name}:$v")
        }
    }
}

dependencies {
    // AndroidX 核心
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // ML Kit(本地推理,无网络)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.image.labeling)

    // Desugar(让 minSdk 26 也能用 Java 8+ API)
    coreLibraryDesugaring(libs.android.desugar)

    // 单元测试
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // Instrumented 测试
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.roborazzi)
    androidTestImplementation(libs.roborazzi.compose)
}

// 让 ktlint 不检查 build / generated 目录
//
// 规则调整说明:
// - disabledRules = ["standard:function-naming"]:Compose 官方惯例
//   @Composable 函数用 PascalCase(如 PixcaseTheme / PlaceholderScreen),
//   ktlint 默认要求 camelCase 会误报,ktlint 不支持 ignoreAnnotated,
//   故直接在 ktlint {} block 禁用该规则(detekt 在外部 yml 里用 ignoreAnnotated)。
ktlint {
    android = true
    disabledRules.add("standard:function-naming")
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/*.kts")
    }
}

// detekt:外部 yml 在 $rootDir/config/detekt/detekt.yml,继承默认 + 项目级调整
detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    allRules = false
}