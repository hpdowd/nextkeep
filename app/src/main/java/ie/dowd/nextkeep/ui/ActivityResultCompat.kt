package ie.dowd.nextkeep.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/*
 * FragmentActivity-safe stand-ins for the AndroidX Activity Result launchers.
 *
 * MainActivity must be a FragmentActivity to host the biometric app-lock prompt.
 * But the Compose result registry (ComponentActivity.activityResultRegistry)
 * assigns request codes >= 0x10000, and FragmentActivity rejects those for both
 * requestPermissions() and startActivityForResult() ("Can only use lower 16 bits
 * for requestCode"). So rememberLauncherForActivityResult crashes the instant you
 * .launch() it, before any dialog appears -- which is why the QR scanner fell over
 * instead of asking for the camera, yet worked once the permission was granted by
 * hand. These helpers launch through the framework APIs (a 16-bit request code, or
 * no result at all) and read the outcome when the activity returns to the
 * foreground, sidestepping the registry entirely.
 */

/** Unwrap a (possibly wrapped) Compose context to its host [Activity], if any. */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Returns a lambda that, once invoked, makes the *next* foreground resume of the
 * host activity run [onReturn]. The resume that arrives when the observer is first
 * registered is ignored, so this only fires after you've actually launched
 * something (a permission dialog, a settings screen, ...).
 */
@Composable
private fun rememberResumeSignal(onReturn: () -> Unit): () -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnReturn = rememberUpdatedState(onReturn)
    var pending by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pending) {
                pending = false
                latestOnReturn.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return { pending = true }
}

/**
 * Requests a runtime [permission] and reports the resulting grant state to
 * [onResult] once the user dismisses the system dialog (read back on resume, not
 * from the callback). Returns a lambda that launches the request.
 */
@Composable
fun rememberPermissionRequest(
    permission: String,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val latestOnResult = rememberUpdatedState(onResult)
    val arm = rememberResumeSignal { latestOnResult.value(context.hasPermission(permission)) }
    return launch@{
        val act = activity ?: return@launch
        arm()
        ActivityCompat.requestPermissions(act, arrayOf(permission), PERMISSION_REQUEST_CODE)
    }
}

/**
 * Launches an [Intent] (typically a system settings screen) and runs [onReturn]
 * when the user comes back -- for flows where you only need to re-read some state
 * on return, not a result code. Returns a lambda that launches a given intent.
 */
@Composable
fun rememberIntentLauncher(onReturn: () -> Unit): (Intent) -> Unit {
    val context = LocalContext.current
    val arm = rememberResumeSignal(onReturn)
    return { intent ->
        arm()
        context.startActivity(intent)
    }
}

// Any value within the lower 16 bits; FragmentActivity rejects anything wider.
private const val PERMISSION_REQUEST_CODE = 0x2A
