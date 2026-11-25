const $ = selector => document.querySelector(selector);
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[char]));
const safeImage = value => /^https:\/\/[^\s"'<>]+$/i.test(String(value || '')) ? String(value) : '';
const iconPaths = {
  menu: 'M3 6h18M3 12h18M3 18h18',
  home: 'M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z',
  explore: 'M4 4h16v16H4zM9 15l6-6M9 9h.01M15 15h.01',
  shorts: 'M7 3.8 17 9.5a3 3 0 0 1-3 5.2l-7-4a3 3 0 0 1 3-5.2l7 4a3 3 0 0 1-3 5.2l-4-2.3',
  tv: 'M3 5h18v13H3zM8 21h8M12 18v3',
  history: 'M3 12a9 9 0 1 0 2.6-6.4L3 8M3 3v5h5M12 7v5l4 2',
  watch: 'M4 5h16v14H4zM8 9h8M8 13h8M8 17h5',
  liked: 'M12 21s-8-4.8-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 6.2-8 11-8 11z',
  playlist: 'M4 6h16M4 11h11M4 16h9M17 14v7m-3.5-3.5h7',
  collection: 'M3 5h7l2 2h9v12H3z',
  subs: 'M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2M9.5 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM20 8v6m-3-3h6',
  search: 'M10.8 18a7.2 7.2 0 1 0 0-14.4 7.2 7.2 0 0 0 0 14.4zM16 16l5 5',
  add: 'M12 5v14M5 12h14',
  bell: 'M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
  settings: 'M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zM19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-1.8 3.1-.2-.1a1.7 1.7 0 0 0-1.8.1l-.2.1h-3.6l-.1-.2a1.7 1.7 0 0 0-1.6-1.1h-.3l-3.1-1.8.1-.2a1.7 1.7 0 0 0-.1-1.8l-.1-.2v-3.6l.2-.1a1.7 1.7 0 0 0 1.1-1.6v-.3l1.8-3.1.2.1a1.7 1.7 0 0 0 1.8-.1l.2-.1h3.6l.1.2a1.7 1.7 0 0 0 1.6 1.1h.3l3.1 1.8-.1.2a1.7 1.7 0 0 0 .1 1.8l.1.2V15z',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  chevron: 'm7 10 5 5 5-5',
  close: 'M6 6l12 12M18 6 6 18',
  play: 'm8 5 11 7-11 7z',
  arrow: 'M5 12h14m-6-6 6 6-6 6'
};
const icon = name => '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="' + (iconPaths[name] || iconPaths.more) + '"/></svg>';
const TOPIC_CATALOG = [
  'Gaming','Technology','Football','Science','Music','History','Education','Design','Programming',
  'AI and machine learning','Cybersecurity','Space','Engineering','Economics','Sports','Cricket',
  'Photography','Cooking','Travel','Cars','Nature','Wildlife','Documentaries','Anime','Comedy',
  'Architecture','Psychology','Mathematics','Health','Fitness','Art','Books','Podcasts','How-to'
];
const NAV_ITEMS = [
  { group: 'main', id: 'home', label: 'Home', icon: 'home' },
  { group: 'main', id: 'explore', label: 'Explore', icon: 'explore' },
  { group: 'main', id: 'shorts', label: 'Shorts', icon: 'shorts' },
  { group: 'main', id: 'tv', label: 'TV Mode', icon: 'tv' },
  { group: 'you', id: 'history', label: 'History', icon: 'history' },
  { group: 'you', id: 'watch-later', label: 'Watch Later', icon: 'watch' },
  { group: 'you', id: 'liked', label: 'Liked Videos', icon: 'liked' },
  { group: 'library', id: 'playlists', label: 'Playlists', icon: 'playlist' },
  { group: 'library', id: 'collections', label: 'Collections', icon: 'collection' },
  { group: 'subscriptions', id: 'subscriptions', label: 'Subscriptions', icon: 'subs' }
];
let page = 'home';
let prefs = { topics: [], sources: [] };
let feedData = { videos: [] };
let shortsData = [];
let searchVideos = [];
let searchChannels = [];
let searchMode = 'videos';
let searchQuery = '';
let currentVideo = { id: '', title: '', creator: '', thumbnail: '', channelId: '' };
let libraryData = { saved: [], history: [], queue: [], playlists: [], items: {} };
let likedItems = readLocal('rush_liked_items_v1');
let feedLoading = false;
let feedError = '';
let searchLoading = false;
let searchError = '';
let shortsLoading = false;
let shortcutsExpanded = false;
let sidebarExpanded = true;
let activeChannelId = '';
let notificationOpen = false;
let userMenuOpen = false;
let profile = { displayName: '', handle: '' };
let currentPlayback = { hasItem: false, isPlaying: false, title: '' };

function readLocal(key) {
  try { const value = JSON.parse(localStorage.getItem(key) || '[]'); return Array.isArray(value) ? value : []; }
  catch (_) { return []; }
}
function writeLocal(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) {}
}
function readPreferences() {
  try {
    if (window.RushNative && RushNative.getFeedPreferences) {
      const value = JSON.parse(RushNative.getFeedPreferences() || '{}');
      if (value && Array.isArray(value.topics)) prefs = { topics: value.topics, sources: value.sources || [] };
    }
  } catch (_) {}
}
function readProfile() {
  try {
    const value = window.RushNative && RushNative.getUserProfile ? JSON.parse(RushNative.getUserProfile() || '{}') : {};
    profile = { displayName: value.displayName || '', handle: value.handle || '' };
  } catch (_) { profile = { displayName: '', handle: '' }; }
}
function renderNav() {
  const groups = [
    ['main', ''],
    ['you', 'You'],
    ['library', 'Subscriptions and playlists'],
    ['subscriptions', 'Subscriptions']
  ];
  const root = $('#nav');
  if (!root) return;
  root.innerHTML = groups.map(([key, heading]) => {
    let items = NAV_ITEMS.filter(item => item.group === key);
    if (key === 'subscriptions') items = [];
    const rows = items.map(item => {
      const active = page === item.id || (item.id === 'home' && page === 'watch');
      return '<a class="nav-link" href="#' + item.id + '" data-nav-route="' + item.id + '" ' +
        (active ? 'aria-current="page"' : '') + '>' + icon(item.icon) +
        '<span class="nav-label">' + item.label + '</span></a>';
    }).join('');
    if (key === 'subscriptions') {
      const sources = (prefs.sources || []).filter(source => source.kind === 'channel');
      const sourceRows = sources.slice(0, 8).map(source =>
        '<a class="nav-link nav-sub-link" href="#subscriptions" data-open-channel="' + escapeHtml(source.id) + '">' +
        '<span class="channel-avatar">' + escapeHtml((source.name || 'R').slice(0, 1).toUpperCase()) + '</span>' +
        '<span class="nav-label">' + escapeHtml(source.name) + '</span></a>'
      ).join('');
      return '<section class="sidebar-group"><a class="nav-link" href="#subscriptions" data-nav-route="subscriptions">' +
        icon('subs') + '<span class="nav-label">Subscriptions</span></a>' +
        (sources.length ? sourceRows : '<span class="sidebar-heading">Followed channels appear here</span>') +
        '</section>';
    }
    if (key === 'library') {
      const playlists = (libraryData.playlists || []).slice(0, 6);
      const playlistRows = playlists.map(item =>
        '<a class="nav-link nav-sub-link" href="#playlists" data-open-playlist="' + escapeHtml(item.id) + '">' +
        '<span class="nav-label">' + escapeHtml(item.name) + '</span></a>'
      ).join('');
      return '<section class="sidebar-group"><div class="sidebar-heading">' + heading + '</div>' + rows +
        (playlistRows ? '<div class="nav-sublist">' + playlistRows + '</div>' : '') + '</section>';
    }
    return '<section class="sidebar-group">' + (heading ? '<div class="sidebar-heading">' + heading + '</div>' : '') + rows + '</section>';
  }).join('');
}
function setSidebar(open) {
  const sidebar = $('#sidebar');
  const backdrop = $('#layoutBackdrop');
  if (!sidebar || !backdrop) return;
  if (matchMedia('(max-width:760px)').matches) {
    sidebar.classList.toggle('mobile-open', !!open);
    backdrop.classList.toggle('visible', !!open);
  } else {
    sidebar.classList.toggle('collapsed', !open);
    sidebarExpanded = !!open;
  }
  const menu = $('#menuToggle');
  if (menu) menu.setAttribute('aria-expanded', String(!!open));
}
function syncSidebar() {
  const sidebar = $('#sidebar');
  if (!sidebar) return;
  if (matchMedia('(max-width:760px)').matches) {
    sidebar.classList.remove('mobile-open');
    $('#layoutBackdrop')?.classList.remove('visible');
  } else {
    sidebar.classList.toggle('collapsed', !sidebarExpanded);
  }
}
function initials(name) {
  const pieces = String(name || 'R').trim().split(/\s+/).filter(Boolean);
  return pieces.slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'R';
}
function durationLabel(value) {
  const raw = String(value || '').trim();
  return raw;
}
function avatar(creator, thumbnail) {
  const src = safeImage(thumbnail);
  return src
    ? '<span class="channel-avatar"><img src="' + escapeHtml(src) + '" width="40" height="40" alt="" loading="lazy" decoding="async"></span>'
    : '<span class="channel-avatar" aria-label="' + escapeHtml(creator || 'Channel') + '">' + escapeHtml(initials(creator)) + '</span>';
}
function videoCard(video, options = {}) {
  if (!video || !video.id) return '';
  const id = escapeHtml(video.id);
  const title = escapeHtml(video.title || 'Untitled video');
  const creator = escapeHtml(video.artist || video.creator || 'YouTube');
  const thumbnail = safeImage(video.thumbnail);
  const creatorThumbnail = safeImage(video.creatorThumbnail);
  const duration = durationLabel(video.duration);
  const channelId = escapeHtml(video.channelId || video.sourceId || '');
  const kind = options.short ? ' short-card' : '';
  return '<article class="video-card' + kind + '">' +
    '<button class="video-open" data-play-video="' + id + '" data-title="' + title + '" data-creator="' + creator +
    '" data-thumbnail="' + escapeHtml(thumbnail) + '" data-creator-thumbnail="' + escapeHtml(creatorThumbnail) +
    '" data-channel-id="' + channelId + '" aria-label="Play ' + title + '">' +
    '<span class="video-thumb">' + (thumbnail ? '<img src="' + escapeHtml(thumbnail) +
    '" width="640" height="360" alt="" loading="' + (options.priority ? 'eager' : 'lazy') +
    '" decoding="async" ' + (options.priority ? 'fetchpriority="high"' : '') + '>' : '') +
    (duration ? '<span class="video-duration">' + escapeHtml(duration) + '</span>' : '') + '</span></button>' +
    '<div class="video-meta">' + avatar(creator, creatorThumbnail) +
    '<div class="video-text"><button class="video-open" data-play-video="' + id + '" data-title="' + title +
    '" data-creator="' + creator + '" data-thumbnail="' + escapeHtml(thumbnail) + '" data-creator-thumbnail="' +
    escapeHtml(creatorThumbnail) + '" data-channel-id="' + channelId + '"><span class="video-title">' + title +
    '</span></button><div class="video-subtitle">' + creator + (duration ? ' · ' + escapeHtml(duration) : '') +
    '</div></div><details class="video-menu"><summary class="video-more" aria-label="More options">' + icon('more') +
    '</summary><div class="video-menu-panel"><button data-save-video="' + id + '" data-title="' + title +
    '" data-creator="' + creator + '" data-thumbnail="' + escapeHtml(thumbnail) + '">Save to Watch Later</button>' +
    '<button data-not-interested="' + id + '" data-creator="' + creator + '">Not interested</button>' +
    '<button data-dont-recommend="' + id + '" data-creator="' + creator + '">Don’t recommend channel</button></div></details></div></article>';
}
function renderVideoShelf(title, items, short = false, limit = 10) {
  const content = (items || []).slice(0, limit).map(item => videoCard(item, { short })).join('');
  if (!content) return '';
  return '<section class="feed-section"><div class="section-head"><h2>' + title + '</h2></div>' +
    '<div class="' + (short ? 'shorts-shelf video-shelf' : 'video-shelf') + '">' + content + '</div></section>';
}
function renderVideoGrid(items, limit = 24) {
  return '<div class="video-grid">' + (items || []).slice(0, limit).map((item, index) =>
    videoCard(item, { priority: index === 0 })).join('') + '</div>';
}
function renderTopics() {
  const topics = (prefs.topics || []).slice(0, 32);
  return '<div class="topic-toolbar"><div class="topic-row" role="group" aria-label="Your topics">' +
    topics.map((topic, index) => '<button class="topic-chip" data-topic-filter="' + escapeHtml(topic) +
    '" aria-pressed="' + (index === 0 ? 'true' : 'false') + '">' + escapeHtml(topic) + '</button>').join('') +
    '</div><button class="topic-settings" data-edit-topics aria-label="Edit topics">' + icon('settings') + '</button></div>';
}
function renderFeedState() {
  if (feedLoading && !feedData.videos.length) {
    return '<div class="loading-state" role="status"><strong>Loading videos</strong><p>Refreshing your recommendations.</p></div>' +
      '<div class="video-grid">' + Array.from({ length: 3 }, () =>
        '<div class="video-card"><div class="video-thumb skeleton-box"></div><div class="video-subtitle">Loading video details</div></div>').join('') + '</div>';
  }
  if (feedError && !feedData.videos.length) {
    return '<div class="error-state" role="alert"><strong>Could not load videos</strong><p>' +
      escapeHtml(feedError) + '</p><button class="inline-action" data-retry-feed>Try again</button></div>';
  }
  if (!feedData.videos.length) {
    return '<div class="empty-state"><strong>No videos to show yet</strong><p>Choose a few topics or follow a channel to build your feed.</p>' +
      '<button class="inline-action" data-edit-topics>Choose topics</button></div>';
  }
  const videos = feedData.videos;
  const featured = renderVideoShelf('In Case You Missed', videos, false, 6);
  const rest = videos.slice(6);
  return featured + '<section class="feed-section"><div class="section-head"><h2>Videos for you</h2></div>' +
    renderVideoGrid(rest.length ? rest : videos.slice(0, 12)) + '</section>' +
    renderVideoShelf('Shorts', shortsData, true, 10);
}
function renderHomePage(title = 'Home') {
  return '<header class="page-heading"><div><h1>' + title + '</h1></div></header>' +
    renderTopics() + '<div id="feedContent">' + renderFeedState() + '</div>';
}
function renderSearchPage() {
  return '<section class="search-page"><header class="page-heading"><div><h1>Search</h1><p>Find videos and channels.</p></div></header>' +
    '<form class="search-page-form" id="searchPageForm"><input id="searchPageInput" type="search" autocomplete="off" ' +
    'aria-label="Search videos and channels" placeholder="Search videos and channels" value="' + escapeHtml(searchQuery) + '">' +
    '<button class="primary-action" type="submit">Search</button></form>' +
    '<div class="result-tabs" role="tablist" aria-label="Search results">' +
    '<button class="result-tab" data-search-mode="videos" role="tab" aria-selected="' + (searchMode === 'videos') + '">Videos</button>' +
    '<button class="result-tab" data-search-mode="channels" role="tab" aria-selected="' + (searchMode === 'channels') + '">Channels</button></div>' +
    '<p class="video-subtitle" id="searchStatus" aria-live="polite">' +
    (searchLoading ? 'Searching…' : searchError || (searchQuery ? '' : 'Enter a search to see results.')) + '</p>' +
    '<div id="searchResults">' + renderSearchResults() + '</div></section>';
}
function renderSearchResults() {
  if (searchLoading) return '<div class="loading-state"><strong>Searching</strong><p>Finding matching videos and channels.</p></div>';
  if (searchError) return '<div class="error-state"><strong>Search failed</strong><p>' + escapeHtml(searchError) + '</p></div>';
  const videos = searchVideos.map(video => videoCard(video));
  const channels = searchChannels.map(channel => {
    const name = escapeHtml(channel.name || 'Channel');
    const src = safeImage(channel.thumbnail);
    const followed = (prefs.sources || []).some(item => item.id === channel.id && item.kind === 'channel');
    return '<article class="channel-result">' +
      (src ? '<img src="' + escapeHtml(src) + '" width="76" height="76" alt="" loading="lazy">' :
        '<span class="channel-avatar">' + escapeHtml(initials(name)) + '</span>') +
      '<div class="channel-result-copy"><strong>' + name + '</strong><span>' +
      escapeHtml(channel.subscribers || 'YouTube channel') + '</span><span>' + escapeHtml(channel.description || '') +
      '</span></div><button class="action-chip" data-follow-channel="' + escapeHtml(channel.id) +
      '" data-channel-name="' + name + '" data-channel-thumbnail="' + escapeHtml(src) + '" aria-pressed="' + followed +
      '">' + (followed ? 'Following' : 'Subscribe') + '</button></article>';
  });
  if (searchMode === 'channels') return channels.join('') || (searchQuery ?
    '<div class="empty-state"><strong>No channels found</strong><p>Try a different channel name.</p></div>' : '');
  return videos.join('') || (searchQuery ?
    '<div class="empty-state"><strong>No videos found</strong><p>Try another search phrase.</p></div>' : '');
}
function renderLibraryItem(id, actionLabel = 'Play') {
  const item = libraryData.items && libraryData.items[id];
  if (!item) return '';
  const thumb = safeImage(item.thumbnail);
  return '<article class="library-row">' + (thumb ?
    '<img src="' + escapeHtml(thumb) + '" width="124" height="70" alt="" loading="lazy">' :
    '<span class="library-placeholder">' + icon('play') + '</span>') +
    '<div class="library-row-copy"><strong>' + escapeHtml(item.title || 'Untitled video') +
    '</strong><span>' + escapeHtml(item.creator || 'YouTube') + '</span></div>' +
    '<div class="library-row-actions"><button class="action-chip" data-play-library="' + escapeHtml(id) + '">' +
    actionLabel + '</button></div></article>';
}
function renderLibraryPage() {
  const list = page === 'history' ? libraryData.history :
    page === 'watch-later' ? libraryData.saved : page === 'liked' ? likedItems.map(item => item.id) : [];
  if (page === 'playlists' || page === 'collections') {
    const playlists = libraryData.playlists || [];
    return '<header class="page-heading"><div><h1>' + (page === 'collections' ? 'Collections' : 'Playlists') +
      '</h1><p>Your saved video groups.</p></div><button class="primary-action" data-create-playlist>Create playlist</button></header>' +
      (playlists.length ? playlists.map(playlist => '<section class="playlist-row"><h3>' + escapeHtml(playlist.name) +
        '</h3><p class="video-subtitle">' + (playlist.items || []).length + ' videos</p>' +
        '<div class="page-heading-actions"><button class="action-chip" data-play-playlist="' + escapeHtml(playlist.id) +
        '">Play all</button><button class="action-chip" data-delete-playlist="' + escapeHtml(playlist.id) +
        '">Delete</button></div>' + (playlist.items || []).map(id => renderLibraryItem(id)).join('') + '</section>').join('') :
        '<div class="empty-state"><strong>No playlists yet</strong><p>Create a playlist to collect videos.</p>' +
        '<button class="primary-action" data-create-playlist>Create playlist</button></div>');
  }
  const title = page === 'history' ? 'History' : page === 'watch-later' ? 'Watch Later' : 'Liked Videos';
  const isLiked = page === 'liked';
  const ids = Array.isArray(list) ? list : [];
  return '<header class="page-heading"><div><h1>' + title + '</h1></div>' +
    (page === 'history' && ids.length ? '<button class="text-action" data-clear-history>Clear history</button>' : '') + '</header>' +
    (libraryLoading ? '<div class="loading-state">Loading your library.</div>' :
      ids.length ? ids.map(id => {
        if (isLiked) {
          const item = likedItems.find(entry => entry.id === id) || {};
          const meta = item;
          return '<article class="library-row">' + (safeImage(meta.thumbnail) ?
            '<img src="' + escapeHtml(meta.thumbnail) + '" width="124" height="70" alt="" loading="lazy">' :
            '<span class="library-placeholder">' + icon('play') + '</span>') +
            '<div class="library-row-copy"><strong>' + escapeHtml(meta.title || 'Liked video') +
            '</strong><span>' + escapeHtml(meta.creator || 'YouTube') + '</span></div>' +
            '<button class="action-chip" data-play-video="' + escapeHtml(meta.id) + '" data-title="' +
            escapeHtml(meta.title) + '" data-creator="' + escapeHtml(meta.creator) + '" data-thumbnail="' +
            escapeHtml(meta.thumbnail) + '">Play</button></article>';
        }
        return renderLibraryItem(id);
      }).join('') : '<div class="empty-state"><strong>' +
        (page === 'history' ? 'No watch history yet' : isLiked ? 'No liked videos yet' : 'Watch Later is empty') +
        '</strong><p>' + (page === 'history' ? 'Videos you watch will appear here.' :
          'Save or like a video to keep it here.') + '</p></div>');
}
let libraryLoading = false;
function renderSubscriptions() {
  const channels = (prefs.sources || []).filter(source => source.kind === 'channel');
  const heading = '<header class="page-heading"><div><h1>Subscriptions</h1><p>Latest videos from channels you follow.</p></div></header>';
  const followed = channels.length ? '<div class="followed-list">' + channels.map(source =>
    '<button class="followed-chip" data-select-channel="' + escapeHtml(source.id) + '">' +
    avatar(source.name, source.thumbnail) + '<span>' + escapeHtml(source.name) + '</span></button>'
  ).join('') + '</div>' : '<div class="empty-state"><strong>No subscriptions yet</strong><p>Search for a channel and subscribe to it.</p>' +
    '<button class="primary-action" data-open-search>Find channels</button></div>';
  const feed = feedData.videos.length ? renderVideoGrid(feedData.videos) :
    '<div class="empty-state"><strong>Your subscription feed is empty</strong><p>New uploads from followed channels will appear here.</p></div>';
  return heading + followed + '<section class="feed-section"><div class="section-head"><h2>Latest videos</h2></div>' + feed + '</section>';
}
function renderShorts() {
  return '<header class="page-heading"><div><h1>Shorts</h1><p>Short-form videos from YouTube.</p></div></header>' +
    (shortsLoading ? '<div class="loading-state">Loading Shorts.</div>' :
      shortsData.length ? '<div class="video-grid shorts-page-grid">' + shortsData.map(item => videoCard(item, { short: true })).join('') + '</div>' :
        '<div class="empty-state"><strong>Shorts are not available yet</strong><p>Refresh to search for short-form videos.</p>' +
        '<button class="inline-action" data-load-shorts>Refresh</button></div>');
}
function renderWatchPage() {
  const channelName = escapeHtml(currentVideo.creator || 'Channel');
  const liked = likedItems.some(item => item.id === currentVideo.id);
  const saved = (libraryData.saved || []).includes(currentVideo.id);
  const recommendations = upNext.map(item => videoCard(item)).join('');
  return '<section class="watch-layout"><div class="watch-player"><div class="video-player-host" id="videoPlayerHost"></div></div>' +
    '<section class="watch-details"><h1 id="watchTitle">' + escapeHtml(currentVideo.title || 'Video') + '</h1>' +
    '<div class="watch-channel-row">' + avatar(channelName, currentVideo.creatorThumbnail) +
    '<div class="watch-channel-copy"><strong>' + channelName + '</strong><span>YouTube channel</span></div>' +
    '<button class="action-chip" data-follow-channel="' + escapeHtml(currentVideo.channelId) +
    '" data-channel-name="' + channelName + '" data-channel-thumbnail="' + escapeHtml(currentVideo.creatorThumbnail) +
    '">Subscribe</button></div><div class="watch-actions">' +
    '<button class="action-chip ' + (liked ? 'active' : '') + '" data-toggle-like>' + (liked ? 'Liked' : 'Like') + '</button>' +
    '<button class="action-chip ' + (saved ? 'active' : '') + '" data-toggle-watch-later>' +
    (saved ? 'Saved' : 'Watch Later') + '</button>' +
    '<button class="action-chip" data-video-quality>Quality</button><button class="action-chip" data-video-captions>Captions</button>' +
    '<button class="action-chip" data-video-fullscreen>Full screen</button></div>' +
    '<p class="video-subtitle" id="videoPlaybackStatus" aria-live="polite">Video ready to play.</p></section>' +
    '<section class="feed-section watch-more"><div class="section-head"><h2>Up next</h2></div>' +
    (upNextLoading ? '<div class="loading-state">Loading related videos.</div>' : recommendations || '<div class="empty-state">No related videos found.</div>') +
    '</section></section>';
}
let upNext = [];
let upNextLoading = false;
let videoQuality = 0;
let captionsEnabled = true;
function renderTopicsDialog() {
  const selected = new Set(prefs.topics || []);
  return '<div class="dialog-backdrop" data-dialog-backdrop><section class="dialog" role="dialog" aria-modal="true" aria-labelledby="topicsDialogTitle">' +
    '<header class="dialog-head"><h2 id="topicsDialogTitle">Edit topics</h2><button class="icon-button" data-close-dialog aria-label="Close">' +
    icon('close') + '</button></header><p class="dialog-copy">Choose the topics that shape your Home recommendations.</p>' +
    '<div class="topic-options">' + TOPIC_CATALOG.map(topic => '<button class="topic-option" data-topic-choice="' +
    escapeHtml(topic) + '" aria-pressed="' + selected.has(topic) + '">' + escapeHtml(topic) + '</button>').join('') +
    '</div><div class="dialog-actions"><button class="action-chip" data-close-dialog>Cancel</button>' +
    '<button class="primary-action" data-save-topics>Save topics</button></div></section></div>';
}
function renderCreateDialog() {
  return '<div class="dialog-backdrop" data-dialog-backdrop><form class="dialog" id="createPlaylistForm" role="dialog" aria-modal="true" aria-labelledby="createPlaylistTitle">' +
    '<header class="dialog-head"><h2 id="createPlaylistTitle">Create playlist</h2><button type="button" class="icon-button" data-close-dialog aria-label="Close">' +
    icon('close') + '</button></header><p class="dialog-copy">Give your video collection a name.</p>' +
    '<label for="playlistName">Playlist name</label><div class="form-row"><input id="playlistName" maxlength="80" required placeholder="Name your playlist">' +
    '<button class="primary-action" type="submit">Create</button></div></form></div>';
}
function renderSettingsPage() {
  let permission = {};
  try { permission = window.RushNative && RushNative.getPermissionStatus ? JSON.parse(RushNative.getPermissionStatus() || '{}') : {}; }
  catch (_) {}
  const notificationLabel = permission.notificationsEnabled ? 'Notifications enabled' :
    permission.notificationsBlockedInSystemSettings ? 'Blocked in Android settings' : 'Notifications not enabled';
  return '<header class="page-heading"><div><h1>Settings</h1><p>Android permissions and playback preferences.</p></div></header>' +
    '<section class="settings-list">' +
    '<div class="settings-row"><div><strong>Notifications</strong><span>' + notificationLabel + '</span></div>' +
    '<button class="action-chip" data-notification-permission>' + (permission.notificationsEnabled ? 'Manage' : 'Enable') + '</button></div>' +
    '<div class="settings-row"><div><strong>Picture-in-Picture</strong><span>Allow video playback over other apps.</span></div>' +
    '<button class="action-chip" data-open-pip-settings>Open settings</button></div>' +
    '<div class="settings-row"><div><strong>Android permissions</strong><span>Review app permissions in system settings.</span></div>' +
    '<button class="action-chip" data-open-android-settings>Manage</button></div></section>';
}
function renderCurrentPage() {
  const content = $('#v');
  if (!content) return;
  document.body.classList.toggle('tv-mode', page === 'tv');
  if (page === 'home') content.innerHTML = renderHomePage('Home');
  else if (page === 'explore') content.innerHTML = renderHomePage('Explore');
  else if (page === 'tv') content.innerHTML = renderHomePage('TV Mode');
  else if (page === 'shorts') content.innerHTML = renderShorts();
  else if (page === 'search') content.innerHTML = renderSearchPage();
  else if (page === 'subscriptions') content.innerHTML = renderSubscriptions();
  else if (['history', 'watch-later', 'liked', 'playlists', 'collections'].includes(page)) content.innerHTML = renderLibraryPage();
  else if (page === 'watch') content.innerHTML = renderWatchPage();
  else if (page === 'settings') content.innerHTML = renderSettingsPage();
  else content.innerHTML = renderHomePage('Home');
  document.querySelectorAll('[data-nav-route]').forEach(link => {
    const active = link.dataset.navRoute === page ||
      (page === 'watch' && link.dataset.navRoute === 'home');
    if (active) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
  const searchInput = $('#searchPageInput');
  if (searchInput) searchInput.value = searchQuery;
  updateVideoPosition();
}
function closePopover() {
  notificationOpen = false;
  userMenuOpen = false;
  $('#notificationsPopover')?.setAttribute('hidden', '');
  $('#userPopover')?.setAttribute('hidden', '');
  $('#notificationButton')?.setAttribute('aria-expanded', 'false');
  $('#profileButton')?.setAttribute('aria-expanded', 'false');
}
function openPopover(kind) {
  closePopover();
  const element = kind === 'notifications' ? $('#notificationsPopover') : $('#userPopover');
  const button = kind === 'notifications' ? $('#notificationButton') : $('#profileButton');
  if (element) element.removeAttribute('hidden');
  if (button) button.setAttribute('aria-expanded', 'true');
  notificationOpen = kind === 'notifications';
  userMenuOpen = kind === 'profile';
}
function showDialog(markup) {
  const root = $('#dialogRoot');
  if (!root) return;
  root.innerHTML = markup;
  const first = root.querySelector('input,button');
  first?.focus();
}
function hideDialog() {
  const root = $('#dialogRoot');
  if (root) root.replaceChildren();
}
function startSearch(query) {
  const value = String(query || '').trim();
  if (!value) return;
  searchQuery = value.slice(0, 200);
  searchMode = 'videos';
  searchVideos = [];
  searchChannels = [];
  searchError = '';
  searchLoading = true;
  page = 'search';
  history.replaceState(null, '', '#search');
  renderCurrentPage();
  readPreferences();
  if (window.RushNative) {
    RushNative.recordSearchQuery(searchQuery);
    RushNative.searchVideos(searchQuery);
    RushNative.searchChannels(searchQuery);
  } else {
    searchLoading = false;
    searchError = 'Search is unavailable in this preview.';
    renderCurrentPage();
  }
}
function renderFeed() {
  const root = $('#feedContent');
  if (root) root.innerHTML = renderFeedState();
  if (page === 'subscriptions') renderCurrentPage();
}
function loadFeed(mode = 'forYou') {
  feedLoading = true;
  feedError = '';
  if (!feedData.videos.length) renderFeed();
  if (window.RushNative) RushNative.loadVideoFeed(mode);
  else {
    feedLoading = false;
    feedError = 'The native video provider is not connected.';
    renderFeed();
  }
}
function loadShorts() {
  if (shortsLoading || !window.RushNative) return;
  shortsLoading = true;
  if (page === 'shorts') renderCurrentPage();
  shortsRequestPurpose = 'shorts';
  RushNative.searchVideos('YouTube Shorts');
}
let shortsRequestPurpose = '';
function loadLibrary() {
  if (!window.RushNative) return;
  libraryLoading = true;
  if (['history', 'watch-later', 'liked', 'playlists', 'collections'].includes(page)) renderCurrentPage();
  RushNative.loadLibrary();
}
function playVideo(video) {
  if (!video || !video.id) return;
  currentVideo = {
    id: String(video.id),
    title: String(video.title || 'Video'),
    creator: String(video.artist || video.creator || 'YouTube'),
    thumbnail: safeImage(video.thumbnail),
    creatorThumbnail: safeImage(video.creatorThumbnail),
    channelId: String(video.channelId || video.sourceId || '')
  };
  page = 'watch';
  history.pushState(null, '', '#watch');
  renderCurrentPage();
  if (window.RushNative) {
    RushNative.playVideo(currentVideo.id, currentVideo.title, currentVideo.creator, currentVideo.thumbnail);
    RushNative.loadUpNext(currentVideo.id, currentVideo.title, currentVideo.creator);
  }
}
function isSaved(id) { return (libraryData.saved || []).includes(id); }
function toggleWatchLater(video) {
  if (!window.RushNative || !video.id) return;
  if (isSaved(video.id)) RushNative.removeSavedLibraryItem(video.id);
  else RushNative.saveLibraryItem(video.id, video.title, video.creator, false, video.thumbnail);
}
function toggleLiked(video) {
  const items = readLocal('rush_liked_items_v1');
  const index = items.findIndex(item => item.id === video.id);
  if (index >= 0) items.splice(index, 1);
  else items.unshift(video);
  likedItems = items.slice(0, 200);
  writeLocal('rush_liked_items_v1', likedItems);
  if (window.RushNative) RushNative.setRecommendationFeedback(video.id, video.creator, index >= 0 ? 'unlike' : 'like');
}
function currentVideoData() {
  return currentVideo.id ? currentVideo : null;
}
function updateVideoPosition() {
  if (!window.RushNative || !currentVideo.id) return;
  const mini = page !== 'watch';
  const host = mini ? $('#videoMiniHost') : $('#videoPlayerHost');
  if (!host) return;
  const rect = host.getBoundingClientRect();
  if (rect.width > 0 && rect.height > 0) RushNative.updateVideoPosition(rect.left, rect.top, rect.width, rect.height, mini);
}
function handleRoute(route) {
  const valid = new Set(['home','explore','shorts','tv','search','subscriptions','history','watch-later','liked','playlists','collections','watch','settings']);
  if (!valid.has(route)) route = 'home';
  page = route;
  if (page !== 'watch' && currentPlayback.hasItem && currentPlayback.isVideo) {
    $('#videoMiniDock')?.removeAttribute('hidden');
  } else {
    $('#videoMiniDock')?.setAttribute('hidden', '');
  }
  if (page === 'home' || page === 'explore' || page === 'tv') {
    readPreferences();
    if (window.RushNative) {
      loadFeed('forYou');
      if (!shortsData.length) loadShorts();
    }
  } else if (page === 'subscriptions') {
    readPreferences();
    loadFeed('following');
  } else if (page === 'shorts') {
    if (!shortsData.length) loadShorts();
  } else if (['history','watch-later','liked','playlists','collections'].includes(page)) loadLibrary();
  else if (page === 'watch' && window.RushNative) {
    RushNative.loadCurrentPlayback();
    if (currentVideo.id) RushNative.loadUpNext(currentVideo.id, currentVideo.title, currentVideo.creator);
  } else if (page === 'settings') readProfile();
  closePopover();
  renderNav();
  renderCurrentPage();
  if (window.RushNative) RushNative.loadCurrentPlayback();
  if (page === 'search' && searchQuery) {
    const status = $('#searchStatus');
    if (status) status.textContent = searchLoading ? 'Searching…' : '';
  }
  requestAnimationFrame(updateVideoPosition);
}
function routeTo(path) {
  closePopover();
  if (matchMedia('(max-width:760px)').matches) setSidebar(false);
  location.hash = '#' + path;
}
function saveTopics() {
  const selected = [...document.querySelectorAll('[data-topic-choice][aria-pressed=true]')].map(button => button.dataset.topicChoice);
  if (!selected.length) return;
  if (window.RushNative) RushNative.saveFeedTopics(JSON.stringify(selected));
  hideDialog();
}
function selectedChannel(video) {
  return (prefs.sources || []).find(source => source.kind === 'channel' &&
    ((video.channelId && source.id === video.channelId) || source.name.toLowerCase() === String(video.creator || '').toLowerCase()));
}
function handleFollow(video) {
  if (!window.RushNative || !video.channelId) return;
  const followed = (prefs.sources || []).some(source => source.kind === 'channel' && source.id === video.channelId);
  if (followed) RushNative.unfollowSource('channel', video.channelId);
  else RushNative.followSource('channel', video.channelId, video.creator || 'Channel', false, video.creatorThumbnail || '');
}
function getVideoFromButton(button) {
  return {
    id: button.dataset.playVideo || button.dataset.saveVideo || currentVideo.id,
    title: button.dataset.title || currentVideo.title,
    creator: button.dataset.creator || currentVideo.creator,
    thumbnail: button.dataset.thumbnail || currentVideo.thumbnail,
    creatorThumbnail: button.dataset.creatorThumbnail || currentVideo.creatorThumbnail,
    channelId: button.dataset.channelId || currentVideo.channelId
  };
}
function closeNotifications() { closePopover(); }
function initHeader() {
  const profileButton = $('#profileButton');
  if (profileButton) {
    const name = profile.displayName || 'Rush';
    profileButton.textContent = initials(name);
    profileButton.setAttribute('aria-label', 'Open profile menu');
  }
}
function initApp() {
  readPreferences();
  readProfile();
  renderNav();
  const start = (location.hash || '#home').slice(1);
  handleRoute(start);
  initHeader();
}
window.rushVideoFeedResults = data => {
  const payload = data || {};
  if (payload.mode && payload.mode === 'following' && page !== 'subscriptions') return;
  if (payload.mode && payload.mode !== 'following' && page === 'subscriptions') return;
  feedData = { videos: Array.isArray(payload.videos) ? payload.videos : [] };
  feedLoading = false;
  feedError = '';
  renderFeed();
};
window.rushHomeFeedLoading = state => {
  if (state && state.mode === 'following' && page !== 'subscriptions') return;
  feedLoading = true;
  feedError = '';
  renderFeed();
};
window.rushHomeFeedError = error => {
  if (error && error.mode === 'following' && page !== 'subscriptions') return;
  feedLoading = false;
  feedError = error && error.message ? error.message : 'Feed refresh failed.';
  renderFeed();
};
window.rushVideoSearchResults = items => {
  const values = Array.isArray(items) ? items : [];
  if (shortsRequestPurpose === 'shorts') {
    shortsRequestPurpose = '';
    shortsData = values;
    shortsLoading = false;
    if (page === 'shorts' || page === 'home' || page === 'explore' || page === 'tv') renderCurrentPage();
    return;
  }
  searchVideos = values;
  searchLoading = false;
  searchError = '';
  if (page === 'search' && searchMode === 'videos') renderCurrentPage();
};
window.rushVideoSearchError = error => {
  const message = error && error.message ? error.message : 'Video search failed.';
  if (shortsRequestPurpose === 'shorts') {
    shortsRequestPurpose = '';
    shortsLoading = false;
    if (page === 'shorts') renderCurrentPage();
    return;
  }
  searchLoading = false;
  searchError = message;
  if (page === 'search') renderCurrentPage();
};
window.rushChannelSearchResults = items => {
  searchChannels = Array.isArray(items) ? items : [];
  searchLoading = false;
  if (page === 'search' && searchMode === 'channels') renderCurrentPage();
};
window.rushChannelSearchError = error => {
  searchLoading = false;
  searchError = error && error.message ? error.message : 'Channel search failed.';
  if (page === 'search') renderCurrentPage();
};
window.rushFeedPreferences = data => {
  if (data && Array.isArray(data.topics)) prefs = { topics: data.topics, sources: data.sources || [] };
  renderNav();
  if (page === 'home' || page === 'explore' || page === 'tv') renderCurrentPage();
  if (page === 'subscriptions') renderCurrentPage();
};
window.rushLibraryData = data => {
  libraryData = data || libraryData;
  libraryLoading = false;
  renderNav();
  if (['history','watch-later','liked','playlists','collections'].includes(page)) renderCurrentPage();
  if (page === 'watch') renderCurrentPage();
};
window.rushLibraryError = error => {
  libraryLoading = false;
  const status = $('#libraryStatus');
  if (status) status.textContent = error && error.message ? error.message : 'Could not load the library.';
};
window.rushCurrentPlayback = data => {
  currentPlayback = data || currentPlayback;
  if (data && data.hasItem && data.isVideo) {
    currentVideo.title = data.title || currentVideo.title;
    const dock = $('#videoMiniDock');
    if (dock) dock.hidden = page === 'watch';
    const title = $('#videoMiniTitle');
    if (title) title.textContent = data.title || 'Now playing';
  } else if (data && !data.hasItem) {
    $('#videoMiniDock')?.setAttribute('hidden', '');
  }
  if (page === 'watch') {
    const title = $('#watchTitle');
    if (title && data.title) title.textContent = data.title;
  }
  requestAnimationFrame(updateVideoPosition);
};
window.rushVideoPlaybackState = state => {
  const status = $('#videoPlaybackStatus');
  if (status) status.textContent = state && state.state === 'playing' ? 'Playing' :
    state && state.state === 'loading' ? 'Connecting to video…' :
    state && state.message ? state.message : (state && state.state || 'Ready');
  requestAnimationFrame(updateVideoPosition);
};
window.rushUpNextResults = items => {
  upNext = Array.isArray(items) ? items : [];
  upNextLoading = false;
  if (page === 'watch') renderCurrentPage();
};
window.rushUpNextError = error => {
  upNextLoading = false;
  if (page === 'watch') renderCurrentPage();
};
window.rushUpdateVideoPosition = updateVideoPosition;
window.rushHandleBack = function() {
  if (notificationOpen || userMenuOpen) { closePopover(); return true; }
  if ($('#dialogRoot')?.firstElementChild) { hideDialog(); return true; }
  if ($('#sidebar')?.classList.contains('mobile-open')) { setSidebar(false); return true; }
  if (page === 'watch') { routeTo('home'); return true; }
  if (page !== 'home') { routeTo('home'); return true; }
  return false;
};

document.addEventListener('click', event => {
  const button = event.target.closest('button');
  const link = event.target.closest('a');
  if (link && link.dataset.navRoute) {
    if (link.dataset.navRoute === page) event.preventDefault();
    return;
  }
  if (!button) return;
  if (button.id === 'menuToggle') { setSidebar(matchMedia('(max-width:760px)').matches ? true : !sidebarExpanded); return; }
  if (button.id === 'notificationButton') { openPopover('notifications'); return; }
  if (button.id === 'profileButton') { openPopover('profile'); return; }
  if (button.hasAttribute('data-open-search')) { const input = $('#globalSearch'); input?.focus(); return; }
  if (button.hasAttribute('data-open-notifications')) { openPopover('notifications'); return; }
  if (button.hasAttribute('data-open-profile')) { openPopover('profile'); return; }
  if (button.hasAttribute('data-open-settings')) { routeTo('settings'); return; }
  if (button.hasAttribute('data-create-playlist')) { showDialog(renderCreateDialog()); return; }
  if (button.hasAttribute('data-close-dialog')) { hideDialog(); return; }
  if (button.hasAttribute('data-dialog-backdrop') && event.target === button) { hideDialog(); return; }
  if (button.hasAttribute('data-edit-topics')) { showDialog(renderTopicsDialog()); return; }
  if (button.hasAttribute('data-topic-choice')) {
    const pressed = button.getAttribute('aria-pressed') === 'true';
    button.setAttribute('aria-pressed', String(!pressed)); return;
  }
  if (button.hasAttribute('data-save-topics')) { saveTopics(); return; }
  if (button.hasAttribute('data-topic-filter')) {
    const topic = button.dataset.topicFilter || '';
    document.querySelectorAll('[data-topic-filter]').forEach(item =>
      item.setAttribute('aria-pressed', String(item === button)));
    startSearch(topic); return;
  }
  if (button.hasAttribute('data-search-mode')) {
    searchMode = button.dataset.searchMode;
    searchLoading = false;
    renderCurrentPage(); return;
  }
  if (button.hasAttribute('data-play-video')) { playVideo(getVideoFromButton(button)); return; }
  if (button.hasAttribute('data-save-video')) { toggleWatchLater(getVideoFromButton(button)); button.closest('.video-menu')?.removeAttribute('open'); return; }
  if (button.hasAttribute('data-not-interested')) {
    if (window.RushNative) RushNative.setRecommendationFeedback(button.dataset.notInterested, button.dataset.creator || '', 'notInterested');
    button.closest('.video-card')?.remove(); return;
  }
  if (button.hasAttribute('data-dont-recommend')) {
    if (window.RushNative) RushNative.setRecommendationFeedback(button.dataset.dontRecommend, button.dataset.creator || '', 'recommendChannel');
    button.closest('.video-card')?.remove(); return;
  }
  if (button.hasAttribute('data-toggle-like')) {
    const video = currentVideoData(); if (video) toggleLiked(video); renderCurrentPage(); return;
  }
  if (button.hasAttribute('data-toggle-watch-later')) {
    const video = currentVideoData(); if (video) toggleWatchLater(video); renderCurrentPage(); return;
  }
  if (button.hasAttribute('data-follow-channel')) {
    const id = button.dataset.followChannel || currentVideo.channelId;
    const name = button.dataset.channelName || currentVideo.creator;
    const thumbnail = button.dataset.channelThumbnail || currentVideo.creatorThumbnail;
    const followed = (prefs.sources || []).some(source => source.kind === 'channel' && source.id === id);
    if (window.RushNative && id) {
      if (followed) RushNative.unfollowSource('channel', id);
      else RushNative.followSource('channel', id, name, false, thumbnail);
    }
    return;
  }
  if (button.hasAttribute('data-select-channel')) {
    activeChannelId = button.dataset.selectChannel || '';
    const source = prefs.sources.find(item => item.id === activeChannelId);
    if (source && window.RushNative) {
      currentChannelSource = source;
      RushNative.loadChannelUploads(source.id, source.name);
    }
    return;
  }
  if (button.hasAttribute('data-open-channel')) {
    activeChannelId = button.dataset.openChannel || '';
    routeTo('subscriptions'); return;
  }
  if (button.hasAttribute('data-open-search')) { routeTo('search'); return; }
  if (button.hasAttribute('data-load-shorts')) { loadShorts(); return; }
  if (button.hasAttribute('data-retry-feed')) { loadFeed(page === 'subscriptions' ? 'following' : 'forYou'); return; }
  if (button.hasAttribute('data-play-library')) {
    if (window.RushNative) RushNative.playLibraryItem(button.dataset.playLibrary);
    return;
  }
  if (button.hasAttribute('data-clear-history')) {
    if (window.RushNative) RushNative.clearPlaybackHistory();
    return;
  }
  if (button.hasAttribute('data-create-playlist')) { showDialog(renderCreateDialog()); return; }
  if (button.hasAttribute('data-delete-playlist')) {
    if (window.RushNative) RushNative.deleteLibraryPlaylist(button.dataset.deletePlaylist);
    return;
  }
  if (button.hasAttribute('data-play-playlist')) {
    if (window.RushNative) RushNative.playLibraryPlaylist(button.dataset.playPlaylist);
    return;
  }
  if (button.hasAttribute('data-video-quality')) {
    const value = prompt('Preferred maximum video quality in pixels. Enter 0 for Auto.', String(videoQuality));
    if (value !== null && window.RushNative) {
      videoQuality = Number(value) || 0;
      RushNative.setVideoQuality(videoQuality);
    }
    return;
  }
  if (button.hasAttribute('data-video-captions')) {
    captionsEnabled = !captionsEnabled;
    if (window.RushNative) RushNative.setVideoCaptionsEnabled(captionsEnabled);
    button.classList.toggle('active', captionsEnabled);
    return;
  }
  if (button.hasAttribute('data-video-fullscreen')) {
    if (window.RushNative) RushNative.setVideoFullscreen(true);
    return;
  }
  if (button.hasAttribute('data-close-video')) {
    if (window.RushNative) RushNative.closeVideo();
    $('#videoMiniDock')?.setAttribute('hidden', '');
    currentPlayback = { hasItem: false, isVideo: false, isPlaying: false };
    return;
  }
  if (button.hasAttribute('data-mini-expand')) { routeTo('watch'); return; }
  if (button.hasAttribute('data-notification-permission')) {
    if (window.RushNative) {
      let state = {};
      try { state = JSON.parse(RushNative.getPermissionStatus() || '{}'); } catch (_) {}
      if (state.notificationsBlockedInSystemSettings) RushNative.openApplicationSettings();
      else RushNative.requestNotificationPermission();
    }
    return;
  }
  if (button.hasAttribute('data-open-pip-settings')) { if (window.RushNative) RushNative.openPictureInPictureSettings(); return; }
  if (button.hasAttribute('data-open-android-settings')) { if (window.RushNative) RushNative.openApplicationSettings(); return; }
  if (button.hasAttribute('data-close-popover')) { closePopover(); return; }
  if (button.hasAttribute('data-go-settings')) { closePopover(); routeTo('settings'); return; }
});
document.addEventListener('submit', event => {
  if (event.target.id === 'searchForm') {
    event.preventDefault();
    startSearch($('#globalSearch')?.value || '');
  }
  if (event.target.id === 'searchPageForm') {
    event.preventDefault();
    startSearch($('#searchPageInput')?.value || '');
  }
  if (event.target.id === 'createPlaylistForm') {
    event.preventDefault();
    const name = $('#playlistName')?.value.trim() || '';
    if (name && window.RushNative) {
      RushNative.createLibraryPlaylist(name);
      hideDialog();
      page = 'playlists';
      history.replaceState(null, '', '#playlists');
      renderCurrentPage();
    }
  }
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape') {
    if (notificationOpen || userMenuOpen) { closePopover(); event.preventDefault(); }
    if ($('#dialogRoot')?.firstElementChild) { hideDialog(); event.preventDefault(); }
    if ($('#sidebar')?.classList.contains('mobile-open')) { setSidebar(false); event.preventDefault(); }
  }
});
document.addEventListener('click', event => {
  if (notificationOpen && !event.target.closest('#notificationsPopover') && !event.target.closest('#notificationButton')) closePopover();
  if (userMenuOpen && !event.target.closest('#userPopover') && !event.target.closest('#profileButton')) closePopover();
});
window.addEventListener('hashchange', () => handleRoute((location.hash || '#home').slice(1)));
window.addEventListener('resize', () => { syncSidebar(); updateVideoPosition(); });
document.addEventListener('scroll', updateVideoPosition, true);
window.addEventListener('orientationchange', () => requestAnimationFrame(updateVideoPosition));

initApp();
