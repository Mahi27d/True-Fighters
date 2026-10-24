package com.example.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppNotification
import com.example.data.model.NotificationPreferences
import com.example.ui.theme.*

@Composable
fun NotificationBellButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("notification_bell_button")
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notifications",
                modifier = Modifier.size(24.dp)
            )
            if (unreadCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = RedPrimary,
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-4).dp)
                        .testTag("notification_unread_badge")
                ) {
                    Text(
                        text = if (unreadCount > 9) "9+" else "$unreadCount",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationInboxDialog(
    notifications: List<AppNotification>,
    onMarkRead: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = RedPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Notifications", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_notification_settings_button")) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Notification Settings", modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.NotificationsNone, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                        Text(text = "No notifications yet", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications) { notif ->
                        NotificationItemCard(
                            notification = notif,
                            onMarkRead = { onMarkRead(notif.effectiveId) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun NotificationItemCard(
    notification: AppNotification,
    onMarkRead: () -> Unit
) {
    val isUnread = !notification.read
    val icon = when (notification.type) {
        "schedule", "class" -> Icons.Default.Event
        "fee", "payment", "invoice" -> Icons.Default.CurrencyRupee
        "grading", "belt", "certificate" -> Icons.Default.MilitaryTech
        "admission" -> Icons.Default.PersonAdd
        else -> Icons.Default.Campaign
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMarkRead() }
            .testTag("notification_item_${notification.effectiveId}")
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isUnread) RedPrimary.copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = if (isUnread) RedPrimary else TextMuted, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(RedPrimary)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = notification.body,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NotificationPreferencesDialog(
    preferences: NotificationPreferences,
    onSave: (NotificationPreferences) -> Unit,
    onDismiss: () -> Unit
) {
    var classReminders by remember { mutableStateOf(preferences.classRemindersEnabled) }
    var feeReminders by remember { mutableStateOf(preferences.feeDueRemindersEnabled) }
    var gradingAlerts by remember { mutableStateOf(preferences.gradingAndBeltsEnabled) }
    var announcements by remember { mutableStateOf(preferences.announcementsEnabled) }
    var admissionsAlerts by remember { mutableStateOf(preferences.admissionsAlertsEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = RedPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notification Preferences", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Control the types of alerts and push notifications you receive.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PreferenceSwitchRow(
                    title = "Class & Schedule Reminders",
                    subtitle = "Upcoming classes, reschedules and cancellations",
                    checked = classReminders,
                    onCheckedChange = { classReminders = it },
                    tag = "pref_switch_classes"
                )

                PreferenceSwitchRow(
                    title = "Fees & Payment Notices",
                    subtitle = "Invoice dues, receipt confirmations and refunds",
                    checked = feeReminders,
                    onCheckedChange = { feeReminders = it },
                    tag = "pref_switch_fees"
                )

                PreferenceSwitchRow(
                    title = "Belt Gradings & Certifications",
                    subtitle = "Assessment reviews, promotion results and certificates",
                    checked = gradingAlerts,
                    onCheckedChange = { gradingAlerts = it },
                    tag = "pref_switch_gradings"
                )

                PreferenceSwitchRow(
                    title = "Academy Announcements",
                    subtitle = "Championships, holidays and academy notices",
                    checked = announcements,
                    onCheckedChange = { announcements = it },
                    tag = "pref_switch_announcements"
                )

                PreferenceSwitchRow(
                    title = "Admission Applications",
                    subtitle = "Alerts for new applicant registrations",
                    checked = admissionsAlerts,
                    onCheckedChange = { admissionsAlerts = it },
                    tag = "pref_switch_admissions"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        preferences.copy(
                            classRemindersEnabled = classReminders,
                            feeDueRemindersEnabled = feeReminders,
                            gradingAndBeltsEnabled = gradingAlerts,
                            announcementsEnabled = announcements,
                            admissionsAlertsEnabled = admissionsAlerts
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("save_notification_preferences_button")
            ) {
                Text("Save Preferences")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
