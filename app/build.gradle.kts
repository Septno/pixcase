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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.pixcase"
        minSdk = 26
        targetSdk = 35
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
            "-Xexplicit-api=strict",
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
        disable += setOf("MissingTranslation") // 我们用 resourceConfigurations 显式控制多语言
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
ktlint {
    android = true
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/*.kts")
    }
}

// detekt:不指定外部 config,使用默认规则集 + buildUponDefaultConfig
// (项目级自定义规则后续按需写到 $rootDir/config/detekt/detekt.yml 再打开 config.setFrom)
detekt {
    buildUponDefaultConfig = true
    autoCorrect = false
    allRules = false
}