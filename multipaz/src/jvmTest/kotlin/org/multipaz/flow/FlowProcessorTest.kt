package org.multipaz.flow

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.flow.annotation.FlowException
import org.multipaz.flow.annotation.FlowInterface
import org.multipaz.flow.annotation.FlowJoin
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.client.FlowBase
import org.multipaz.flow.handler.AesGcmCipher
import org.multipaz.flow.handler.FlowDispatcherHttp
import org.multipaz.flow.handler.FlowDispatcherLocal
import org.multipaz.flow.handler.FlowExceptionMap
import org.multipaz.flow.handler.FlowNotifier
import org.multipaz.flow.handler.FlowNotifierPoll
import org.multipaz.flow.handler.FlowPoll
import org.multipaz.flow.handler.FlowPollHttp
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.flow.handler.HttpHandler
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

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
interface QuadraticSolverFlow: FlowBase {
    // A method is a general-purpose operation. It corresponds to HTTP POST request. Methods
    // can take zero or more parameters and optionally return a value. Methods also have
    // access to flow state (can read and modify it).
    @FlowMethod
    suspend fun solve(equation: QuadraticEquation): QuadraticSolution

    @FlowMethod
    suspend fun getName(): String
}

@FlowInterface
interface SolverFactoryFlow: FlowBase {

    // A flow can spawn sub-flows. This is indicated by returning a flow as method's result.
    // When a sub-flow FlowBaseInterface::complete is called, the parent flow is notified.
    @FlowMethod
    suspend fun createQuadraticSolver(name: String): QuadraticSolverFlow

    @FlowMethod
    suspend fun getCount(): Int

    // Just to test nullable parameters and return values
    @FlowMethod
    suspend fun nullableIdentity(value: String?): String?

    // Test passing flows as parameters
    @FlowMethod
    suspend fun examineSolver(solver: QuadraticSolverFlow): String
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

// Abstract base state classes do not need to be serializable, but still have to be marked with
// FlowState annotation.
@FlowState(
    flowInterface = QuadraticSolverFlow::class
)
abstract class AbstractSolverState {
    abstract fun finalCount(): Int

    companion object
}

@CborSerializable
@FlowState(
    flowInterface = QuadraticSolverFlow::class,
    path = "direct"
)
data class DirectQuadraticSolverState(
    var count: Int = 0
) : AbstractSolverState() {
    companion object

    @FlowMethod
    fun getName(env: FlowEnvironment): String {
        return "Direct"
    }

    @FlowMethod
    fun solve(env: FlowEnvironment, equation: QuadraticEquation): QuadraticSolution {
        val det = equation.b * equation.b - 4 * equation.a * equation.c
        count++
        val result = if (det > 0) {
            val d = sqrt(det)
            QuadraticSolution(
                (-equation.b + d)/(2*equation.a),
                (-equation.b - d)/(2*equation.a)
            )
        } else if (det == 0.0) {
            QuadraticSolution(-equation.b/(2*equation.a))
        } else {
            throw NoSolutionsException("No solutions")
        }
        return result
    }

    override fun finalCount(): Int = count

    override fun toString(): String = "DirectQuadraticSolverState[$count]"
}

@CborSerializable
@FlowState(
    flowInterface = QuadraticSolverFlow::class,
    path = "mock"
)
class MockQuadraticSolverState : AbstractSolverState() {
    companion object

    @FlowMethod
    fun getName(env: FlowEnvironment): String {
        return "Mock"
    }

    @FlowMethod
    fun solve(env: FlowEnvironment, equation: QuadraticEquation): QuadraticSolution {
        if (equation.a == 1.0 && equation.b == -6.0 && equation.c == 9.0) {
            return QuadraticSolution(3.0)
        } else if (equation.a == 1.0 && equation.b == 0.0 && equation.c == 1.0) {
            throw NoSolutionsException("No solutions")
        }
        throw IllegalStateException()
    }

    override fun finalCount(): Int = 2

    override fun toString(): String = "MockQuadraticSolverState"
}

@CborSerializable
@FlowException
sealed class SolverException(message: String? = null) : Exception(message) {
    companion object
}

// No need for annotations or registration for subclasses of sealed class.
class NoSolutionsException(message: String? = null) : SolverException(message)
class UnknownSolverException(message: String? = null) : SolverException(message)

@CborSerializable
@FlowState(
    flowInterface = SolverFactoryFlow::class,
    path = "factory",
    creatable = true
)
data class SolverFactoryState(
    var totalCount: Int = 0
) {
    companion object

    // Returning a flow on client is done merely by returning the flow state on the server.
    @FlowMethod
    fun createQuadraticSolver(env: FlowEnvironment, name: String): AbstractSolverState {
        return when (name) {
            "Direct" -> DirectQuadraticSolverState()
            "Mock" -> MockQuadraticSolverState()
            else -> throw UnknownSolverException("No such solver")
        }
    }

    // When the sub-flow of a given type is completed using FlowBaseInterface::complete call,
    // a join method is called for the corresponding flow type.
    @FlowJoin
    fun completeQuadraticSolver(env: FlowEnvironment, solver: AbstractSolverState) {
        totalCount += solver.finalCount()
    }

    @FlowMethod
    fun getCount(env: FlowEnvironment): Int = totalCount

    @FlowMethod
    fun nullableIdentity(env: FlowEnvironment, value: String?): String? = value

    @FlowMethod
    fun examineSolver(env: FlowEnvironment, solver: AbstractSolverState): String {
        return solver.toString()
    }
}

// FlowHandlerLocal handles flow calls that executed locally. It is for use on the server,
// for running code in the app for development, and for testing.
fun buildLocalDispatcher(exceptionMap: FlowExceptionMap): FlowDispatcherLocal {
    val builder = FlowDispatcherLocal.Builder()
    DirectQuadraticSolverState.register(builder)
    MockQuadraticSolverState.register(builder)
    SolverFactoryState.register(builder)
    return builder.build(
        FlowEnvironment.EMPTY,
        AesGcmCipher(Random.Default.nextBytes(16)),
        exceptionMap
    )
}

fun buildExceptionMap(): FlowExceptionMap {
    val builder = FlowExceptionMap.Builder()
    SolverException.register(builder)
    return builder.build()
}

class FlowProcessorTest {
    private val exceptionMap = buildExceptionMap()
    private val localDispatcher = buildLocalDispatcher(exceptionMap)

    @Test
    fun localDirect() {
        testFlow("Direct", localFactory())
    }

    @Test
    fun localMock() {
        testFlow("Mock", localFactory())
    }

    @Test
    fun localUnexpectedNameException() {
        runBlocking {
            try {
                localFactory().createQuadraticSolver("Foo")
                fail()
            } catch (e: UnknownSolverException) {
                // expected
            }
        }
    }

    @Test
    fun remoteDirect() {
        testFlow("Direct", remoteFactory())
    }

    @Test
    fun remoteMock() {
        testFlow("Mock", remoteFactory())
    }

    @Test
    fun nullable() {
        runBlocking {
            val factory = remoteFactory()
            assertEquals("Foo", factory.nullableIdentity("Foo"))
            assertNull(factory.nullableIdentity(null))
        }
    }

    @Test
    fun flowParameter() {
        runBlocking {
            val factory = localFactory()
            assertEquals(
                "MockQuadraticSolverState",
                factory.examineSolver(factory.createQuadraticSolver("Mock"))
            )
            assertEquals(
                "DirectQuadraticSolverState[0]",
                factory.examineSolver(factory.createQuadraticSolver("Direct"))
            )
        }
    }

    @Test
    fun remoteUnexpectedNameException() {
        runBlocking {
            try {
                remoteFactory().createQuadraticSolver("Foo")
                fail()
            } catch (e: UnknownSolverException) {
                // expected
            }
        }
    }

    fun localFactory(): SolverFactoryFlow {
        return SolverFactoryFlowImpl(
            "factory",
            Bstr(byteArrayOf()),
            localDispatcher,
            FlowNotifier.SILENT
        )
    }

    fun remoteFactory(): SolverFactoryFlow {
        // Remote handler executes flow calls by marshalling them to another handler, typically
        // through HTTP. We can, however short-circuit them to just go to our local handler
        // for testing.
        val http = HttpHandler(localDispatcher, FlowPoll.SILENT)
        val remoteHandler = FlowDispatcherHttp(http, exceptionMap)
        val remoteNotifier = FlowNotifierPoll(FlowPollHttp(http))
        return SolverFactoryFlowImpl(
            "factory",
            Bstr(byteArrayOf()),
            remoteHandler,
            remoteNotifier)
    }

    fun testFlow(name: String, factory: SolverFactoryFlow) {
        runBlocking {
            val solver = factory.createQuadraticSolver(name)
            assertEquals(name, solver.getName())
            val solution1 = solver.solve(QuadraticEquation(a = 1.0, b = -6.0, c = 9.0))
            assertNotNull(solution1.x1)
            assertEquals(3.0, solution1.x1, 1e-10)
            assertNull(solution1.x2)
            try {
                solver.solve(QuadraticEquation(a = 1.0, b = 0.0, c = 1.0))
                fail()
            } catch (err: NoSolutionsException) {
                // expect to happen
            }
            assertEquals(0, factory.getCount())
            solver.complete()
            assertEquals(2, factory.getCount())
        }
    }
}