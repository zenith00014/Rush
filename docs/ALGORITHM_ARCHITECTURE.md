# Rush Video Discovery and Search

## Product contract

Rush is a YouTube video client. Its Home feed and search discover standard video results; the interface does not expose Shorts as a product area. Feed recommendations are on-device and are not intended to replicate Google's private recommendation system.

## Candidate pipeline

1. Retrieve public YouTube video results through the current NewPipeExtractor/YouTube search integration.
2. Reject candidates marked with a Shorts title marker or a reported duration of three minutes or less.
3. Deduplicate by YouTube video ID.
4. Rank remaining results using the existing Rush lexical relevance scorer and the selected topic queries.
5. Render video metadata through the WebView/native bridge.

The same Shorts filter is applied to video search, Home feed results and browsed public video playlists. Channel and playlist discovery are not themselves video playback results and are not duration-filtered.

## Recommendation signals

The current feed query set combines selected interests, followed channel names and, in the Following tab, only followed channel names. Search results are reranked using the existing BM25-style lexical search scorer, including title/creator/summary field relevance, phrase and prefix signals, typo tolerance and a small quality adjustment. Local watch history is retained for the profile and for the app library; the feed is not a collaborative or account-synchronised recommender.

The Home feed uses a short-lived device-local cache so a recently loaded feed can appear while the app refreshes. Follows, selected topics, Watch Later, history, playlists and queues are stored on-device.

## Shorts exclusion trade-off

The extractor does not consistently expose a canonical Shorts flag. Rush uses two available signals: a Shorts marker in the title and a duration threshold of 180 seconds. This deliberately suppresses more than Shorts alone: ordinary videos of three minutes or less can be hidden. Shorts with no duration and no title marker may not be detected. A future extractor update that provides a reliable content-format field should replace this heuristic.

## Scope and limits

Rush provides video discovery, channel follows, public playlists, local Watch Later/playlists/history/queue, offline downloads, native playback and Android Picture-in-Picture. It does not currently sign in to a Google account, so account-backed likes, dislikes, comments, live chat, uploads, Premium features and cloud synchronisation are outside the present scope.

No ad SDK or Rush-created ad placement is included. This is not a guarantee that creator-inserted sponsorships or every YouTube-served playback promotion can be removed.

## Privacy and evaluation

Topic selections, follows and playback library data stay in Android app-private storage. No cross-user profile, ad tracking integration or Rush account service is introduced. Useful future measures include search MRR/NDCG, Shorts false-positive rate, creator diversity, duplicate rate, save rate and playback-start success. The duration cutoff should be revisited once the extractor surfaces a reliable Shorts indicator.
