# JStatebox - A statebox implementation for the JVM

JStatebox is a JVM-based implementation of the Erlang statebox utility, which is likened
to a Monad. It allows asynchronous JVM applications to alter the state of objects without
blocking and by resolving conflicts at read time, rather than write time.

The original implementation of statebox resides here:

    https://github.com/mochi/statebox

### Language Support

This statebox implementation natively understands Java, Groovy, and Scala. This means you
can use any of:

1. An anonymous class derived from a special JStatebox interface.
2. A Groovy closure.
3. A Scala anonymous function.
3. A Runnable (which returns null).
4. A Callable (which returns a value).

What this means for your app is higher overall throughput because there's no synchronization
and, unless you do it on purpose in your handler, no blocking. This is probably only really
useful if you're doing an asynchronous or non-blocking app and you need to safely mutate the
state of objects without killing your performance and scalability by synchronizing access to
shared resources.

Generally this will involve mutating Lists, Sets, Maps and the like. But in this example,
we'll demonstrate how to safely mutate a string using a statebox.

To create a statebox to manage a value, use a factory method:

    def state1 = Statebox.create("Hello")

To mutate the value stored inside `state1`, you call the `modify` method and pass it an
operation. If the Groovy runtime is available, then you can just pass a closure. In pure Java,
you'd need to implement an anonymous inner class derived from the Operation<T> interface.

    def state2 = state1.modify({ s -> s + " " })

The value you return from this operation will be the new value of `state2`. If you modified
`state1` again by calling the `modify` method with a new operation, you'll get an entirely new
statebox with an entirely new value.

    def state3 = state1.modify({ s -> s + "World!" })

`state3`'s value is "HelloWorld!" (without the space added in `state2`). To merge the various
stateboxes into a single value, call the `merge` method.

    def state4 = state1.merge(state2, state3)

To get the value of `state4`, call the `value()` method.

    def greeting = state4.value()

The value of `state4` is now "Hello World!". It is the composition of all the operations performed
on all the stateboxes you merge. They are applied in order based on the system time. Operations
added to a statebox within 1ms of another operation are still performed, but the order in which
they are performed is undefined.

### License

JStatebox is Apache 2.0 licensed.

    Copyright 2011 the original author or authors.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.