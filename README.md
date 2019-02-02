## Parallel Task Framework

This library provides a concurrent task framework
that allows stopping all tasks when a certain
condition is met. You define the stopping point.
If no ```Definition of Done``` stopping point is
set when the tasks start execution then all tasks
run to completion.

### Example

The following example, taken from the tests,
provides a good idea of what you can do. Let's
say you want to query multiple servers for some
data. Here we are adding 3 tasks, each of which
will do an HTTP query out to different servers: 
Google, The Verge, and Apple websites. We also
define a callback when all tasks complete. Note
also that each task is able to define their own
handler as well.

```Java
parallelTasks = new ParallelTasks<>();

parallelTasks.addTask(
        google, () -> clientRequest(google, googleUrl, factory)
).handle(this::googleCallback);

parallelTasks.addTask(
        verge, () -> clientRequest(verge, vergeUrl, factory)
).handle(this::vergeCallback);

parallelTasks.addTask(
        apple, () -> clientRequest(apple, appleUrl, factory)
).handle(this::appleCallback);

parallelTasks.handle(() -> {
    mainCallbackExecuted.set(true);
    log.info("All tasks finished!");
});
```

At this point the tasks have not started. You now
have the option of either:

 1. Starting the tasks without blocking the
 current thread by calling
 ```parallelTasks.start()```
 
 1. Block the current thread execution by
 calling ```parallelTasks.waitForTasks()```
 
 1. Or, block the current thread execution and
 return the results of your ```Definition of Done```
 stopping point by calling ```waitForResults()```

Refer to
[```ParallelTasksTest```](https://github.com/clrossel/parallelTasks/blob/master/library/src/test/java/io/github/clrossel/ParallelTasksTest.java)
for examples on how to retrieve results from the
tasks and using the callbacks.

Refer to 
[Travis CI parallelTasks page](http://travis-ci.org/clrossel/parallelTasks)
to see the test code in action. 
