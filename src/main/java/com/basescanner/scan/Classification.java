package com.basescanner.scan;

/**
 * Result of classifying one block state or entity.
 *
 * @param category  what the object counts as
 * @param typeName  human-readable type used in the per-type breakdown
 * @param scoreTag  optional extra tag consumed by the scorer (see {@link ScoreTags}); may be null
 */
public record Classification(ScanCategory category, String typeName, String scoreTag) {

    /** Marker for states that belong to an object already counted elsewhere (right chest half, bed foot, upper door half). */
    public static final Classification SKIP = new Classification(null, "", null);

    public boolean isSkip() {
        return this == SKIP;
    }
}
