package com.rush.app.data.recommendation

/**
* Rush's staged ranking pipeline. Flow's original signal calculators are retained and credited;
* this mixer changes how their outputs are grouped and combined for Rush.
*/
internal data class RushScoreLayers(
val topicFit: Double, val contextFit: Double, val noveltyFit: Double,
val affinityFit: Double, val sourceSignal: Double, val coWatchSignal: Double,
val formatSignal: Double, val serendipity: Double, val curiosity: Double,
val exploration: Double, val quality: Double, val recency: Double,
val session: Double, val category: Double, val interestGuard: Double,
val feedbackGuard: Double, val repeatGuard: Double, val watchGuard: Double,
val momentum: Double, val onboarding: Double,
)

internal object RushRankingMixer {
fun combine(layers: RushScoreLayers): Double {
val preference = (layers.topicFit * 0.60 + layers.contextFit * 0.25 +
  layers.affinityFit * 0.15).coerceIn(0.0, 1.0)
val discovery = (layers.noveltyFit * 0.48 + layers.serendipity * 0.20 +
  layers.curiosity * 0.12 + layers.exploration * 0.20).coerceIn(0.0, 1.0)
val relevance = preference * 0.72 + discovery * 0.28
val source = (layers.sourceSignal + layers.coWatchSignal).coerceIn(-0.20, 0.24)
val candidateQuality = (relevance + source + layers.formatSignal).coerceIn(0.0, 1.35)
val quality = layers.quality.coerceIn(0.20, 1.10)
val temporal = (layers.recency.coerceIn(0.05, 1.20) *
  layers.session.coerceIn(0.05, 1.30)).coerceIn(0.02, 1.50)
val guardrails = (layers.interestGuard * layers.feedbackGuard *
  layers.repeatGuard * layers.watchGuard).coerceIn(0.0, 1.0)
return (candidateQuality * quality * temporal *
  layers.category.coerceIn(0.0, 1.25) * guardrails +
  layers.momentum.coerceIn(-0.20, 0.20) +
  layers.onboarding.coerceIn(0.0, 0.15)).coerceAtLeast(0.0)
}
}
