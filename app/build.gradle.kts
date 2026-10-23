import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("android")
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
}

android {
    buildFeatures {
        compose = true
    }
    // Zashboard (Debug → Zashboard): assets/zashboard in the APK. Apart from
    // src/main/assets, which `clean` empties of the downloaded geo files.
    sourceSets["main"].assets.srcDir("zashboard/assets")
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
    implementation(libs.androidx.activity.ktx)
    implementation("com.google.zxing:core:3.5.3")

    // Jetpack Compose — entry points host Compose screens from :design
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.window)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val geoFilesDownloadDir = "src/main/assets"

task("downloadGeoFiles") {

    val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        // "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb" to "country.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z" to "BundleMRS.7z",
    )

    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val url = URL(downloadUrl)
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()
            url.openStream().use { input ->
                Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("$outputFileName downloaded to $outputPath")
            }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file(geoFilesDownloadDir))
}

/**
 * Keeps zashboard/assets/zashboard at Zashboard's latest release, before
 * every build: asks GitHub for the latest release and, when it is newer than
 * its VERSION, downloads dist-no-fonts.zip, checks it against the SHA-256
 * GitHub publishes for the file, and puts it in place of the old one
 * (LICENSE kept, VERSION and zashboard/README.md updated). Never fails the
 * build: offline, rate-limited, a checksum that does not match — the current
 * copy stays, with a warning. ZASHBOARD_AUTO_UPDATE=0 skips the check.
 */
val updateZashboard by tasks.registering {
    val dest = file("zashboard/assets/zashboard")
    val notes = file("zashboard/README.md")

    doLast {
        val current = dest.resolve("VERSION").takeIf { it.exists() }?.readText()?.trim().orEmpty()
        fun keep(why: String) = logger.warn("Zashboard: $why; keeping v${current.ifEmpty { "?" }}")

        if (System.getenv("ZASHBOARD_AUTO_UPDATE") == "0") {
            logger.lifecycle("Zashboard: update check off (ZASHBOARD_AUTO_UPDATE=0), v$current")
            return@doLast
        }

        fun open(url: String, timeout: Int): InputStream {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.instanceFollowRedirects = true
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream
        }

        val release = try {
            open("https://api.github.com/repos/Zephyruso/zashboard/releases/latest", 8_000)
                .use { groovy.json.JsonSlurper().parse(it) } as Map<*, *>
        } catch (e: Exception) {
            return@doLast keep("GitHub did not answer (${e.message})")
        }
        val latest = (release["tag_name"] as? String).orEmpty().removePrefix("v")
        if (latest.isEmpty()) return@doLast keep("no release tag")
        if (latest == current) {
            logger.lifecycle("Zashboard: v$current is the latest")
            return@doLast
        }
        val asset = (release["assets"] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .firstOrNull { it["name"] == "dist-no-fonts.zip" }
            ?: return@doLast keep("v$latest has no dist-no-fonts.zip")
        val url = asset["browser_download_url"] as? String ?: return@doLast keep("v$latest has no download")
        val digest = (asset["digest"] as? String).orEmpty()
        if (!digest.startsWith("sha256:")) return@doLast keep("v$latest publishes no checksum")

        val zip = try {
            open(url, 120_000).use { it.readBytes() }
        } catch (e: Exception) {
            return@doLast keep("could not download v$latest (${e.message})")
        }
        val sum = MessageDigest.getInstance("SHA-256").digest(zip)
            .joinToString("") { "%02x".format(it) }
        if ("sha256:$sum" != digest) return@doLast keep("v$latest does not match its published checksum")

        // Unpacked beside the old copy first: nothing changes unless all of it is there.
        val unpacked = temporaryDir.resolve("unpacked").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(zip.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val target = unpacked.resolve(entry.name).canonicalFile
                    if (!target.path.startsWith(unpacked.canonicalPath + File.separator)) error("${entry.name} outside the zip's folder")
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile.mkdirs()
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        } catch (e: Exception) {
            return@doLast keep("could not unpack v$latest (${e.message})")
        }
        val src = unpacked.resolve("dist")
        if (!src.resolve("index.html").exists()) return@doLast keep("v$latest is not laid out as expected (no dist/index.html)")

        // The release's files in place of the old ones; ours (LICENSE, VERSION) stay.
        dest.listFiles().orEmpty().filter { it.name != "LICENSE" && it.name != "VERSION" }.forEach { it.deleteRecursively() }
        src.listFiles().orEmpty().filter { it.name != "LICENSE" && it.name != "VERSION" }.forEach {
            it.copyRecursively(dest.resolve(it.name), overwrite = true)
        }
        dest.resolve("VERSION").writeText("$latest\n")
        if (notes.exists()) {
            notes.writeText(
                notes.readText()
                    .replace(Regex("(?m)^- Release: v[^,]*,"), "- Release: v$latest,")
                    .replace(Regex("(?m)^- SHA-256 of the zip: .*"), "- SHA-256 of the zip: $sum")
            )
        }
        logger.lifecycle("Zashboard: updated v${current.ifEmpty { "?" }} → v$latest")
    }
}

tasks.named("preBuild") {
    dependsOn(updateZashboard)
}
