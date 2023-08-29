# ThreadTape

The double sided red tape that sticks the Android Lifecycle to asynchronous tasks and events in
Java.<br>
Specifically, callbacks will wait for their corresponding ``Lifecycle`` to become active again
before being executed on the main Thread while the async work is executed separately but in certain
synchronicity to its callbacks.

## Events

``LifecycleEventManager`` manages event ``Consumer``s that are bound to a ``LifecycleOwner`` and
its ``Lifecycle``. Once an event is fired by the manager, it will be dispatched on the main Thread
to all suitable ``Consumer``s.
If its corresponding ``Lifecycle`` is not in an active state, events will be queued and dispatched
once an active state is reached again.

### Usage

First obtain a manager instance. A manager is independent of other instances. You may hold a
reference to it in whichever scope you require, allowing for large scoped application wide managers
as well as small scoped Fragment or Activity wide managers.

~~~
LifecycleEventManager manager = new LifecycleEventManager();
~~~

Next you need to register ``Consumer``s that receive fired events. You may register a Consumer
multiple times, however only once per ``LifecycleOwner`` and event type combination. Duplicate
registrations will replace the old Consumer with the new one.
The managers API also allows for method chaining.

~~~
manager.register(fragment, UserLogoutEvent.class, e ->  { // lambda
  // hanlde UserLogoutEvent
}).register(activity, UserLogoutEvent.class, this::handleLogoutEvent // method reference
).register(otherLifecycleOwner, UserLogoutEvent.class, new UserLogoutConsumer()); // separate class
~~~

By default ``Consumer``s will also receive events that are a subtype of the registered event type.
But if desired, the ``Consumer`` can be registered to the specific event type only, disabling this
behaviour.

~~~
manager..register(owner, UserLogoutEvent.class, this::handleLogoutEvent, false);
~~~

Finally fire your event. The manager will let you know if your event has any potential ``Consumer``s
by returning true or false. Suitable ``Consumer``s with an active ``Lifecycle`` state will
immediately be called on the main Thread. Others will receive the event on the main Thread,
once the Lifecycle reached an active state again.

~~~
if (!manager.fire(new UserLogoutEvent("user_id"))) {
  // no consumers available for UserLogoutEvent
}
~~~

If the Lifecycle of a ``Consumer`` reaches DESTROYED it will automatically be unregistered from the
manager. ``Consumer``s can also be removed manually.

~~~
manager.unregister(UserLogoutEvent.class, previouslyRegisteredConsumer); // removes a specific Consumer for a specific event type
manager.unregister(previouslyRegisteredConsumer); // removes a specific Consumers for all event types
manager.unregisterAll(UserLogoutEvent.class); // removes all Consumers for a specific event type
manager.unregisterAll(); // removes all Consumers
~~~

## Tasks

``LifecycleTask`` executes an asynchronous ``Task`` while providing callbacks regarding its progress
and state on the main Thread. Callbacks are bound to the ``Lifecycle`` that is used to create
the ``LifecycleTask`` and are only executed, if the ``Lifecycle``
has an active state. Otherwise the ``LifecycleTask`` will wait for the ``Lifecycle`` to reach an
active state, before continuing. If the ``Lifecycle`` should reach DESTROYED the execution will
abort after finishing ongoing steps.

### Usage

A ``LifecycleTask`` consists of the following execution steps
<ul>
  <li>Before: Callback executed first, before the main work of the execution begins</li>
  <li>Task: The Task of this execution, carrying out the main work asynchronously</li>
  <li>After: Callback executed after successful execution of the Task. Can also be used to process the Tasks result</li>
  <li>Error handling: Callbacks executed after failure of the Task. Used to process the error that occurred during execution</li>
</ul>

To create a ``LifecycleTask`` first obtain a ``Builder``. Depending on if the Task may yield a
result for the after callback the ``Builder`` can be created for a ``Task`` or a ``VoidTask``.

~~~
LifecycleTask.Builder<Void> voidBuilder = LifecycleTask.Builder.create(() -> postCall("token"));
LifecycleTask.Builder<String> taskBuilder = LifecycleTask.Builder.create(() -> getCall("token"));
~~~

Next the callbacks can be registered. Note that these are optional. The ``Builder`` also supports
method chaining.

~~~
LifecycleTask.Builder<String> taskBuilder = LifecycleTask.Builder.create(() -> getCall("token"))
  .before(() -> showLoading())
  .after(result -> {
    hideLoading();
    System.out.println(result);
  });
~~~

Register ``ErrorHandler``s to handle any errors that may occur during the Tasks execution. Similar
to a try-catch construct the ``LifecycleTask`` will pick the ``ErrorHandler`` that was registered
for the closest related error type to handle an error.

~~~
taskBuilder.onError(IOException.class, io -> showNetworkError())
  .onError(RESTException.class, ex -> inspectRestError(ex))
  .onUnspecifiedError(throwable -> explode());
~~~

After configurating, create the ``LifecycleTask`` with the build method. This is where the execution
is bound to the ``Lifecycle``

~~~
LifecycleTask<String> lifecycleTask = taskBuilder.build(lifecycle);
~~~

By calling the execute methods it can either be executed on the calling Thread or create its own.
Note that executions are serial and multiple calls to ``execute()`` will wait their turn.

~~~
lifecycleTask.execute();
Thread startedThread = lifecycleTask.executeAsync();
~~~

A complete example could look like this

~~~
LifecycleTask.Builder.create(() -> loadUserInformation("userId"))
  .before(this::showLoading)
  .after(user -> {
    hideLoading();
    updateUI(user);
  }).onError(IOException.class, this::showNetworkError)
  .onError(RestException.class, this::handleRestException)
  .onUnspecifiedError(t -> {
    createErrorLog(t);
    showUnknownError();
  }).build(getLifecycle())
  .executeAsync();
~~~
