package com.fitlens.companion.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R

/**
 * The leading icon of every search field: the magnifying glass, so it still reads as search, with the FitLens
 * character beside it (the owner's artwork in `branding/character/`, placed unedited).
 */
@Composable
fun SearchFieldIcon() {
    Row(
        Modifier.padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null)
        Image(
            painterResource(R.drawable.search_character),
            contentDescription = null,
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
        )
    }
}
