# Rush recommendation pipeline

1. Taste fit: candidate similarity to the viewer's learned preference vector.
2. Context fit: similarity to current/session context.
3. Discovery: novelty, serendipity, curiosity and uncertainty-based exploration.
4. Source evidence: subscription/channel preference and co-watch affinity.
5. Quality and time: engagement quality, upload freshness and session fatigue.
6. Guardrails: topic probation, explicit rejection, anti-recommendation, repeat exposure and watch-history penalties.

RushRankingMixer combines these stages separately from the upstream search implementation. Recommendation eligibility does not restrict general search.
