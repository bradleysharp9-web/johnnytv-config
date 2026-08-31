package com.johnnytv.player

/**
 * WHITE-LABEL CONFIG
 *
 * Everything you change per build lives here (plus app_name in strings.xml, the
 * colours in colors.xml, the icons in res/mipmap-*, and applicationId in
 * app/build.gradle.kts).
 */
object Config {

    /**
     * THE IMPORTANT ONE.
     *
     * A small JSON file you host. The app reads it every time it starts, so you can
     * change the portal address, post a message, or announce an update WITHOUT
     * rebuilding the app or asking anyone to reinstall.
     *
     * Free to host: put config.json in a PUBLIC GitHub repo and use its raw URL.
     * Leave blank and the app falls back to asking the user for a server address.
     */
    const val CONFIG_URL: String =
        "https://raw.githubusercontent.com/bradleysharp9-web/johnnytv-config/main/config.json"

    /** Used only if CONFIG_URL is blank or unreachable and nothing is cached yet. */
    const val DEFAULT_SERVER: String = ""

    /** Set both for a build that skips the login screen entirely. Normally left blank. */
    const val PRESET_USERNAME: String = ""
    const val PRESET_PASSWORD: String = ""

    /** Live streams are tried in this order until one plays. */
    val LIVE_CONTAINERS: List<String> = listOf("m3u8", "ts")

    const val USER_AGENT: String = "JohnnyTV/1.0 (Android)"
}
