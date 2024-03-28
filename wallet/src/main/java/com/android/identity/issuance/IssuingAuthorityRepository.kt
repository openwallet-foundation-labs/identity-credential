package com.android.identity.issuance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * A class that contains Issuing Authorities known to the application.
 */
class IssuingAuthorityRepository {
    private val issuingAuthorities: MutableList<IssuingAuthority> = mutableListOf()

    /**
     * Add a Issuing Authority to the repository.
     *
     * @param issuingAuthority the Issuing Authority to add.
     */
    fun add(issuingAuthority: IssuingAuthority) {
        issuingAuthorities.add(issuingAuthority)
        CoroutineScope(Dispatchers.IO).launch {
            issuingAuthority.eventFlow.collect { _eventFlow.emit(it) }
        }
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

    private val _eventFlow = MutableSharedFlow<Pair<IssuingAuthority, String>>()

    /**
     * A [SharedFlow] which can be used to listen for when a credential has changed state
     * on the issuer side.
     */
    val eventFlow
        get() = _eventFlow.asSharedFlow()
}
