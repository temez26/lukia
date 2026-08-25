package io.lukia

interface Platform {
    val name: String

    /**
     * Whether this build is one somebody holds rather than points at.
     *
     * The app decides most of its layout from the window's width - see
     * `WindowWidthClass` - but which navigation the app has is not a layout
     * question. A phone turned to landscape is 844dp wide and would answer
     * "wide" to the width class, and losing its tab bar for the length of a
     * rotation is not a thing any phone does.
     */
    val isMobile: Boolean
}

expect fun getPlatform(): Platform
