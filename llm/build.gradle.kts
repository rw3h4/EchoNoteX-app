plugins {
    alias(libs.plugins.android.library)
}

configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "org.rw3h4.echonotex.llm"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters.add("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++17")
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {

}
