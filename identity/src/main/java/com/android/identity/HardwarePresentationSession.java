package com.android.identity;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricPrompt;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class HardwarePresentationSession extends PresentationSession {
    private static final String TAG = "HwPresentationSession"; // limit to <= 23 chars
    private final android.security.identity.PresentationSession mSession;

    HardwarePresentationSession(android.security.identity.PresentationSession session) {
        mSession = session;
    }

    @NonNull
    @Override
    public KeyPair getEphemeralKeyPair() {
        return mSession.getEphemeralKeyPair();
    }

    @Override
    public void setReaderEphemeralPublicKey(@NonNull PublicKey readerEphemeralPublicKey) throws InvalidKeyException {
        mSession.setReaderEphemeralPublicKey(readerEphemeralPublicKey);
    }

    @Override
    public void setSessionTranscript(@NonNull byte[] sessionTranscript) {
        mSession.setSessionTranscript(sessionTranscript);
    }

    @Nullable
    @Override
    public CredentialDataResult getCredentialData(@NonNull String credentialName, @NonNull CredentialDataRequest request) throws NoAuthenticationKeyAvailableException, InvalidReaderSignatureException, InvalidRequestMessageException, EphemeralPublicKeyNotFoundException {

        android.security.identity.CredentialDataRequest platformRequest =
                request.getAsPlatformRequest();
        try {
            android.security.identity.CredentialDataResult platformResult =
                    mSession.getCredentialData(credentialName, platformRequest);

            return new PlatformCredentialDataResult(platformResult);

        } catch (android.security.identity.EphemeralPublicKeyNotFoundException e) {
            throw new EphemeralPublicKeyNotFoundException(e.getMessage(), e);
        } catch (android.security.identity.InvalidReaderSignatureException e) {
            throw new InvalidReaderSignatureException(e.getMessage(), e);
        } catch (android.security.identity.InvalidRequestMessageException e) {
            throw new InvalidRequestMessageException(e.getMessage(), e);
        } catch (android.security.identity.NoAuthenticationKeyAvailableException e) {
            throw new NoAuthenticationKeyAvailableException(e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public BiometricPrompt.CryptoObject getCryptoObject() {
        return new BiometricPrompt.CryptoObject(mSession);
    }
}
