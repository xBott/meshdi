package me.bottdev.meshdi.moduleit.api;

import java.util.List;

/// Represents the result of a batch operation in ModuleManager.
/// Exposes exactly which elements were affected by the operation.
/// @param <T> type of the affected elements, e.g. [ModuleHandle] or [ModuleCandidate].
public sealed interface ModuleBatchResult<T> permits
        ModuleBatchResult.Unchanged,
        ModuleBatchResult.Success,
        ModuleBatchResult.PartialSuccess,
        ModuleBatchResult.Failed
{

    /// @return true if the batch operation was completely or partially successful, or had nothing to do.
    boolean isSuccess();

    /// @return elements that were successfully processed by the operation.
    List<T> processed();

    /// @return elements that the operation attempted to process, but failed.
    List<T> failed();

    /// Creates a result that matches the provided lists:
    /// [Unchanged] if both are empty, [Failed] if nothing was processed,
    /// [Success] if nothing failed, otherwise [PartialSuccess].
    static <T> ModuleBatchResult<T> of(List<T> processed, List<T> failed) {
        if (processed.isEmpty() && failed.isEmpty()) return new Unchanged<>();
        if (processed.isEmpty()) return new Failed<>(failed);
        if (failed.isEmpty()) return new Success<>(processed);
        return new PartialSuccess<>(processed, failed);
    }

    /// The batch had nothing to process.
    record Unchanged<T>() implements ModuleBatchResult<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public List<T> processed() {
            return List.of();
        }

        @Override
        public List<T> failed() {
            return List.of();
        }
    }

    /// All elements in the batch were processed successfully.
    record Success<T>(List<T> processed) implements ModuleBatchResult<T> {
        public Success {
            processed = List.copyOf(processed);
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public List<T> failed() {
            return List.of();
        }
    }

    /// Some elements in the batch failed to process, but at least one succeeded.
    record PartialSuccess<T>(List<T> processed, List<T> failed) implements ModuleBatchResult<T> {
        public PartialSuccess {
            processed = List.copyOf(processed);
            failed = List.copyOf(failed);
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /// All elements in the batch failed to process, or the batch operation completely failed.
    record Failed<T>(List<T> failed) implements ModuleBatchResult<T> {
        public Failed {
            failed = List.copyOf(failed);
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public List<T> processed() {
            return List.of();
        }
    }

}
