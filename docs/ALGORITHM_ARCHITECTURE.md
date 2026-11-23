# Rush Discovery, Recommendation and Search

## Product contract

Rush has one shared intelligence layer plus a specialist scorer for each content section. The shared layer uses the same understanding of interests, follows, local watch/listen history, freshness, metadata quality and novelty. Each section then adds its own ranking signals. The two layers are combined for recommendations.

**Recommendations prioritise productive, informative content. Search is deliberately broader.** Someone searching for memes, pranks, celebrity news, entertainment or another low-focus subject should still find matching results. Feed preferences reorder suggestions; they do not act as search eligibility filters.

## Pipeline

1. Retrieve candidates from YouTube, YouTube Music and Google News RSS. For You video discovery combines followed-channel searches with up to five unique selected-topic searches; Following searches only followed sources.
2. Normalise Unicode, case and punctuation while preserving provider IDs and metadata.
3. Score common signals from interests, follows, local history, freshness, completeness, novelty and engagement when genuinely available.
4. Apply a dedicated scorer for video, news, music, channel or playlist content.
5. Combine 64% shared score and 36% section specialist score.
6. Diversify creators, publishers and topic tags.
7. Render through the existing WebView/native callback contracts.

The profile is built on-device from preferences and playback history. It is not uploaded to a Rush account or recommendation server.

## Shared recommendation model

Each shared signal is normalised to 0..1:

| Signal | Weight | Purpose |
|---|---:|---|
| Topic affinity | 0.26 | Selected interests and detected topic tags |
| Follow affinity | 0.18 | Followed creator, artist or publisher |
| History affinity | 0.14 | Local watch/listen title and creator overlap |
| Freshness | 0.10 | Exponential age decay; unknown dates are neutral |
| Metadata completeness | 0.12 | Useful title, source and available summary |
| Engagement | 0.05 | Provider-supplied value only; neutral if missing |
| Novelty | 0.15 | Prefer unseen candidates and reduce repeats |

Popularity stays low-weighted so already viral items do not overwhelm useful, personally relevant material.

## Section specialists

### Videos

For You retrieval is seeded by the selected topic list and supplemented by searches for every followed YouTube channel. The Following tab remains restricted to followed channels. Topic queries are deduplicated case-insensitively and bounded to five requests per refresh, then candidates enter the same scoring and creator-diversity pipeline. This lets the feed produce useful video candidates before a user has followed channels without weakening the source-only contract of Following.

Video ranking favours explainers, tutorials, lectures, research, documentaries, science, engineering, lessons and analysis. Longer material receives a modest depth signal, not an eligibility rule. Low-information patterns such as rage bait, pranks, memes and gossip are soft penalties only. Useful short videos remain eligible.

### News

News combines contextual/reporting language, metadata completeness, topic relevance and freshness. RSS publication time uses exponential decay with a 96-hour time scale, bounded so older reporting can still surface. Followed publishers receive a shared relevance boost. RSS metadata alone is not treated as proof of factual accuracy, ownership or editorial authority.

### Music

Music is taste-led, not productivity-gated. Followed artists, recent listening, title/artist overlap, novelty and the shared profile are combined. Songs are not demoted merely because they are entertainment. Repeat penalties and artist diversity help avoid repetitive suggestions.

### Channels and playlists

Channel and playlist discovery use the shared profile plus metadata completeness and novelty. Their direct search results still use the broad search algorithm.

## Broad search

Search is separate from recommendation eligibility. It searches the candidates returned by YouTube, YouTube Music and Google News RSS regardless of selected topics, followed-source status or productivity classification.

The reranker uses BM25-style lexical relevance with title/creator/summary field weights, exact phrase and title boosts, prefix matching, bounded Levenshtein typo matching, a small abbreviation/synonym map (AI, ML and football/soccer), and a very small spam-quality adjustment.

Search returns every provider candidate it receives. The quality adjustment only reorders results; it does not suppress memes, pranks, celebrity content or other entertainment. Provider-side limits and availability still apply. This is federated live-provider search, not a whole-web crawler or a copy of Google's private ranking system.

## Privacy and limitations

No account, cross-user collaborative filtering, tracking pixel or analytics service is introduced. Profile signals stay on-device. Engagement is neutral when no reliable provider signal exists. No verified publisher trust score or original-reporting signal is invented from an RSS entry.

SQLite FTS5 over all locally cached media, on-device semantic embeddings and learned ranking are future milestones. They should be added only after the current lexical baseline is measured. Useful evaluation targets include NDCG@10 and MRR for search, creator/topic diversity, repeat rate, saves and explicit feedback. Do not optimise only for watch time or raw clicks because those can reward clickbait.
