package com.android.identity_credential.wallet

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.MutableLiveData
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier

class PermissionTracker(private val mActivity: ComponentActivity, private val mPermissions: Map<String, String>) {
    private val mState = mPermissions.mapValues { MutableLiveData(false) }

    private val permissionRequester =
        mActivity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updatePermissions();
        }

    fun updatePermissions() {
        for (permissionEntry in mState.entries) {
            permissionEntry.value.value = mActivity.checkSelfPermission(permissionEntry.key) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermission(permission: String) {
        permissionRequester.launch(permission)
    }

    @Composable
    fun PermissionCheck(permissions: List<String>, content: @Composable () -> Unit) {

        var allGranted = true
        for (permission in permissions) {
            if (!mState[permission]!!.observeAsState().value!!) {
                allGranted = false
                Text(mPermissions[permission]!!, modifier = Modifier.fillMaxWidth())
                Button(onClick = {requestPermission(permission)}) {
                    Text("Request")
                }
            }
        }
        if (allGranted) {
            content()
        }
    }


}