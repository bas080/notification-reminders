package com.bas080.notificationreminders

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FastlaneMetadataTest {

    private lateinit var metadataDir: File

    @Before
    fun setUp() {
        var dir = File("fastlane/metadata/android/en-US")
        if (!dir.exists()) {
            dir = File("../fastlane/metadata/android/en-US")
        }
        assertTrue("Fastlane metadata directory should exist", dir.exists() && dir.isDirectory)
        metadataDir = dir
    }

    @Test
    fun testTitleTextExistsAndIsNonEmpty() {
        val titleFile = File(metadataDir, "title.txt")
        assertTrue("title.txt should exist", titleFile.exists())
        val title = titleFile.readText().trim()
        assertTrue("title.txt should not be empty", title.isNotEmpty())
    }

    @Test
    fun testShortDescriptionLengthWithinLimit() {
        val shortDescFile = File(metadataDir, "short_description.txt")
        assertTrue("short_description.txt should exist", shortDescFile.exists())
        val shortDesc = shortDescFile.readText().trim()
        assertTrue("short_description.txt should not be empty", shortDesc.isNotEmpty())
        assertTrue(
            "short_description.txt must be 80 characters or fewer, but was ${shortDesc.length}",
            shortDesc.length <= 80
        )
    }

    @Test
    fun testFullDescriptionLengthWithinLimit() {
        val fullDescFile = File(metadataDir, "full_description.txt")
        assertTrue("full_description.txt should exist", fullDescFile.exists())
        val fullDesc = fullDescFile.readText().trim()
        assertTrue("full_description.txt should not be empty", fullDesc.isNotEmpty())
        assertTrue(
            "full_description.txt must be 4000 characters or fewer, but was ${fullDesc.length}",
            fullDesc.length <= 4000
        )
    }

    @Test
    fun testChangelogExistsForVersionCode() {
        val changelogFile = File(metadataDir, "changelogs/3.txt")
        assertTrue("changelogs/3.txt should exist for versionCode = 3", changelogFile.exists())
        val text = changelogFile.readText().trim()
        assertTrue("Changelog should not be empty", text.isNotEmpty())
        assertTrue("Changelog size should be under 500 bytes", changelogFile.length() < 500)
    }

    @Test
    fun testIconImageDimensions() {
        val iconFile = File(metadataDir, "images/icon.png")
        assertTrue("icon.png should exist", iconFile.exists())
        val dimensions = getPngDimensions(iconFile)
        assertNotNull("icon.png should be a valid PNG file", dimensions)
        val (width, height) = dimensions!!
        assertTrue(
            "icon.png width should be 512px, but was $width",
            width == 512
        )
        assertTrue(
            "icon.png height should be 512px, but was $height",
            height == 512
        )
    }

    @Test
    fun testFeatureGraphicDimensions() {
        val fgFile = File(metadataDir, "images/featureGraphic.png")
        assertTrue("featureGraphic.png should exist", fgFile.exists())
        val dimensions = getPngDimensions(fgFile)
        assertNotNull("featureGraphic.png should be a valid PNG file", dimensions)
        val (width, height) = dimensions!!
        assertTrue(
            "featureGraphic.png width should be 1024px, but was $width",
            width == 1024
        )
        assertTrue(
            "featureGraphic.png height should be 500px, but was $height",
            height == 500
        )
    }

    @Test
    fun testTabletScreenshotsExistAndHaveValidAspectRatios() {
        val screenshotsDir = File(metadataDir, "images/tenInchScreenshots")
        assertTrue("tenInchScreenshots directory should exist", screenshotsDir.exists() && screenshotsDir.isDirectory)
        val screenshots = screenshotsDir.listFiles { _, name -> name.endsWith(".png") }
        assertTrue("There should be at least one tablet screenshot", screenshots != null && screenshots.isNotEmpty())

        for (screenshot in screenshots!!) {
            val dimensions = getPngDimensions(screenshot)
            assertNotNull("Tablet screenshot ${screenshot.name} should be a valid PNG file", dimensions)
            val (width, height) = dimensions!!
            val maxEdge = maxOf(width, height).toDouble()
            val minEdge = minOf(width, height).toDouble()
            val ratio = maxEdge / minEdge
            assertTrue(
                "Tablet screenshot ${screenshot.name} aspect ratio ($ratio) should not exceed 2.1",
                ratio <= 2.1
            )
        }
    }

    @Test
    fun testPhoneScreenshotsExistAndHaveValidAspectRatios() {
        val screenshotsDir = File(metadataDir, "images/phoneScreenshots")
        assertTrue("phoneScreenshots directory should exist", screenshotsDir.exists() && screenshotsDir.isDirectory)
        val screenshots = screenshotsDir.listFiles { _, name -> name.endsWith(".png") }
        assertTrue("There should be at least one screenshot", screenshots != null && screenshots.isNotEmpty())

        for (screenshot in screenshots!!) {
            val dimensions = getPngDimensions(screenshot)
            assertNotNull("Screenshot ${screenshot.name} should be a valid PNG file", dimensions)
            val (width, height) = dimensions!!
            val maxEdge = maxOf(width, height).toDouble()
            val minEdge = minOf(width, height).toDouble()
            val ratio = maxEdge / minEdge
            assertTrue(
                "Screenshot ${screenshot.name} aspect ratio ($ratio) should not exceed 2.1",
                ratio <= 2.1
            )
        }
    }

    private fun getPngDimensions(file: File): Pair<Int, Int>? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size >= 24 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()
        ) {
            val width = ((bytes[16].toInt() and 0xFF) shl 24) or
                    ((bytes[17].toInt() and 0xFF) shl 16) or
                    ((bytes[18].toInt() and 0xFF) shl 8) or
                    (bytes[19].toInt() and 0xFF)
            val height = ((bytes[20].toInt() and 0xFF) shl 24) or
                    ((bytes[21].toInt() and 0xFF) shl 16) or
                    ((bytes[22].toInt() and 0xFF) shl 8) or
                    (bytes[23].toInt() and 0xFF)
            return Pair(width, height)
        }
        return null
    }
}
