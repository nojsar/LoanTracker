package com.nojus.loantracker

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.nojus.loantracker.ui.AppNavigation
import com.nojus.loantracker.ui.theme.LoanTrackerTheme

class MainActivity : ComponentActivity() {

    private lateinit var appUpdateManager: AppUpdateManager

    // Play returns the update flow through this launcher. For an IMMEDIATE update
    // Play drives its own full-screen UI; we only observe the final result code.
    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        if (result.resultCode != RESULT_OK) {
            // The user cancelled or the download failed. The app requires the latest
            // version, so re-check on the next resume to prompt again.
            Log.w(TAG, "In-app update did not complete: resultCode=${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForImmediateUpdate()

        setContent {
            LoanTrackerTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume an immediate update that was interrupted (e.g. the app was
        // backgrounded mid-download). Play reports it as in progress so we can
        // re-launch the blocking flow.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediateUpdate(info)
            }
        }
    }

    private fun checkForImmediateUpdate() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                val updateAvailable =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                if (updateAvailable && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    startImmediateUpdate(info)
                }
            }
            .addOnFailureListener { error ->
                // No Play Store, offline, or sideloaded build: let the app run as-is.
                Log.w(TAG, "Unable to check for in-app updates", error)
            }
    }

    private fun startImmediateUpdate(info: AppUpdateInfo) {
        runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateResultLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to start in-app update flow", error)
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
