package com.example.bangumioauthdemo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonMatcherTest {
    @Test fun quarterAndTVFiltersRejectNonAnimeAndOutOfSeason() {
        assertTrue(SeasonMatcher.matches(2, "2026-10-01", "TV", 2026, 4, 1))
        assertTrue(SeasonMatcher.matches(2, "2026-12-31", "TV", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(1, "2026-10-01", "TV", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(3, "2026-10-01", "TV", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(2, "2026-09-30", "TV", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(2, "2027-01-01", "TV", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(2, "2026-10-22", "Movie", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(2, "", "TV", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(2, "2026-10-22", "", 2026, 4, 1))
    }

    @Test fun tvWebOvaMovieAndAllAreDistinct() {
        assertTrue(SeasonMatcher.matches(2, "2026-10-10", "TV", 2026, 4, 1))
        assertTrue(SeasonMatcher.matches(2, "2026-10-10", "Web", 2026, 4, 5))
        assertTrue(SeasonMatcher.matches(2, "2026-10-10", "OVA", 2026, 4, 2))
        assertTrue(SeasonMatcher.matches(2, "2026-10-10", "Movie", 2026, 4, 3))
        assertTrue(SeasonMatcher.matches(2, "2026-10-10", "Movie", 2026, 4, 0))
        assertFalse(SeasonMatcher.matches(2, "2026-10-10", "Web", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(2, "2026-10-10", "TV", 2026, 4, 5))
        assertFalse(SeasonMatcher.matches(2, "2026-10-10", "TV", 2026, 4, 8))
    }

    @Test fun handlesFourQuarterBoundaries() {
        assertTrue(SeasonMatcher.matches(2, "2026-01-01", "TV", 2026, 1, 1))
        assertTrue(SeasonMatcher.matches(2, "2026-04-01", "TV", 2026, 2, 1))
        assertTrue(SeasonMatcher.matches(2, "2026-07-01", "TV", 2026, 3, 1))
        assertTrue(SeasonMatcher.matches(2, "2026-10-01", "TV", 2026, 4, 1))
        assertFalse(SeasonMatcher.matches(2, "2026-04-01", "TV", 2026, 1, 1))
    }
}
