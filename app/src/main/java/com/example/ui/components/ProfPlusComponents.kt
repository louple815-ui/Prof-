package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.MediaUtils
import com.example.data.model.UserRole
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldOnContainer
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.ProfessionalBlue
import com.example.ui.theme.WarningAmber
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProfPlusWordmark(
    modifier: Modifier = Modifier,
    profColor: Color = Color.White,
    plusColor: Color = GoldAccent,
    fontSize: TextUnit = 26.sp
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = profColor,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize
                )
            ) {
                append("prof")
            }
            withStyle(
                SpanStyle(
                    color = plusColor,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize
                )
            ) {
                append("+")
            }
        },
        modifier = modifier
    )
}

@Composable
fun UserAvatar(
    displayName: String,
    photoData: String = "",
    verified: Boolean = false,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val isUriOrUrl = remember(photoData) {
        photoData.startsWith("http://") ||
            photoData.startsWith("https://") ||
            photoData.startsWith("content://") ||
            photoData.startsWith("file://")
    }
    val bitmap: Bitmap? = remember(photoData, isUriOrUrl) {
        if (isUriOrUrl) null else MediaUtils.decodeDataUriToBitmap(photoData)
    }
    val initials = remember(displayName) {
        displayName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "P+" }
    }
    Box(modifier = modifier.size(size)) {
        if (isUriOrUrl) {
            AsyncImage(
                model = photoData,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.5.dp, GoldAccent.copy(alpha = 0.7f), CircleShape)
            )
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.5.dp, GoldAccent.copy(alpha = 0.7f), CircleShape)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(NavyPrimary)
                    .border(1.5.dp, GoldAccent.copy(alpha = 0.7f), CircleShape)
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (verified) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "Compte vérifié",
                tint = GoldAccent,
                modifier = Modifier
                    .size(size * 0.36f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.dp)
            )
        }
    }
}

@Composable
fun RoleChip(roleRaw: String, lang: String = "fr") {
    val role = UserRole.fromRaw(roleRaw)
    val label = if (lang == "en") role.labelEn else role.labelFr
    val (bg, fg) = when (role) {
        UserRole.ADMINISTRATEUR -> GoldContainer to GoldOnContainer
        UserRole.MODERATEUR -> MaterialTheme.colorScheme.primaryContainer to ProfessionalBlue
        UserRole.RESPONSABLE_ETABLISSEMENT -> NavyPrimary.copy(alpha = 0.1f) to NavyPrimary
        UserRole.ENSEIGNANT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun OfflineStatusBanner(text: String) {
    Surface(
        color = WarningAmber.copy(alpha = 0.16f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ProfPlusEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_academic_collab),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(160.dp)
                .clip(RoundedCornerShape(18.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

fun formatTimestamp(ts: Timestamp?): String {
    if (ts == null) return "À l'instant"
    val diffSec = ((System.currentTimeMillis() / 1000L) - ts.seconds).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "À l'instant"
        diffSec < 3600L -> "Il y a ${diffSec / 60L} min"
        diffSec < 86400L -> "Il y a ${diffSec / 3600L} h"
        else -> {
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.FRENCH)
            fmt.format(ts.toDate())
        }
    }
}
