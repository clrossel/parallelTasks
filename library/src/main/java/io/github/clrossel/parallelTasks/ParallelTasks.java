package io.github.clrossel.parallelTasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * This class provides a framework for easily running multiple operations (tasks) concurrently and then either:
 *
 * - Allowing all tasks to run to completion
 * - Set a callback for each task
 * - Set a callback for when all tasks finish execution
 * - Define a function, referred to as "definition of done" for when all tasks should stop
 *
 * The goal is to use this for concurrent independent REST API calls to speed up processing, though this class
 * was purposefully left generic to allow for any concurrent operation.
 *
 * Refer to ParallelTasksTest for example usage, but the general flow is this:
 *
 * {@code
 *
 * ParallelTasks tasks = ParallelTasks.build(someExecutorService);
 * tasks
 *    .addTask("query CI", commonIdentityClient.queryUser).handle(callbackOnUserQuery)
 *    .addTask("get feature toggle for user", featureClient.getToggle).handle(featureClientCallback)
 *    .addTask("geohint lookup", geoHintClient.DetermineIPLocation).handle(geoHintLookupCallback)
 *    .handle(mainCallbackWhenAllTasksFinished);
 *
 * // After this point you can can call any of following, depending on what you need to happen
 *
 * tasks.start();                       // Run tasks and move on (does not block)
 * tasks.waitForTasks();                // Wait for tasks to finish (blocks current thread)
 * }
 *
 * Refer to ParallelTasksTest for usage examples.
 */
public class ParallelTasks<R> {
    private static final Logger log = LoggerFactory.getLogger(ParallelTasks.class);

    private Runnable mainCallback;
    private final ExecutorService executorService;
    private volatile CompletableFuture<Void> allFutures;
    private final List<ParallelTask<?, R>> tasks = new LinkedList<>();
    private DefinitionOfDone<R> definitionOfDone;
    private DefinitionOfDoneResults<R> definitionOfDoneResults = new DefinitionOfDoneResults<>();
    private volatile boolean started = false;
    private final LoggingContext loggingContext;

    /**
     * Uses a work stealing Executor service pool for processing of tasks with no inherited logging context.
     */
    public ParallelTasks() {
        this(null, null);
    }

    /**
     * Tasks are processed on the given executor service, usually this is the constructor used as it allows
     * customizing executor service to your needs. Note that behavior of how tasks are scheduled to run is
     * entirely dependent on the ExecutorService passed in (i.e. how it's configured).
     * @param executorService The ExecutorService used to run the tasks
     * @param inheritedLoggingContext Used to pull in any required logging context, e.g. MDC values
     */
    public ParallelTasks(ExecutorService executorService, LoggingContext inheritedLoggingContext) {
        this.executorService = executorService != null ? executorService : Executors.newWorkStealingPool();
        loggingContext = inheritedLoggingContext;
    }

    /**
     * Schedules a task to run in the provided ExecutorService. Tasks do not actually start execution until one of
     * the following methods is called:
     *
     * {@link #start()}
     * {@link #waitForTasks()}
     *
     * @param taskName Identifier for your task
     * @param task Callable function containing what task will actually execute when task runs
     * @param <T> Result of task execution, this is eventually passed to definition of done check (if set)
     * @return {@link ParallelTask}
     */
    public <T> ParallelTask<T, R> addTask(String taskName, Supplier<T> task) {
        if (alreadyStarted()) throw new IllegalStateException("Cannot add new tasks to currently executing instance");
        ParallelTask<T, R> parallelTask = new ParallelTask<>(
                taskName, this, Objects.requireNonNull(task, "task cannot be null!")
        );
        tasks.add(parallelTask);
        parallelTask.createTask();
        return parallelTask;
    }

    /**
     * Configures a callback for when the task finishes execution.
     * @param callback Runnable containing callback logic
     * @return {@link ParallelTasks}
     */
    public ParallelTasks<R> handle(Runnable callback) {
        if (alreadyStarted()) throw new IllegalStateException("Cannot change callback of currently executing instance");
        this.mainCallback = Objects.requireNonNull(callback, "callback cannot be null!");
        return this;
    }

    private boolean canStart() {
        return !started;
    }

    /**
     * Kicks off task execution, does not block calling thread.
     * @return {@link ParallelTasks}
     */
    public ParallelTasks<R> start() {
        if (canStart()) {
            if (tasks.isEmpty()) {
                throw new IllegalStateException("No tasks defined!");
            }

            started = true;
            allFutures = CompletableFuture.allOf(getAllTasks(), getAllTaskCallbacks());

            allFutures.whenCompleteAsync((result, exception) -> {
                if (mainCallback != null) {
                    try {
                        createLoggingContext();
                        mainCallback.run();
                    } catch (Exception e) {
                        log.info("Exception executing main callback", e);
                    } finally {
                        clearLoggingContext();
                    }
                }
            }, executorService);
        }

        return this;
    }

    /**
     * Blocks calling thread and waits for all tasks to finish. By default, all tasks run to completion.
     * However, if "definition of done" is configured (i.e. a function that determines when tasks should
     * stop) then this will block on when that condition is met instead.
     */
    public void waitForTasks() {
        start();
        allFutures.join();

    }

    public DefinitionOfDoneResults<R> waitForResults() {
        waitForTasks();
        return definitionOfDoneResults;
    }

    public R waitForSingleResult() throws IllegalStateException {
        waitForTasks();
        DefinitionOfDoneResult<R> result = definitionOfDoneResults.getSingleResult();
        return result.getResult();
    }

    /**
     * Sets logic for when completion of all requested tasks is "considered done". This accepts a function
     * that takes an Object (effectively the task completion result) and returns a boolean.
     *
     * For example, let's assume each task queries a different server for the same data. You don't care which server
     * responds first, only that the data is valid. This method allows defining that logic.
     *
     * Note that the function passed in is evaluated on every task execution for as long as the function returns false.
     * Once true is returned, all remaining task executions are cancelled (along with their callbacks).
     *
     * @param callback Function that will evaluate each task execution result
     * @return {@link ParallelTasks}
     */
    public ParallelTasks<R> setDefinitionOfDone(TriFunction<Object, Throwable, String, DefinitionOfDoneResult<R>> callback) {
        definitionOfDone = new DefinitionOfDone<>(callback);
        tasks.forEach(ParallelTask::setDefinitionOfDoneCallback);
        return this;
    }

    /**
     * Determines whether all tasks have finished
     * @return true if tasks done, false otherwise
     */
    public boolean isDone() {
        return allFutures != null && allFutures.isDone();
    }

    /* Private methods below */

    private CompletableFuture<Void> getAllTaskCallbacks() {
        return CompletableFuture.allOf(
                tasks.stream().map(ParallelTask::getCallback)
                        .filter(Objects::nonNull).toArray(CompletableFuture[]::new)
        );
    }

    private CompletableFuture<Void> getAllTasks() {
        return CompletableFuture.allOf(
                tasks.stream().map(ParallelTask::getTask)
                        .filter(Objects::nonNull).toArray(CompletableFuture[]::new)
        );
    }

    private long countRemainingTasks() {
        return tasks.stream().map(ParallelTask::getTask).filter(f -> !f.isDone()).count();
    }

    /* Package level access methods below (ParallelTask needs to call these, but we don't want them public) */

    ExecutorService getExecutor() {
        return executorService;
    }

    <T> DefinitionOfDoneResult<R> apply(T taskResult, Throwable taskException, String taskName) {
        return definitionOfDone.apply(taskResult, taskException, taskName);
    }

    boolean hasDefinitionOfDone() {
        return definitionOfDone != null;
    }

    <T> void cancelRemainingTasks(ParallelTask<T, R> currentTask) {
        if (countRemainingTasks() > 0) {
            tasks.forEach(task -> {
                if (task != currentTask) {
                    if (task.hasCallback()) task.getCallback().cancel(true);
                    task.getTask().cancel(true);
                }
            });
        }
    }

    <T> void trackResult(ParallelTask<T, R> task, DefinitionOfDoneResult<R> taskResult) {
        definitionOfDoneResults.trackResult(task, taskResult);
    }

    <T> DefinitionOfDoneResult<R> getResultFor(ParallelTask<T, R> task) {
        return definitionOfDoneResults.getResultFor(task);
    }

    /**
     * Determines if tasks have already started running.
     * @return true if running, false otherwise
     */
    boolean alreadyStarted() {
        return allFutures != null;
    }

    LoggingContext getLoggingContext() {
        return loggingContext;
    }

    boolean hasLoggingContext() {
        return getLoggingContext() != null;
    }

    void createLoggingContext() {
        if (hasLoggingContext()) {
            getLoggingContext().create();
        }
    }

    void clearLoggingContext() {
        if (hasLoggingContext()) {
            try {
                getLoggingContext().close();
            } catch (Exception e) {
                log.error("Exception closing logging context", e);
            }
        }
    }
}
