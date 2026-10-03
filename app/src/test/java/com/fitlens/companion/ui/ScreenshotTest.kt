package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.design.ExerciseCard
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SetCell
import com.fitlens.companion.ui.design.SetRow
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsSwitchRow
import com.fitlens.companion.ui.design.StatTile
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.Trend
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshot tests for the shared design components (#95), rendered on the JVM by Robolectric's native graphics, so
 * there's no emulator. Each component is drawn at FitNotes's two common phone widths (360dp and 411dp) and at font
 * scale 1.0 and 2.0, with made-up data only.
 *
 * The golden images live in `app/src/test/screenshots/`. A component whose golden exists is verified against it, and
 * a change fails the build, with the diff in `app/build/outputs/roborazzi/` (CI keeps it as an artifact). A component
 * without a golden is recorded instead; CI keeps the new images as an artifact so they can be committed. To accept an
 * intended change, delete the old golden and commit the newly recorded one.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w420dp-h2000dp-xhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private data class Variant(val width: Int, val fontScale: Float, val label: String)

    private val variants = listOf(
        Variant(360, 1f, "360dp"),
        Variant(411, 1f, "411dp"),
        Variant(360, 2f, "360dp_font2"),
        Variant(411, 2f, "411dp_font2")
    )

    /** Draws [content] once, then captures it in every variant (a compose rule allows only one setContent per test). */
    private fun shoot(name: String, content: @Composable () -> Unit) {
        GOLDEN_DIR.mkdirs()
        var variant by mutableStateOf(variants.first())
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, variant.fontScale)) {
                FitLensTheme {
                    Box(Modifier.testTag(TAG).width(variant.width.dp).background(Brand.Black)) { content() }
                }
            }
        }
        for (v in variants) {
            compose.runOnIdle { variant = v }
            compose.waitForIdle()
            val golden = File(GOLDEN_DIR, "${name}_${v.label}.png")
            val options = RoborazziOptions(
                taskType = if (golden.exists()) RoborazziTaskType.Verify else RoborazziTaskType.Record,
                // Anti-aliasing can move a handful of pixels between runs; a real layout change moves far more.
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f)
            )
            compose.onNodeWithTag(TAG).captureRoboImage(golden, roborazziOptions = options)
        }
    }

    @Test fun topBar() = shoot("top_bar") {
        FitTopBar(
            title = "Thursday, 2 Oct",
            actions = listOf(
                TopBarAction(Icons.Filled.DateRange, "Calendar") {},
                TopBarAction(Icons.Filled.Add, "Add exercise") {}
            ),
            overflow = listOf(MenuAction("Settings") {})
        )
    }

    @Test fun exerciseCard() = shoot("exercise_card") {
        ExerciseCard(
            name = "Flat Barbell Bench Press",
            categoryColor = Brand.Gold,
            onClick = {},
            comment = "Paused reps, elbows tucked",
            hasPr = true,
            setsDone = 2,
            setsTotal = 3
        ) {
            SetRow(1, "60 kg, 10 reps", cells = cells("60.0", "10"), done = true, onDoneChange = {})
            SetRow(2, "80 kg, 6 reps", cells = cells("80.0", "6"), isPr = true, done = true, onDoneChange = {})
            SetRow(3, "80 kg, 5 reps", cells = cells("80.0", "5"), comment = "Last rep slow", done = false, onDoneChange = {})
        }
    }

    @Test fun setRows() = shoot("set_rows") {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionLabel("Track", Modifier.padding(horizontal = 16.dp))
            SetRow(1, "Warm-up, 40 kg, 12 reps", cells = cells("40.0", "12"), badge = "W", badgeSpoken = "warm-up")
            SetRow(2, "100 kg, 5 reps", cells = cells("100.0", "5"), effort = "RPE 8", effortSpoken = "2 reps in reserve")
            SetRow(3, "100 kg, 5 reps", cells = cells("100.0", "5"), selected = true)
        }
    }

    @Test fun statTiles() = shoot("stat_tiles") {
        Row(Modifier.padding(8.dp)) {
            StatTile("Body weight", "82.4 kg", Modifier.weight(1f), delta = "−1.2 kg from 83.6 kg", trend = Trend.Down, dateLine = "2 Oct")
            StatTile("Est. 1RM", "120 kg", Modifier.weight(1f), delta = "+5 kg from 115 kg", trend = Trend.Up)
        }
    }

    @Test fun settingsRows() = shoot("settings_rows") {
        Column {
            SettingsGroup("General")
            SettingsSwitchRow("Count warm-up sets", checked = true, summary = "Include warm-ups in totals and records") {}
            SettingsChoiceRow("Weight unit", options = listOf("kg", "lbs"), selected = 0) {}
            SettingsActionRow("Back up now", summary = "Saves a .fitlens backup to your chosen folder", value = "Today") {}
        }
    }

    @Test fun tabsAndButtons() = shoot("tabs_buttons") {
        Column {
            FitTabRow(titles = listOf("Track", "History", "Graph"), selected = 0, onSelect = {})
            Row(Modifier.padding(16.dp)) {
                GoldButton(onClick = {}) { Text("Save") }
                GlassOutlinedButton(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Text("Clear") }
            }
        }
    }

    private fun cells(weight: String, reps: String) = listOf(
        SetCell(weight, "kg", "$weight kilograms"),
        SetCell(reps, "reps", "$reps reps")
    )

    private companion object {
        const val TAG = "screenshot"
        /** Relative to the module directory, the working directory Gradle runs unit tests in. */
        val GOLDEN_DIR = File("src/test/screenshots")
    }
}
