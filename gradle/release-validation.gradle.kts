// Build-time guard for RELEASE builds. Applied from app/build.gradle.kts, which passes the resolved
// values through project.extra. It runs before anything else in the release variant (preReleaseBuild),
// so a wrong/test API URL, an unsigned build or a stray server secret can never end up in a Play APK.
//
// Debug builds are not validated here: they use the DEBUG_* keys (see env.properties.example) and can
// never influence a release build.

@Suppress("UNCHECKED_CAST")
val releaseInputs = project.extra["lokmago.releaseInputs"] as Map<String, String>
@Suppress("UNCHECKED_CAST")
val envFileKeys = project.extra["lokmago.envFileKeys"] as Set<String>

val prodHost = "restoran-api.lokma.uz"
// Names that belong to the SERVER. If one is found in env.properties something was pasted in the wrong file.
val forbiddenKey = Regex("(?i)(mongo|jwt|click|paynet|cloudinary|service[_-]?account|private[_-]?key|webhook|(^|_)secret($|_))")
// Values that must never ship in a production APK.
val forbiddenHostHint = Regex("(?i)(localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2|\\.local\\b|example\\.|staging|\\btest\\b|\\bdev\\b|ngrok|192\\.168\\.)")

fun checkUrl(name: String, raw: String, errors: MutableList<String>, allowEmpty: Boolean) {
    if (raw.isBlank()) { if (!allowEmpty) errors += "$name is required for release"; return }
    val uri = runCatching { java.net.URI(raw.trim()) }.getOrNull()
    if (uri == null || uri.host == null) { errors += "$name is not a valid URL: '$raw'"; return }
    if (uri.scheme != "https") errors += "$name must use https (got '${uri.scheme}')"
    if (uri.host != prodHost) errors += "$name must point to $prodHost (got '${uri.host}')"
    if (forbiddenHostHint.containsMatchIn(raw)) errors += "$name looks like a test/local endpoint: '$raw'"
    if (uri.userInfo != null) errors += "$name must not contain credentials"
    if (uri.port != -1 && uri.port != 443) errors += "$name must use the default HTTPS port (got ${uri.port})"
    if (!uri.path.isNullOrEmpty() && uri.path != "/") errors += "$name must be a bare origin, put the path in API_PATH_PREFIX / SOCKET_PATH"
}

tasks.register("validateReleaseConfig") {
    group = "verification"
    description = "Fails the build unless the release configuration is production-safe."
    doLast {
        val errors = mutableListOf<String>()
        fun v(k: String) = releaseInputs[k].orEmpty()

        checkUrl("API_GATEWAY_URL", v("API_GATEWAY_URL"), errors, allowEmpty = false)
        checkUrl("SOCKET_URL", v("SOCKET_URL"), errors, allowEmpty = true)   // empty => derived from API_GATEWAY_URL
        if (!v("SOCKET_PATH").startsWith("/")) errors += "SOCKET_PATH must start with '/' (got '${v("SOCKET_PATH")}')"
        if (v("API_PATH_PREFIX").isBlank() || "://" in v("API_PATH_PREFIX")) errors += "API_PATH_PREFIX must be a relative path such as restaurant/v1/"

        // Versioning is env-driven and must be explicit, otherwise two releases could share a versionCode.
        val vc = v("VERSION_CODE").toIntOrNull()
        if (v("VERSION_CODE_RAW").isBlank() || vc == null || vc < 1) errors += "VERSION_CODE must be set explicitly to a positive integer"
        if (v("VERSION_NAME_RAW").isBlank()) errors += "VERSION_NAME must be set explicitly (e.g. 1.0.0)"

        // Signing: a release APK without a real key is useless for install/Play and must not be produced silently.
        val ks = v("KEYSTORE_PATH")
        if (ks.isBlank()) errors += "KEYSTORE_PATH is required (release must be signed)"
        else {
            val f = file(ks)
            if (!f.isFile) errors += "KEYSTORE_PATH does not exist: $ks"
            else if (f.canonicalPath.startsWith(rootProject.projectDir.canonicalPath) && !f.name.endsWith(".jks") && !f.name.endsWith(".keystore"))
                errors += "keystore inside the repository must be *.jks / *.keystore so .gitignore covers it"
            else if (f.canonicalPath.startsWith(rootProject.projectDir.canonicalPath))
                logger.warn("WARNING: keystore is inside the repository directory; keep it outside the checkout when possible.")
        }
        for (k in listOf("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")) if (v(k).isBlank()) errors += "$k is required for release signing"

        // Server secrets must not be near the client build at all.
        envFileKeys.filter { forbiddenKey.containsMatchIn(it) }.forEach {
            errors += "env.properties contains '$it' — that is a SERVER secret and must never be part of the Android project"
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                "Release configuration rejected:\n" + errors.joinToString("\n") { " - $it" } +
                    "\nSee env.properties.example and docs/RELEASE.md."
            )
        }
        logger.lifecycle("Release configuration OK: api=${v("API_GATEWAY_URL")} versionCode=$vc versionName=${v("VERSION_NAME")}")
    }
}

// Every release variant task (assemble/bundle/package/lint/test) starts with preReleaseBuild.
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn("validateReleaseConfig") }
