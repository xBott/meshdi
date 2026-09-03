package me.bottdev.meshdi.moduleit.api;

/// Represents the result of a batch operation in ModuleManager.
public sealed interface ModuleBatchResult permits 
        ModuleBatchResult.Success, 
        ModuleBatchResult.PartialSuccess, 
        ModuleBatchResult.Failed 
{
    
    /// @return true if the batch operation was completely or partially successful.
    boolean isSuccess();

    /// All modules in the batch were processed successfully.
    record Success(int processedCount) implements ModuleBatchResult {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /// Some modules in the batch failed to process, but at least one succeeded.
    record PartialSuccess(int processedCount, int failedCount) implements ModuleBatchResult {
        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    /// All modules in the batch failed to process, or the batch operation completely failed.
    record Failed(int failedCount) implements ModuleBatchResult {
        @Override
        public boolean isSuccess() {
            return false;
        }
    }

}
