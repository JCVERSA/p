package dot.jcversa.novay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dot.jcversa.novay.ui.components.AnimateEntrance
import dot.jcversa.novay.ui.components.bounceClick
import dot.jcversa.novay.ui.theme.Amber500
import dot.jcversa.novay.ui.theme.DarkSurfaceVariant
import dot.jcversa.novay.ui.theme.TextMuted

private val demoQualities = listOf("360P", "480P", "720P", "1080P")

/**
 * Écran Lecteur (squelette V0). V1 : media3/ExoPlayer — HLS avec en-têtes
 * Referer/UA du catalogue ; les résolutions sont lues dans la master
 * playlist du lecteur (cf. parseVoiranimePlayer côté bot).
 */
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.bounceClick(onClick = onBack)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Épisode 1",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Amber500,
                modifier = Modifier.size(72.dp)
            )
        }

        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Qualité",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                demoQualities.forEachIndexed { index, quality ->
                    AnimateEntrance(index = index) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = DarkSurfaceVariant
                        ) {
                            Text(
                                text = quality,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Résolutions lues depuis le lecteur du catalogue en V1.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "VF · VOSTFR",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
