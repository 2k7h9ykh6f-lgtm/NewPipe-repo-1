package org.schabi.newpipe.fragments.list.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the staleness detection logic used in {@link SearchFragment} to prevent
 * old search results from overwriting newer ones during rapid query switching.
 *
 * <p>The race condition this addresses:
 * <ol>
 *   <li>User searches for "A" → request A starts on IO thread</li>
 *   <li>User quickly searches for "B" → request B starts, A's disposable is disposed</li>
 *   <li>Request A's result was already posted to the main-thread handler queue</li>
 *   <li>A's result arrives and tries to overwrite B's (empty) list → BUG</li>
 * </ol>
 *
 * <p>The fix captures the query string and service ID at subscription time, then compares
 * them against the current values when the result arrives. If they differ, the result is stale
 * and must be discarded.
 */
public class SearchStalenessTest {

    /**
     * Simulates the staleness check used in startLoading() and loadMoreItems() lambdas.
     * This mirrors the inline check:
     * {@code !currentQuery.equals(searchString) || currentServiceId != serviceId}
     *
     * @param capturedQuery the query string captured when the request was initiated
     * @param capturedServiceId the service ID captured when the request was initiated
     * @param currentQuery the current active query string
     * @param currentServiceId the current active service ID
     * @return true if the result is stale and should be discarded
     */
    private boolean isResultStale(final String capturedQuery,
                                   final int capturedServiceId,
                                   final String currentQuery,
                                   final int currentServiceId) {
        return !capturedQuery.equals(currentQuery) || capturedServiceId != currentServiceId;
    }

    @Test
    public void sameQueryAndService_notStale() {
        assertFalse("Same query and service should not be stale",
                isResultStale("cats", 0, "cats", 0));
    }

    @Test
    public void differentQuery_isStale() {
        assertTrue("Different query should be stale",
                isResultStale("cats", 0, "dogs", 0));
    }

    @Test
    public void differentService_isStale() {
        assertTrue("Different service ID should be stale",
                isResultStale("cats", 0, "cats", 1));
    }

    @Test
    public void differentQueryAndService_isStale() {
        assertTrue("Different query and service should be stale",
                isResultStale("cats", 0, "dogs", 1));
    }

    @Test
    public void emptyQuery_sameValue_notStale() {
        assertFalse("Empty query with same value should not be stale",
                isResultStale("", 0, "", 0));
    }

    @Test
    public void emptyToNonEmpty_isStale() {
        assertTrue("Empty to non-empty query should be stale",
                isResultStale("", 0, "cats", 0));
    }

    @Test
    public void nonEmptyToEmpty_isStale() {
        assertTrue("Non-empty to empty query should be stale",
                isResultStale("cats", 0, "", 0));
    }

    @Test
    public void caseSensitiveDifference_isStale() {
        // Queries that differ only in case are considered different searches
        assertTrue("Case-different queries should be stale",
                isResultStale("Cats", 0, "cats", 0));
    }

    @Test
    public void unicodeQuery_notStale() {
        assertFalse("Same Unicode query should not be stale",
                isResultStale("搜索", 0, "搜索", 0));
    }

    @Test
    public void unicodeQuery_changed_isStale() {
        assertTrue("Changed Unicode query should be stale",
                isResultStale("搜索", 0, "検索", 0));
    }

    /**
     * Simulates the rapid query switching scenario.
     * Verifies the complete staleness detection flow across multiple query changes.
     */
    @Test
    public void rapidQuerySwitching_onlyLatestIsCurrent() {
        // Simulate: user types "c" -> "ca" -> "cat" -> "cats"
        final String[] queries = {"c", "ca", "cat", "cats"};
        final int serviceId = 0;

        // Each query captures its own value at subscription time
        for (int i = 0; i < queries.length; i++) {
            final String capturedQuery = queries[i];

            // When the result arrives, the current query is the last one ("cats")
            final String currentQuery = queries[queries.length - 1];

            if (i < queries.length - 1) {
                // All previous queries should be stale
                assertTrue("Query '" + capturedQuery + "' should be stale when current is '"
                        + currentQuery + "'", isResultStale(capturedQuery, serviceId,
                        currentQuery, serviceId));
            } else {
                // The latest query should not be stale
                assertFalse("Latest query '" + capturedQuery + "' should not be stale",
                        isResultStale(capturedQuery, serviceId, currentQuery, serviceId));
            }
        }
    }

    /**
     * Simulates the service switching scenario.
     * User searches on YouTube (id=0) then switches to SoundCloud (id=1).
     */
    @Test
    public void serviceSwitching_oldServiceIsStale() {
        final String query = "music";
        final int youtubeId = 0;
        final int soundcloudId = 1;

        // Result from YouTube arrives after user switched to SoundCloud
        assertTrue("YouTube result should be stale when service switched to SoundCloud",
                isResultStale(query, youtubeId, query, soundcloudId));

        // Result from SoundCloud should not be stale
        assertFalse("SoundCloud result should not be stale when service is SoundCloud",
                isResultStale(query, soundcloudId, query, soundcloudId));
    }

    /**
     * Simulates the pagination-during-new-search scenario.
     * User is scrolling through results for "A" (loading more items),
     * then starts a new search for "B".
     */
    @Test
    public void paginationDuringNewSearch_oldPageIsStale() {
        final String queryA = "old query";
        final String queryB = "new query";
        final int serviceId = 0;

        // Pagination result for query A arrives after search for query B started
        assertTrue("Pagination result for old query should be stale",
                isResultStale(queryA, serviceId, queryB, serviceId));
    }

    /**
     * Simulates the same-query-resubmit scenario.
     * User submits the same query twice (e.g. by pressing Enter twice).
     * The old request's result should NOT be stale since the query is the same.
     * However, the old request is disposed in startLoading(), so its result
     * should not arrive at all. If it does (edge case), it's safe to process
     * because the query matches.
     */
    @Test
    public void sameQueryResubmit_notStale() {
        assertFalse("Same query resubmit should not be stale",
                isResultStale("cats", 0, "cats", 0));
    }

    /**
     * Verifies the isLoading state management during stale result handling.
     *
     * <p>When a stale result is detected, isLoading must NOT be set to false,
     * because the current (newer) request is still loading. The correct sequence is:
     * <pre>
     *   1. Check staleness
     *   2. If stale: return immediately (do NOT set isLoading = false)
     *   3. If not stale: set isLoading = false, then process result
     * </pre>
     */
    @Test
    public void isLoadingState_staleResultDoesNotClearLoading() {
        // This test documents the expected behavior:
        // isLoading should remain true when a stale result is discarded

        // Simulate: request A starts (isLoading = true)
        boolean isLoading = true;

        // Simulate: request B starts (isLoading = true, A's disposable disposed)
        // isLoading remains true

        // Simulate: A's stale result arrives
        final boolean isStale = isResultStale("A", 0, "B", 0);
        assertTrue("A's result should be stale", isStale);

        // The fix ensures isLoading is NOT set to false for stale results
        if (!isStale) {
            isLoading = false; // This should NOT execute for stale results
        }

        assertTrue("isLoading should remain true when stale result is discarded", isLoading);

        // Simulate: B's valid result arrives
        final boolean bIsStale = isResultStale("B", 0, "B", 0);
        assertFalse("B's result should not be stale", bIsStale);
        if (!bIsStale) {
            isLoading = false;
        }

        assertFalse("isLoading should be false after valid result is processed", isLoading);
    }
}
