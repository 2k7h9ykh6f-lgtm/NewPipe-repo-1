package org.schabi.newpipe.fragments.list.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Unit tests for {@link SearchRequestKey}, the value used by {@code SearchFragment} to discard
 * results that arrive after the user switched query/service/filter (the stale-result race that
 * caused old responses to overwrite a newer keyword's list).
 */
public class SearchRequestKeyTest {

    private static SearchRequestKey key(final int serviceId,
                                        final String query,
                                        final String[] contentFilter,
                                        final String sortFilter) {
        return new SearchRequestKey(serviceId, query, contentFilter, sortFilter);
    }

    @Test
    public void sameParametersAreEqual() {
        assertEquals(
                key(0, "cats", new String[]{"videos"}, ""),
                key(0, "cats", new String[]{"videos"}, ""));
    }

    @Test
    public void equalKeysShareHashCode() {
        assertEquals(
                key(0, "cats", new String[]{"videos"}, "").hashCode(),
                key(0, "cats", new String[]{"videos"}, "").hashCode());
    }

    @Test
    public void differentQueryIsNotEqual() {
        // The core of the bug: a response for "cats" must not be accepted while showing "dogs".
        assertNotEquals(
                key(0, "cats", new String[0], ""),
                key(0, "dogs", new String[0], ""));
    }

    @Test
    public void differentServiceIsNotEqual() {
        assertNotEquals(
                key(0, "cats", new String[0], ""),
                key(1, "cats", new String[0], ""));
    }

    @Test
    public void differentContentFilterIsNotEqual() {
        assertNotEquals(
                key(0, "cats", new String[]{"videos"}, ""),
                key(0, "cats", new String[]{"channels"}, ""));
    }

    @Test
    public void differentSortFilterIsNotEqual() {
        assertNotEquals(
                key(0, "cats", new String[0], "relevance"),
                key(0, "cats", new String[0], "rating"));
    }

    @Test
    public void nullAndEmptyContentFilterAreEquivalent() {
        // SearchFragment starts with `new String[0]`; a null must be treated the same way so a
        // freshly constructed key never looks "stale" against itself.
        assertEquals(
                key(0, "cats", null, ""),
                key(0, "cats", new String[0], ""));
    }

    @Test
    public void nullQueryEqualsNullQuery() {
        assertEquals(
                key(0, null, new String[0], ""),
                key(0, null, new String[0], ""));
    }
}
