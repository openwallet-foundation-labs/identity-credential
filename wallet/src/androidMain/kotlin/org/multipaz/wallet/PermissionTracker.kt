package org.multipaz_credential.wallet

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.MutableLiveData
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * An object that tracks granted permissions and obtains required ones as needed.
 *
 * The permission model used by this class is very simple: all potentially needed permissions
 * must be listed upfront. A subset of permissions required for the functionality of a particular
 * "target" screen is checked when attempting to show that screen. If all permissions required by
 * the target screen are granted, it is shown normally. Otherwise a missing permission screen is
 * shown with prompts to grant permission. When user grants permissions, view is changed to show
 * the target screen automatically.
 *
 * To use this functionality, create this object when activity is created. Call [updatePermissions]
 * in [ComponentActivity.onCreate]. Then simply wrap the UI of any target screen in
 * [PermissionCheck] call.
 *
 * @param activity activity that hosts this object
 * @param permissions a map from permission string to the text shown to the user to explain why
 *     that permission is necessary for this app
 */
class PermissionTracker(private val activity: ComponentActivity, private val permissions: Map<String, Int>) {
    private val state = permissions.mapValues { MutableLiveData(false) }
    private val requestedSet = mutableSetOf<String>()

    private val permissionRequester =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updatePermissions()
            if (requestedSet.isNotEmpty()) {
                requestNextPermission(requestedSet.iterator().next())
            }
        }

    /**
     * Reads all the permission states (granted or not) from the system.
     *
     * Must be explicitly called from [ComponentActivity.onCreate].
     */
    fun updatePermissions() {
        for (permissionEntry in state.entries) {
            permissionEntry.value.value = activity.checkSelfPermission(permissionEntry.key) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions(permissions: Iterable<String>) {
        requestedSet.addAll(permissions)
        requestNextPermission(permissions.iterator().next())
    }

    private fun requestNextPermission(permission: String) {
        requestedSet.remove(permission)
        permissionRequester.launch(permission)
    }

    /**
     * Check if the given set of permissions is granted.
     */
    @Composable
    fun granted(permissions: Iterable<String>): Boolean {
        for (permission in permissions) {
            if (!state[permission]!!.observeAsState().value!!) {
                return false
            }
        }
        return true
    }

    /**
     * Ensure that the given set of permissions is granted and show the given content.
     *
     * If some permissions are not granted, display permission request UI in place of the
     * content until all the required permissions are granted.
     */
    @Composable
    fun PermissionCheck(permissions: Iterable<String>, displayPermissionRequest: Boolean = true,
                        content: @Composable () -> Unit) {
        if (granted(permissions)) {
            content()
        } else if (displayPermissionRequest) {
            Column {
                PermissionRequests(permissions)
            }
        }
    }

    @Composable
    fun PermissionRequests(permissions: Iterable<String>,
                           extraButtons: @Composable RowScope.() -> Unit = {}) {
        var count = 0
        for (permission in permissions) {
            if (!state[permission]!!.observeAsState().value!!) {
                count++
                SinglePermissionRequestText(permission = permission)
            }
        }
        val textId = if (count > 1) {
            R.string.permission_button_grant_permissions
        } else {
            R.string.permission_button_grant_permission
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(
                modifier = Modifier.padding(8.dp),
                onClick = { requestPermissions(permissions) }) {
                Text(stringResource(textId))
            }
            extraButtons()
        }
    }

    @Composable
    private fun SinglePermissionRequestText(permission: String) {
        val reasoningTxt: String = activity.getString(this.permissions[permission]!!)
        Text(text = reasoningTxt,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(8.dp))
        Spacer(modifier = Modifier.height(16.dp))
    }
}