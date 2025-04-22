# Multipaz RPC design

RPC annotation processor in this folder (together with the code in the core Multipaz library)
implements "Multipaz RPC" system. This file describes how it works.

## About Multipaz RPC

Multipaz RPC was created to simplify server-client communication. It was designed to have these
properties:
 * parameters and results are Cbor-serialized objects,
 * primary programming language target is Kotlin on both server and client side,
 * all RPC marshalling can be generated automatically using Kotlin annotation processor,
 * back-end state is round-tripped to the client, no server storage is inherently required,
 * RPC can work through stateless network protocol such as HTTP and HTTPS (i.e. persistent 
   connection, such as a socket is not required)
 * back-end code can be (if appropriate) directly run in the client environment (no marshalling),
 * integrated with Kotlin coroutines, RPC calls are suspendable, rather than blocking,
 * integrated with Kotlin exceptions,
 * pluggable integrated authorization mechanism,
 * server push mechanism is integrated and exposed using APIs compatible with Kotlin `Flow`,
 * while Kotlin is the primary target, the protocol itself is language-agnostic.

The basic pattern for Multipaz RPC is similar to other RPC systems: APIs are defined using
Kotlin interfaces. Back-end provides implementation of the interface and for the client a stub
is generated that delegates implementation to the back-end by marshalling it through dispatching
mechanism that can run through network.

## Back-end objects and data

An object such as an interface, parameter, or result can either be a _back-end object_ or _data_.

Data objects are just marshalled (serialized and deserialized) between the client and the server.
They can be primitive (e.g. `String`) or of a Cbor-serializable type. Their representation is
exactly the same on the server and on the client. Exceptions and notifications are always data
objects.

Back-end objects are interfaces that the server exposes for the client to call. These interfaces
must be marked with `@RpcInterface` and methods to be exposed must be marked with `@RpcMethod`.
All methods must be asynchronous, i.e. marked with `suspend` keyword.
On the client, they are _stubs_, i.e. automatically generated implementations of RPC interfaces.
On the back-end they are actual interface implementations. They must be Cbor-serializable and
marked with `@RpcState` annotation. Back-end state is internally marshalled between
the back-end and the client on each RPC call, but it is opaque for the client as back-end encrypts
and decrypts it as it marshals/unmarshals it. Therefore the back-end and client implementations
of an RPC interface are very different. The back-end object is a Cbor-serializable Kotlin class with
actual implementation of the interface exposed through RPC. On the client, back-end objects are
represented by a stub class implementing that interface.

Multiple implementations can be exposed for a given interface, so in the protocol a back-end object
is always represented by the (opaque, serialized and encrypted) state and the _endpoint_ that
identifies a particular implementation on the server (by default the endpoint is just the name of
the state class).

Exceptions can be thrown in the back-end-side code. If they are Cbor-serializable, marked with 
`@RpcException` annotation and registered with `RpcDispatcher` on both server and the client,
they will be caught, marshalled, and rethrown from the client stub. By convention, when operating
through HTTP uncaught `UnsupportedOperationException` is mapped to HTTP status 404 and
uncaught `IllegalStateException` is mapped to HTTP status 405. All other uncaught exceptions are
mapped to HTTP status 500.

## Dispatcher

The lowest level of the Multipaz RPC stack is a dispatcher. It is represented by this interface:

```kotlin
interface RpcDispatcher {
    val exceptionMap: RpcExceptionMap
    suspend fun dispatch(target: String, method: String, args: DataItem): List<DataItem>
}
```

Here `target` is the endpoint of the object, `method` is endpoint of the method to be called
(method name by default) and `args` holds back-end state and method arguments. If
authorization is not used, `args` is simply an array that contains back-end state (encrypted and
wrapped in `Bstr`) and call arguments. For data arguments their Cbor representation is used.
Back-end object arguments are encoded as two-element Cbor array, where the first element is
the endpoint (as `Tstr`) and the second is opaque back-end state (as `Bstr`). If authorization is
used, args hold an authorization object (Cbor map) where arguments are encoded in `payload` field
as `Bstr` holding serialized argument array, and the rest of the fields depend on authorization
implementation.

Result array is encoded as 3- to 5-element array:
 * the first element is updated opaque back-end state,
 * the second element is an integer indicating result type,
 * the third element is updated authorization system parameter, such as new nonce (or `null`,
   if authorization not used or does not require parameter updates).

The possible values for the second element are defined in `RpcReturnCode` enum:
 * `RESULT` (value `0`) - additional element in the result array is `DataItem` which is a result
   returned by the method.
 * `EXCEPTION` (value `1`) - additional two elements contain exception id and serialized
   exception object as `DataItem`
 * `NONCE_RETRY` (value `2`) - indicates a problem with the authorization that could be solved
   by simply retrying request with updated authorization parameters.

Kotlin `Unit` return type is represented as an empty `Bstr`. Data return types and exceptions
are represented by their Cbor representations. Returned back-end objects are represented as
two-element arrays. The first element of the array is a `Tstr` that holds result object's endpoint, 
and the second is the opaque state of the result back-end object.

## RPC Stubs

To be able to call server-side code, one needs to obtain an appropriate `RpcDispatcher` object,
create a client-side implementation of an interface marshalled through RPC, and (possibly) make
a call in a context of an RPC session.

Let's start with stub creation. All Multipaz RPC stubs inherit from `RpcStub` class:

```kotlin
abstract class RpcStub(
    val rpcEndpoint: String,
    val rpcDispatcher: RpcDispatcher,
    val rpcNotifier: RpcNotifier,
    var rpcState: DataItem
)
```

The fields are:
- `rpcEndpoint` - RPC object endpoint name (identifying the back-end object)
- `rpcDispatcher` - object that dispatches RPC calls (from the client to the back-end)
- `rpcNofifier` - object that dispatches notifications (from the back-end to the client)
- `rpcState` - opaque state of the back-end object, potentially updated after each RPC call

By default the stub implementation class generated by the annotation processor from the interface
has the same name as the interface with `Stub` suffix added at the end. Stub implementation class
constructor is be defined like this:

```kotlin
class ExampleImpl(
    endpoint: String,
    dispatcher: RpcDispatcher,
    notifier: RpcNotifier,
    state: DataItem = Bstr(byteArrayOf())
)
```

A single RPC stub can be used from multiple threads or coroutines, but if its state changes during
the call and multiple calls are done simultaneously, it is not determined which state will "win".
It is only guaranteed that it is going to be one or the other (and not some sort of superposition).

## RPC session

Certain authorization scheme rely on constant updates to the authorization parameters to protect
against stealing over-the-wire credentials and replay attacks. In this case, something is needed to
hold onto these updates and pass them from one RPC call to the next. In Multipaz RPC mechanism
this is done by `RpcAuthClientSession` object. Create one such object for an RPC session and pass
it in coroutine context using `withContext(session) { ... }` statement. An RPC session must not have
overlapping method calls, i.e. one call must complete before the next one can be made. If overlap
is needed, multiple sessions must be created.

## Back-end object creating

A back-end object that is marked with `creatable = true` in its `@RpcState` annotation, can
be created directly by the client.

By convention, empty `Bstr` passed to `state` parameter (the default) will be translated into
creating a new state object that corresponds to the given `endpoint` with no parameters.
`RpcNotifier` will be covered in a later section.

## Example

Suppose we have interfaces defined like this:
```kotlin
@CborSerializable
data class MyData(val text: String, val num: Int)

@RpcInterface
interface Example {
    @RpcMethod
    suspend fun exampleMethod(data: MyData): ByteString
}

@RpcInterface
interface ExampleFactory {
    @RpcMethod
    suspend fun create(name: String): Example
}
```

Server-side implementation could look like this:

```kotlin
@CborSerializable
@RpcState
data class ExampleState(var someData: Int = 0): Example {
    companion object {}
    
    override suspend fun exampleMethod(data: MyData): ByteString {
        // possibly modify someData...
        return /* some value */
    }
}

@CborSerializable
@RpcState(creatable = true)
data class ExampleFactoryState(var someText: String = ""): ExampleFactory {
    companion object {}
    
    @RpcMethod
    override suspend fun create(env: RpcEnvironment, name: String): ExampleState {
        return ExampleState(/* params */)
    }
    
    @RpcMethod
    override suspend fun finish(obj: ExampleState) {
      // some code
    }
}
```

Now, suppose we have an `RpcDispatcher` implementation in a `dispatcher` variable. Running
this code

```kotlin
val factory = ExampleFactoryStub(
    "ExampleFactoryState", Bstr(), dispatcher, RpcNotifier.SILENT)
val example = factory.create("Test")
```

will result in the call to `dispatcher.dispatch` with the following parameters:

```kotlin
dispatcher.dispatch("ExampleFactoryState", "create", [Bstr(), Tstr("Test")])
```

which after dispatching it to the server-side code will return something like:

```
[Bstr(<factory-state-1>), 0, null, ["ExampleState", Bstr(<example-state-1>)]]
```

Making this call

```kotlin
example.exampleMethod(MyData("foobar", 57))
```

will produce the following dispatch

```kotlin
dispatcher.dispatch("ExampleState", "exampleMethod",
    [Bstr(<example-state-1>), {"text": Tstr("foobar"), "num": 57}])
```

which should produce the following response:

```
[Bstr(<example-state-2>), 0, null, Bstr(<returned-byte-string>)]
```

Finally, calling

```kotlin
factory.finish(example)
```

will produce this dispatch

```kotlin
rpcDispatcher.dispatch("ExampleFactoryState", "finish",
    [Bstr(<factory-state-1>), [Tstr("ExampleState"), Bstr(<example-state-2>)]])
```

and response

```kotlin
[Bstr(<factory-state-2>), 0, null, Bstr()]
```

For more fleshed out example, study unit test `RpcProcessorTest`.

## Running server code locally and transporting over HTTP

Once we routed Kotlin calls to `RpcDispatcher` interface, the next steps are
 * to (possibly) transport this call and its response across the network,
 * to dispatch `RpcDispatcher` call to actual back-end code.

Let's consider the last task first. Multipaz RPC provides a `RpcDispatcher` interface implementation
called `RpcDispatcherLocal`. Every back-end object and every exception must be registered
for dispatch to work.

Using example code above, this code is needed (register methods are generated by the
annotation processor):

```kotlin
    val builder = RpcDispatcherLocal.Builder()
    ExampleFactoryState.register(builder)
    ExampleState.register(builder)
    val rpcDispatcher = builder.build(
        backendEnvironment,
        AesGcmCipher(Random.Default.nextBytes(16)),
        RpcExceptionMap.Builder().build()
    )
```

If it is desired to run server-side code locally, `rpcDispatcher` built in this way can be directly
passed to the constructor of the client-side stub implementation.

A more interesting application is to run RPC across the network. On the client this can be achieved
by using a different implementation of the dispatcher `RpcDispatcherHttp` that translates calls
to `RpcDispatcher.dispatch` to HTTP POST requests, `target` and `method` parameters of
`RpcDispatcher.dispatch` call are appended as path items to the base url, `args` and result
are serialized as Cbor arrays. On the server side `HttpHandler` can be used to do the reverse:
route HTTP POST requests to `RpcDispatcher.dispatch` calls.

## Notifications

A unique feature of Multipaz RPC system is support for notifications. While traditional RPC is
used to route the call from the client to the server, notifications allow server to request an
action on the client.

**Note**: notifications are delivered on the "best effort" basis. There are inherent race
conditions in supporting RPC calls and issuing notifications on the same object, as the
object identity becomes difficult to define. Moreover, notification delivery channels that
are likely to be used for large-scale applications (e.g. Android notifications) are also
"best effort" mechanisms.

Notifications are issued by back-end objects. An RPC interface that supports notifications of type
`T` must  implement interface `RpcNotifiable<T>`. Type `T` must be Cbor-serializable. This interface
exposes a method `RpcNotifiable<T>.collect(collector: FlowCollector<T>)` which then can be used
to collect notifications.

For back-end code, when the implemented interface is notifiable, annotation processor will
generate `emit` function that takes the notification object as a parameter.

Low-level notification interface on the server side is

```kotlin
interface RpcNotifications {
    suspend fun emit(target: String, state: DataItem, notification: DataItem)
}
```

On the server, this interface is expected to be provided through the `RpcEnvironment` object (i.e.
by calling `env.getInterface(RpcNotifications::class)`).

And on the client notifications are supported by this interface

```kotlin
interface RpcNotifier {
    suspend fun <NotificationT : Any> register(
        target: String,
        opaqueState: DataItem,
        notifications: MutableSharedFlow<NotificationT>,
        deserializer: (DataItem) -> NotificationT
    )
    suspend fun unregister(target: String, opaqueState: DataItem)
}
```

An instance of a class implementing this must be passed to the stub implementation constructor.

The important part here is that the client registers for notifications emitted to a particular
back-end instance identified by its endpoint and opaque state (encrypted and serialized back-end state).
On the server, however when notification is sent it is identified by the endpoint and Cbor
representation of the state (i.e. not serialized to the sequence of bytes and not encrypted).

`RpcNotificationsLocal` is an implementation for both of these interfaces which is suitable for
the case when server-side code is being run locally.

When Multipaz RPC operates over HTTP, a simple implementation of `RpcNotifier` can be
created like this `RpcNotifierPoll(RpcPollHttp(httpTransport))`. It uses long poll over a
regular HTTP connection. On the server `RpcNotificationsLocalPoll(cipher)` creates an object
that implements both `RpcNotifications` interface needed by `RpcEnvironment` and `RpcPoll`
interface that needs to be passed as a parameter to `HttpHandler` constructor.

Long-polling works in the following way: client collects all the listening stubs that
could be notified (a stub "listens" from the time it is first returned to the client and
until `RpcNotifiable.dispose` is called). Then the client sends a POST request using `HttpTransport`
interface to this URL: "_/poll". The body of this request is serialized Cbor array where
the first element is "consume token" `Tstr` (initially an empty string) and then for each
listening stub a `Tstr` representing the endpoint, followed by `Bstr` that holds back-end object's
opaque state. Server then responds with the serialized Cbor array containing 3 elements: updated
"consume token" for the next long poll, zero-based index of the object to be notified, and
Cbor representation of the notification object. If there are no notifications, server
holds off replying until there is one - or times-out the request. The client detects the
time-out response and sends the poll request again using the same "consume token". Client
also can abort the request and send a new one if a stub enters or exits listening state
or its opaque state changes (as a result of an RPC call).

Note that in high-volume environments these interfaces will need to be implemented through
something like Android notifications.
