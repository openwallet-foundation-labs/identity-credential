# Flow RPC design

This module (together with the Flow RPC annotation processor) implements "Flow RPC" system.
This file describes how Flow RPC works.

## About Flow RPC

Flow RPC was created to simplify server-client communication. It was designed to have these
properties:
 * parameters and results are Cbor-serialized objects,
 * primary programming language target is Kotlin on both server and client side,
 * all RPC marshalling can be generated automatically using Kotlin annotation processor 
 * server-side state is round-tripped to the client, no server storage is inherently required,
 * server-side code can also be easily run in the client environment if desired.

The name "flow" was used because many of the interfaces exposed by this mechanism represent some
kind of a workflow, e.g. a number of methods that the client must call in certain sequence. This
pattern is captured by the Flow RPC design, as client-side RPC stubs store the server-side state
as opaque data, and that state is automatically updated on every method call, thus every call
always has the state from the previous call.

The word "flow" in "Flow RPC" is not related to Kotlin flows.

While Kotlin is the primary target, the protocol itself is language-agnostic.

## Flows and data

An object such as an interface, parameter, or result can either be a _flow_ or _data_.

Data objects are just marshalled (serialized and deserialized) between the client and the server.
They can be primitive (e.g. `String`) or of a Cbor-serializable type. Their representation is
exactly the same on the server and on the client. Exceptions and notifications are always data
objects.

Flows are interfaces that the server exposes for the client to call. On the client, they
are represented by interfaces derived from `FlowBase` interface and marked with `FlowInterface`
annotation. The client stubs implementing these interfaces are automatically generated by the
Flow RPC annotation processor. On the server flows are represented by a Cbor-serializable state
objects marked with `FlowState` annotation. Server state is internally marshalled between
the server and the client on each RPC call, but it is opaque for the client as server encrypts
and decrypts it as it marshals/unmarshals it. Therefore the server and client representation of
a flow is very different. On the server, flow is a Cbor-serializable Kotlin class with some
methods marked with `FlowMethod`. On the client, flow is an interface and an automatically
generated stub class implementing that interface.

On the server the signature of methods that are marked with `FlowMethod` annotation
must match corresponding methods in the flow interface, except that there is always an additional
(first) parameter of type `FlowEnvironment`. That parameter helps server-side code to access
server-side APIs, such as `Configuration` or `Storage`.

Multiple implementations can be exposed for a given flow interface, so in the protocol a flow
is always represented by the (opaque, serialized and encrypted) state and the "path" that
identifies a particular implementation on the server (by default the path is just the name of the
state class).

Exceptions can be thrown in server-side code. if they are Cbor-serializable, marked with 
`FlowException` annotation and registered with `FlowDispatcher` on both server and the client,
they will be caught, marshalled, and rethrown from the client stub. By convention, when operating
through HTTP uncaught `UnsupportedOperationException` is mapped to HTTP status 404 and
uncaught `IllegalStateException` is mapped to HTTP status 405. All other uncaught exceptions are
mapped to HTTP status 500.

## Dispatcher

The lowest level of the Flow RPC stack is a dispatcher. It is represented by this interface:

```kotlin
interface FlowDispatcher {
    val exceptionMap: FlowExceptionMap
    suspend fun dispatch(flow: String, method: String, args: List<DataItem>): List<DataItem>
}
```

Here `flow` is the path of the flow, `method` is method name to be called and `args`
represents flow state and method arguments. The first element is always opaque server-side
state: Cbor-serialized state object encrypted with the secret server key and wrapped into
Cbor `Bstr`. Other elements of the array encode method arguments one by one. For data arguments
their Cbor representation is used. Flow arguments are encoded as two-element Cbor array, where
the first element is flow path (as `Tstr`) and the second is opaque flow state (as `Bstr`).

Result array is encoded as three- or four- element array: the first element is updated opaque
flow state, the second element is an integer indicating result type: `0` for normal result
and `1` for an exception. For the normal result the third element represents the returned value.
For an exception, the third element is an exception id, and the fourth is Cbor representation
of the exception object.

Kotlin `Unit` return type is represented as an empty `Bstr`. Data return types and exceptions
are represented by their Cbor representations. Returned flows are represented as three-element
arrays. The first element of the array is a `Tstr` that holds result flow's path, the second is
the name of the flow _join_ method as `Tstr` (see the next section) and the third is the opaque
state of the result flow.

## Flow creating and joining

A from which is marked with `creatable = true` in its `FlowState` annotation, can be created
directly by the client. By default the stub implementation class generated by the annotation
processor from the interface has the same name as the interface with `Impl` suffix added at the
end. Stub implementation class constructor will be defined like this:

```kotlin
class ExampleImpl(flowPath: String, flowState: DataItem,
    flowDispatcher: FlowDispatcher, flowNotifier: FlowNotifier,
    onComplete: suspend (DataItem) -> Unit = {})
```

By convention, empty `Bstr` passed to `flowState` parameter will be translated into creating
a new state object that corresponds to the given `flowPath` with no parameters. `FlowNotifier`
will be covered in a later section.

A common pattern is for an API on a flow interface `A` to create another flow interface `B`, with
the expectation that the client will call some additional APIs on the flow `B`, and then switch
back interacting with the original flow `A`. Flow creation is simple: a method of the flow `A`
just needs to return an object corresponding to the flow `B`. To represent the hand-over back to the
original flow, `FlowBase` exposes `complete` method. This method should be called when the
interaction with the flow (`B` in our example) is done. The server-side implementation for flow `A`
can expose a method marked with `FlowJoin` annotation that takes the state for the flow `B` as
a parameter. This method will be called when `complete` method is called for `B` on the client
side. If a `FlowJoin` method is not defined on `A`, calling `complete` on `B` is a no-op.

## Example

Suppose we have interfaces defined like this:
```kotlin
@CborSerializable
data class MyData(val text: String, val num: Int)

@FlowInterface
interface ExampleFlow: FlowBase {
    @FlowMethod
    suspend fun exampleMethod(data: MyData): ByteString
}

@FlowInterface
interface ExampleFactory: FlowBase {
    @FlowMethod
    suspend fun create(name: String): ExampleFlow
}
```

Server-side implementation could look like this:

```kotlin
@CborSerializable
@FlowState(flowInterface = ExampleFlow::class)
data class ExampleState(var someData: Int = 0) {
    companion object
    
    @FlowMethod
    fun exampleMethod(env: FlowEnvironment, data: MyData): ByteString {
        // possibly modify someData...
        return /* some value */
    }
}

@CborSerializable
@FlowState(flowInterface = ExampleFactory::class, creatable = true)
data class ExampleFactoryState(var someText: String = "") {
    companion object
    
    @FlowMethod
    fun create(env: FlowEnvironment, name: String): ExampleState {
        return ExampleState(/* params */)
    }
    
    @FlowJoin
    fun join(env: FlowEnvironment, obj: ExampleState): Unit {
      // some code
    }
}
```

Now, suppose we have a `FlowDispatcher` implementation in a `flowDispatcher` variable. Running
this code

```kotlin
val factory = ExampleFactoryImpl(
    "ExampleFactoryState", Bstr(), flowDispatcher, FlowNotifier.SILENT)
val example = factory.create("Test")
```

will result in the call to `flowDispatcher.dispatch` with the following parameters:

```kotlin
flowDispatcher.dispatch("ExampleFactoryState", "create", [Bstr(), Tstr("Test")])
```

which after dispatching it to the server-side code will return something like:

```kotlin
[Bstr(<factory-state-1>), 0, ["ExampleState", "join", Bstr(<example-state-1>)]]
```

Making this call

```kotlin
example.exampleMethod(MyData("foobar", 57))
```

will produce the following dispatch

```kotlin
flowDispatcher.dispatch("ExampleState", "exampleMethod",
    [Bstr(<example-state-1>), {"text": Tstr("foobar"), "num": 57}])
```

which should produce the following response:

```kotlin
[Bstr(<example-state-2>), 0, Bstr(<returned-byte-string>)]
```

Finally, calling

```kotlin
example.complete()
```

will produce this dispatch

```kotlin
flowDispatcher.dispatch("ExampleFactoryState", "join",
    [Bstr(<factory-state-1>), [Tstr("ExampleState"), Bstr(<example-state-2>)]])
```

and response

```kotlin
[Bstr(<factory-state-2>), 0, Bstr()]
```

For more fleshed out example, study unit test `FlowProcessorTest`.

## Running server code locally and transporting over HTTP

Once we routed Kotlin calls to `FlowDispatch` interface, the next steps are
 * to (possibly) transport this call and its response across the network,
 * to dispatch `FlowDispatch` call to actual server-side code.

Let's consider the last task first. Flow RPC provides a `FlowDispatch` interface implementation
called `FlowDispatcherLocal`. Every server-side flow class and every exception must be registered
for dispatch to work.

Using example code above, this code is needed (register methods are generated by the
annotation processor):

```kotlin
    val builder = FlowDispatcherLocal.Builder()
    ExampleFactoryState.register(builder)
    ExampleState.register(builder)
    val flowDispatch = builder.build(
        flowEnvironment,
        AesGcmCipher(Random.Default.nextBytes(16)),
        FlowExceptionMap.Builder().build()
    )
```

If it is desired to run server-side code locally, `flowDispatch` built in this way can be directly
passed to the constructor of the client-side stub implementation.

A more interesting application is to run RPC across the network. On the client this can be achieved
by using a different implementation of the dispatcher `FlowDispatcherHttp` that translates calls
to `FlowDispatcher.dispatch` to HTTP POST requests, `flow` and `method` parameters of
`FlowDispatcher.dispatch` call are appended as path items to the base url, `args` and result
are serialized as Cbor arrays. On the server side `HttpHandler` can be used to do the reverse:
route HTTP POST requests to `FlowDispatcher.dispatch` calls.

## Notifications

A unique feature of Flow RPC system is support for notifications. While traditional RPC is
used to route the call from the client to the server, notifications allow server to request an
action on the client.

**Note**: notifications are delivered on the "best effort" basis. There are inherent race
conditions in supporting RPC calls and issuing notifications on the same object, as the
object identity becomes difficult to define. Moreover, notification delivery channels that
are likely to be used for large-scale applications (e.g. Android notifications) are also
"best effort" mechanisms.

Notifications are issued by flows. A flow interface that supports notifications of type `T` must
implement interface `FlowNotifiable<T>` (which extends `FlowBase`). Type `T` must be
Cbor-serializable. This interface exposes a field `FlowNotifiable<T>.notifications: SharedFlow<T>`
which then can be collected to receive notifications (in this case `SharedFlow` is a Kotlin
flow, unrelated to "flow" in Flow RPC name).

For server-side code, when the implemented interface is notifiable, annotation processor will
generate `emit` function that takes `FlowEnvironment` and the notification object.

Low-level notification interface on the server side is

```kotlin
interface FlowNotifications {
    suspend fun emit(flowName: String, state: DataItem, notification: DataItem)
}
```

On the server, this interface is expected to be provided through the `FlowEnvironment` object (i.e.
by calling `env.getInterface(FlowNotifications::class)`).

And on the client notifications are supported by this interface

```kotlin
interface FlowNotifier {
    suspend fun <NotificationT : Any> register(
        flowName: String,
        opaqueState: DataItem,
        notifications: MutableSharedFlow<NotificationT>,
        deserializer: (DataItem) -> NotificationT
    )
    suspend fun unregister(flowName: String, opaqueState: DataItem)
}
```

An instance of a class implementing this must be passed to the stub implementation constructor.

The important part here is that the client registers for notifications emitted to a particular
flow instance identified by its flow name and opaque state (encrypted and serialized flow state).
On the server, however when notification is sent it is identified by the flow name and Cbor
representation of the state (i.e. not serialized to the sequence of bytes and not encrypted).

`FlowNotificationsLocal` is an implementation for both of these interfaces which is suitable for
the case when server-side code is being run locally.

When Flow RPC operates over HTTP, a simple implementation of `FlowNotifier` can be
created like this `FlowNotifierPoll(FlowPollHttp(httpTransport))`. It uses long poll over a
regular HTTP connection. On the server `FlowNotificationsLocalPoll(cipher)` creates an object
that implements both `FlowNotifications` interface needed by `FlowEnvironment` and `FlowPoll`
interface that needs to be passed as a parameter to `HttpHandler` constructor.

Long-polling works in the following way: client collects all the listening flows that
could be notified (flow "listens" from the time it is first returned to the client and
until `FlowBase.complete` is called). Then the client sends a POST request using `HttpTransport`
interface to this URL: "_/poll". The body of this request is serialized Cbor array where
the first element is "consume token" `Tstr` (initially an empty string) and then for each
listening flow a `Tstr` representing flow path, followed by `Bstr` that holds flow's opaque
state. Server then responds with the serialized Cbor array containing 3 elements: updated
"consume token" for the next long poll, zero-based index of the flow to be notified, and
Cbor representation of the notification object. If there are no notifications, server
holds off replying until there is one - or times-out the request. The client detects the
time-out response and sends the poll request again using the same "consume token". Client
also can abort the request and send a new one if a flow enters or exits listening state
or its opaque state changes (as a result of an RPC call).

Note that in high-volume environments these interfaces will need to be implemented through
something like Android notifications.

## Authentication and security issues

Flow RPC does not by itself have any user authentication mechanism. However it is fairly easy
to integrate it with an existing authentication, by adding authenticated user id as part of the
flow state. Since flow state is encrypted and opaque to the client, it automatically serves
as an authentication token (this, of course, depends on the type of encryption used, authenticated
encryption is strongly recommended and used by default). Similarly, Flow RPC does not include
built-in replay protection or state expiration: flow state can be cloned and reused. If these
features are desired, similarly, appropriate fields can be included in the flow state.
