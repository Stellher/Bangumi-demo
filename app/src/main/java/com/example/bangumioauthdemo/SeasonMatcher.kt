package com.example.bangumioauthdemo

import java.time.LocalDate

/**
 * Official Bangumi catalogue type: 2 = anime.
 * Anime categories: 1 = TV, 2 = OVA, 3 = movie, 5 = WEB, 0 = all.
 * This is a release-quarter catalogue, not an editorial broadcast schedule.
 */
internal object SeasonMatcher {
    fun matches(
        subjectType: Int,
        airDate: String,
        platform: String,
        year: Int,
        quarter: Int,
        category: Int
    ): Boolean {
        if (subjectType != 2) return false
        if (quarter !in 1..4) return false
        val releaseDate = try {
            LocalDate.parse(airDate.take(10))
        } catch (_: Exception) {
            return false
        }
        val first = LocalDate.of(year, (quarter - 1) * 3 + 1, 1)
        if (releaseDate.isBefore(first) || !releaseDate.isBefore(first.plusMonths(3))) {
            return false
        }
        val kind = platform.trim().lowercase()
        return when (category) {
            0 -> true
            1 -> kind == "tv"
            2 -> kind == "ova"
            3 -> kind == "movie" || kind == "剧场版"
            5 -> kind == "web" || kind == "web animation" || kind == "网络动画"
            else -> false
        }
    }
}
