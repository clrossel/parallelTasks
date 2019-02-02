package io.github.clrossel.parallelTasks;

public class DefinitionOfDoneResult<T> {

    private Boolean succeeded;  // Is the task result what you expected?
    private T result;           // The object returned by the task

    public DefinitionOfDoneResult() { }

    public DefinitionOfDoneResult(boolean succeeded, T result) {
        this.succeeded = succeeded;
        this.result = result;
    }

    public DefinitionOfDoneResult(DefinitionOfDoneResult<T> taskResult) {
        this.succeeded = taskResult.isSuccessful();
        this.result = taskResult.getResult();
    }

    public DefinitionOfDoneResult setSucceeded(boolean succeeded) {
        this.succeeded = succeeded;
        return this;
    }

    public boolean isSuccessful() {
        return succeeded != null && succeeded;
    }

    public DefinitionOfDoneResult setResult(T result) {
        this.result = result;
        return this;
    }

    public T getResult() {
        return result;
    }

    public boolean hasResult() {
        return result != null;
    }

}
