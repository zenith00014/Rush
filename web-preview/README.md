# Rush Web Preview

Browser-native Rush client served from `web-preview/`, with Vercel serverless discovery endpoints in `/api`.

## Features

- Live YouTube video search through the YouTube web search endpoint
- YouTube embedded playback
- Google News RSS discovery
- Topic-personalized feed and Following mode
- Browser-local follows, saved items and watch history
- Responsive desktop and mobile layout

## Deploy

Create a Vercel project for this repository with the repository root as the project root. `vercel.json` publishes `web-preview/` as the static output while `/api/*.js` runs as serverless functions.

## Limitations

This is a browser companion, not a full Android runtime. Playback uses YouTube's embed player and is subject to YouTube availability, account restrictions and ads. Preferences are stored in the current browser profile; cross-device account sync, native background playback and Android app-private offline downloads are not included. YouTube and Google News endpoints are unofficial/public web interfaces and may change.
