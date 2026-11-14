package me.bottdev.meshdi.moduleit.core;

import me.bottdev.meshdi.moduleit.api.LeakDetectorResult;
import me.bottdev.meshdi.moduleit.api.ModuleClassLoaderLeakDetector;

import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Map<String, List<CompletableFuture<LeakDetectorResult>>> waiters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private volatile BiConsumer<String, Boolean> onResult = (_, _) -> {};

    /// Delays of the GC nudges after the first unload of a batch.
    private static final List<Duration> DEFAULT_GC_NUDGES =
            List.of(Duration.ZERO, Duration.ofMillis(300), Duration.ofMillis(1000));

    private final List<Duration> gcNudges;
    /// True while a round of nudges is scheduled. Unloads arriving in that window share the round,
    /// so unloading N modules at once costs one round of collections instead of N.
    private final AtomicBoolean nudgeRoundActive = new AtomicBoolean(false);

    public AsyncModuleClassLoaderLeakDetector() {
        this(DEFAULT_GC_NUDGES);
    }

    /// @param gcNudges delays at which `System.gc()` is requested after an unload. Pass an empty list to never
    /// request a collection (for JVMs that run with `-XX:+DisableExplicitGC` the nudges are no-ops anyway);
    /// class loaders are then reported as freed whenever the JVM collects them on its own.
    public AsyncModuleClassLoaderLeakDetector(List<Duration> gcNudges) {
        this.gcNudges = List.copyOf(gcNudges);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "meshdi-classloader-leak-detector");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::drainQueue, 500, 500, TimeUnit.MILLISECONDS);
    }

    /// Schedules one round of GC nudges unless a round is already pending.
    private void scheduleNudgeRound() {
        if (gcNudges.isEmpty()) return;
        if (!nudgeRoundActive.compareAndSet(false, true)) return;

        long last = 0;
        for (Duration delay : gcNudges) {
            nudgeAndDrain(delay.toMillis());
            last = Math.max(last, delay.toMillis());
        }
        scheduler.schedule(() -> nudgeRoundActive.set(false), last, TimeUnit.MILLISECONDS);
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
    public CompletableFuture<LeakDetectorResult> awaitUnloadAsync(String moduleId, Duration timeout) {
        CompletableFuture<LeakDetectorResult> future = new CompletableFuture<>();
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
        // The round is shared by all unloads that arrive while it is pending.
        scheduleNudgeRound();

        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (!future.isDone()) {
                removeWaiter(moduleId, future);
                // The verdict is advisory. Stop tracking, so a leaked loader does not stay in the map forever.
                tracked.values().removeIf(moduleId::equals);
                future.complete(new LeakDetectorResult.Leaked(new IllegalStateException(
                        "ClassLoader for module '" + moduleId + "' was not garbage collected within "
                                + timeout + ". Something still holds a strong reference to it or to one "
                                + "of its loaded classes (static fields, ThreadLocals, live threads started "
                                + "by the module, listeners registered in a longer-lived component, etc.)."
                )));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        future.whenComplete((v, ex) -> timeoutTask.cancel(false));

        return future;
    }

    private void removeWaiter(String moduleId, CompletableFuture<LeakDetectorResult> future) {
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
        List<CompletableFuture<LeakDetectorResult>> list = waiters.remove(moduleId);
        if (list != null) {
            for (CompletableFuture<LeakDetectorResult> future : list) {
                future.complete(new LeakDetectorResult.Freed());
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