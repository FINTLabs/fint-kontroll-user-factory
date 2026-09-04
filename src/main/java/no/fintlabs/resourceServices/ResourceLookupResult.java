package no.fintlabs.resourceServices;

import java.util.Optional;

/**
 * Represents lookup outcomes where Optional would hide an important business
 * distinction: missing links in the source data invalidate an existing user,
 * while cache misses and inactive linked resources should skip publishing.
 */
public record ResourceLookupResult<T>(ResourceLookupStatus status, T resource) {

    public static <T> ResourceLookupResult<T> found(T resource) {
        return new ResourceLookupResult<>(ResourceLookupStatus.FOUND, resource);
    }

    public static <T> ResourceLookupResult<T> missingLink() {
        return new ResourceLookupResult<>(ResourceLookupStatus.MISSING_LINK, null);
    }

    public static <T> ResourceLookupResult<T> cacheMiss() {
        return new ResourceLookupResult<>(ResourceLookupStatus.CACHE_MISS, null);
    }

    public static <T> ResourceLookupResult<T> notValid() {
        return new ResourceLookupResult<>(ResourceLookupStatus.NOT_VALID, null);
    }

    public boolean isFound() {
        return ResourceLookupStatus.FOUND.equals(status);
    }

    public boolean isMissingLink() {
        return ResourceLookupStatus.MISSING_LINK.equals(status);
    }

    public boolean isCacheMiss() {
        return ResourceLookupStatus.CACHE_MISS.equals(status);
    }

    public boolean isNotValid() {
        return ResourceLookupStatus.NOT_VALID.equals(status);
    }

    public Optional<T> asOptional() {
        return Optional.ofNullable(resource);
    }
}
