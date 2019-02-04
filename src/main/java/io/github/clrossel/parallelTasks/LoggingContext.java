package io.github.clrossel.parallelTasks;

interface LoggingContext extends AutoCloseable {
    void create();
}
