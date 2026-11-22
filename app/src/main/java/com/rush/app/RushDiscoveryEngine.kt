package com.rush.app

import java.text.Normalizer
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

/**
 * Shared local intelligence for Rush. Recommendations combine common user signals with a
 * section specialist. Search uses a separate broad relevance model and has no productivity gate.
 */
object RushDiscoveryEngine {
    enum class Kind { VIDEO, MUSIC, NEWS, CHANNEL, PLAYLIST }

    data class Profile(
        val topics: List<String> = emptyList(),
        val followedCreators: Set<String> = emptySet(),
        val historyTerms: Set<String> = emptySet(),
        val recentIds: Set<String> = emptySet()
    )

    data class Candidate(
        val id: String,
        val kind: Kind,
        val title: String,
        val creator: String = "",
        val summary: String = "",
        val publishedAtEpochSeconds: Long = 0L,
        val durationSeconds: Long = 0L,
        val sourceFollowed: Boolean = false,
        val engagement: Double? = null,
        val topicTags: Set<String> = emptySet()
    )

    data class SearchDocument<T>(
        val value: T,
        val title: String,
        val creator: String = "",
        val summary: String = "",
        val kind: Kind
    )

    data class SearchHit<T>(val value: T, val score: Double, val originalIndex: Int)
    data class FeedHit(val candidate: Candidate, val score: Int)

    private val usefulSignals = listOf(
        "explained", "analysis", "documentary", "research", "lecture", "tutorial",
        "how it works", "how to", "deep dive", "history of", "investigation", "report",
        "review", "guide", "course", "lesson", "learn", "masterclass", "breakdown",
        "case study", "science", "engineering", "theory", "evidence", "walkthrough",
        "educational", "study", "field notes", "critical analysis"
    )
    private val lowInformationSignals = listOf(
        "brainrot", "meme", "memes", "prank", "pranks", "rage bait", "ragebait",
        "celebrity gossip", "influencer drama", "internet drama", "viral challenge",
        "reaction prank", "shocking", "you won't believe", "you will not believe",
        "exposed", "destroyed", "insane reaction"
    )
    private val spamSignals = listOf(
        "free gift", "click here", "subscribe now", "watch till end", "watch till the end",
        "giveaway winner", "shocking!!!", "100% guaranteed", "make money fast"
    )
    private val expansions = mapOf(
        "ai" to setOf("artificial", "intelligence", "machine", "learning"),
        "ml" to setOf("machine", "learning"),
        "cs" to setOf("computer", "science"),
        "climate" to setOf("environment", "global", "warming"),
        "football" to setOf("soccer"),
        "soccer" to setOf("football")
    )

    /** Recommendation score from 0..1000; larger is better. */
    fun scoreFeed(
        candidate: Candidate,
        profile: Profile,
        nowEpochSeconds: Long = Instant.now().epochSecond
    ): Int {
        val title = normalize(candidate.title)
        val summary = normalize(candidate.summary)
        val creator = normalize(candidate.creator)
        val body = title + " " + summary + " " + creator
        val bodyTokens = tokens(body).toSet()
        val topic = topicAffinity(body, profile.topics, candidate.topicTags)
        val followed = candidate.sourceFollowed || profile.followedCreators.any { name ->
            val key = normalize(name)
            creator.isNotBlank() && key.isNotBlank() &&
                (creator == key || creator.contains(key) || key.contains(creator))
        }
        val follow = if (followed) 1.0 else 0.0
        val history = if (profile.historyTerms.isEmpty()) 0.35 else {
            val hits = profile.historyTerms.map(::normalize).filter(String::isNotBlank).count { term ->
                term.length >= 3 && (body.contains(term) || term.split(' ').any { it in bodyTokens })
            }
            (hits.toDouble() / minOf(profile.historyTerms.size, 8).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        }
        val fresh = freshness(candidate.publishedAtEpochSeconds, nowEpochSeconds)
        val complete = completeness(candidate.title, candidate.creator, candidate.summary)
        val engagement = candidate.engagement?.coerceIn(0.0, 1.0) ?: 0.45
        val novelty = if (candidate.id.isNotBlank() && candidate.id !in profile.recentIds) 0.75 else 0.12
        val utility = utilitySignal(body, candidate.summary)
        val taste = maxOf(topic, history * 0.85)

        // Common profile layer used by every section.
        val shared =
            topic * 0.26 +
            follow * 0.18 +
            history * 0.14 +
            fresh * 0.10 +
            complete * 0.12 +
            engagement * 0.05 +
            novelty * 0.15

        // Specialist algorithms: productive material is preferred in video/news; music is taste-led.
        val specialist = when (candidate.kind) {
            Kind.VIDEO -> {
                val depth = when {
                    candidate.durationSeconds <= 0L -> 0.45
                    candidate.durationSeconds >= 600L -> 0.92
                    candidate.durationSeconds >= 240L -> 0.72
                    candidate.durationSeconds >= 60L -> 0.52
                    else -> 0.36
                }
                (utility * 0.58 + depth * 0.20 + complete * 0.12 + topic * 0.10 -
                    lowInfoPenalty(body)).coerceIn(0.0, 1.0)
            }
            Kind.NEWS -> {
                val context = utility * 0.52 + complete * 0.24 + topic * 0.14 + fresh * 0.10
                (context - lowInfoPenalty(body) * 1.15).coerceIn(0.0, 1.0)
            }
            Kind.MUSIC -> (taste * 0.58 + follow * 0.23 + novelty * 0.12 + fresh * 0.07)
                .coerceIn(0.0, 1.0)
            Kind.CHANNEL -> topic * 0.45 + complete * 0.25 + novelty * 0.30
            Kind.PLAYLIST -> topic * 0.48 + complete * 0.30 + novelty * 0.22
        }

        var combined = shared * 0.64 + specialist * 0.36
        if (candidate.kind == Kind.VIDEO || candidate.kind == Kind.NEWS) {
            combined += (utility - 0.45) * 0.08
            combined -= lowInfoPenalty(body) * 0.05
        }
        if (candidate.id in profile.recentIds) combined -= 0.12
        return (combined.coerceIn(0.0, 1.0) * 1000.0).toInt()
    }

    fun rankFeed(candidates: List<Candidate>, profile: Profile): List<FeedHit> =
        candidates.map { FeedHit(it, scoreFeed(it, profile)) }.sortedByDescending { it.score }

    /** Greedy creator/topic diversification reduces repetitive feeds. */
    fun diversifyFeed(hits: List<FeedHit>, limit: Int, maxPerCreator: Int = 2): List<FeedHit> {
        if (limit <= 0 || hits.isEmpty()) return emptyList()
        val remaining = hits.toMutableList()
        val selected = ArrayList<FeedHit>(minOf(limit, hits.size))
        val creators = HashMap<String, Int>()
        val topics = HashMap<String, Int>()
        while (remaining.isNotEmpty() && selected.size < limit) {
            val eligible = remaining.filter { hit ->
                val creator = normalize(hit.candidate.creator)
                creator.isBlank() || (creators[creator] ?: 0) < maxPerCreator ||
                    remaining.size <= limit - selected.size
            }
            val pool = eligible.ifEmpty { remaining }
            val next = pool.maxByOrNull { hit ->
                val creator = normalize(hit.candidate.creator)
                val creatorPenalty = (creators[creator] ?: 0) * 105
                val topicPenalty = hit.candidate.topicTags.sumOf { (topics[normalize(it)] ?: 0) * 24 }
                hit.score - creatorPenalty - topicPenalty
            } ?: break
            remaining.remove(next)
            selected += next
            val creator = normalize(next.candidate.creator)
            if (creator.isNotBlank()) creators[creator] = (creators[creator] ?: 0) + 1
            next.candidate.topicTags.forEach {
                val key = normalize(it)
                if (key.isNotBlank()) topics[key] = (topics[key] ?: 0) + 1
            }
        }
        return selected
    }

    /**
     * Broad search: BM25-style lexical relevance + phrase, prefix and typo matching.
     * It never filters by selected topics, followed sources or productivity.
     */
    fun <T> rankSearch(query: String, documents: List<SearchDocument<T>>): List<SearchHit<T>> {
        if (documents.isEmpty()) return emptyList()
        val queryText = normalize(query)
        val queryTokens = tokens(queryText).distinct()
        if (queryTokens.isEmpty()) return documents.mapIndexed { index, doc -> SearchHit(doc.value, 0.0, index) }

        val titleTokens = documents.map { tokens(normalize(it.title)) }
        val creatorTokens = documents.map { tokens(normalize(it.creator)) }
        val summaryTokens = documents.map { tokens(normalize(it.summary)) }
        val df = queryTokens.associateWith { term ->
            documents.indices.count { index ->
                term in titleTokens[index] || term in creatorTokens[index] || term in summaryTokens[index]
            }
        }
        val averageLength = documents.indices.map { index ->
            titleTokens[index].size + creatorTokens[index].size * 0.7 + summaryTokens[index].size * 0.45
        }.average().coerceAtLeast(1.0)

        return documents.mapIndexed { index, doc ->
            val title = normalize(doc.title)
            val creator = normalize(doc.creator)
            val summary = normalize(doc.summary)
            val titleTerms = titleTokens[index]
            val creatorTerms = creatorTokens[index]
            val summaryTerms = summaryTokens[index]
            val length = titleTerms.size + creatorTerms.size * 0.7 + summaryTerms.size * 0.45
            val normLength = 1.2 * (1.0 - 0.68 + 0.68 * length / averageLength)
            var score = 0.0
            var matched = 0
            queryTokens.forEach { term ->
                val idf = ln(1.0 + (documents.size - (df[term] ?: 0) + 0.5) / ((df[term] ?: 0) + 0.5))
                val titleTf = titleTerms.count { it == term }
                val creatorTf = creatorTerms.count { it == term }
                val summaryTf = summaryTerms.count { it == term }
                val exactTf = titleTf * 3.0 + creatorTf * 2.1 + summaryTf * 0.72
                val expansionTf = expansions[term].orEmpty().sumOf { alternative ->
                    titleTerms.count { it == alternative } * 1.05 +
                        creatorTerms.count { it == alternative } * 0.72 +
                        summaryTerms.count { it == alternative } * 0.30
                }
                var tf = exactTf + expansionTf * 0.36
                if (tf == 0.0) tf = maxOf(
                    fuzzyMatch(term, titleTerms) * 2.2,
                    fuzzyMatch(term, creatorTerms) * 1.6,
                    fuzzyMatch(term, summaryTerms) * 0.55
                )
                if (tf > 0.0) {
                    matched++
                    score += idf * (tf * 2.2 / (tf + normLength))
                }
                if (titleTerms.any { it.startsWith(term) } || creatorTerms.any { it.startsWith(term) }) score += idf * 0.24
            }
            val phrase = when {
                title == queryText -> 8.0
                title.contains(queryText) -> 5.0
                creator == queryText -> 4.2
                creator.contains(queryText) -> 2.2
                summary.contains(queryText) -> 1.2
                else -> 0.0
            }
            val coverage = matched.toDouble() / queryTokens.size
            val quality = completeness(doc.title, doc.creator, doc.summary)
            val spamPenalty = spamSignals.count { signal ->
                val key = normalize(signal)
                key.isNotBlank() && (title.contains(key) || summary.contains(key))
            } * 0.20
            val typeFit = when (doc.kind) {
                Kind.MUSIC -> if (creatorTokens[index].any { it in queryTokens }) 0.25 else 0.0
                Kind.NEWS -> if (summary.isNotBlank()) 0.05 else 0.0
                else -> 0.0
            }
            SearchHit(doc.value, score + phrase + coverage * 0.45 + quality * 0.12 + typeFit - spamPenalty, index)
        }.sortedWith(compareByDescending<SearchHit<T>> { it.score }.thenBy { it.originalIndex })
    }

    fun scoreSearch(query: String, title: String, creator: String = "", summary: String = ""): Double =
        rankSearch(query, listOf(SearchDocument(Unit, title, creator, summary, Kind.NEWS)))
            .firstOrNull()?.score ?: 0.0

    fun legacyProductivityScore(title: String, description: String, topics: List<String>): Int =
        scoreFeed(Candidate("", Kind.VIDEO, title, summary = description), Profile(topics = topics))

    fun parseRssDate(value: String): Long = runCatching {
        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
    }.getOrDefault(0L)

    private fun topicAffinity(body: String, topics: List<String>, tags: Set<String>): Double {
        if (topics.isEmpty() && tags.isEmpty()) return 0.35
        val expanded = (topics + tags).flatMap { topic ->
            val key = normalize(topic)
            listOf(key) + key.split(' ').filter(String::isNotBlank) + expansions[key].orEmpty()
        }.filter(String::isNotBlank).distinct()
        val hits = expanded.count(body::contains)
        val strong = (topics + tags).count { normalize(it).let { key -> key.length >= 4 && body.contains(key) } }
        return (0.12 + hits * 0.075 + strong * 0.12).coerceIn(0.0, 1.0)
    }

    private fun utilitySignal(body: String, summary: String): Double {
        val matches = usefulSignals.count(body::contains)
        val depth = when {
            summary.length >= 700 -> 0.30
            summary.length >= 300 -> 0.22
            summary.length >= 120 -> 0.14
            summary.length >= 40 -> 0.07
            else -> 0.0
        }
        return (0.28 + matches * 0.11 + depth).coerceIn(0.0, 1.0)
    }

    private fun lowInfoPenalty(body: String): Double =
        (lowInformationSignals.count(body::contains) * 0.095).coerceAtMost(0.42)

    private fun freshness(publishedAt: Long, now: Long): Double {
        if (publishedAt <= 0L || publishedAt > now + 86_400L) return 0.42
        val ageHours = (now - publishedAt).coerceAtLeast(0L) / 3600.0
        return exp(-ageHours / 96.0).coerceIn(0.08, 1.0)
    }

    private fun completeness(title: String, creator: String, summary: String): Double {
        val titleScore = when {
            title.trim().length >= 45 -> 0.95
            title.trim().length >= 22 -> 0.78
            title.trim().length >= 8 -> 0.55
            else -> 0.20
        }
        val creatorScore = if (creator.isNotBlank()) 0.18 else 0.0
        val summaryScore = when {
            summary.trim().length >= 500 -> 0.50
            summary.trim().length >= 180 -> 0.38
            summary.trim().length >= 60 -> 0.24
            summary.isNotBlank() -> 0.10
            else -> 0.0
        }
        return (titleScore * 0.48 + creatorScore + summaryScore * 0.52).coerceIn(0.0, 1.0)
    }

    private fun fuzzyMatch(term: String, candidates: List<String>): Double {
        if (term.length < 4 || candidates.isEmpty()) return 0.0
        val maxDistance = if (term.length <= 6) 1 else 2
        return candidates.asSequence().filter { kotlin.math.abs(it.length - term.length) <= maxDistance }
            .map { levenshtein(term, it) }.filter { it in 1..maxDistance }.minOrNull()
            ?.let { (1.0 - it.toDouble() / term.length).coerceIn(0.0, 1.0) } ?: 0.0
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun tokens(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
