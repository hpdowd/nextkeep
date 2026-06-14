package ie.dowd.nextkeep

import ie.dowd.nextkeep.data.Updater.Companion.compareVersions
import ie.dowd.nextkeep.data.Updater.Companion.isReleaseApkName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The updater compares a GitHub release tag against the running build's version
 * name to decide whether to offer an update, and picks the stable-signed APK
 * from the release's assets. Both are pure and easy to get subtly wrong (numeric
 * vs. lexical ordering, the `v` prefix, git-describe and rc suffixes), so pin
 * them down here.
 */
class UpdaterTest {

    @Test
    fun newerTag_isOffered() {
        assertTrue(compareVersions("v1.1", "1.0") > 0)
        assertTrue(compareVersions("v2.0", "1.9") > 0)
    }

    @Test
    fun sameVersion_isNotOffered() {
        assertTrue(compareVersions("v1.0", "1.0") == 0)
        assertTrue(compareVersions("v1.2", "1.2") == 0)
    }

    @Test
    fun olderTag_isNotOffered() {
        assertTrue(compareVersions("v1.0", "1.1") < 0)
    }

    @Test
    fun versionsCompareNumerically_notLexically() {
        // "1.10" must outrank "1.9" — a string compare would get this wrong.
        assertTrue(compareVersions("v1.10", "1.9") > 0)
    }

    @Test
    fun devBuildAheadOfTag_isNotOffered() {
        // git describe past v1.1 looks like "1.1-3-gabc123"; its series is still
        // 1.1, so the v1.1 release must not be offered as an "update".
        assertTrue(compareVersions("v1.1", "1.1-3-gabc123-dev") == 0)
    }

    @Test
    fun newerTagOverDevBuild_isOffered() {
        assertTrue(compareVersions("v1.2", "1.1-3-gabc123") > 0)
    }

    @Test
    fun rcSuffix_sharesItsBaseSeries() {
        // rc and stable of the same version are one series (see compareVersions).
        assertTrue(compareVersions("v1.1-rc1", "1.1") == 0)
        assertTrue(compareVersions("v1.1-rc1", "1.0") > 0)
    }

    @Test
    fun selectsTheReleaseApk_notDebugOrUnsigned() {
        assertTrue(isReleaseApkName("app-release.apk"))
        assertFalse(isReleaseApkName("app-debug.apk"))
        assertFalse(isReleaseApkName("app-release-unsigned.apk"))
        assertFalse(isReleaseApkName("notes.txt"))
    }
}
