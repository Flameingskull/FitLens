@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.Spacing

/** An icon action in a [FitTopBar]. [description] is what TalkBack reads. */
data class TopBarAction(
    val icon: ImageVector,
    val description: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/** One entry in an overflow menu ([FitTopBar], [ExerciseCard], [ListRowWithMenu]). */
data class MenuAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * The FitLens top bar (#80): title, optional letter-spaced subtitle, up to three [actions], an optional Settings gear
 * and an overflow menu, over a gold hairline.
 *
 * - Pushed screens pass [onBack] and get a back arrow; tab screens leave it null and are centre-aligned.
 * - To collapse on scroll, pass a [scrollBehavior] and add `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)`
 *   to the scrolling content.
 * - [trailing] is a slot for anything the lists can't express, such as an existing dropdown; it sits after [actions].
 * - [titleMenu] turns the title into a dropdown (with a ▾), as the library's routine switcher does in FitNotes (#21).
 * - [brandMark] puts the FitLens character before the title, where FitNotes shows its app icon (#141). It's the torso
 *   art, as every small icon is (`CLAUDE.md` rule 4), placed unedited. Only for a screen with no back arrow.
 */
@Composable
fun FitTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    centered: Boolean = onBack == null,
    actions: List<TopBarAction> = emptyList(),
    overflow: List<MenuAction> = emptyList(),
    onSettings: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** What TalkBack reads for the back arrow, e.g. "Close full screen" where Back would mislead (#96). */
    backLabel: String = "Back",
    titleMenu: List<MenuAction> = emptyList(),
    /** An icon in the navigation slot instead of a back arrow, such as the exercise screen's workout drawer (#129). */
    navigation: TopBarAction? = null,
    brandMark: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    var titleOpen by remember { mutableStateOf(false) }
    val colors = TopAppBarDefaults.topAppBarColors(
        // Transparent over the glass gradient drawn behind the bar (#102).
        containerColor = Brand.Black.copy(alpha = 0f),
        scrolledContainerColor = Brand.Black.copy(alpha = 0f),
        titleContentColor = Brand.Ivory,
        navigationIconContentColor = Brand.Gold,
        actionIconContentColor = Brand.Gold
    )
    val titleContent: @Composable () -> Unit = {
        Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            if (titleMenu.isEmpty()) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (onBack != null || navigation != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
                )
            } else {
                Box {
                    Row(
                        Modifier
                            .heightIn(min = Spacing.touch)
                            .clickable(onClickLabel = "Switch") { titleOpen = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Brand.Gold)
                    }
                    DropdownMenu(expanded = titleOpen, onDismissRequest = { titleOpen = false }) {
                        titleMenu.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                onClick = { titleOpen = false; m.onClick() },
                                enabled = m.enabled
                            )
                        }
                    }
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle.uppercase(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    val navigationContent: @Composable () -> Unit = {
        if (navigation != null) {
            IconButton(onClick = navigation.onClick, enabled = navigation.enabled) {
                Icon(navigation.icon, contentDescription = navigation.description)
            }
        } else if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
            }
        } else if (brandMark) {
            Image(
                painterResource(R.drawable.search_character),
                contentDescription = null,
                // The torso art is wider than tall (665 × 414), so the mark keeps that shape.
                modifier = Modifier.padding(start = Spacing.md).size(width = 45.dp, height = 28.dp).clip(RoundedCornerShape(4.dp))
            )
        }
    }
    val actionContent: @Composable RowScope.() -> Unit = {
        actions.take(3).forEach { a ->
            IconButton(onClick = a.onClick, enabled = a.enabled) {
                Icon(a.icon, contentDescription = a.description)
            }
        }
        trailing()
        if (onSettings != null) {
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        }
        if (overflow.isNotEmpty()) OverflowMenu(overflow)
    }
    Column(modifier.background(Glass.bar)) {
        if (centered) {
            CenterAlignedTopAppBar(
                title = titleContent,
                navigationIcon = navigationContent,
                actions = actionContent,
                colors = colors,
                scrollBehavior = scrollBehavior
            )
        } else {
            TopAppBar(
                title = titleContent,
                navigationIcon = navigationContent,
                actions = actionContent,
                colors = colors,
                scrollBehavior = scrollBehavior
            )
        }
        GoldHairline()
    }
}

/** A 48dp overflow ("more") button that opens [items] as a dropdown. */
@Composable
fun OverflowMenu(items: List<MenuAction>, description: String = "More options") {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = description)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    onClick = { open = false; m.onClick() },
                    enabled = m.enabled
                )
            }
        }
    }
}
