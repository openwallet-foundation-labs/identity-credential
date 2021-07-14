/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.viewmodel

import android.app.Application
import android.util.Log
import android.view.View
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.security.identity.IdentityCredentialException
import com.ul.ims.gmdl.cbordata.model.UserCredential.Companion.CREDENTIAL_NAME
import com.ul.ims.gmdl.issuerauthority.MockIssuerAuthority
import com.ul.ims.gmdl.offlinetransfer.utils.BiometricUtils
import com.ul.ims.gmdl.provisioning.ProvisioningManager
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import com.ul.ims.gmdl.util.SharedPreferenceUtils
import io.reactivex.Completable
import io.reactivex.CompletableObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch

class CredentialsListViewModel(private val app : Application) : AndroidViewModel(app) {

    companion object {
        const val LOG_TAG = "CredentialsListViewModel"
    }

    var credentialVisibility = ObservableInt()
    var loadingVisibility = ObservableInt()
    private var credentialLoadSuccess = MutableLiveData<Boolean>()
    private val sharedPref = SharedPreferenceUtils(app.applicationContext)

    fun credentialLoadStatus() : LiveData<Boolean> {
        return credentialLoadSuccess
    }

    fun provisionCredential() {
        provisionCredentialAsync()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ProvisionConsumer())
    }

    private inner class ProvisionConsumer : CompletableObserver {
        override fun onComplete() {
            credentialVisibility.set(View.VISIBLE)
            loadingVisibility.set(View.GONE)

            credentialLoadSuccess.value = true
        }

        override fun onSubscribe(d: Disposable) {
            loadingVisibility.set(View.VISIBLE)
            credentialVisibility.set(View.INVISIBLE)
        }

        override fun onError(e: Throwable) {
            credentialVisibility.set(View.INVISIBLE)
            loadingVisibility.set(View.GONE)

            credentialLoadSuccess.value = false
        }
    }

    private fun provisionCredentialAsync() : Completable {
        return Completable.create { emitter ->
            viewModelScope.launch {

                if (sharedPref.isDeviceProvisioned()) {
                    emitter.onComplete()
                    return@launch
                }

                try {
                    // Google IC API only supports authentication for api level 28 or higher
                    // for more info check android.hardware.biometrics.BiometricPrompt.CryptoObject
                    val authRequired = BiometricUtils.setUserAuth(app.applicationContext)

                    // insert credential into Identity Credential API
                    ProvisioningManager.createCredential(
                        app.applicationContext,
                        CREDENTIAL_NAME,
                        MockIssuerAuthority.getInstance(app.applicationContext), authRequired
                    )

                    sharedPref.setBiometricAuthRequired(authRequired)

                } catch (ex: IdentityCredentialException) {
                    onError(ex)
                    emitter.onError(RuntimeException(ex.message))
                    return@launch
                }

                // Create a Ephemeral Key Pair and give the mDL Holder Key to the
                // Issuer Authority
                val holderSession = HolderSessionManager.getInstance(
                    app.applicationContext,
                    CREDENTIAL_NAME
                )

                holderSession.initializeHolderSession()

                // Generate the MSO and Sign it
                holderSession.setAuthenticationData(MockIssuerAuthority.
                    getInstance(app.applicationContext))

                sharedPref.setDeviceProvisioned(true)

                emitter.onComplete()
            }
        }
    }

    private fun onError(ex: Throwable) {
        Log.e(LOG_TAG, ex.message, ex)
        sharedPref.setDeviceProvisioned(false)
    }
}