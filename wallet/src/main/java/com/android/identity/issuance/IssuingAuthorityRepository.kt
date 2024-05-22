package com.android.identity.issuance

import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.util.Logger
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * A class that contains Issuing Authorities known to the application.
 */
class IssuingAuthorityRepository(
    builderBlock: suspend BuildEnv.() -> Unit
) {
    private val issuingAuthorities = mutableListOf<Record>()
    private val buildJob = Job()
    private val _eventFlow = MutableSharedFlow<Pair<Record, String>>()

    init {
        val buildEnv = BuildEnv(issuingAuthorities, _eventFlow)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                buildEnv.builderBlock()
            } finally {
                buildJob.complete()
            }
        }
    }

    class BuildEnv(
        private val issuingAuthorities: MutableList<Record>,
        private val eventFlow: MutableSharedFlow<Pair<Record, String>>
    ) {

        /**
         * Add a Issuing Authority to the repository.
         *
         * TODO: simplify this API once we move fully to the server-side issuing authorities.
         * Builder is only needed because notification is coupled differently between server
         * and local implementations.
         *
         * @param configuration configuration for the new Issuing Authority
         * @param issuingAuthority a builder for the Issuing Authority to add.
         */
        fun addIssuingAuthority(
            configuration: IssuingAuthorityConfiguration,
            issuingAuthorityBuilder: (
                emitOnStateChanged: suspend (documentId: String) -> Unit
            ) -> IssuingAuthority
        ) {
            val record = Record(configuration, issuingAuthorityBuilder)
            issuingAuthorities.add(record)
            CoroutineScope(Dispatchers.IO).launch {
                record.eventFlow.collect { eventFlow.emit(it) }
            }
        }
    }

    suspend fun waitUntilReady() {
        joinAll(buildJob)
    }

    /**
     * Get all the Issuing Authorities that are in the repository.
     *
     * @return all the [issuingAuthority] instances in the repository.
     */
    fun getIssuingAuthorities(): List<Record> {
        return issuingAuthorities
    }

    /**
     * Looks up an issuing authority by identifier.
     *
     * @return An [IssuingAuthority] or null if there is no issuer with the given identifier.
     */
    fun lookupIssuingAuthority(issuingAuthorityIdentifier: String): Record? {
        return issuingAuthorities.find {
            it.configuration.identifier.equals(issuingAuthorityIdentifier)
        }
    }

    /**
     * A [SharedFlow] which can be used to listen for when a credential has changed state
     * on the issuer side.
     */
    val eventFlow
        get() = _eventFlow.asSharedFlow()

    class Record(
        /**
         * Issuing authority static information.
         */
        val configuration: IssuingAuthorityConfiguration,
        issuingAuthorityBuilder: (
            emitOnStateChanged: suspend (documentId: String) -> Unit
        ) -> IssuingAuthority
    ) {
        private val _eventFlow = MutableSharedFlow<Pair<Record, String>>()

        /**
         * A [SharedFlow] which can be used to listen for when a document has changed state
         * on the issuer side. The first element in the pair is an [IssuingAuthority], the second
         * element is the `documentId`.
         */
        val eventFlow: SharedFlow<Pair<Record, String>>
            get() = _eventFlow.asSharedFlow()

        /**
         * Issuing authority functional interface.
         */
        val issuingAuthority: IssuingAuthority = issuingAuthorityBuilder() { documentId ->
            _eventFlow.emit(Pair(this, documentId))
        }
    }
}
