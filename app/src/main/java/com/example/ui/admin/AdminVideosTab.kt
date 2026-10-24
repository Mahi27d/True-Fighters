package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Program
import com.example.data.model.Video
import com.example.ui.components.StatusBadge
import com.example.ui.theme.GoldBelt
import com.example.ui.theme.RedPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.video.VideoPlayerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminVideosTab(
    programs: List<Program>,
    videos: List<Video>,
    onCreateVideo: (Video) -> Unit,
    onTogglePublish: (videoId: String, currentPublished: Boolean) -> Unit,
    onDeleteVideo: (videoId: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var previewingVideo by remember { mutableStateOf<Video?>(null) }

    val categories = listOf("All", "Techniques", "Warmups", "Forms/Kata", "Sparring", "Self Defense", "Conditioning")

    val filteredVideos = remember(videos, searchQuery, selectedCategory) {
        videos.filter { v ->
            val matchQuery = searchQuery.isBlank() ||
                v.title.contains(searchQuery, ignoreCase = true) ||
                v.programName.contains(searchQuery, ignoreCase = true) ||
                v.category.contains(searchQuery, ignoreCase = true)
            val matchCategory = selectedCategory == "All" || v.category.equals(selectedCategory, ignoreCase = true)
            matchQuery && matchCategory
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Training Video Catalogue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Curate drills, katas, and technique tutorials for students & coaches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                modifier = Modifier.testTag("add_video_btn")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Video")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by video title, program, or category...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("search_videos_input"),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category filter row
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = RedPrimary,
            edgePadding = 8.dp
        ) {
            categories.forEach { cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    text = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredVideos.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            Text("No training videos found in catalogue.", color = TextMuted)
                        }
                    }
                }
            }

            items(filteredVideos) { video ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("video_item_${video.effectiveId}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = video.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${video.programName.ifBlank { "All Programs" }} • ${video.category} • ${video.durationText}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = if (video.published) "Published" else "Draft")
                        }

                        if (video.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = video.description, fontSize = 12.sp, color = TextMuted)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Min Belt Level: ${video.targetBelt} (#${video.minRankOrder})",
                                    fontSize = 11.sp,
                                    color = GoldBelt,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { previewingVideo = video },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Preview", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { onTogglePublish(video.effectiveId, video.published) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (video.published) MaterialTheme.colorScheme.surfaceVariant else SuccessGreen
                                    ),
                                    modifier = Modifier.height(36.dp).testTag("toggle_publish_${video.effectiveId}")
                                ) {
                                    Text(
                                        text = if (video.published) "Unpublish" else "Publish",
                                        fontSize = 11.sp,
                                        color = if (video.published) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.White
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteVideo(video.effectiveId) },
                                    modifier = Modifier.size(36.dp).testTag("delete_video_${video.effectiveId}")
                                ) {
                                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RedPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Video Dialog
    if (showAddDialog) {
        var videoTitle by remember { mutableStateOf("") }
        var videoDesc by remember { mutableStateOf("") }
        var videoUrl by remember { mutableStateOf("") }
        var progId by remember { mutableStateOf(programs.firstOrNull()?.effectiveId ?: "") }
        var cat by remember { mutableStateOf("Techniques") }
        var durationText by remember { mutableStateOf("12 mins") }
        var minRankOrderText by remember { mutableStateOf("1") }
        var targetBeltText by remember { mutableStateOf("All") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Training Video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = videoTitle,
                        onValueChange = { videoTitle = it },
                        label = { Text("Video Title *") },
                        modifier = Modifier.fillMaxWidth().testTag("new_video_title_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = videoUrl,
                        onValueChange = { videoUrl = it },
                        label = { Text("Video URL / Storage Ref *") },
                        placeholder = { Text("https://www.truefighters.in/training/...") },
                        modifier = Modifier.fillMaxWidth().testTag("new_video_url_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = videoDesc,
                        onValueChange = { videoDesc = it },
                        label = { Text("Description & Instructions") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it },
                            label = { Text("Duration (e.g. 15 mins)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minRankOrderText,
                            onValueChange = { minRankOrderText = it },
                            label = { Text("Min Belt Rank #") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Category selection chips
                    Text(text = "Category *", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Techniques", "Forms/Kata", "Warmups", "Sparring").forEach { c ->
                            FilterChip(
                                selected = cat == c,
                                onClick = { cat = c },
                                label = { Text(c, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val progName = programs.find { it.effectiveId == progId }?.effectiveName ?: "Martial Arts"
                        val vid = Video(
                            title = videoTitle.trim(),
                            description = videoDesc.trim(),
                            videoUrl = videoUrl.trim(),
                            programId = progId,
                            programName = progName,
                            category = cat,
                            durationText = durationText.trim(),
                            minRankOrder = minRankOrderText.toIntOrNull() ?: 1,
                            targetBelt = targetBeltText.trim(),
                            published = true
                        )
                        onCreateVideo(vid)
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    modifier = Modifier.testTag("submit_new_video_btn")
                ) {
                    Text("Add to Library")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    previewingVideo?.let { v ->
        VideoPlayerDialog(
            video = v,
            onDismiss = { previewingVideo = null }
        )
    }
}
