package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.model.CotisationStatus
import com.example.ui.theme.AmberGold
import com.example.ui.theme.StatusPendingYellow
import com.example.ui.theme.StatusUnpaidRed
import com.example.ui.theme.StatusValidatedGreen

@Composable
fun StatusBadge(
    status: CotisationStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label, icon) = when (status) {
        CotisationStatus.VALIDATED -> Quadruple(
            StatusValidatedGreen.copy(alpha = 0.18f),
            StatusValidatedGreen,
            "Validé",
            Icons.Default.Check
        )
        CotisationStatus.PENDING -> Quadruple(
            StatusPendingYellow.copy(alpha = 0.18f),
            StatusPendingYellow,
            "En attente",
            Icons.Default.HourglassTop
        )
        CotisationStatus.UNPAID -> Quadruple(
            StatusUnpaidRed.copy(alpha = 0.18f),
            StatusUnpaidRed,
            "Non payé",
            Icons.Default.Close
        )
        CotisationStatus.LATE_UNPAID -> Quadruple(
            StatusUnpaidRed.copy(alpha = 0.25f),
            StatusUnpaidRed,
            "Retard (+Amende)",
            Icons.Default.Warning
        )
        CotisationStatus.BENEFICIARY_EXEMPT -> Quadruple(
            AmberGold.copy(alpha = 0.2f),
            AmberGold,
            "Receveur (Exempté)",
            Icons.Default.Star
        )
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
