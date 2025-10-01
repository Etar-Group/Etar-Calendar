import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ec4j.editorconfig)
}

editorconfig {
	excludes = listOf("metadata/**", "**/*.webp")
}

kotlin {
    jvmToolchain(21)
}

android {
	namespace = "ws.xsoh.etar"
	testNamespace = "com.android.calendar.tests"
	compileSdk = 36

	defaultConfig {
		minSdk = 23
		targetSdk = 35
		versionCode = 51
		versionName = "1.0.51"
		applicationId = "ws.xsoh.etar"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			// TODO: could be enabled for ProGuard minimization
			isMinifyEnabled = false
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".CalendarRecentSuggestionsProvider"
			)
		}

		debug {
			isMinifyEnabled = false

			applicationIdSuffix = ".debug"
			resValue(
				"string",
				"search_authority",
				defaultConfig.applicationId + ".debug.CalendarRecentSuggestionsProvider"
			)
		}
	}

	buildFeatures {
        buildConfig = true
		viewBinding = true
	}

	/*
	 * To sign release build, create file gradle.properties in ~/.gradle/ with this content:
	 *
	 * signingStoreLocation=/home/key.store
	 * signingStorePassword=xxx
	 * signingKeyAlias=alias
	 * signingKeyPassword=xxx
	 */
	val signingStoreLocation: String? by project
	val signingStorePassword: String? by project
	val signingKeyAlias: String? by project
	val signingKeyPassword: String? by project

	if (
		signingStoreLocation != null &&
		signingStorePassword != null &&
		signingKeyAlias != null &&
		signingKeyPassword != null
	) {
		println("Found sign properties in gradle.properties! Signing build…")

		signingConfigs {
			named("release").configure {
				storeFile = File(signingStoreLocation!!)
				storePassword = signingStorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}

		buildTypes.named("release").get().signingConfig = signingConfigs.named("release").get()
	} else {
		buildTypes.named("release").get().signingConfig = null
	}

	lint {
		lintConfig = file("lint.xml")
		// TODO: Resolve lint errors due to 363aa9c237a33e9e1a40bdfd9039dcaaa855a5a0
		abortOnError = false
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true

		sourceCompatibility(JavaVersion.VERSION_21)
		targetCompatibility(JavaVersion.VERSION_21)
	}

kotlin {
    compilerOptions {
         jvmTarget = JvmTarget.JVM_21
    }
}

	useLibrary("android.test.base")
	useLibrary("android.test.mock")

	androidResources {
		generateLocaleConfig = true
	}

}

dependencies {

	// Core
	implementation(libs.androidx.core)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.google.android.material)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.concurrent.futures)
    testImplementation(libs.junit)

	coreLibraryDesugaring(libs.android.tools.desugar)

	// Coroutines
	implementation(libs.kotlinx.coroutines.android)

	// https://mvnrepository.com/artifact/org.dmfs/lib-recur
	implementation(libs.dmfs.lib.recur)

	// lifecycle
	implementation(libs.androidx.lifecycle.livedata)
}
