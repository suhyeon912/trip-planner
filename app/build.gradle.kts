import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)   // Room 이 코드를 자동 생성할 때 사용
}

// 공유·초대(Firebase): app/google-services.json 이 있을 때만 켬 → 없어도 빌드·실행은 됨 (공유 메뉴만 '설정 필요' 안내)
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) apply(plugin = "com.google.gms.google-services")

// 지도 키는 local.properties 에서 읽음 (깃에 올리지 않는 파일). 없으면 빈 값 → OpenStreetMap 으로 표시
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localKey(name: String): String = localProps.getProperty(name, "").trim()

android {
    namespace = "com.tripplanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tripplanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // 국내 지도: 카카오 네이티브 앱 키 / 해외 지도: Google Maps API 키
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${localKey("KAKAO_NATIVE_APP_KEY")}\"")
        buildConfigField("String", "MAPS_API_KEY", "\"${localKey("MAPS_API_KEY")}\"")
        // 장소 검색(국내): 카카오 로컬 REST API 키
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebaseConfig.toString())
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${localKey("KAKAO_REST_API_KEY")}\"")
        manifestPlaceholders["MAPS_API_KEY"] = localKey("MAPS_API_KEY")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        // 카카오맵 엔진(.so)을 설치 시 풀어서 저장 → x86 에뮬레이터의 ARM 변환이 파일을 찾을 수 있게 함
        jniLibs { useLegacyPackaging = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)

    // ViewModel · LiveData (화면 회전에도 데이터 유지)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room (기기 안 데이터베이스)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 앱 안 지도 (OpenStreetMap). API 키·결제 등록 없이 국내·해외 모두 표시
    implementation(libs.osmdroid.android)
    // 국내: 카카오맵 SDK, 해외: Google Maps SDK (키가 없으면 위 OpenStreetMap 으로 대신 표시)
    implementation(libs.kakao.map)
    implementation(libs.play.services.maps)

    // 공유·초대: Firebase 익명 로그인 + Firestore (무료 Spark 요금제)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")   // Firebase 작업을 await 로

    testImplementation(libs.junit)
    // 단위 테스트에서 org.json(장소 검색 결과 읽기)을 실제로 쓰기 위함
    testImplementation("org.json:json:20240303")
}
