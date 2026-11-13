package dot.jcversa.novay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dot.jcversa.novay.downloads.Downloads
import dot.jcversa.novay.downloads.StreamHeadersRegistry
import dot.jcversa.novay.ui.NoVaYApp
import dot.jcversa.novay.ui.theme.NoVaYTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // V2 : registre d'en-têtes (lecture SharedPreferences, rapide) de
        // façon synchrone, puis cache offline + DownloadManager (I/O disque
        // : index SQLite) en arrière-plan — manager() est synchronisé et
        // idempotent, les premiers usages attendent simplement qu'il soit
        // prêt, et les téléchargements interrompus reprennent ici.
        StreamHeadersRegistry.init(this)
        lifecycleScope.launch(Dispatchers.IO) { Downloads.init(this@MainActivity) }
        setContent {
            NoVaYTheme {
                NoVaYApp()
            }
        }
    }
}
