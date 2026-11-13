package com.fongmi.android.tv.model;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

final class ViewModelSearchRunner {

    /** Dedicated pool: at most 8 site searches run at once (P6). The shared
     * Task.largeExecutor() is a 20-thread pool used by other subsystems, so
     * search concurrency is capped here instead of shrinking it. */
    private static final ListeningExecutorService searchExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(8));

    private final List<Future<?>> futures;
    private final AtomicInteger epoch;

    ViewModelSearchRunner() {
        futures = new CopyOnWriteArrayList<>();
        epoch = new AtomicInteger(0);
    }

    void start(List<Site> sites, Function<Site, Callable<Result>> taskFactory, Consumer<Result> onResult, boolean quick) {
        int current = nextEpoch();
        cancelFutures();
        sites.forEach(site -> execute(taskFactory.apply(site), current, onResult, quick));
    }

    void stop() {
        nextEpoch();
        cancelFutures();
    }

    private int nextEpoch() {
        return epoch.incrementAndGet();
    }

    private void cancelFutures() {
        futures.forEach(future -> future.cancel(true));
        futures.clear();
    }

    private void execute(Callable<Result> callable, int current, Consumer<Result> onResult, boolean quick) {
        long timeout = quick ? Constant.TIMEOUT_SEARCH_QUICK : Constant.TIMEOUT_SEARCH;
        FluentFuture<Result> future = FluentFuture.from(searchExecutor.submit(callable)).withTimeout(timeout, TimeUnit.MILLISECONDS, Task.scheduler());
        futures.add(future);
        future.addCallback(Task.callback(
                result -> {
                    if (epoch.get() == current) onResult.accept(result);
                }
        ), MoreExecutors.directExecutor());
    }
}
