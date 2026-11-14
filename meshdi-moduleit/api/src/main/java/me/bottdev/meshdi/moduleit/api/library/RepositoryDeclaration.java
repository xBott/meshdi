package me.bottdev.meshdi.moduleit.api.library;

import lombok.NonNull;

import java.net.URI;
import java.net.URISyntaxException;

/// A Maven repository, declared by the host or by a module.
///
/// @param id the repository id
/// @param url the repository url
public record RepositoryDeclaration(
        @NonNull String id,
        @NonNull String url
) {

    /// @return `true` if the url is a well-formed `https` url with a host.
    /// Plain `http` is not secure: artifacts and their checksums come from the same connection
    /// and can both be replaced in transit.
    public boolean isSecure() {
        return isSecureUrl(url);
    }

    /// @return `true` if the text is a well-formed `https` url with a host.
    public static boolean isSecureUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

}
