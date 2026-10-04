package com.fitlens.companion.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** The in-app "What's new" (#33) reads the bundled RELEASE_NOTES.md with this small Markdown parser. */
class ReleaseNotesTest {

    @Test
    fun headingsParagraphsAndNestedBullets() {
        val md = """
            ## Overview

            This update adds
            two things.

            - **First:** one
              continues here.
              - nested
            - Second
        """.trimIndent()
        assertEquals(
            listOf(
                MdBlock.Heading("Overview"),
                MdBlock.Para("This update adds two things."),
                MdBlock.Bullet("**First:** one continues here.", 0),
                MdBlock.Bullet("nested", 1),
                MdBlock.Bullet("Second", 0)
            ),
            parseMarkdown(md)
        )
    }

    @Test
    fun inlineMarksBecomePlainText() {
        val text = inlineMarkdown("Open **Settings** → `Backups`, see [the guide](https://example.com).", Color.Unspecified)
        assertEquals("Open Settings → Backups, see the guide.", text.text)
        assertEquals(1, text.spanStyles.size)
    }

    @Test
    fun anUnclosedBoldMarkStaysAsWritten() {
        assertEquals("a **b", inlineMarkdown("a **b", Color.Unspecified).text)
    }
}
