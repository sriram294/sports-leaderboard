package com.org.playboard.ui.matches

import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.match.toDetail
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingChangeTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes missing rating changes as unavailable`() {
        assertNull(json.decodeFromString<MatchDetailDto>(detailJson()).ratingChanges)
    }

    @Test
    fun `decodes rated and guest participants`() {
        val detail = json.decodeFromString<MatchDetailDto>(
            detailJson(",\"ratingChanges\":[{\"userId\":\"u1\",\"ratingDelta\":4.2},{\"userId\":\"guest\",\"ratingDelta\":null}]")
        )
        assertEquals(4.2, detail.ratingChanges?.first()?.ratingDelta)
        assertNull(detail.ratingChanges?.last()?.ratingDelta)
        assertEquals(detail.ratingChanges?.map { it.ratingDelta }, detail.toDetail().ratingChanges?.map { it.ratingDelta })
    }

    @Test
    fun `formats signed one decimal and guest values`() {
        assertEquals("+4.2", formatRatingDelta(4.24))
        assertEquals("−2.7", formatRatingDelta(-2.66))
        assertEquals("0.0", formatRatingDelta(0.0))
        assertEquals("Not rated", formatRatingDelta(null))
    }

    private fun detailJson(extra: String = "") =
        """{"id":"m1","playedAt":"2026-08-09T06:58:00Z","teams":[],"sets":[],"recordedBy":{"userId":"u1","displayName":"Raj"},"recordedAt":"2026-08-09T06:58:00Z","events":[]$extra}"""
}
