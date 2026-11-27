package dot.jcversa.novay

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.performance.NovayPerformanceState
import dot.jcversa.novay.ui.NoVaYApp
import dot.jcversa.novay.ui.theme.NoVaYTheme
import dot.jcversa.novay.ui.theme.NovayThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Thème, mouvement & performances : lus avant le premier rendu, sans blocage I/O.
        NovayPerformanceState.init(this)
        NovayThemeState.init(this)
        dot.jcversa.novay.ui.components.NovayMotionState.init(this)
        lifecycleScope.launch(Dispatchers.IO) { Downloads.init(this@MainActivity) }
        setContent {
            NoVaYTheme {
                NoVaYApp()
            }
        }
    }
}
