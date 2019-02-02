package io.github.clrossel.parallelTasks;

class DefinitionOfDone<T> {

    private final TriFunction<Object, Throwable, String, DefinitionOfDoneResult<T>> definitionOfDone;

    DefinitionOfDone(TriFunction<Object, Throwable, String, DefinitionOfDoneResult<T>> callback) {
        definitionOfDone = callback;
    }

    DefinitionOfDoneResult<T> apply(Object taskResult, Throwable taskException, String taskName) {
        return definitionOfDone.apply(taskResult, taskException, taskName);
    }
}
