package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

fun getBeltColor(rank: String): Color {
    val lower = rank.lowercase()
    return when {
        lower.contains("yellow") -> Color(0xFFFFD600)
        lower.contains("orange") -> Color(0xFFFF6D00)
        lower.contains("green") -> Color(0xFF00C853)
        lower.contains("blue") -> Color(0xFF2979FF)
        lower.contains("brown") -> Color(0xFF795548)
        lower.contains("red") -> Color(0xFFD50000)
        lower.contains("black") -> Color(0xFF212121)
        else -> Color(0xFFEEEEEE) // White belt
    }
}

@Composable
fun BeltBadge(
    rank: String,
    modifier: Modifier = Modifier
) {
    val beltColor = getBeltColor(rank)
    val isWhite = beltColor == Color(0xFFEEEEEE)
    val textColor = if (beltColor == Color(0xFF212121)) Color.White else Color.Black

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = if (isWhite) Color.Gray else beltColor,
                shape = RoundedCornerShape(6.dp)
            ),
        color = beltColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isWhite) Color.Black else Color.White, CircleShape)
            )
            Text(
                text = rank,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color = RedPrimary,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(iconTint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "present", "verified", "active", "approved", "completed", "paid" -> Pair(SuccessGreen.copy(alpha = 0.15f), SuccessGreen)
        "late", "partially_paid", "pending", "in_progress" -> Pair(WarningOrange.copy(alpha = 0.15f), WarningOrange)
        "absent", "unpaid", "rejected", "cancelled", "overdue" -> Pair(RedPrimary.copy(alpha = 0.15f), RedPrimary)
        else -> Pair(Color.Gray.copy(alpha = 0.15f), Color.LightGray)
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(4.dp)),
        color = bgColor
    ) {
        Text(
            text = status.replace("_", " ").replaceFirstChar { it.uppercase() },
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
