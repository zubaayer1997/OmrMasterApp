package com.omrmaster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omrmaster.app.ui.navigation.OmrMainScaffold
import com.omrmaster.app.ui.theme.OmrMasterTheme
import com.omrmaster.app.viewmodel.OmrViewModel
import com.omrmaster.app.viewmodel.OmrViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OmrApplication
        setContent {
            OmrMasterTheme {
                val vm: OmrViewModel = viewModel(
                    factory = OmrViewModelFactory(application, app.repository)
                )
                OmrMainScaffold(viewModel = vm)
            }
        }
    }
}
