package me.bottdev.meshdi.moduleit.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/// Tracks whether module ClassLoaders are actually garbage collected after unload,
/// using PhantomReference + ReferenceQueue under the hood (see implementations).
/// A PhantomReference only tells you "the object is unreachable and GC-ready" -
/// it never gives the object back. That's exactly what's needed here: we don't
/// care about the ClassLoader instance itself anymore, only about the fact that
/// nothing else in the JVM still references it.
public interface ModuleClassLoaderLeakDetector extends AutoCloseable {

    /// Start tracking a module's ClassLoader. Call this ONLY after every strong
    /// reference you control (handle field, context, export registry, etc.)
    /// has already been released. Never blocks.
    void track(String moduleId, ClassLoader classLoader);

    /// Registers a callback fired (on the detector's own background thread,
    /// NOT the caller's thread) once a tracked module's ClassLoader is confirmed collected.
    void onUnloaded(BiConsumer<String, Boolean> callback);

    /// Non-blocking check/wait: completes when the module's ClassLoader is confirmed
    /// garbage collected, or completes exceptionally with IllegalStateException if it
    /// hasn't happened within `timeout`. Never blocks the calling thread -
    /// GC nudging and polling both happen on the detector's background thread.
    CompletableFuture<LeakDetectorResult> awaitUnloadAsync(String moduleId, Duration timeout);

    /// Number of ClassLoaders currently believed to still be alive.
    int trackedCount();

    @Override
    void close();

}