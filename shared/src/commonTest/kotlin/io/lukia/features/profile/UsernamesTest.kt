package io.lukia.features.profile

import io.lukia.features.profile.domain.UserProfile
import io.lukia.features.profile.domain.UsernameProblem
import io.lukia.features.profile.domain.Usernames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the app will accept as a name, and what it makes of what it is given. */
class UsernamesTest {

    @Test
    fun outerSpaceIsDropped() {
        assertEquals("Ada", Usernames.clean("  Ada  "))
    }

    @Test
    fun innerRunsOfSpaceCollapse() {
        assertEquals("Ada Lovelace", Usernames.clean("Ada    Lovelace"))
    }

    /** A pasted paragraph is shortened in front of the reader, not rejected. */
    @Test
    fun aLongNameIsCutToTheLimit() {
        val cleaned = Usernames.clean("A".repeat(Usernames.MaxLength * 2))

        assertEquals(Usernames.MaxLength, cleaned.length)
    }

    @Test
    fun aUsableNameHasNoProblem() {
        assertNull(Usernames.problemWith("Ada"))
    }

    @Test
    fun nothingTypedIsEmptyRatherThanTooShort() {
        assertEquals(UsernameProblem.Empty, Usernames.problemWith("   "))
    }

    @Test
    fun oneLetterIsTooShort() {
        assertEquals(UsernameProblem.TooShort, Usernames.problemWith("A"))
    }

    @Test
    fun initialsTakeTheFirstLetterOfTwoParts() {
        assertEquals("AL", Usernames.initialsOf("ada lovelace"))
    }

    /** Three names still give two letters; a monogram is not an acronym. */
    @Test
    fun initialsStopAtTwoLetters() {
        assertEquals("JR", Usernames.initialsOf("John Ronald Reuel Tolkien"))
    }

    @Test
    fun aSingleWordGivesOneLetter() {
        assertEquals("A", Usernames.initialsOf("ada"))
    }

    @Test
    fun hyphensAndUnderscoresDivideAName() {
        assertEquals("MC", Usernames.initialsOf("marie-curie"))
        assertEquals("MC", Usernames.initialsOf("marie_curie"))
    }

    /** The avatar always draws something, even for a name it cannot read. */
    @Test
    fun aNameWithNoLettersFallsBackToAQuestionMark() {
        assertEquals("?", Usernames.initialsOf("   "))
    }

    @Test
    fun aProfileDrawsTheInitialsOfItsOwnName() {
        assertEquals("AL", UserProfile(username = "Ada Lovelace").initials)
    }
}
