package com.android.identity.processor

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowGetter
import com.android.identity.flow.annotation.FlowInterface
import com.android.identity.flow.annotation.FlowJoin
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.FlowBaseInterface
import com.android.identity.flow.handler.FlowEnvironment
import com.android.identity.flow.handler.FlowHandlerLocal
import com.android.identity.flow.handler.FlowHandlerRemote
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import org.junit.Assert
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

// The following data structures are shared between the client and the server. They can be
// used as method parameters and return values.

@CborSerializable
data class QuadraticEquation(val a: Double, val b: Double, val c: Double)

@CborSerializable
data class QuadraticSolution(val x1: Double? = null, val x2: Double? = null)

// These interfaces represent client API. Each interface must have FlowBaseInterface as base.
// Each method exposed by an interface must be marked as 'suspend' as they are generally processed
// through a network request and response. Flow holds opaque state that its methods can read
// and modify across their invocations. The state is round-tripped between the client and the
// server as a blob, like a big HTTP cookie, except that it is encrypted with the key (typically
// only known to the server).

@FlowInterface
interface QuadraticSolverFlow: FlowBaseInterface {

    // A getter can only return information that does not change for a given server. Getters
    // correspond to HTTP GET requests. Getters cannot have parameters.
    @FlowGetter
    suspend fun getName(): String

    // A method is a general-purpose operation. It corresponds to HTTP POST request. Methods
    // can take zero or more parameters and optionally return a value. Methods also have
    // access to flow state (can read and modify it).
    @FlowMethod
    suspend fun solve(equation: QuadraticEquation): QuadraticSolution
}

@FlowInterface
interface SolverFactoryFlow: FlowBaseInterface {

    // A flow can spawn sub-flows. This is indicated by returning a flow as method's result.
    // When a sub-flow FlowBaseInterface::complete is called, the parent flow is notified.
    @FlowMethod
    suspend fun createQuadraticSolver(): QuadraticSolverFlow

    // Note that this is a method, not a getter. Getters cannot access state and must return
    // the same result if invoked multiple times (which is regular HTTP GET semantics).
    @FlowMethod
    suspend fun getCount(): Int
}

// The following definitions are server-side (although we can patch them to run on the client
// for development/demo purposes). Each state represents the state for a particular flow,
// referenced by flowInterface. State can be thought as a type of HTTP cookie, except that it
// is not transported as HTTP cookie and it, being encrypted, it is totally opaque for the
// client.
//
// A single flow interface can be "implemented" by multiple states. Every state class must
// have a distinct value for the path parameter (which is optional, default path value is
// the state class simple name). Note that state instances are serialized and deserialized
// across invocations, moreover if server runs on multiple nodes, flow method invocations may
// happen across multiple nodes. Thus only data stored in the state is meaningful.
//
// FlowEnvironment is a way to access interfaces exposed by the server engine. If a method only
// needs flow state and parameters to do its job (a common case), this parameter will go unused.
// In the future we may make it optional.
//
// Each state must be trivially constructable and have defaults for all its fields.

@CborSerializable
@FlowState(
    flowInterface = QuadraticSolverFlow::class,
    path = "direct"
)
data class DirectQuadraticSolverState(
    var count: Int = 0
) {
    companion object {
        // Getters cannot access the state, thus they must be implemented as companion methods.
        @FlowGetter
        fun getName(env: FlowEnvironment): String {
            return "Direct"
        }
    }

    @FlowMethod
    fun solve(env: FlowEnvironment, equation: QuadraticEquation): QuadraticSolution {
        val det = equation.b * equation.b - 4 * equation.a * equation.c
        val result = if (det > 0) {
            val d = sqrt(det)
            QuadraticSolution(
                (-equation.b + d)/(2*equation.a),
                (-equation.b - d)/(2*equation.a)
            )
        } else if (det == 0.0) {
            QuadraticSolution(-equation.b/(2*equation.a))
        } else {
            QuadraticSolution()
        }
        count++
        return result
    }

    @FlowMethod
    fun getCount(env: FlowEnvironment): Int = count
}

@CborSerializable
@FlowState(
    flowInterface = QuadraticSolverFlow::class,
    path = "factory"
)
data class SolverFactoryState(
    var totalCount: Int = 0
) {
    companion object

    // Returning a flow on client is done merely by returning the flow state on the server.
    @FlowMethod
    fun createQuadraticSolver(env: FlowEnvironment): DirectQuadraticSolverState {
        return DirectQuadraticSolverState()
    }

    // When the sub-flow of a given type is completed using FlowBaseInterface::complete call,
    // a join method is called for the corresponding flow type.
    @FlowJoin
    fun completeQuadraticSolver(env: FlowEnvironment, solver: DirectQuadraticSolverState) {
        totalCount += solver.count
    }

    @FlowMethod
    fun getCount(env: FlowEnvironment): Int = totalCount
}

// FlowHandlerLocal handles flow calls that executed locally. It is for use on the server,
// for running code in the app for development, and for testing.
fun buildLocalHandler(): FlowHandlerLocal {
    val builder = FlowHandlerLocal.Builder()
    DirectQuadraticSolverState.register(builder)
    SolverFactoryState.register(builder)
    return builder.build(Random.Default.nextBytes(16))
}

class FlowProcessorTest {
    val localHandler = buildLocalHandler()

    @Test
    fun local() {
        testFlow(SolverFactoryFlowImpl(localHandler, "factory"))
    }

    @Test
    fun remote() {
        // Remote handler executes flow calls by marshalling them to another handler, typically
        // through HTTP. We can, however short-circuit them to just go to our local handler
        // for testing.
        val remoteHandler = FlowHandlerRemote(object : FlowHandlerRemote.HttpClient {
            override suspend fun get(url: String): FlowHandlerRemote.HttpResponse {
                val (flow, method) = url.split("/")
                return FlowHandlerRemote.HttpResponse(
                    200,
                    "OK",
                    localHandler.handleGet(flow, method)
                )
            }

            override suspend fun post(url: String, data: ByteString): FlowHandlerRemote.HttpResponse {
                val (flow, method) = url.split("/")
                return FlowHandlerRemote.HttpResponse(
                    200,
                    "OK",
                    localHandler.handlePost(flow, method, data)
                )
            }
        })
        testFlow(SolverFactoryFlowImpl(remoteHandler, "factory"))
    }

    fun testFlow(factory: SolverFactoryFlow) {
        runBlocking {
            val solver = factory.createQuadraticSolver()
            Assert.assertEquals("Direct", solver.getName())
            val solution1 = solver.solve(QuadraticEquation(a = 1.0, b = -6.0, c = 9.0))
            Assert.assertNotNull(solution1.x1)
            Assert.assertEquals(3.0, solution1.x1!!, 1e-10)
            Assert.assertNull(solution1.x2)
            val solution2 = solver.solve(QuadraticEquation(a = 4.0, b = 4.0, c = 1.1))
            Assert.assertNull(solution2.x1)
            Assert.assertNull(solution2.x2)
            Assert.assertEquals(0, factory.getCount())
            solver.complete()
            Assert.assertEquals(2, factory.getCount())
        }
    }
}