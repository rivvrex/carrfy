package com.carrfy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip

data class SidebarItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val destination: SidebarDestination
)

enum class SidebarDestination {
    NOW_PLAYING,
    RECENTS,
    LIBRARY,
    RADIO,
    SEARCH
}

@Composable
fun Sidebar(
    selected: SidebarDestination,
    onSelect: (SidebarDestination) -> Unit,
    userName: String = "User",
    onProfileClick: () -> Unit = {}
) {
    val sidebarItems = listOf(
        SidebarItem("Now Playing", Icons.Default.PlayCircle, SidebarDestination.NOW_PLAYING),
        SidebarItem("Recents", Icons.Default.Wifi, SidebarDestination.RECENTS),
        SidebarItem("Library", Icons.Default.GridOn, SidebarDestination.LIBRARY),
        SidebarItem("Radio", Icons.Default.MusicNote, SidebarDestination.RADIO),
        SidebarItem("Search", Icons.Default.Search, SidebarDestination.SEARCH)
    )
    
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(180.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(sidebarItems) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 16.dp)
                        .clickable { onSelect(item.destination) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected == item.destination) Color.White else Color.LightGray,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = item.label,
                        fontSize = 18.sp,
                        color = if (selected == item.destination) Color.White else Color.LightGray
                    )
                }
            }
        }

        // User Profile Section at Bottom - Cleaner Design
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .clickable { onProfileClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Round Profile Picture
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF9C27B0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.firstOrNull()?.uppercase() ?: "U",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                
                // Username
                Text(
                    text = userName.take(12),
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}