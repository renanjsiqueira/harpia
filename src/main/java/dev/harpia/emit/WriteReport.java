package dev.harpia.emit;

import java.util.List;

/**
 * What one synchronization did to the output directory.
 *
 * <p>Every list is sorted, so two identical runs report identically.
 *
 * @param created files that did not exist before
 * @param updated files whose content changed
 * @param unchanged files already byte-identical, which are not rewritten
 * @param stale files Harpia owned in a previous build and no longer generates
 * @param deleted stale files actually removed, which requires {@code --clean}
 * @param unknown files under the output directory that Harpia never generated and never deletes
 */
public record WriteReport(
        List<String> created,
        List<String> updated,
        List<String> unchanged,
        List<String> stale,
        List<String> deleted,
        List<String> unknown) {

    public WriteReport {
        created = List.copyOf(created);
        updated = List.copyOf(updated);
        unchanged = List.copyOf(unchanged);
        stale = List.copyOf(stale);
        deleted = List.copyOf(deleted);
        unknown = List.copyOf(unknown);
    }

    public int written() {
        return created.size() + updated.size() + unchanged.size();
    }

    /** True when the second of two identical builds touched nothing. */
    public boolean idempotent() {
        return created.isEmpty() && updated.isEmpty() && deleted.isEmpty();
    }
}
