package io.github.clrossel.parallelTasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;


public class ParallelTask<T, R> {
    private static final Logger log = LoggerFactory.getLogger(ParallelTask.class);

    private final String name;
    private final ParallelTasks<R> client;
    private final Supplier<T> taskRequest;
    private CompletableFuture<T> task;
    private CompletableFuture<?> taskCallback;
    private CompletableFuture<?> definitionOfDoneFuture;
    private BiConsumer<? super T, Throwable> callback;
    private boolean caughtException = false;
    private Exception exception = null;

    /**
     * Not meant to be instantiated directly, use {@link ParallelTasks#addTask(String, Supplier)} instead
     * @param taskName Identifier of this task
     * @param client ParallelTasks that manages running the tasks
     * @param task What do you want this task to do?
     */
    ParallelTask(final String taskName, final ParallelTasks<R> client, final Supplier<T> task) {
        this.client = client;
        this.name = taskName;
        this.taskRequest = task;
    }

    /**
     * Allows setting a callback for when this task completes execution. The object passed to the callback
     * is the same as what the task returns when execution completes.
     * @param callback What should happen when the task completes execution?
     * @return Returns ParallelTasks so you can chain {@link ParallelTasks#addTask(String, Supplier)}
     */
    public ParallelTasks handle(final BiConsumer<? super T, Throwable> callback) {
        if (client.alreadyStarted())
            throw new IllegalStateException("Cannot change callback of currently executing instance");
        this.callback = callback;
        setDefinitionOfDoneCallback();
        return client;
    }

    /**
     * Identifier for the task for tracking purposes
     * @return task identifier
     */
    public String getName() {
        return name;
    }

    /* Private methods below */

    private void createCallbackCompletableFuture() {
        definitionOfDoneFuture = task.handleAsync((r, e) -> {
            if (hasCallback() && taskCallback == null) {
                if (!caughtException) {
                    taskCallback = task.handleAsync(this::createCallback, client.getExecutor());
                } else {
                    taskCallback = CompletableFuture.supplyAsync(() -> createCallback(null, exception));
                }
            } else {
                if (caughtException) log.error("Exception running task [{}]", getName(), exception);
            }
            return true; // we're required to return something due to handleAsync method signature
        }, client.getExecutor());
    }

    private T createCallback(T taskResult, Throwable taskException) {
        try {
            createLoggingContext();
            if (hasCallback()) {
                callback.accept(taskResult, taskException);
            } else {
                if (taskException != null) {
                    log.info("Exception executing task [{}]", getName(), taskException);
                }
            }
        } catch (Exception e) {
            log.info("Exception executing callback for [{}]", getName(), e);
            throw e;
        } finally {
            clearLoggingContext();
        }
        return taskResult;
    }

    /* Package level access methods below (ParallelTasks needs to call these, but we don't want them public) */

    CompletableFuture<T> getTask() {
        return task;
    }

    CompletableFuture<?> getCallback() {
        return taskCallback;
    }

    void createTask() {
        task = CompletableFuture.supplyAsync(() -> {
            T result = null;
            try {
                createLoggingContext();
                result = taskRequest.get();
            } catch (Exception e) {
                caughtException = true;
                exception = e;
            } finally {
                clearLoggingContext();
            }
            return result;
        }, client.getExecutor());
        createCallbackCompletableFuture();
        setDefinitionOfDoneCallback();
    }

    void setDefinitionOfDoneCallback() {
        if (client.hasDefinitionOfDone()) {
            definitionOfDoneFuture.thenRunAsync(() -> taskCallback.whenComplete((taskResult, taskException) -> {
                try {
                    createLoggingContext();
                    DefinitionOfDoneResult<R> definitionOfDoneResult = client.apply(taskResult, taskException, getName());
                    client.trackResult(this, definitionOfDoneResult);
                } catch (Exception e) {
                    log.error("Exception with definition of done on task [{}]", getName(), e);
                } finally {
                    if (client.getResultFor(this).isSuccessful()) {
                        client.cancelRemainingTasks(this);
                    }
                    clearLoggingContext();
                }
            }), client.getExecutor());

        }
    }

    boolean hasCallback() {
        return callback != null;
    }

    private boolean hasLoggingContext() {
        return getLoggingContext() != null;
    }

    private LoggingContext getLoggingContext() {
        return client.getLoggingContext();
    }

    private void createLoggingContext() {
        client.createLoggingContext();
    }

    private void clearLoggingContext() {
        client.clearLoggingContext();
    }

}
