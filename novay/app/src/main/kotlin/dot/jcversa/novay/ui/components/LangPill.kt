package dot.jcversa.novay.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dot.jcversa.novay.ui.theme.Amber500
import dot.jcversa.novay.ui.theme.MintGreen

/**
 * Badge de langue : VF en ambre, VOSTFR en vert menthe (couleur « succès »
 * du panel).
 */
@Composable
fun LangPill(
    text: String,
    vf: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tint = if (vf) Amber500 else MintGreen
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.14f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}
