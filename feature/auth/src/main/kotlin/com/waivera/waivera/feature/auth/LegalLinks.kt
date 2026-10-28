package com.waivera.waivera.feature.auth

import androidx.compose.runtime.Immutable
import okhttp3.HttpUrl

/**
 * The public Terms of Service and Privacy Policy pages on the web app, linked from sign-in.
 * They hang off the configured web base (`waivera.webBaseUrl`, per build type or CI), at the
 * routes the web Login page links, so no environment is ever written here.
 */
@Immutable
data class LegalLinks(val termsOfService: String, val privacyPolicy: String) {
    companion object {
        /** Web app routes (`sprout_web` App.tsx; linked from its Login page). */
        const val TERMS_OF_SERVICE_PATH = "terms-of-service"
        const val PRIVACY_POLICY_PATH = "privacy-policy"

        /** Null when the build has no web base, so the screen shows no dead links. */
        fun from(webBase: HttpUrl?): LegalLinks? {
            val terms = webBase?.resolve(TERMS_OF_SERVICE_PATH) ?: return null
            val privacy = webBase.resolve(PRIVACY_POLICY_PATH) ?: return null
            return LegalLinks(termsOfService = terms.toString(), privacyPolicy = privacy.toString())
        }
    }
}
