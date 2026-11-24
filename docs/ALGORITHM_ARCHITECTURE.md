# Rush Discovery, Recommendation and Search

## Product contract

Rush uses a **YouTube-inspired recommendation pipeline**, not a copy of YouTube's private model. It follows the public pattern of candidate retrieval, personal relevance scoring, explicit feedback, freshness and creator/topic diversification. Recommendations are personalised on-device. Search stays broad and is never restricted to the user's selected topics or watch history.

The app does not claim to reproduce YouTube's proprietary ranking, collaborative filtering or watch-time model. It does not invent view counts or engagement values when a provider does not expose reliable data.

## Pipeline

1. Retrieve candidates from YouTube/YouTube Music and Google News RSS. Explore's For You feed uses selected topics, recent history terms and followed sources to generate candidates.
2. Normalise Unicode, case and punctuation while preserving provider IDs and media metadata.
3. Score shared signals from interests, local watch/listen history, follows, freshness, metadata completeness, provider engagement when available, novelty, likes and dislikes.
4. Apply a specialist scorer for video, news, music, channel or playlist content.
5. Combine 68% shared score with 32% section-specialist score.
6. Suppress explicit "Not interested" items and creators the user has blocked.
7. Diversify creators and topic tags so one source cannot occupy the entire shelf.
8. Render the ranked result through the existing WebView/native callback contracts.

Profile and feedback signals remain on the device. Rush does not introduce cross-user tracking or a remote recommendation profile.

## Shared recommendation model

Signals are normalised to 0..1 unless they are explicit negative feedback:

| Signal | Weight | Purpose |
|---|---:|---|
| Topic affinity | 0.22 | Selected interests and candidate topic tags |
| Watch/listen history affinity | 0.26 | Local playback history title and creator overlap |
| Follow affinity | 0.16 | Followed channel, artist or publisher |
| Freshness | 0.10 | Exponential age decay; unknown dates remain neutral |
| Metadata completeness | 0.06 | Useful title, creator and available summary |
| Provider engagement | 0.04 | Provider-supplied signal only; neutral when absent |
| Novelty | 0.08 | Prefer unseen items and reduce immediate repeats |
| Explicit like | +0.12 | Strong positive feedback |
| Explicit dislike | -0.42 | Strong negative feedback |

"Not interested" and "Don't recommend this channel" are hard exclusions from recommendation ranking. The user can reverse feedback from the app's local preference controls.

## Section specialists

- **Videos:** topic match, history overlap, freshness and followed-channel affinity.
- **Music:** topic and listening-history overlap, artist follows and freshness.
- **News:** topic/history relevance, freshness and followed-publisher affinity.
- **Channels and playlists:** topic/history relevance and novelty.

A creator-diversity pass limits repeated sources and spreads topic coverage. No fixed productivity or entertainment category is used as an eligibility gate; user interest and explicit feedback drive recommendations.

## Broad search

Search is separate from recommendation eligibility. It searches the provider candidates returned by YouTube, YouTube Music and Google News RSS regardless of selected topics, followed-source status, likes or productivity classification.

The reranker uses BM25-style lexical relevance with title/creator/summary field weights, exact phrase and title boosts, prefix matching, bounded Levenshtein typo matching and a small abbreviation/synonym map. Search returns every provider candidate it receives; ranking only reorders them. Provider-side limits and availability still apply.

## Current limitations

- Watch/listen history is used, but precise watch-time, completion and satisfaction duration are not yet ranking inputs.
- Reliable view-count/like-count engagement data is not consistently available from the current provider, so missing engagement stays neutral.
- No account-based collaborative filtering, cross-device profile sync or remote embeddings are used.
- SQLite FTS5, local semantic embeddings and learned ranking are future milestones. Evaluate search with NDCG@10/MRR and recommendations with explicit feedback, creator/topic diversity, repeat rate and user satisfaction. Do not optimise only for watch time or raw clicks.
