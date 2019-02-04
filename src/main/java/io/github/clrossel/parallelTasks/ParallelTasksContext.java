package io.github.clrossel.parallelTasks;

import java.util.Objects;


public class ParallelTasksContext <T> {

    private final String taskName;
    private DefinitionOfDoneResult<T> taskResult;

    public ParallelTasksContext(String taskName) {
        this.taskName = Objects.requireNonNull(taskName);
    }

    public ParallelTasksContext setResult(DefinitionOfDoneResult<T> taskResult) {
        this.taskResult = taskResult;
        return this;
    }

    public boolean isSuccessful() {
        return taskResult.isSuccessful();
    }

    public T getResult() {
        return taskResult.getResult();
    }

    public String getName() {
        return taskName;
    }

    public boolean hasResult() {
        return taskResult.hasResult();
    }
}
