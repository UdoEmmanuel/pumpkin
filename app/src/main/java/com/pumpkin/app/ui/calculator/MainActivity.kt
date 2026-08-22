package com.pumpkin.app.ui.calculator

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.pumpkin.app.ui.navigation.PumpkinNavHost
import com.pumpkin.app.ui.theme.PumpkinTheme

// FragmentActivity (not plain ComponentActivity) because BiometricPrompt,
// used from the lock screen further down the nav graph, requires one.
// Named MainActivity / lives in ui.calculator because it doubles as the
// decoy calculator screen's host — see AndroidManifest.xml for why.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PumpkinTheme {
                PumpkinNavHost(activity = this)
            }
        }
    }
}
