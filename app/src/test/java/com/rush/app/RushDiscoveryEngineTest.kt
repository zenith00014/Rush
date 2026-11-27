package com.rush.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RushDiscoveryEngineTest {
    @Test fun productiveSignalsWinVideoFeedRanking() {
        val profile = RushDiscoveryEngine.Profile(topics = listOf("Science", "Technology"))
        val useful = RushDiscoveryEngine.Candidate(
            "useful", RushDiscoveryEngine.Kind.VIDEO,
            "How quantum computers work — physics explained", "Science Academy",
            "A detailed documentary lesson explaining research, engineering and evidence.",
            durationSeconds = 1200, sourceFollowed = true
        )
        val low = RushDiscoveryEngine.Candidate(
            "low", RushDiscoveryEngine.Kind.VIDEO, "Shocking celebrity prank meme reaction",
            "Science Academy", "A short viral reaction prank.", durationSeconds = 45, sourceFollowed = true
        )
        assertTrue(RushDiscoveryEngine.scoreFeed(useful, profile) > RushDiscoveryEngine.scoreFeed(low, profile))
    }

    @Test fun musicIsTasteLedNotProductivityGated() {
        val profile = RushDiscoveryEngine.Profile(
            followedCreators = setOf("Aster Band"), historyTerms = setOf("ambient", "electronic", "aster")
        )
        val liked = RushDiscoveryEngine.Candidate(
            "track1", RushDiscoveryEngine.Kind.MUSIC, "Ambient electronic session", "Aster Band", sourceFollowed = true
        )
        val other = RushDiscoveryEngine.Candidate(
            "track2", RushDiscoveryEngine.Kind.MUSIC, "Unrelated pop song", "Unknown Artist"
        )
        assertTrue(RushDiscoveryEngine.scoreFeed(liked, profile) > RushDiscoveryEngine.scoreFeed(other, profile))
    }

    @Test fun broadSearchKeepsEveryCandidateIncludingMemesAndPranks() {
        val docs = listOf(
            RushDiscoveryEngine.SearchDocument("course", "Science explained for beginners", "Open Course", kind = RushDiscoveryEngine.Kind.VIDEO),
            RushDiscoveryEngine.SearchDocument("meme", "Prank meme compilation", "Comedy Hub", kind = RushDiscoveryEngine.Kind.VIDEO),
            RushDiscoveryEngine.SearchDocument("news", "Science research breakthrough", "Daily Report", "New findings", RushDiscoveryEngine.Kind.NEWS)
        )
        val hits = RushDiscoveryEngine.rankSearch("prank meme", docs)
        assertEquals(3, hits.size)
        assertEquals("meme", hits.first().value)
    }

    @Test fun fuzzySearchFindsSmallTypos() {
        val docs = listOf(
            RushDiscoveryEngine.SearchDocument("physics", "Physics and mathematics course", kind = RushDiscoveryEngine.Kind.VIDEO),
            RushDiscoveryEngine.SearchDocument("cooking", "Cooking basics and recipes", kind = RushDiscoveryEngine.Kind.VIDEO)
        )
        assertEquals("physics", RushDiscoveryEngine.rankSearch("physcis", docs).first().value)
    }

    @Test fun abbreviationExpansionFindsMachineLearning() {
        val docs = listOf(
            RushDiscoveryEngine.SearchDocument("ml", "Machine learning fundamentals", kind = RushDiscoveryEngine.Kind.VIDEO),
            RushDiscoveryEngine.SearchDocument("other", "History of ancient cities", kind = RushDiscoveryEngine.Kind.VIDEO)
        )
        assertEquals("ml", RushDiscoveryEngine.rankSearch("ml", docs).first().value)
    }

    @Test fun freshNewsRanksHigherButOldStoriesRemain() {
        val now = 2_000_000_000L
        val recent = RushDiscoveryEngine.Candidate(
            "recent", RushDiscoveryEngine.Kind.NEWS, "Climate research report and analysis",
            "Science Desk", "Detailed research and context.", publishedAtEpochSeconds = now - 3600
        )
        val old = recent.copy(id = "old", publishedAtEpochSeconds = now - 60L * 24 * 3600)
        val profile = RushDiscoveryEngine.Profile(topics = listOf("Climate", "Science"))
        assertTrue(RushDiscoveryEngine.scoreFeed(recent, profile, now) > RushDiscoveryEngine.scoreFeed(old, profile, now))
    }

    @Test fun creatorDiversityPreventsOneSourceFromFillingFeed() {
        val candidates = listOf(
            RushDiscoveryEngine.Candidate("a1", RushDiscoveryEngine.Kind.VIDEO, "First", "Creator A"),
            RushDiscoveryEngine.Candidate("a2", RushDiscoveryEngine.Kind.VIDEO, "Second", "Creator A"),
            RushDiscoveryEngine.Candidate("a3", RushDiscoveryEngine.Kind.VIDEO, "Third", "Creator A"),
            RushDiscoveryEngine.Candidate("b1", RushDiscoveryEngine.Kind.VIDEO, "Fourth", "Creator B")
        )
        val hits = candidates.mapIndexed { index, item -> RushDiscoveryEngine.FeedHit(item, 1000 - index * 10) }
        val selected = RushDiscoveryEngine.diversifyFeed(hits, 3, maxPerCreator = 1)
        assertEquals(3, selected.size)
        assertTrue(selected.any { it.candidate.creator == "Creator B" })
    }

    @Test fun positiveAndNegativeFeedbackChangePersonalizedVideoScores() {
        val candidate = RushDiscoveryEngine.Candidate(
            "feedback", RushDiscoveryEngine.Kind.VIDEO,
            "Quantum computing explained with practical examples", "Science Desk",
            "A detailed engineering and technology lesson.", durationSeconds = 900
        )
        val base = RushDiscoveryEngine.Profile(topics = listOf("Technology"))
        val liked = base.copy(likedTerms = setOf("quantum", "computing"))
        val disliked = base.copy(dislikedTerms = setOf("quantum", "computing"))
        assertTrue(RushDiscoveryEngine.scoreFeed(candidate, liked) > RushDiscoveryEngine.scoreFeed(candidate, base))
        assertTrue(RushDiscoveryEngine.scoreFeed(candidate, disliked) < RushDiscoveryEngine.scoreFeed(candidate, base))
    }

    @Test fun showMoreTopicAddsARecommendationBoost() {
        val candidate = RushDiscoveryEngine.Candidate(
            "design", RushDiscoveryEngine.Kind.VIDEO,
            "Urban sketching basics", "Drawing Studio",
            "A practical lesson in sketching and observation.", durationSeconds = 600
        )
        val base = RushDiscoveryEngine.Profile(topics = listOf("Art"))
        val more = base.copy(showMoreTopics = setOf("Urban sketching"))
        assertTrue(RushDiscoveryEngine.scoreFeed(candidate, more) > RushDiscoveryEngine.scoreFeed(candidate, base))
    }
}
