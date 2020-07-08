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

package com.ul.ims.gmdl.reader.viewmodel

import android.app.Application
import android.view.View
import androidx.databinding.ObservableField
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.cbordata.model.UserCredential
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.IssuerNameSpaces
import com.ul.ims.gmdl.cbordata.security.mso.MobileSecurityObject
import com.ul.ims.gmdl.security.issuerdataauthentication.IssuerDataAuthenticationException
import com.ul.ims.gmdl.security.issuerdataauthentication.IssuerDataAuthenticator
import com.ul.ims.gmdl.security.issuerdataauthentication.RootCertificateInitialiser
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.doAsync
import java.util.*

class DisplayCredentialsViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        const val LOG_TAG = "DisplayCredentialsViewModel"
    }

    private var credentialLiveData = MutableLiveData<UserCredential>()
    private var errorLiveData = MutableLiveData<Throwable>()
    var shareButtonVisibility = ObservableField<Int>()
    var credentialVisibility = ObservableField<Int>()
    var loadingVisibility = ObservableField<Int>()
    var issuerAuthImg = ObservableField<Int>()
    var issuerAuthVisibility = ObservableField<Int>()

    fun getCredentialLiveData(): LiveData<UserCredential> {
        return credentialLiveData
    }

    fun getProvisionErrorsLiveData(): LiveData<Throwable> {
        return errorLiveData
    }

    fun verifyIssuerDataAuthenticity(
        issuerAuth: CoseSign1?,
        issuerNameSpaces: IssuerNameSpaces?
    ) {
        issuerAuthVisibility.set(View.INVISIBLE)
        issuerAuth?.let {
            issuerNameSpaces?.let {
                verifyIssuerDataVerifierAsync(issuerAuth, issuerNameSpaces)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(IssuerDataAuthenticationConsumer())
            }
        }
    }

    fun provisionCredential(credential: UserCredential?) {
        provisionCredentialAsync(credential)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(ProvisionConsumer(credential?.let { false } ?: run { true }))
    }

    private fun verifyIssuerDataVerifierAsync(
        coseSign1: CoseSign1,
        issuerNameSpaces: IssuerNameSpaces
    ): Single<Boolean> {
        return Single.create { emitter ->
            doAsync {
                val mso = MobileSecurityObject.Builder()
                    .decode(coseSign1.payloadData)
                    .build()

                mso?.let {
                    val rootCertificateInitializer =
                        RootCertificateInitialiser(app.applicationContext)

                    val issuerDataAuthenticator = IssuerDataAuthenticator(
                        rootCertificateInitializer.rootCertificatesAndPublicKeys,
                        coseSign1,
                        issuerNameSpaces,
                        mso.documentType,
                        null
                    )
                    try {
                        emitter.onSuccess(issuerDataAuthenticator.isDataAuthentic(Date()))
                    } catch (ex: IssuerDataAuthenticationException) {
                        emitter.onSuccess(false)
                    }
                } ?: run {
                    emitter.onError(RuntimeException("MSO Object is Null"))
                }
                emitter.onSuccess(false)
            }
        }
    }

    private fun provisionCredentialAsync(credential: UserCredential?): Single<UserCredential> {
        return Single.create { emitter ->
            doAsync {
                // Credential received after a BLE transfer.
                if (credential != null) {
                    emitter.onSuccess(credential)
                } else {
                    emitter.onError(RuntimeException("credential is null"))
                }
            }
        }
    }

    private inner class ProvisionConsumer(val showShareButton: Boolean) :
        SingleObserver<UserCredential> {
        override fun onSuccess(t: UserCredential) {
            loadingVisibility.set(View.GONE)
            credentialVisibility.set(View.VISIBLE)

            if (showShareButton) {
                shareButtonVisibility.set(View.VISIBLE)
            }
            credentialLiveData.postValue(t)
        }

        override fun onSubscribe(d: Disposable) {
            loadingVisibility.set(View.VISIBLE)
            credentialVisibility.set(View.GONE)
            shareButtonVisibility.set(View.GONE)
        }

        override fun onError(e: Throwable) {
            credentialVisibility.set(View.GONE)
            shareButtonVisibility.set(View.GONE)
            loadingVisibility.set(View.GONE)

            errorLiveData.postValue(e)
        }
    }

    private inner class IssuerDataAuthenticationConsumer : SingleObserver<Boolean> {
        override fun onSuccess(t: Boolean) {
            if (t) {
                issuerAuthImg.set(R.drawable.ic_baseline_done)
            } else {
                issuerAuthImg.set(R.drawable.ic_baseline_error_outline)
            }

            issuerAuthVisibility.set(View.VISIBLE)
        }

        override fun onSubscribe(d: Disposable) {
            issuerAuthVisibility.set(View.INVISIBLE)
        }

        override fun onError(e: Throwable) {
            issuerAuthImg.set(R.drawable.ic_baseline_error_outline)

            issuerAuthVisibility.set(View.VISIBLE)
        }
    }
}