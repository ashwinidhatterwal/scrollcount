plugins { id("com.android.application") }
val uploadStore = System.getenv("SCROLLCOUNT_KEYSTORE")
android {
 namespace = "in.scrollcount.probe"
 compileSdk = 36
 defaultConfig { applicationId = "in.scrollcount.probe"; minSdk = 26; targetSdk = 36; versionCode = 37; versionName = "1.2.19" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 if (!uploadStore.isNullOrBlank()) {
  signingConfigs.create("releaseUpload") {
   storeFile = file(uploadStore)
   storePassword = requireNotNull(System.getenv("SCROLLCOUNT_STORE_PASSWORD")) { "SCROLLCOUNT_STORE_PASSWORD is required" }
   keyAlias = requireNotNull(System.getenv("SCROLLCOUNT_KEY_ALIAS")) { "SCROLLCOUNT_KEY_ALIAS is required" }
   keyPassword = requireNotNull(System.getenv("SCROLLCOUNT_KEY_PASSWORD")) { "SCROLLCOUNT_KEY_PASSWORD is required" }
  }
 }
 buildTypes {
  // Keep the already-compressed brand PNGs unchanged; avoid a redundant crunch pass.
  getByName("debug") { isCrunchPngs = false }
  getByName("release") {
   isMinifyEnabled = false
   isCrunchPngs = false
   if (!uploadStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("releaseUpload")
  }
 }
}

// A signed release must identify a real public privacy contact.
val checkReleasePolicy by tasks.registering {
 doLast {
  val metadata = groovy.json.JsonSlurper().parse(rootProject.file("release-config.json")) as Map<*, *>
  val email = metadata["supportEmail"]?.toString().orEmpty()
  check(email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
   "Set a real support/privacy email using: python3 tools/configure_release.py --email YOUR_EMAIL"
  }
  check(file("src/main/java/in/scrollcount/probe/ReleaseInfo.java").readText().contains(email)) {
   "Regenerate release metadata: python3 tools/configure_release.py --check"
  }
 }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleasePolicy) }
