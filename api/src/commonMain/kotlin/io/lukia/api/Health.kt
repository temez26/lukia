package io.lukia.api

import kotlinx.serialization.Serializable

/**
 * What a server says about itself before anyone has signed in, so the app can
 * show whom it is about to connect to and whether an admin still has to be made.
 */
@Serializable
data class HealthResponse(
    val name: String,
    val version: String,
    /** True until the first account exists; the app then offers to create the admin. */
    val setupRequired: Boolean,
    /**
     * Whether anyone may create an account here - see [ApiRoutes.SignUp]. Null
     * from a server older than this field, which takes invite codes instead.
     */
    val signUpOpen: Boolean? = null,
)
