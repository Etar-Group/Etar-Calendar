import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.ec4j.editorconfig")
}

editorconfig {
	excludes = listOf("external/**", "metadata/**", "**/*.webp")
}

android {
	namespace = "ws.xsoh.etar"
	testNamespace = "com.android.calendar.tests"
	compileSdk = 34

	defaultConfig {
		minSdk = 21
		targetSdk = 34
		versionCode = 46
		versionName = "1.0.46"
		applicationId = "ws.xsoh.etar"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	sourceSets {
		named("main").get().java.srcDirs("../external/ex/common/java")
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

		sourceCompatibility(JavaVersion.VERSION_17)
		targetCompatibility(JavaVersion.VERSION_17)
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	useLibrary("android.test.base")
	useLibrary("android.test.mock")

	androidResources {
		generateLocaleConfig = true
	}

}

dependencies {

	// Core
	implementation("androidx.core:core-ktx:1.12.0")
	implementation(fileTree("include" to arrayOf("*.jar", "*.aar"), "dir" to "libs"))
	implementation("androidx.preference:preference:1.2.1")
	implementation("androidx.appcompat:appcompat:1.6.1")
	implementation("androidx.constraintlayout:constraintlayout:2.1.4")
	implementation("com.google.android.material:material:1.11.0")
	testImplementation("junit:junit:4.13.2")

	coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

	// Coroutines
	val coroutines_version = "1.8.0"
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version")

	// https://mvnrepository.com/artifact/org.dmfs/lib-recur
	implementation("org.dmfs:lib-recur:0.16.0")

	// lifecycle
	val lifecycle_version = "2.7.0"
	implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")
}

tasks.preBuild.dependsOn(":aarGen")
