package com.yagay.javcatalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Native mobile cards for actors, makers, series and other non-movie directory entries. */
@Composable
internal fun NativeDirectoryGrid(
    entries: List<DirectoryEntry>,
    currentUrl: String,
    userAgent: String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(entries, key = { it.url }) { entry ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(entry.url) },
                shape = RoundedCornerShape(12.dp)) {
                if (entry.cover.isNotBlank()) {
                    CoverImage(url = entry.cover, referrer = currentUrl, userAgent = userAgent,
                        title = entry.title, modifier = Modifier.fillMaxWidth().height(146.dp))
                }
                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
