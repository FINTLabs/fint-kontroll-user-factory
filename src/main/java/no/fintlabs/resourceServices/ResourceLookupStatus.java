package no.fintlabs.resourceServices;

/**
 * FOUND means the linked resource was resolved. MISSING_LINK means the FINT
 * source record lacks a required relation. CACHE_MISS means the relation exists,
 * but the linked resource is not available in the local cache yet. NOT_VALID
 * means the linked resource exists, but is outside the selection validity window.
 */
public enum ResourceLookupStatus {
    FOUND,
    MISSING_LINK,
    CACHE_MISS,
    NOT_VALID
}
