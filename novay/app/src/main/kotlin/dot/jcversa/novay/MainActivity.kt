package dot.jcversa.novay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dot.jcversa.novay.ui.NoVaYApp
import dot.jcversa.novay.ui.theme.NoVaYTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoVaYTheme {
                NoVaYApp()
            }
        }
    }
}
