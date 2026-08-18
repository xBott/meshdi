package me.bottdev.meshdi.moduleit.core;

import me.bottdev.meshdi.moduleit.api.ModuleClassLoaderLeakDetector;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * ModuleClassLoaderLeakDetector implementation backed by PhantomReference + ReferenceQueue,
 * with a single daemon background thread doing all the polling and GC-nudging.
 *
 * Nothing in this class ever blocks the caller's thread - awaitUnloadAsync() returns
 * immediately with a CompletableFuture; the actual GC calls, queue polling and
 * timeout bookkeeping all run on the internal scheduler thread.
 */
public class AsyncModuleClassLoaderLeakDetector implements ModuleClassLoaderLeakDetector {

    private final ReferenceQueue<ClassLoader> queue = new ReferenceQueue<>();
    private final Map<Reference<?>, String> tracked = new ConcurrentHashMap<>();
    private final Map<String, List<CompletableFuture<Void>>> waiters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private volatile BiConsumer<String, Boolean> onResult = (_, _) -> {};

    public AsyncModuleClassLoaderLeakDetector() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "meshdi-classloader-leak-detector");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::drainQueue, 500, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void track(String moduleId, ClassLoader classLoader) {
        PhantomReference<ClassLoader> ref = new PhantomReference<>(classLoader, queue);
        tracked.put(ref, moduleId);
    }

    @Override
    public void onUnloaded(BiConsumer<String, Boolean> callback) {
        this.onResult = callback;
    }

    private void nudgeAndDrain(long delayMillis) {
        scheduler.schedule(() -> {
            try {
                System.gc();
            } finally {
                drainQueue();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public CompletableFuture<Void> awaitUnloadAsync(String moduleId, Duration timeout) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        waiters.computeIfAbsent(moduleId, id -> new CopyOnWriteArrayList<>()).add(future);

        // System.gc() is a real pause - it must never run on the caller's thread.
        // Runs on the detector's own background thread instead.
        //
        // IMPORTANT: System.gc() stops ALL threads, including the JVM's own
        // Reference Handler thread that moves collected PhantomReferences into
        // our ReferenceQueue. Hammering System.gc() back-to-back with no gap
        // starves that thread of a chance to actually run and enqueue anything.
        // A single explicit GC is normally enough for class unloading on HotSpot;
        // we schedule a couple of *spaced out* follow-up nudges instead of a tight loop,
        // each with its own drainQueue() so newly-enqueued references are picked up promptly.
        nudgeAndDrain(0);
        nudgeAndDrain(300);
        nudgeAndDrain(1000);

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (!future.isDone()) {
                removeWaiter(moduleId, future);
                future.completeExceptionally(new IllegalStateException(
                        "ClassLoader for module '" + moduleId + "' was not garbage collected within "
                                + timeout + ". Something still holds a strong reference to it or to one "
                                + "of its loaded classes (static fields, ThreadLocals, live threads started "
                                + "by the module, listeners registered in a longer-lived component, etc.)."
                ));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        future.whenComplete((v, ex) -> timeoutTask.cancel(false));

        return future;
    }

    private void removeWaiter(String moduleId, CompletableFuture<Void> future) {
        waiters.computeIfPresent(moduleId, (id, list) -> {
            list.remove(future);
            return list.isEmpty() ? null : list;
        });
    }

    private void drainQueue() {
        Reference<?> ref;
        while ((ref = queue.poll()) != null) {
            String moduleId = tracked.remove(ref);
            if (moduleId != null) {
                onResult.accept(moduleId, true);
                completeWaiters(moduleId);
            }
        }
    }

    private void completeWaiters(String moduleId) {
        List<CompletableFuture<Void>> list = waiters.remove(moduleId);
        if (list != null) {
            for (CompletableFuture<Void> future : list) {
                future.complete(null);
            }
        }
    }

    @Override
    public int trackedCount() {
        return tracked.size();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

}