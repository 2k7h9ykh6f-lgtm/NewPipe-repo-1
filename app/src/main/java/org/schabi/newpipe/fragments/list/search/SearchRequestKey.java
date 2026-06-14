package org.schabi.newpipe.fragments.list.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable identifier for a search (or load-more) request.
 *
 * <p>It is used to detect and discard results that arrive after the user has switched to a
 * different query, service or filter. Cancelling the previous RxJava subscription is not enough
 * on its own: a delivery runnable scheduled on the main thread by {@code observeOn} can already
 * be queued (or running) when {@code dispose()} races in from the IO pool, so a slow in-flight
 * request can still resolve <em>after</em> a newer one and overwrite the list with results for an
 * outdated query. Comparing the key captured at request time against the current one closes that
 * window.</p>
 */
final class SearchRequestKey {
    private final int serviceId;
    @Nullable
    private final String searchString;
    @NonNull
    private final List<String> contentFilter;
    @Nullable
    private final String sortFilter;

    SearchRequestKey(final int serviceId,
                     @Nullable final String searchString,
                     @Nullable final String[] contentFilter,
                     @Nullable final String sortFilter) {
        this.serviceId = serviceId;
        this.searchString = searchString;
        // Defensive copy so later reassignment/mutation of the field cannot change this key.
        this.contentFilter = contentFilter == null
                ? Collections.emptyList()
                : new ArrayList<>(Arrays.asList(contentFilter));
        this.sortFilter = sortFilter;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchRequestKey)) {
            return false;
        }
        final SearchRequestKey that = (SearchRequestKey) o;
        return serviceId == that.serviceId
                && Objects.equals(searchString, that.searchString)
                && contentFilter.equals(that.contentFilter)
                && Objects.equals(sortFilter, that.sortFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceId, searchString, contentFilter, sortFilter);
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchRequestKey{serviceId=" + serviceId
                + ", searchString='" + searchString + '\''
                + ", contentFilter=" + contentFilter
                + ", sortFilter='" + sortFilter + '\'' + '}';
    }
}
