package io.github.clrossel;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import io.github.clrossel.parallelTasks.DefinitionOfDoneResult;
import io.github.clrossel.parallelTasks.DefinitionOfDoneResults;
import io.github.clrossel.parallelTasks.ParallelTasks;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Test
public class ParallelTasksTest {
    private static Logger log = LoggerFactory.getLogger(ParallelTasksTest.class);

    private ParallelTasks<String> parallelTasks;
    private final AtomicBoolean mainCallbackExecuted = new AtomicBoolean();
    private final long maxWait = TimeUnit.MINUTES.convert(3, TimeUnit.MINUTES);
    private volatile AtomicInteger taskCount = new AtomicInteger();
    private volatile AtomicInteger callbackCount = new AtomicInteger();

    private static final String google = "Google News";
    private static final String apple = "apple";
    private static final String verge = "The Verge";

    private static final String googleUrl = "https://news.google.com";
    private static final String appleUrl = "https://www.apple.com";
    private static final String vergeUrl = "https://www.theverge.com";

    private static final ExecutorService executor = Executors.newWorkStealingPool();

    @AfterClass
    private void close() {
        executor.shutdownNow();
    }

    @BeforeMethod
    private void init() {
        mainCallbackExecuted.set(false);
        taskCount.set(0);
        callbackCount.set(0);

        TestClientFactory factory = new TestClientFactory(
                new OkHttpClient.Builder()
        );

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
    }

    /**
     * This test actually checks 2 different things:
     *
     *    - Verifies that calling methods that kick off tasks only run tasks one time, that is,
     *      calling start() followed by waitForTasks() won't create duplicate tasks.
     *
     *    - Verifies that all tasks are executed since we have not defined a stopping point
     */
    public void testDefinitionOfDoneNotSet() {
        parallelTasks.start().waitForTasks();
        verifyAllTasksAndCallbacksExecuted();
    }

    public void testDefinitionSetThatDoesNotFindStoppingPoint() {
        parallelTasks.setDefinitionOfDone(ParallelTasksTest::htmlContainsMickeyMouse).waitForTasks();
        verifyAllTasksAndCallbacksExecuted();
    }

    public void testDefinitionSetThatFindsAStoppingPoint() {
        parallelTasks.setDefinitionOfDone(ParallelTasksTest::htmlContainsApple).start();
        verifyTaskAndCallbackExecutedAtLeast(1L);
    }

    public void testDefinitionSetThatFindsAStoppingPointAndWaitsForTasks() {
        DefinitionOfDoneResults<String> results =
                parallelTasks.setDefinitionOfDone(ParallelTasksTest::htmlContainsApple).waitForResults();
        verifyTaskAndCallbackExecutedAtLeast(1L);
        boolean moreThanOneResult = results.hasMoreThanOneResult();
        log.info("Has more than one result -> {}", moreThanOneResult);
        if (moreThanOneResult) {
            results.getTasks().forEach(task -> {
                log.info("[{}] : succeeded ? -> {}", task, results.getResults().get(task).isSuccessful());
            });
        }
    }

    /**
     * Verifies that if an exception is thrown when setting up the task and a handler (callback) is set
     * then the ParallelTasks framework will not log the error since it is assumed the handler will
     * take the appropriate action.
     */
    public void testTaskThatThrowsExceptionAndHandlesError() {
        parallelTasks.addTask("throw Exception", this::throwError).handle((result, exception) -> {
            Assert.assertNull(result);
            Assert.assertNotNull(exception);
            log.info("Successfully caught an exception when creating the task", exception);
        });
        parallelTasks.waitForTasks();
        verifyAllTasksAndCallbacksExecuted();
    }

    /**
     * Verifies that if an exception is thrown when setting up the task but no handler (callback) has been
     * configured on the task, the ParallelTasks framework will automatically log the error.
     */
    public void testTaskThatThrowsException() {
        parallelTasks.addTask("throw Exception", this::throwError);
        parallelTasks.waitForTasks();
        verifyAllTasksAndCallbacksExecuted();
    }

    public boolean throwError() {
        Map<String, String> stringMap = new Hashtable<>();
        stringMap.put(null, null);
        return true;
    }

    static class TestClientFactory {
        OkHttpClient.Builder builder = null;

        private TestClientFactory(OkHttpClient.Builder builder) {
            this.builder = builder;
        }

        Call newClient(String baseUrl) {
            final Request request = new Request.Builder().url(baseUrl).build();
            return builder.build().newCall(request);
        }
    }

    private static DefinitionOfDoneResult<String> htmlContainsApple(Object o, Throwable exception, String taskName) {
        return htmlContainsWord(o, apple, taskName);
    }

    private static DefinitionOfDoneResult<String> htmlContainsMickeyMouse(Object o, Throwable exception,
                                                                          String taskName) {
        return htmlContainsWord(o, "mickeymouse", taskName);
    }

    private static DefinitionOfDoneResult<String> htmlContainsWord(Object futureResponse, String keywordToSearchFor,
                                                                   String taskName) {

        DefinitionOfDoneResult<String> taskResult = new DefinitionOfDoneResult<>();
        if (assertTaskResultType(futureResponse)) {
            try {
                Response response = ((Response) futureResponse);
                String html = response.body().string();
                taskResult.setResult(html);
                if (html != null && !html.isEmpty()) {
                    if (html.toLowerCase().contains(keywordToSearchFor)) {
                        log.info("Found {} in html in task {}", keywordToSearchFor, taskName);
                        taskResult.setSucceeded(true);
                    } else {
                        log.info("Could not find {} in html", keywordToSearchFor);
                    }
                } else {
                    log.error("html is null or empty!");
                }
            } catch (Exception e) {
                log.error("Could not search html for {}", keywordToSearchFor, e);
            }
        }
        return taskResult;
    }

    private void appleCallback(Object futureResponse, Throwable e) {
        handleCallback(futureResponse, e, "Apple");
    }

    private void googleCallback(Object futureResponse, Throwable e) {
        handleCallback(futureResponse, e, "Google News");
    }

    private void vergeCallback(Object futureResponse, Throwable e) {
        handleCallback(futureResponse, e, "The Verge");
    }

    private void handleCallback(Object futureResponse, Throwable e, String website) {
        if (futureResponse instanceof Response) {
            try {
                Response response = ((Response) futureResponse);
                log.info("{} response -> {}", website, response.code() + " " + response.message());
                callbackCount.incrementAndGet();
            } catch (Exception ex) {
                log.error("Error", ex);
            }
        }
    }

    private static int getRandomIntInRange(int min, int max) {
        return new Random().nextInt((max - min) + 1) + min;
    }

    private Response clientRequest(String identifier, String url, TestClientFactory factory) {
        taskCount.incrementAndGet();

        if (!url.toLowerCase().contains("apple")) {
            int sleepFor = getRandomIntInRange(1, 10);
            log.info("{} test starting with {} second sleep", identifier, sleepFor);
            Uninterruptibles.sleepUninterruptibly(sleepFor, TimeUnit.SECONDS);
            log.info("{} sleep finished", identifier);
        } else {
            log.info("{} test starting with no sleep", identifier);
        }

        Response response = null;
        try {
            response = factory.newClient(url).execute();
        } catch (Exception e) {
            log.info("There was an error", e);
        }

        return response;
    }

    private void verifyAllTasksAndCallbacksExecuted() {
        verifyMainCallbackExecuted();
        Assert.assertEquals(getTasksExecutedCount(), 3L);
        Assert.assertEquals(getCallbacksExecutedCount(), 3L);
    }

    private void verifyTaskAndCallbackExecutedAtLeast(long count) {
        verifyMainCallbackExecuted();
        Assert.assertTrue(getTasksExecutedCount() >= count);
        Assert.assertTrue(getCallbacksExecutedCount() >= count);
    }

    private long getTasksExecutedCount() {
        int tasksExecuted = taskCount.get();
        log.info("Number of executed tasks -> {}", tasksExecuted);
        return tasksExecuted;
    }

    private long getCallbacksExecutedCount() {
        long callbacksExecuted = callbackCount.get();
        log.info("Number of executed callbacks -> {}", callbacksExecuted);
        return callbacksExecuted;
    }

    private void verifyMainCallbackExecuted() {
        Stopwatch watch = Stopwatch.createStarted();
        while (watch.elapsed(TimeUnit.MINUTES) <= maxWait && !parallelTasks.isDone()) {
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
        }
        watch.stop();

        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);

        Assert.assertTrue(mainCallbackExecuted.get());

        logTaskResults();
    }

    private void logTaskResults() {
        DefinitionOfDoneResults<String> results = parallelTasks.waitForResults();
        Assert.assertNotNull(results);
        Map<String, DefinitionOfDoneResult<String>> resultMap = results.getResults();
        List<String> tasks = parallelTasks.waitForResults().getTasks();
        if (!results.isEmpty()) {
            tasks.forEach(task -> {
                DefinitionOfDoneResult<String> result = resultMap.get(task);
                log.info("task {} : has result? -> {} : is successful -> {} : html length -> {}",
                        task,
                        result.hasResult(),
                        result.isSuccessful(),
                        result.getResult() != null ? result.getResult().length() : 0);
            });
        } else {
            log.info("Definition of done results is empty, definition of done not set");
        }

    }

    private static boolean assertTaskResultType(Object result) {
        boolean isExpectedType = result instanceof Response;
        Assert.assertTrue(isExpectedType);
        return isExpectedType;
    }
}
