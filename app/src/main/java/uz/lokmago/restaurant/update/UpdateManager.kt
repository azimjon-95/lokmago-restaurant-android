package uz.lokmago.restaurant.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.data.remote.dto.VersionDto
import uz.lokmago.restaurant.util.compareVersions

/**
 * Google Play In-App Updates (never a self-hosted APK).
 *  - normal release  -> FLEXIBLE   (downloads in background, then "Qayta ishga tushirish")
 *  - critical        -> IMMEDIATE  (full-screen, blocks the app until updated)
 * Critical = backend says forceUpdate / installed < minimumVersion, or Play update priority >= 4.
 */
class UpdateManager(private val activity: Activity, private val launcher: ActivityResultLauncher<IntentSenderRequest>) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    private val _readyToInstall = MutableStateFlow(false)
    /** FLEXIBLE update downloaded — UI shows a snackbar that calls [completeFlexible]. */
    val readyToInstall: StateFlow<Boolean> = _readyToInstall.asStateFlow()

    private val _blocking = MutableStateFlow(false)
    /** True while a mandatory update is required but the Play flow isn't running (e.g. user cancelled). */
    val blocking: StateFlow<Boolean> = _blocking.asStateFlow()

    private val listener = InstallStateUpdatedListener { st ->
        if (st.installStatus() == InstallStatus.DOWNLOADED) _readyToInstall.value = true
    }

    fun register() = manager.registerListener(listener)
    fun unregister() = manager.unregisterListener(listener)

    fun completeFlexible() { manager.completeUpdate() }

    /** Call on every start & resume. [policy] comes from GET /app/version (may be null if offline). */
    fun check(policy: VersionDto.AndroidVersion?) {
        val forcedByBackend = policy != null &&
            (policy.forceUpdate || compareVersions(BuildConfig.VERSION_NAME.substringBefore('-'), policy.minimumVersion) < 0)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _readyToInstall.value = true
                // an IMMEDIATE update that was interrupted (app killed / backgrounded) must be resumed
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    start(info, AppUpdateType.IMMEDIATE)
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
                    val critical = forcedByBackend || info.updatePriority() >= 4
                    when {
                        critical && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> { _blocking.value = true; start(info, AppUpdateType.IMMEDIATE) }
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> start(info, AppUpdateType.FLEXIBLE)
                    }
                }
                else -> _blocking.value = forcedByBackend
            }
        }
        // Backend demands a newer build but Play shows nothing (e.g. staged rollout): keep the gate up.
        if (forcedByBackend) _blocking.value = true
    }

    /** If the user cancelled a mandatory update the gate stays up; its button calls this to re-open Play. */
    fun retryBlocking(policy: VersionDto.AndroidVersion?) = check(policy)

    private fun start(info: AppUpdateInfo, type: Int) {
        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
    }
}
