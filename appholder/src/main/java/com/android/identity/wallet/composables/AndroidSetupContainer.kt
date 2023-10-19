package com.android.identity.wallet.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.identity.wallet.R
import com.android.identity.wallet.composables.state.AuthTypeState
import com.android.identity.wallet.selfsigned.OutlinedContainerVertical

@Composable
fun AndroidSetupContainer(
    modifier: Modifier = Modifier,
    isOn: Boolean,
    timeoutSeconds: Int,
    lskfAuthTypeState: AuthTypeState,
    biometricAuthTypeState: AuthTypeState,
    useStrongBox: AuthTypeState,
    onUserAuthenticationChanged: (isOn: Boolean) -> Unit,
    onAuthTimeoutChanged: (authTimeout: Int) -> Unit,
    onLskfAuthChanged: (isOn: Boolean) -> Unit,
    onBiometricAuthChanged: (isOn: Boolean) -> Unit,
    onStrongBoxChanged: (isOn: Boolean) -> Unit
) {
    Column(modifier = modifier) {
        OutlinedContainerVertical(modifier = Modifier.fillMaxWidth()) {
            val labelOn = stringResource(id = R.string.user_authentication_on)
            val labelOff = stringResource(id = R.string.user_authentication_off)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ValueLabel(
                    modifier = Modifier.weight(1f),
                    label = if (isOn) labelOn else labelOff,
                )
                Switch(
                    modifier = Modifier.padding(start = 8.dp),
                    checked = isOn,
                    onCheckedChange = onUserAuthenticationChanged
                )
            }
            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = isOn
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ValueLabel(
                            modifier = Modifier.weight(1f),
                            label = stringResource(id = R.string.keystore_android_user_auth_timeout)
                        )
                        NumberChanger(
                            number = timeoutSeconds,
                            onNumberChanged = onAuthTimeoutChanged,
                            counterTextStyle = MaterialTheme.typography.titleLarge
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alpha = if (lskfAuthTypeState.canBeModified) 1f else .5f
                        ValueLabel(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(alpha),
                            label = stringResource(id = R.string.user_auth_type_allow_lskf)
                        )
                        Checkbox(
                            checked = lskfAuthTypeState.isEnabled,
                            onCheckedChange = onLskfAuthChanged,
                            enabled = lskfAuthTypeState.canBeModified
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alpha = if (biometricAuthTypeState.canBeModified) 1f else .5f
                        ValueLabel(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(alpha),
                            label = stringResource(id = R.string.user_auth_type_allow_biometric)
                        )
                        Checkbox(
                            checked = biometricAuthTypeState.isEnabled,
                            onCheckedChange = onBiometricAuthChanged,
                            enabled = biometricAuthTypeState.canBeModified
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val alpha = if (useStrongBox.canBeModified) 1f else .5f
                        ValueLabel(
                            modifier = Modifier
                                .weight(1f)
                                .alpha(alpha),
                            label = stringResource(id = R.string.user_auth_use_strong_box)
                        )
                        Checkbox(
                            checked = useStrongBox.isEnabled,
                            onCheckedChange = onStrongBoxChanged,
                            enabled = useStrongBox.canBeModified
                        )
                    }
                }
            }
        }
    }
}

