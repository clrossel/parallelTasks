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

### Using ParallelTasks in Your Project

> Maven dependency

```XML
<dependency>
    <groupId>io.github.clrossel</groupId>
    <artifactId>paralleltasks</artifactId>
    <version>r03</version>
</dependency>
```

### Building ParallelTasks

Docker images are conveniently provided such
that the only required software to build are
[Docker Desktop](https://www.docker.com/products/docker-desktop) and ```make``` utility.

> **Note:** Instructions below are for MacOS, adjust
instructions as needed for other operating systems. 

   1. Download and install [Docker Desktop for MacOS](https://hub.docker.com/editions/community/docker-ce-desktop-mac)
   1. Make
      1. Open a terminal window
      1. (on recent MacOS versions, just type
      ```make``` and follow prompts to install the
      XCode Command Line tools)
   1. Clone this repo to a directory
   1. Run ```make compile```, which will do all of the following:
      1. Pull down the ```Ubuntu/16.04``` Docker image
      1. Build ```parallelTools/java``` Docker image based on ```Ubuntu/16.04```
      1. Build ```parallelTools/devenv``` dev environment based on ```parallelTools/java``` image
      1. Start an instance of the ```parallelTools/devenv``` container
      1. Run ```mvn clean package``` inside the ```parallelTools/devenv``` image
      
 