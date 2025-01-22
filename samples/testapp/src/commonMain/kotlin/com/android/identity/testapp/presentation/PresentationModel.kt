package com.android.identity.testapp.presentation

import com.android.identity.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// TODO: move this entire package to identity-appsupport when it's fully baked.

/**
 * A model used for credential presentations.
 *
 * This is a singleton, use [getInstance] to obtain a reference.
 */
class PresentationModel {

    companion object {
        private const val TAG = "PresentationModel"

        private val _instance = PresentationModel()

        /**
         * Gets the singleton instance.
         */
        fun getInstance(): PresentationModel = _instance
    }

    /**
     * Possible states that the model can be in.
     */
    enum class State {
        /**
         * Presentation is not active.
         */
        IDLE,

        /**
         * A presentation has been started but the mechanism used to communicate with the credential reader is not yet
         * available.
         */
        WAITING,

        /**
         * A presentation is currently underway and the mechanism to communicate with the credential reader is
         * available.
         */
        RUNNING,

        /**
         * A presentation has been completed.
         */
        COMPLETED,
    }

    private val _state = MutableStateFlow<State>(State.IDLE)

    /**
     * The current state of the presentation model.
     */
    val state: StateFlow<State> = _state.asStateFlow()

    var _presentationScope: CoroutineScope? = null

    /**
     * A [CoroutineScope] for the presentation.
     *
     * Any coroutine launched in this scope will be automatically canceled when the presentation completes.
     *
     * This should only be read in state [State.WAITING] or [State.Running] and will throw [IllegalStateException] if
     * this is not the case.
     */
    val presentationScope: CoroutineScope
        get() {
            check(_presentationScope != null)
            check(_state.value != State.IDLE)
            return _presentationScope!!
        }

    private var _mechanism: PresentationMechanism? = null

    /**
     * The mechanism being used to communicate with the remote credential reader.
     */
    val mechanism: PresentationMechanism?
        get() = _mechanism

    private var _error: Throwable? = null

    /**
     * If the presentation fails, this will be set with a [Throwable] with more information about the failure.
     */
    val error: Throwable?
        get() = _error

    /**
     * Resets the model to [State.IDLE].
     */
    fun reset() {
        Logger.i(TAG, "reset")
        _mechanism?.close()
        _mechanism = null
        _presentationScope?.cancel(CancellationException("PresentationModel reset"))
        _presentationScope = null
        _state.value = State.IDLE
    }

    /**
     * Sets the model to [State.WAITING].
     */
    fun setWaiting() {
        Logger.i(TAG, "setWaiting")
        check(_state.value == State.IDLE)
        _presentationScope = CoroutineScope(Dispatchers.Main)
        _state.value = State.WAITING
    }

    /**
     * Sets the model to [State.RUNNING].
     *
     * @param mechanism the [PresentationMechanism] to use.
     */
    fun setRunning(mechanism: PresentationMechanism) {
        Logger.i(TAG, "setRunning $mechanism")
        check(_state.value == State.WAITING)
        _mechanism = mechanism
        _state.value = State.RUNNING
    }

    /**
     * Sets the model to [State.COMPLETED]
     *
     * @param error pass a [Throwable] if the presentation failed, `null` if successful.
     */
    fun setCompleted(error: Throwable? = null) {
        _mechanism?.close()
        _mechanism = null
        _presentationScope?.cancel(CancellationException("PresentationModel completed"))
        _presentationScope = null
        _error = error
        _state.value = State.COMPLETED
    }
}