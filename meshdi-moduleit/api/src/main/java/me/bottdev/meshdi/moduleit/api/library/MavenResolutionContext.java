package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;
import java.util.Map;

/**
 * Encapsulates the context required for resolving Maven dependencies.
 * This makes the resolvers (PomResolver, MavenDependencyResolver) completely stateless.
 * 
 * @param repositoryChain The repository chain to use for this specific resolution.
 *                        This can be a shared global chain or a module-specific isolated chain.
 * @param sharedPomCache  A global, thread-safe cache of parsed raw PomModels.
 *                        Because POMs are immutable, this cache should be shared across all
 *                        resolution contexts to avoid redundant downloading and parsing.
 */
public record MavenResolutionContext(
        @NonNull MavenRepositoryChain repositoryChain,
        @NonNull Map<String, PomModel> sharedPomCache
) {}
