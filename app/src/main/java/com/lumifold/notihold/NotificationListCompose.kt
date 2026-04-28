package com.lumifold.notihold

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationList(
    notifications: List<NotificationListItem>,
    onItemClick: (NotificationDisplayModel) -> Unit,
    onItemLongClick: (NotificationDisplayModel) -> Unit,
    onStarClick: (NotificationDisplayModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = notifications,
            key = { item ->
                when (item) {
                    is NotificationListItem.Header -> "header_${item.title}"
                    is NotificationListItem.DateHeader -> "date_${item.date}"
                    is NotificationListItem.Item -> "item_${item.model.id}"
                }
            }
        ) { item ->
            when (item) {
                is NotificationListItem.Header -> {
                    NotificationHeader(
                        title = item.title
                    )
                }
                is NotificationListItem.DateHeader -> {
                    NotificationHeader(
                        title = item.date
                    )
                }
                is NotificationListItem.Item -> {
                    NotificationItem(
                        notification = item.model,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onStarClick = onStarClick
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun NotificationItem(
    notification: NotificationDisplayModel,
    onItemClick: (NotificationDisplayModel) -> Unit,
    onItemLongClick: (NotificationDisplayModel) -> Unit,
    onStarClick: (NotificationDisplayModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onItemClick(notification) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon - Optimized with Coil and packageNameUri
            AsyncImage(
                model = notification.packageNameUri,
                contentDescription = notification.appName,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp),
                error = null
            )
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.appName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (notification.isBold) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        text = notification.formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (notification.isBold) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (notification.label != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = notification.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            // Star Button - Fixed visibility in dark mode by using Tertiary (Amber)
            IconButton(
                onClick = { onStarClick(notification) },
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Icon(
                    imageVector = if (notification.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (notification.isStarred) {
                        // 使用 tertiary (Amber) で視認性を確保
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
