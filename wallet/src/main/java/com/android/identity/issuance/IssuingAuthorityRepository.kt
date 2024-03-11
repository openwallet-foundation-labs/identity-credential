package com.android.identity.issuance

/**
 * A class that contains Issuing Authorities known to the application.
 */
class IssuingAuthorityRepository {
    private val issuingAuthorities: MutableList<IssuingAuthority> = mutableListOf()

    private val observer = object : IssuingAuthority.Observer {
        override fun onCredentialStateChanged(
            issuingAuthority: IssuingAuthority,
            credentialId: String
        ) {
            for (repoObserver in observers) {
                repoObserver.onCredentialStateChanged(issuingAuthority, credentialId)
            }
        }
    }

    /**
     * Add a Issuing Authority to the repository.
     *
     * @param issuingAuthority the Issuing Authority to add.
     */
    fun add(issuingAuthority: IssuingAuthority) {
        issuingAuthorities.add(issuingAuthority)
        issuingAuthority.startObserving(observer)
    }

    /**
     * Get all the Issuing Authorities that are in the repository.
     *
     * @return all the [issuingAuthority] instances in the repository.
     */
    fun getIssuingAuthorities(): List<IssuingAuthority> {
        return issuingAuthorities
    }

    /**
     * Looks up an issuing authority by identifier.
     *
     * @return An [IssuingAuthority] or null if there is no issuer with the given identifier.
     */
    fun lookupIssuingAuthority(issuingAuthorityIdentifier: String): IssuingAuthority? {
        return issuingAuthorities.find {
            it.configuration.identifier.equals(issuingAuthorityIdentifier)
        }
    }

    private val observers = mutableListOf<Observer>()

    /**
     * Sets an observer to be notified when a credential has an updated state.
     *
     * Updates might be implemented using a lossy mechanism (e.g. push notifications)
     * so applications must not rely on getting a callback whenever the state changes.
     *
     * The observer can be removed using [stopObserving].
     *
     * @param observer the observer.
     */
    fun startObserving(observer: Observer) {
        observers.add(observer)
    }

    /**
     * Removes the observer previously set with [startObserving].
     *
     * @param observer the observer.
     */
    fun stopObserving(observer: Observer) {
        observers.remove(observer)
    }


    /**
     * An interface which can be used to be informed when a credential has changed from
     * in one of the registered [IssuingAuthority] instances.
     */
    interface Observer {
        /**
         * This is called when a credential's state has changed.
         *
         * The application should call [IssuingAuthority.credentialGetState] to collect
         * the new state.
         *
         * @param issuingAuthority the issuing authority.
         * @param credentialId the credential which state has changed.
         */
        fun onCredentialStateChanged(issuingAuthority: IssuingAuthority, credentialId: String)
    }


}
