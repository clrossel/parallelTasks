package io.github.clrossel.parallelTasks;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class DefinitionOfDoneResults<R> {

    private final Map<ParallelTask<?, R>, DefinitionOfDoneResult<R>> taskResults = new ConcurrentHashMap<>();

    public Map<String, DefinitionOfDoneResult<R>> getResults() {
        Map<String, DefinitionOfDoneResult<R>> results = new ConcurrentHashMap<>();
        taskResults.forEach((task, result) -> results.put(task.getName(), result));
        return results;
    }

    public List<String> getTasks() {
        List<String> results = new LinkedList<>();
        taskResults.forEach((task, result) -> results.add(task.getName()));
        return results;
    }

    /**
     * Return task result from definition of done
     * @return {@link DefinitionOfDoneResult}
     * @throws IllegalStateException If more than one result or no results found
     */
    public DefinitionOfDoneResult<R> getSingleResult() throws IllegalStateException {
        if (hasMoreThanOneResult()) {
            throw new IllegalStateException("More than one result found, you will need to pick out what you need.");
        }

        return taskResults.entrySet().stream().findAny().orElseThrow(
                () -> new IllegalStateException("No results found!")
        ).getValue();

    }

    public boolean isEmpty() {
        return taskResults.isEmpty();
    }

    public boolean hasMoreThanOneResult() {
        return taskResults.entrySet().stream().filter(item -> item.getValue().isSuccessful()).count() > 1;
    }

    <T> DefinitionOfDoneResult<R> getResultFor(ParallelTask<T, R> task) {
        return taskResults.get(task);
    }

    <T> void trackResult(ParallelTask<T, R> task, DefinitionOfDoneResult<R> taskResult) {
        taskResults.put(task, taskResult);
    }
}
