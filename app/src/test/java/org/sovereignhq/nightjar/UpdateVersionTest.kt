package org.sovereignhq.nightjar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sovereignhq.nightjar.update.UpdateChecker

/**
 * Version comparison for the self-updater.
 *
 * Worth testing carefully because both failure directions are bad in different ways: too eager and
 * the app nags people to install what they already have, too shy and a real fix never reaches
 * anyone. The classic bug is comparing as text, where "1.10.0" sorts below "1.9.0" — so that case
 * gets its own test.
 */
class UpdateVersionTest {

    @Test
    fun `a higher version is newer`() {
        assertTrue(UpdateChecker.isNewer("v1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(UpdateChecker.isNewer("v1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("v1.1.0", "2.0.0"))
    }

    @Test
    fun `versions compare numerically, not alphabetically`() {
        // Text comparison puts "1.10.0" below "1.9.0", which would strand everyone on 1.9.
        assertTrue(UpdateChecker.isNewer("v1.10.0", "1.9.0"))
        assertFalse(UpdateChecker.isNewer("v1.9.0", "1.10.0"))
        assertTrue(UpdateChecker.isNewer("v1.2.11", "1.2.9"))
    }

    @Test
    fun `missing components count as zero`() {
        assertTrue(UpdateChecker.isNewer("v1.2", "1.1.9"))
        assertFalse(UpdateChecker.isNewer("v1.1", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("v1.1.1", "1.1"))
    }

    @Test
    fun `suffixes are ignored rather than misread`() {
        assertFalse(UpdateChecker.isNewer("v1.1.0-beta", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("v1.2.0-rc1", "1.1.0"))
    }

    @Test
    fun `an unreadable tag never prompts an install`() {
        // A malformed release tag must fail closed. Offering an install off the back of a tag we
        // could not parse is the one outcome with no upside.
        assertFalse(UpdateChecker.isNewer("", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("latest", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("nightly-build", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("v", "1.1.0"))
    }
}
