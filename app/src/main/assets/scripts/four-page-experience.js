/* Four-page navigation and screen composition override the earlier prototype UI. */
let movieResults=[],movieSearchLoaded=false,movieDiscoveryRequested=false,followingFeedRequested=false;
let followingFeedCache={videos:[],music:[],news:[]};
let selectedSubscription='all';
let videoSpeed=1,videoQuality=0,videoCaptions=true;
let currentVideo={id:'',title:'',creator:'',thumbnail:''};
let searchFocusOpen=false;
let lastExploreMarkupKey='';
let explorePriorityImageAssigned=false;
const LOCAL_LISTS={liked:'rush_liked_items_v1',watchlist:'rush_watchlist_v1'};
function safeLocalRead(key){try{return JSON.parse(localStorage.getItem(key)||'[]')}catch(e){return []}}
function safeLocalWrite(key,value){try{localStorage.setItem(key,JSON.stringify(value))}catch(e){}}
function localItems(kind){return safeLocalRead(LOCAL_LISTS[kind]||'')}
function toggleLocalItem(kind,item){
 const list=localItems(kind),index=list.findIndex(x=>x.id===item.id);
 if(index>=0)list.splice(index,1);else list.unshift(item);
 safeLocalWrite(LOCAL_LISTS[kind],list.slice(0,100));
}
function isLocalItem(kind,id){return localItems(kind).some(x=>x.id===id)}
function searchHistory(){return safeLocalRead('rush_search_history_v1').slice(0,8)}
function rememberSearch(query){
 const q=String(query||'').trim().slice(0,120);if(!q)return;
 const items=[q,...searchHistory().filter(x=>String(x).toLowerCase()!==q.toLowerCase())].slice(0,8);
 safeLocalWrite('rush_search_history_v1',items);
}
function creatorInitial(name){return (String(name||'R').trim().replace(/^@/,'')[0]||'R').toUpperCase()}
function creatorAvatar(name,providedThumbnail=""){
 const followed=(prefs.sources||[]).find(source=>(source.kind==="channel"||source.kind==="artist")&&source.name.toLowerCase()===String(name||"").toLowerCase());
 const thumbnail=safeUrl(providedThumbnail)||safeUrl(followed&&followed.thumbnail);
 return '<span class="creator-avatar" aria-hidden="true">'+(thumbnail?'<img src="'+esc(thumbnail)+'" alt="" loading="lazy" decoding="async" onerror="this.hidden=true">':esc(creatorInitial(name)))+'</span>';
}
function videoTile(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail),creator=x.artist||x.creator||'YouTube';
 const priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 return '<article class="media-tile"><button class="home-media-open" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'" aria-label="Watch '+esc(x.title)+'"><span class="media-art video">'+(thumb?'<img src="'+esc(thumb)+'" alt="" width="190" height="107" decoding="async"'+priority+' onerror="this.hidden=true">':'')+'<span class="media-kind">VIDEO</span><span class="media-play">'+ic('play')+'</span></span></button><span class="media-copy">'+creatorAvatar(creator,x.creatorThumbnail)+'<span><b>'+esc(x.title)+'</b><span>'+esc(creator)+(x.duration?' · '+esc(x.duration):'')+'</span></span></span></article>';
}
function movieTile(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail),creator=x.artist||x.creator||'YouTube';
 const priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 return '<article class="media-tile movie"><button class="home-media-open" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'" aria-label="Play '+esc(x.title)+'"><span class="media-art movie">'+(thumb?'<img src="'+esc(thumb)+'" alt="" width="132" height="198" decoding="async"'+priority+' onerror="this.hidden=true">':'')+'<span class="media-kind">FILM</span><span class="media-play">'+ic('play')+'</span></span></button><span class="media-copy"><span><b>'+esc(x.title)+'</b><span>'+esc(creator)+'</span></span></span></article>';
}
function musicTile(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail),artist=x.artist||'Unknown artist';
 const priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 return '<article class="media-tile music"><button class="home-media-open" data-track="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-artist="'+esc(artist)+'" data-thumbnail="'+esc(thumb)+'" aria-label="Play '+esc(x.title)+'"><span class="media-art music">'+(thumb?'<img src="'+esc(thumb)+'" alt="" width="144" height="144" decoding="async"'+priority+' onerror="this.hidden=true">':'')+'<span class="media-kind">AUDIO</span><span class="media-play">'+ic('play')+'</span></span></button><span class="media-copy"><span><b>'+esc(x.title)+'</b><span>'+esc(artist)+(x.duration?' · '+esc(x.duration):'')+'</span></span></span></article>';
}
function newsShelfCard(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail);
 const priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 return '<button class="news-shelf-card" data-news-url="'+esc(safeUrl(x.url))+'"><img src="'+esc(thumb)+'" alt="" width="108" height="78" decoding="async"'+priority+' referrerpolicy="no-referrer" onerror="this.hidden=true"><span class="news-shelf-copy"><small>'+esc(x.source||'News')+'</small><b>'+esc(x.title)+'</b><span>'+esc(x.date||'Latest report')+'</span></span></button>';
}
function shelf(title,items,renderer,label){
 if(!items||!items.length)return '';
 const rendered=items.map((item,index)=>{
  const priority=!explorePriorityImageAssigned&&!!safeUrl(item.thumbnail);
  if(priority)explorePriorityImageAssigned=true;
  return renderer(item,index,priority);
 }).join('');
 return '<section class="explore-shelf"><div class="shelf-heading"><h2>'+esc(title)+'</h2><small>'+esc(label||String(items.length)+' PICKS')+'</small></div><div class="shelf-row">'+rendered+'</div></section>';
}
function topicShelf(){
 return '<section class="explore-shelf"><div class="shelf-heading"><h2>Explore topics</h2><small>FIND YOUR NEXT INTEREST</small></div><div class="topic-shelf">'+(selectedTopics().slice(0,8).length?selectedTopics().slice(0,8):TOPICS.slice(0,8)).map((x,i)=>'<button class="topic-tile" data-topic-search="'+esc(x)+'"><b>'+esc(x)+'</b><span>Explore stories and videos ↗</span></button>').join('')+'</div></section>';
}
function renderFeedResults(data){
 if(page!=='explore'&&page!=='home')return;
 const expectedMode='forYou';
 if(data&&data.mode&&data.mode!==expectedMode)return;
 homeFeedCache=data||{videos:[],music:[],news:[]};
 const status=$('#homeFeedStatus');
 const count=(homeFeedCache.videos||[]).length+(homeFeedCache.music||[]).length+(homeFeedCache.news||[]).length;
 if(status)status.textContent=homeFeedCache.message||(count?count+' recommendations for you':'Explore picks will appear as your interests and subscriptions grow.');
 if(typeof renderExploreContent==='function')renderExploreContent(homeFeedCache);
}
function libraryEntry(id){
 const item=libraryData.items&&libraryData.items[id];
 return item?Object.assign({id},item):null;
}
function videoHero(x,label="CONTINUE WATCHING"){
 if(!x)return "";
 const thumb=safeUrl(x.thumbnail),creator=x.artist||x.creator||"YouTube";
 return '<section class="explore-hero"><div class="shelf-heading"><h2>'+esc(label)+'</h2><small>RESUME</small></div>'+
 '<button class="hero-media-open" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'" aria-label="Continue watching '+esc(x.title)+'">'+
 '<span class="media-art video">'+(thumb?'<img src="'+esc(thumb)+'" alt="" width="360" height="203" decoding="async" loading="eager" fetchpriority="high" onerror="this.hidden=true">':'')+'<span class="media-play">'+ic("play")+'</span></span></button>'+
 '<span class="media-copy">'+creatorAvatar(creator,x.creatorThumbnail)+'<span><b>'+esc(x.title)+'</b><span>'+esc(creator)+(x.duration?' · '+esc(x.duration):'')+'</span></span></span></section>';
}
function compactNewsRow(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail),priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 return '<button class="news-shelf-card compact" data-news-url="'+esc(safeUrl(x.url))+'">'+
 (thumb?'<img src="'+esc(thumb)+'" alt="" width="96" height="72" decoding="async"'+priority+' referrerpolicy="no-referrer" onerror="this.hidden=true">':'<span class="news-thumb-placeholder">NEWS</span>')+
 '<span class="news-shelf-copy"><small>'+esc(x.source||"News")+'</small><b>'+esc(x.title)+'</b><span>'+esc(x.date||"Latest report")+'</span></span></button>';
}
function renderExploreContent(feed){
 const root=$("#exploreShelves");if(!root)return;
 const videos=feed.videos||[],music=feed.music||[],news=feed.news||[];
 const historyItems=(libraryData.history||[]).slice(0,12).map(libraryEntry).filter(Boolean);
 const historyVideos=historyItems.filter(x=>!x.music),historyMusic=historyItems.filter(x=>x.music);
 const historyIds=new Set(historyItems.map(x=>x.id));
 const recommendedVideos=videos.filter(x=>!historyIds.has(x.id));
 const followedVideos=followingFeedCache.videos||[],followedMusic=followingFeedCache.music||[],followedNews=followingFeedCache.news||[];
 const markupKey=JSON.stringify({
  filter:homeFilter,videos:videos.map(x=>[x.id,x.title,x.thumbnail]),music:music.map(x=>[x.id,x.title,x.thumbnail]),
  news:news.map(x=>[x.url,x.title,x.thumbnail]),movies:movieResults.map(x=>[x.id,x.title,x.thumbnail]),
  followingVideos:followedVideos.map(x=>[x.id,x.title,x.thumbnail]),followingMusic:followedMusic.map(x=>[x.id,x.title,x.thumbnail]),
  followingNews:followedNews.map(x=>[x.url,x.title,x.thumbnail]),history:(libraryData.history||[]).slice(0,12),topics:selectedTopics()
 });
 if(markupKey===lastExploreMarkupKey&&root.childElementCount)return;
 lastExploreMarkupKey=markupKey;explorePriorityImageAssigned=false;
 let html="";
 if(homeFilter==="Movies"){
  const items=movieResults.length?movieResults:videos;
  html=items.length?shelf("Movies & long-form",items,movieTile,"POSTER VIEW"):'<div class="section-empty">Search for a film title to find long-form videos available from the connected provider.</div>';
  root.innerHTML=html;return;
 }
 if(homeFilter==="Videos"){
  const hero=recommendedVideos[0]||videos[0];
  if(hero)html+=videoHero(hero,"Recommended videos");
  const rest=recommendedVideos.length?recommendedVideos.slice(hero===recommendedVideos[0]?1:0):videos.slice(1);
  html+=shelf("More videos",rest,videoTile);
  html+=shelf("From your subscriptions",followedVideos,videoTile,"FOLLOWING");
 }else if(homeFilter==="Music"){
  html+=shelf("Mixes & albums",music,musicTile,"YOUR LISTENING MIX");
  html+=shelf("Recently played",historyMusic,musicTile,"HISTORY");
  html+=shelf("From followed artists",followedMusic,musicTile,"FOLLOWING");
 }else if(homeFilter==="News"){
  if(news.length)html+='<section class="explore-shelf news-list-shelf"><div class="shelf-heading"><h2>Top headlines</h2><small>'+news.length+' STORIES</small></div><div class="news-list">'+news.map((x,i)=>compactNewsRow(x,i,i===0)).join("")+'</div></section>';
  if(followedNews.length)html+='<section class="explore-shelf news-list-shelf"><div class="shelf-heading"><h2>From followed publishers</h2><small>FOLLOWING</small></div><div class="news-list">'+followedNews.map(x=>compactNewsRow(x)).join("")+'</div></section>';
 }else{
  if(historyVideos.length){
   html+=videoHero(historyVideos[0],"Continue watching");
   if(historyVideos.length>1)html+=shelf("Pick up where you left off",historyVideos.slice(1),videoTile,"WATCH HISTORY");
  }
  if(recommendedVideos.length){
   if(!historyVideos.length)html+=videoHero(recommendedVideos[0],"Recommended for you");
   html+=shelf("Suggested for you",recommendedVideos.slice(historyVideos.length?0:1),videoTile,"BASED ON YOUR INTERESTS");
  }
  if(historyMusic.length)html+=shelf("Recently played",historyMusic,musicTile,"LISTEN AGAIN");
  if(music.length)html+=shelf("Mixes & albums",music,musicTile);
  if(movieResults.length)html+=shelf("Featured cinema",movieResults.slice(0,10),movieTile,"MOVIE DISCOVERY");
  if(news.length)html+='<section class="explore-shelf news-list-shelf"><div class="shelf-heading"><h2>Top headlines</h2><small>NEWS</small></div><div class="news-list">'+news.slice(0,5).map((x,i)=>compactNewsRow(x,i,i===0)).join("")+'</div></section>';
  if(followedVideos.length)html+=shelf("From your subscriptions",followedVideos,videoTile,"FOLLOWING");
  if(followedMusic.length)html+=shelf("Latest from followed artists",followedMusic,musicTile,"FOLLOWING");
  if(followedNews.length)html+='<section class="explore-shelf news-list-shelf"><div class="shelf-heading"><h2>Publisher updates</h2><small>FOLLOWING</small></div><div class="news-list">'+followedNews.slice(0,4).map(x=>compactNewsRow(x)).join("")+'</div></section>';
  if(music.length>1||recommendedVideos.length>1){
   html+='<section class="explore-shelf"><div class="shelf-heading"><h2>More to explore</h2><small>YOUR MIX</small></div><div class="explore-mosaic">'+music.slice(0,2).map((x,i)=>musicTile(x,i,i===0)).join("")+(recommendedVideos[1]?videoTile(recommendedVideos[1]):"")+'</div></section>';
  }
  html+=topicShelf();
 }
 if(!html)html='<div class="section-empty">No '+esc(homeFilter.toLowerCase())+' are available yet. Follow a few sources or explore topics in Search.</div>';
 root.innerHTML=html;
}
renderHome = function(){
 const filters=["All","Videos","Music","News","Movies"];
 return '<div class="explore-tabs" role="tablist" aria-label="Explore categories">'+
  filters.map(x=>'<button class="explore-tab" role="tab" data-home-filter="'+x+'" aria-selected="'+(homeFilter===x)+'" aria-pressed="'+(homeFilter===x)+'">'+x+'</button>').join("")+
  '</div><p class="meta pad home-feed-status" id="homeFeedStatus" aria-live="polite">Preparing your picks…</p><div id="exploreShelves"></div>';
};
renderFeedResults = function(data){
 if(page!=='explore'&&page!=='home')return;
 const expectedMode='forYou';
 if(data&&data.mode&&data.mode!==expectedMode)return;
 const feed=data||{videos:[],music:[],news:[]};
 homeFeedCache=feed;
 const count=(feed.videos||[]).length+(feed.music||[]).length+(feed.news||[]).length;
 const status=$('#homeFeedStatus');if(status)status.textContent=data.message||(count?count+' picks selected for you':'Your recommendations will appear here as you explore and follow sources.');
 renderExploreContent(feed);
};
renderSearchPage = function(){
 const history=searchHistory();
 const trending=(selectedTopics().length?selectedTopics():TOPICS).slice(0,10);
 return '<label class="search-box">'+ic("search")+'<input id="q" type="search" placeholder="Search Rush" aria-label="Search Rush" autocomplete="off"><button class="ib" type="button" data-search-clear aria-label="Clear search">×</button></label>'+
 '<div id="searchIdle"><div class="search-idle-heading">Trending topics</div><div class="trend-row">'+trending.map(x=>'<button class="trend-chip" data-topic-search="'+esc(x)+'">'+esc(x)+'</button>').join("")+'</div>'+
 '<div class="search-idle-heading">Your subscriptions</div><div class="source-rail">'+(prefs.sources.length?prefs.sources.slice(0,12).map(x=>'<button class="source-avatar" data-search-source="'+esc(x.kind)+'" data-source-name="'+esc(x.name)+'"><span class="avatar">'+(safeUrl(x.thumbnail)?'<img src="'+esc(safeUrl(x.thumbnail))+'" alt="" loading="lazy" decoding="async">':esc(creatorInitial(x.name)))+'</span><b>'+esc(x.name)+'</b></button>').join(""):'<span class="meta">Follow channels and publishers to see them here.</span>')+'</div>'+
 '<div class="search-idle-heading">Browse categories</div><div class="trend-row">'+["Videos","Music","Movies","News","Technology","Science","Sports","Culture"].map(x=>'<button class="trend-chip" data-topic-search="'+esc(x)+'">'+x+'</button>').join("")+'</div>'+
 '<div class="search-idle-heading">Recent searches</div><div class="search-history" id="searchHistory">'+(history.length?history.map(x=>'<button class="search-history-item" data-search-history="'+esc(x)+'">◷ <span>'+esc(x)+'</span></button>').join(""):'<span class="meta">Your recent searches will appear here.</span>')+'</div></div>'+
 '<div class="chips search-filters"><button class="chip" data-search-filter="all" aria-pressed="true">All</button><button class="chip" data-search-filter="videos" aria-pressed="false">Videos</button><button class="chip" data-search-filter="music" aria-pressed="false">Music</button><button class="chip" data-search-filter="news" aria-pressed="false">News</button><button class="chip" data-search-filter="channels" aria-pressed="false">Channels</button><button class="chip" data-search-filter="playlists" aria-pressed="false">Playlists</button></div>'+
 '<p class="meta pad" id="unifiedStatus">Search across Rush.</p><div id="unifiedResults"></div><div id="publicPlaylistContent"></div>';
};
renderSubscribedPage = function(){
 const sources=prefs.sources||[];
 return '<div class="source-rail subscriptions" id="subscriptionSources">'+
 '<button class="source-avatar all-source" data-subscription-all aria-pressed="'+(selectedSubscription==="all")+'"><span class="avatar">ALL</span><b>All</b></button>'+
 (sources.length?sources.map((x,i)=>'<button class="source-avatar" data-subscription-source="'+i+'" aria-pressed="'+(selectedSubscription===x.name)+'"><span class="avatar">'+(safeUrl(x.thumbnail)?'<img src="'+esc(safeUrl(x.thumbnail))+'" alt="" loading="lazy" decoding="async">':esc(creatorInitial(x.name)))+'</span><b>'+esc(x.name)+'</b></button>').join(""):'<button class="source-avatar" data-open-search><span class="avatar">+</span><b>Find creators</b></button>')+
 '</div><div class="subscription-feed"><div class="subscription-toolbar"><b id="subscriptionTitle">'+(selectedSubscription==="all"?"All subscriptions":esc(selectedSubscription))+'</b><span><small id="subscriptionStatus">LATEST UPDATES</small></span></div><div id="subscriptionFeed" class="subscription-results"><p class="meta pad">Loading your subscriptions…</p></div></div>';
};
function renderSubscribedFeed(data){
 const root=$("#subscriptionFeed");if(!root)return;
 const queues=[
  (data.videos||[]).map(x=>({kind:"video",item:x})),
  (data.music||[]).map(x=>({kind:"music",item:x})),
  (data.news||[]).map(x=>({kind:"news",item:x}))
 ];
 const ordered=[];
 while(queues.some(queue=>queue.length))queues.forEach(queue=>{if(queue.length)ordered.push(queue.shift())});
 const items=ordered.map(({kind,item})=>kind==="video"?videoRow(item):kind==="music"?musicRow(item):newsCard(item)).join("");
 root.innerHTML=items||'<div class="section-empty">No recent posts from your subscriptions yet. Follow a channel, artist or publisher to build this feed.</div>';
 const status=$("#subscriptionStatus");if(status)status.textContent=ordered.length+" UPDATES";
 const title=$("#subscriptionTitle");if(title)title.textContent=selectedSubscription==="all"?"All subscriptions":selectedSubscription;
}
function subscriptionSourceSearch(index){
 const source=prefs.sources[index];if(!source)return;
 selectedSubscription=source.name;
 document.querySelectorAll('#subscriptionSources [data-subscription-all],#subscriptionSources [data-subscription-source]').forEach(x=>x.setAttribute('aria-pressed',String(x.dataset.subscriptionSource!==undefined&&Number(x.dataset.subscriptionSource)===index)));
 const title=$('#subscriptionTitle');if(title)title.textContent=source.name;
 const root=$('#subscriptionFeed');if(root)root.innerHTML='<p class="meta pad">Loading '+esc(source.name)+'…</p>';
 if(!window.RushNative)return;
 if(source.kind==='artist')RushNative.searchMusic(source.name);
 else if(source.kind==='news')RushNative.searchNews(source.name);
 else RushNative.searchVideos(source.name);
}
function renderLibraryOverviewMarkup(){
 const history=(libraryData.history||[]).slice(0,8).map(libraryEntry).filter(Boolean);
 const saved=(libraryData.saved||[]).slice(0,8).map(libraryEntry).filter(Boolean);
 const watchlist=localItems("watchlist").slice(0,8);
 const playlists=libraryData.playlists||[];
 let html="";
 if(history.length)html+=shelf("History",history,(x,i,p)=>x.music?musicTile(Object.assign({artist:x.creator},x),i,p):videoTile(x,i,p),"RECENTLY PLAYED");
 if(saved.length)html+=shelf("Saved items",saved,(x,i,p)=>x.music?musicTile(Object.assign({artist:x.creator},x),i,p):videoTile(x,i,p),"YOUR LIBRARY");
 if(watchlist.length)html+=shelf("Watchlist",watchlist,movieTile,"SAVED FOR LATER");
 if(playlists.length)html+='<section class="explore-shelf"><div class="shelf-heading"><h2>Playlists</h2><small>'+playlists.length+' COLLECTIONS</small></div><div class="playlist-shelf">'+playlists.map(p=>'<button class="playlist-shelf-tile" data-library-view="playlists"><span class="playlist-shelf-art">≡</span><b>'+esc(p.name||"Playlist")+'</b><small>'+((p.items||[]).length)+' ITEMS</small></button>').join("")+'</div></section>';
 return html||'<div class="section-empty">Your history, saved media and playlists will appear here as you use Rush.</div>';
}
function renderLibraryOverview(){const root=$("#libraryOverview");if(root)root.innerHTML=renderLibraryOverviewMarkup()}
renderLibraryPage = function(){
 const views=[["saved","Saved items","♡","Videos and music you kept"],["history","History","◷","What you watched and played"],["watchlist","Watchlist","▣","Films and videos to watch later"],["liked","Liked","♥","Your liked media"],["playlists","Playlists","≡","Your collections"],["downloads","Downloads","⇩","Available offline"],["queue","Queue","♫","Up next"]];
 return '<div class="library-profile"><button class="library-profile-main" data-open-profile><span class="avatar">'+esc((rushProfile.displayName||"R").trim().slice(0,1).toUpperCase())+'</span><span class="profile-copy"><b>'+esc(rushProfile.displayName||"Your Rush profile")+'</b><span>'+(rushProfile.handle?"@"+esc(rushProfile.handle):"Saved locally on this device")+'</span></span><i>EDIT ›</i></button><button class="ib" data-open-settings aria-label="Settings">⚙</button></div>'+
 '<div id="libraryOverview">'+renderLibraryOverviewMarkup()+'</div>'+
 '<div class="library-menu">'+views.map(([key,name,icon,desc])=>'<button data-library-view="'+key+'"><span class="hardware-icon">'+icon+'</span><span class="library-menu-copy"><b>'+name+'</b><small>'+desc+'</small></span><span>›</span></button>').join("")+
 '<button data-open-settings><span class="hardware-icon">⚙</span><span class="library-menu-copy"><b>Settings</b><small>Permissions, playback and privacy</small></span><span>›</span></button></div>'+
 '<p class="meta pad" id="libraryStatus">Your saved media and listening history.</p><div class="chips library-tabs">'+views.map(([key,name])=>'<button class="chip" data-library-view="'+key+'" aria-pressed="'+(libraryView===key)+'">'+name+'</button>').join("")+'</div><div id="libraryContent"></div>';
};
const renderOriginalLibraryContent=renderLibraryContent;
renderLibraryContent = function(){
 if(libraryView==='liked'||libraryView==='watchlist'){
  const items=localItems(libraryView),root=$('#libraryContent');if(!root)return;
  document.querySelectorAll('[data-library-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.libraryView===libraryView)));
  root.innerHTML=items.length?items.map(x=>'<div class="local-list-row"><img src="'+esc(safeUrl(x.thumbnail))+'" alt="" onerror="this.hidden=true"><span class="copy"><b>'+esc(x.title)+'</b><span>'+esc(x.creator||'')+'</span></span><button class="chip" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(x.creator||'')+'" data-thumbnail="'+esc(x.thumbnail||'')+'">PLAY</button></div>').join(''):'<div class="empty-state"><b>'+(libraryView==='liked'?'No liked items yet':'Your watchlist is empty')+'</b><p>Use the actions on a video to save it here.</p></div>';
  return;
 }
 renderOriginalLibraryContent();
};
renderWatchPage = function(){
 const x=currentVideo;
 const liked=isLocalItem('liked',x.id),saved=isLocalItem('watchlist',x.id);
 return '<div class="watch-toolbar"><span>NOW PLAYING</span><button class="chip" data-video-fullscreen>FULL SCREEN ↗</button></div>'+
 '<div class="watch-player"><div class="video-player-host" id="videoPlayerHost"></div><div class="watch-buffer-overlay" id="videoBuffering" hidden><span class="buffer-indicator"><i class="buffer-spinner"></i><b>BUFFERING</b></span></div></div>'+
 '<section class="watch-details"><h2 id="watchTitle">'+esc(x.title||'Video loading…')+'</h2>'+
 '<div class="watch-channel">'+creatorAvatar(x.creator)+'<span class="watch-channel-copy"><b>'+esc(x.creator||'Creator')+'</b><span>Video creator</span></span><button class="chip" data-follow-kind="channel" data-source-id="'+esc(x.creator||'')+'" data-source-name="'+esc(x.creator||'')+'">Subscribe</button></div>'+
 '<div class="watch-actions"><button class="watch-action '+(liked?'on':'')+'" data-local-like>♥ '+(liked?'Liked':'Like')+'</button><button class="watch-action '+(saved?'on':'')+'" data-local-watchlist>＋ '+(saved?'In watchlist':'Watch later')+'</button><button class="watch-action" data-video-comments>Comments ↗</button><button class="watch-action" data-option-menu="speed">1× Speed</button><button class="watch-action" data-option-menu="quality">Quality</button><button class="watch-action" data-option-menu="captions">CC</button></div>'+
 '<div class="video-options" id="videoOptions" hidden></div><p class="meta" id="videoPlaybackStatus" aria-live="polite">Video controls and playback options are available here.</p></section>'+
 '<div class="watch-suggestions"><div class="shelf-heading"><h2>Up next</h2><small>MORE TO WATCH</small></div><div id="watchSuggestions">'+(homeFeedCache.videos||[]).filter(v=>v.id!==x.id).map(videoRow).join('')+'</div></div>';
};
renderPlayerPage = function(){
 const x=mediaState;
 return '<section class="player-screen"><div class="player-head"><button class="chip" data-player-back>← Back to Explore</button><span class="stk">RUSH DECK</span></div>'+
 '<div class="music-deck"><div class="deck-label"><span>COMPACT DISC PLAYER</span><span>MODE · AUDIO</span></div>'+
 '<div class="deck-display"><div class="deck-disc-wrap"><div class="deck-disc" id="deckDisc"></div><div class="deck-arm"></div></div><div class="deck-info"><h2 id="playerTitle">'+esc(x.title||'Choose a track')+'</h2><p id="playerArtist">'+esc(x.artist||'Your music collection')+'</p><div class="deck-led"><i></i><i></i><i></i></div><div class="deck-vu"></div><div class="music-buffer-label" id="musicBuffering" hidden><i class="buffer-spinner"></i>BUFFERING AUDIO</div></div></div>'+
 '<div class="deck-controls"><button class="deck-control" data-player-prev aria-label="Previous track" disabled>|◀</button><button id="playerToggle" class="deck-control main" data-player-toggle aria-label="Play or pause" disabled>'+(x.isPlaying?'Ⅱ':'▶')+'</button><button class="deck-control" data-player-next aria-label="Next track" disabled>▶|</button></div>'+
 '<p class="meta pad" id="playerStatus">'+(x.hasItem?'NOW PLAYING':'Nothing is playing yet.')+'</p>'+
 '<div class="player-progress"><input id="playerSeek" type="range" min="0" max="1000" step="1" value="0" aria-label="Seek through track"><div class="player-time"><span id="playerElapsed">0:00</span><span id="playerDuration">0:00</span></div></div></div>'+
 '<div class="player-utilities"><button class="chip" data-player-lyrics-toggle disabled>Lyrics</button><button class="chip" data-sleep-timer>Sleep timer</button><button class="chip" data-player-queue>Up next</button><button class="chip" id="playerSave" data-player-save disabled>Save track</button><button class="chip" id="playerModeToggle" data-player-watch-video hidden>Watch video</button></div><div id="sleepTimerOptions" class="video-options" hidden><button class="video-option" data-sleep-choice="15">15 min</button><button class="video-option" data-sleep-choice="30">30 min</button><button class="video-option" data-sleep-choice="45">45 min</button><button class="video-option" data-sleep-choice="60">60 min</button><button class="video-option" data-sleep-choice="0">Cancel</button></div>'+
 '<div class="lyrics-section" id="playerLyrics" hidden><h2>Lyrics</h2><p class="meta" id="playerLyricsStatus">Synced lyrics</p><div class="lyrics-panel" id="playerLyricsPanel"></div></div>';
};
renderNewsPage = function(){homeFilter='News';return renderHome()};
renderVideoPage = function(){homeFilter='Videos';return renderHome()};
renderMusicPage = function(){homeFilter='Music';return renderHome()};
const renderTopicsOriginal=renderTopicsPage;
renderTopicsPage = function(){return pageTop('Settings','your preferences')+renderTopicsOriginal()};
R.explore=renderHome;R.home=renderHome;R.search=renderSearchPage;R.subscribed=renderSubscribedPage;R.library=renderLibraryPage;R.watch=renderWatchPage;R.player=renderPlayerPage;R.topics=renderTopicsPage;
R.videos=renderVideoPage;R.music=renderMusicPage;R.news=renderNewsPage;
go = function(){
 page=(location.hash||'#explore').slice(1);
 if(page==='home')page='explore';
 if(!R[page])page='explore';
 if(page==='subscribed')selectedSubscription='all';
 loadFeedPreferences();
 $('#v').innerHTML=R[page]();
 const activePage=page==='watch'?'explore':page==='player'?'explore':page;
 document.querySelectorAll('#nav a').forEach(a=>a.dataset.k===activePage?a.setAttribute('aria-current','page'):a.removeAttribute('aria-current'));
 $('.app').classList.toggle('player-open',page==='player');
 $('#sc').scrollTop=0;navPosition();indicator();
 if(page==='topics')updateTopicOptions();
 if(page==='library')loadLibrary();
 if(page==='explore'){
  followingFeedRequested=false;
  loadHomeFeed();
  if(window.RushNative)RushNative.loadLibrary();
 }else if(page==='subscribed'){
  if(window.RushNative)RushNative.loadHomeFeed('following');
 }
 if(page==='search'){
  window.setTimeout(()=>{const input=$('#q');if(input){input.focus();searchFocusOpen=true;renderSearchHistory() }},90);
 }
 if(page==='player'&&window.RushNative){RushNative.loadCurrentPlayback();RushNative.loadCurrentMusicLyrics()}
 if(page==='watch'&&window.RushNative){RushNative.loadCurrentPlayback();if(currentVideo.id)RushNative.searchVideos(currentVideo.title||currentVideo.creator)}
 const dock=$('#videoMiniDock');if(dock){const show=page!=='watch'&&mediaState.isVideo&&mediaState.hasItem;dock.hidden=!show;if(show){const title=$('#videoMiniTitle');if(title)title.textContent=mediaState.title||'Now playing'}}
 window.setTimeout(()=>window.rushUpdateVideoPosition&&window.rushUpdateVideoPosition(),120);
 if(window.RushNative)RushNative.loadCurrentPlayback();
};
function renderSearchHistory(){
 const root=$('#searchHistory');if(!root)return;
 const items=searchHistory();
 root.innerHTML=items.length?items.map(x=>'<button class="search-history-item" data-search-history="'+esc(x)+'">◷ <span>'+esc(x)+'</span></button>').join('')+'<button class="trend-chip" data-search-history-clear>Clear history</button>':'<span class="meta">No recent searches.</span>';
}
function runUnifiedSearch(){
 const input=$('#q'),q=input?.value.trim();if(!q)return;
 rememberSearch(q);unifiedQuery=q;Object.keys(unifiedData).forEach(k=>unifiedData[k]=[]);
 const idle=$('#searchIdle');if(idle)idle.hidden=true;
 const status=$('#unifiedStatus');if(status)status.textContent='Searching videos, music, news, channels and playlists…';
 const root=$('#unifiedResults');if(root)root.innerHTML='';
 if(window.RushNative){RushNative.searchVideos(q);RushNative.searchMusic(q);RushNative.searchNews(q);RushNative.searchChannels(q);RushNative.searchPlaylists(q)}
}
window.rushHomeFeedResults = function(data){
 if(page==='subscribed'&&data&&data.mode==='following'){if(selectedSubscription==='all')renderSubscribedFeed(data);return}
 if(page==='explore'&&data&&data.mode==='following'){followingFeedCache=data;renderExploreContent(homeFeedCache);return;}
 if(page==='explore'&&data&&data.mode==='forYou'&&data.fetchedAt&&!followingFeedRequested){
  followingFeedRequested=true;
  window.setTimeout(()=>{if(page==='explore'&&window.RushNative)RushNative.loadHomeFeed('following')},120);
 }
 renderFeedResults(data);
};
const previousVideoResults=window.rushVideoSearchResults;
window.rushVideoSearchResults=function(items){
 if(movieDiscoveryRequested){
  movieDiscoveryRequested=false;
  if(page==='explore'){movieResults=items||[];movieSearchLoaded=true;renderExploreContent(homeFeedCache)}
  return;
 }
 if(page==='explore'&&homeFilter==='Movies'){movieResults=items||[];renderExploreContent(homeFeedCache);return}
 if(page==='subscribed'&&selectedSubscription!=='all'){
  const root=$('#subscriptionFeed');if(root)root.innerHTML=(items||[]).map(videoRow).join('')||'<p class="meta pad">No videos found for this creator.</p>';return;
 }
 if(page==='watch'){
  const root=$('#watchSuggestions');if(root)root.innerHTML=(items||[]).filter(x=>x.id!==currentVideo.id).slice(0,12).map(videoRow).join('');return;
 }
 if(previousVideoResults)previousVideoResults(items);
};
const previousVideoSearchError=window.rushVideoSearchError;
window.rushVideoSearchError=function(error){
 if(movieDiscoveryRequested){movieDiscoveryRequested=false;movieSearchLoaded=false;if(page==='explore')renderExploreContent(homeFeedCache);return}
 if(previousVideoSearchError)previousVideoSearchError(error);
};
const previousMusicResults=window.rushMusicSearchResults;
window.rushMusicSearchResults=function(items){
 if(page==='subscribed'&&selectedSubscription!=='all'){const root=$('#subscriptionFeed');if(root)root.innerHTML=(items||[]).map(musicRow).join('');return}
 if(previousMusicResults)previousMusicResults(items);
};
const previousNewsResults=window.rushNewsResults;
window.rushNewsResults=function(items){
 if(page==='subscribed'&&selectedSubscription!=='all'){const root=$('#subscriptionFeed');if(root)root.innerHTML=(items||[]).map(newsCard).join('');return}
 if(previousNewsResults)previousNewsResults(items);
};
const previousFeedPreferences=window.rushFeedPreferences;
window.rushFeedPreferences=function(data){
 followingFeedRequested=false;
 followingFeedCache={videos:[],music:[],news:[]};
 if(previousFeedPreferences)previousFeedPreferences(data);
};
const previousLibraryData=window.rushLibraryData;
window.rushLibraryData=function(data){libraryData=data||libraryData;if(page==='explore')renderExploreContent(homeFeedCache);else if(page==='library'){renderLibraryOverview();renderLibraryContent()}};
const updateMiniOriginal=updateMini;
updateMini=function(){updateMiniOriginal();const show=!!mediaState.hasItem&&!!mediaState.isMusic;const mini=$('#mini');if(mini){mini.classList.toggle('on',show);mini.inert=!show;mini.setAttribute('aria-hidden',String(!show))}$('.app').classList.toggle('has-mini',show)};
const previousCurrentPlayback=window.rushCurrentPlayback;
window.rushCurrentPlayback=function(data){
 if(previousCurrentPlayback)previousCurrentPlayback(data);
 const disc=$('#deckDisc');if(disc)disc.classList.toggle('paused',!data.isPlaying);
 const dock=$('#videoMiniDock');if(dock){dock.hidden=!(page!=='watch'&&data.isVideo&&data.hasItem);const title=$('#videoMiniTitle');if(title)title.textContent=data.title||'Now playing'}
 window.setTimeout(()=>window.rushUpdateVideoPosition&&window.rushUpdateVideoPosition(),90);
};
const previousVideoState=window.rushVideoPlaybackState;
window.rushVideoPlaybackState=function(state){
 if(previousVideoState)previousVideoState(state);
 if(state.state==='playing'||state.state==='loading'){
  mediaState.isVideo=true;mediaState.isMusic=false;mediaState.hasItem=true;
  if(page==='watch'){
   const t=$('#videoPlaybackStatus');if(t)t.textContent=state.state==='playing'?'STARTING PLAYBACK · '+(state.title||currentVideo.title):'Preparing video…';
   if(state.state==='loading'&&window.rushVideoPreparation)window.rushVideoPreparation();
  }
  const dock=$('#videoMiniDock');if(dock){dock.hidden=page==='watch';const title=$('#videoMiniTitle');if(title)title.textContent=state.title||currentVideo.title||'Now playing'}
  window.setTimeout(()=>window.rushUpdateVideoPosition&&window.rushUpdateVideoPosition(),220);
 }else if(state.state==='error'){
  if(page==='watch'&&window.rushVideoBufferingError)window.rushVideoBufferingError(state.message||'Could not start playback. Check your connection.');
 }
};
window.rushUpdateVideoPosition=function(){
 if(!window.RushNative||!mediaState.isVideo||!mediaState.hasItem)return;
 if(page==='watch'){
  const host=$('#videoPlayerHost');if(!host)return;
  const r=host.getBoundingClientRect();
  RushNative.updateVideoPosition(r.left,r.top,r.width,r.height,false);
 }else{
  const dock=$('#videoMiniDock');if(dock)dock.hidden=false;
  const host=$('#videoMiniHost');if(!host)return;
  const r=host.getBoundingClientRect();
  RushNative.updateVideoPosition(r.left,r.top,r.width,r.height,true);
 }
};
document.addEventListener('focusin',e=>{
 if(e.target&&e.target.id==='q'){searchFocusOpen=true;const idle=$('#searchIdle');if(idle)idle.hidden=false;renderSearchHistory()}
});
document.addEventListener('input',e=>{
 if(e.target&&e.target.id==='q'){const idle=$('#searchIdle');if(idle)idle.hidden=false}
});
document.addEventListener('click',e=>{
 const b=e.target.closest('button');if(!b)return;
 const handled=b.hasAttribute('data-home-filter')||b.hasAttribute('data-open-library')||b.hasAttribute('data-open-search')||b.hasAttribute('data-open-settings')||b.hasAttribute('data-video-id')||b.hasAttribute('data-track')||b.hasAttribute('data-mini-expand')||b.hasAttribute('data-video-close')||b.hasAttribute('data-video-fullscreen')||b.hasAttribute('data-video-comments')||b.hasAttribute('data-local-like')||b.hasAttribute('data-local-watchlist')||b.hasAttribute('data-subscription-source')||b.hasAttribute('data-topic-search')||b.hasAttribute('data-search-history')||b.hasAttribute('data-search-clear')||b.hasAttribute('data-search-source')||b.hasAttribute('data-option-menu')||b.hasAttribute('data-video-speed-choice')||b.hasAttribute('data-video-quality-choice')||b.hasAttribute('data-video-cc-toggle')||b.hasAttribute('data-sleep-timer')||b.hasAttribute('data-sleep-choice')||b.hasAttribute('data-player-back')||b.hasAttribute('data-player-toggle')||b.hasAttribute('data-player-prev')||b.hasAttribute('data-player-next')||b.hasAttribute('data-player-save')||b.hasAttribute('data-player-queue')||b.hasAttribute('data-player-lyrics-toggle')||(b.hasAttribute('data-library-view')&&['liked','watchlist'].includes(b.dataset.libraryView));
 if(handled){e.preventDefault();e.stopImmediatePropagation()}
 if(b.hasAttribute('data-home-filter')){
  homeFilter=b.dataset.homeFilter||'All';
  document.querySelectorAll('[data-home-filter]').forEach(x=>{x.setAttribute('aria-pressed',String(x===b));x.setAttribute('aria-selected',String(x===b))});
  if(homeFilter==='Movies'&&window.RushNative&&!movieSearchLoaded){
   movieResults=[];movieDiscoveryRequested=true;
   const root=$('#exploreShelves');if(root)root.innerHTML='<p class="meta pad">Finding films on YouTube…</p>';
   RushNative.searchVideos('full length movie film');
  }
  else renderExploreContent(homeFeedCache);
  return;
 }
 if(b.hasAttribute('data-open-library')){location.hash='#library';return}
 if(b.hasAttribute('data-open-search')){location.hash='#search';return}
 if(b.hasAttribute('data-open-settings')){location.hash='#settings';return}
 if(b.hasAttribute('data-video-id')){
  currentVideo={id:b.dataset.videoId||'',title:b.dataset.title||'Video',creator:b.dataset.creator||'',thumbnail:b.dataset.thumbnail||''};
  mediaState.hasItem=true;mediaState.isVideo=true;mediaState.isMusic=false;
  location.hash='#watch';
  if(window.RushNative){RushNative.playVideo(currentVideo.id,currentVideo.title,currentVideo.creator,currentVideo.thumbnail)}
  return;
 }
 if(b.hasAttribute('data-track')){
  const d=b.dataset;location.hash='#player';
  if(window.RushNative)RushNative.playMusic(d.track,d.title||'',d.artist||'',d.thumbnail||'');
  return;
 }
 if(b.hasAttribute('data-mini-expand')){location.hash='#watch';window.setTimeout(()=>window.rushUpdateVideoPosition&&window.rushUpdateVideoPosition(),100);return}
 if(b.hasAttribute('data-video-close')){
  if(window.RushNative&&RushNative.closeVideo)RushNative.closeVideo();
  const dock=$('#videoMiniDock');if(dock)dock.hidden=true;
  mediaState.isVideo=false;mediaState.hasItem=false;return;
 }
 if(b.hasAttribute('data-video-fullscreen')){if(window.RushNative)RushNative.setVideoFullscreen(true);return}
 if(b.hasAttribute('data-option-menu')){
  const root=$('#videoOptions');if(!root)return;
  const kind=b.dataset.optionMenu;
  const speedChoices=[0.5,0.75,1,1.25,1.5,1.75,2];
  const qualityChoices=[['Auto',0],['1080p',1080],['720p',720],['480p',480],['360p',360]];
  if(kind==='speed')root.innerHTML=speedChoices.map(x=>'<button class="video-option" data-video-speed-choice="'+x+'" aria-pressed="'+(videoSpeed===x)+'">'+x+'×</button>').join('');
  else if(kind==='quality')root.innerHTML=qualityChoices.map(([label,value])=>'<button class="video-option" data-video-quality-choice="'+value+'" aria-pressed="'+(videoQuality===value)+'">'+label+'</button>').join('');
  else root.innerHTML='<button class="video-option" data-video-cc-toggle>'+ (videoCaptions?'Turn captions off':'Turn captions on')+'</button>';
  root.hidden=false;return;
 }
 if(b.hasAttribute('data-video-speed-choice')){
  videoSpeed=Number(b.dataset.videoSpeedChoice)||1;if(window.RushNative)RushNative.setPlaybackSpeed(videoSpeed);
  const label=$('[data-option-menu="speed"]');if(label)label.textContent=videoSpeed+'× Speed';const root=$('#videoOptions');if(root)root.hidden=true;return;
 }
 if(b.hasAttribute('data-video-quality-choice')){
  videoQuality=Number(b.dataset.videoQualityChoice)||0;if(window.RushNative)RushNative.setVideoQuality(videoQuality);
  const label=$('[data-option-menu="quality"]');if(label)label.textContent=videoQuality?videoQuality+'p':'Auto quality';const root=$('#videoOptions');if(root)root.hidden=true;return;
 }
 if(b.hasAttribute('data-video-cc-toggle')){
  videoCaptions=!videoCaptions;if(window.RushNative)RushNative.setVideoCaptionsEnabled(videoCaptions);
  const label=$('[data-option-menu="captions"]');if(label)label.textContent=videoCaptions?'CC On':'CC Off';const root=$('#videoOptions');if(root)root.hidden=true;return;
 }
 if(b.hasAttribute('data-sleep-timer')){
  const root=$('#sleepTimerOptions');if(root)root.hidden=!root.hidden;return;
 }
 if(b.hasAttribute('data-sleep-choice')){
  const minutes=Number(b.dataset.sleepChoice)||0;if(window.RushNative)RushNative.setSleepTimer(minutes);
  const status=$('#playerStatus');if(status)status.textContent=minutes?'Sleep timer · '+minutes+' min':'Sleep timer cancelled';
  const root=$('#sleepTimerOptions');if(root)root.hidden=true;return;
 }
 if(b.hasAttribute('data-local-like')||b.hasAttribute('data-local-watchlist')){
  const kind=b.hasAttribute('data-local-like')?'liked':'watchlist';
  toggleLocalItem(kind,{id:currentVideo.id,title:currentVideo.title,creator:currentVideo.creator,thumbnail:currentVideo.thumbnail});
  b.classList.toggle('on',isLocalItem(kind,currentVideo.id));
  b.textContent=kind==='liked'?(isLocalItem(kind,currentVideo.id)?'♥ Liked':'♥ Like'):(isLocalItem(kind,currentVideo.id)?'✓ In watchlist':'＋ Watch later');
  return;
 }
 if(b.hasAttribute('data-subscription-source')){subscriptionSourceSearch(Number(b.dataset.subscriptionSource));return}
 if(b.hasAttribute('data-topic-search')){
  const value=b.dataset.topicSearch||'';location.hash='#search';
  window.setTimeout(()=>{const input=$('#q');if(input){input.value=value;runUnifiedSearch()}},150);return;
 }
 if(b.hasAttribute('data-search-history')){
  const value=b.dataset.searchHistory||'';const input=$('#q');if(input){input.value=value;runUnifiedSearch()}return;
 }
 if(b.hasAttribute('data-search-history-clear')){
  safeLocalWrite('rush_search_history_v1',[]);renderSearchHistory();return;
 }
 if(b.hasAttribute('data-subscription-all')){
  selectedSubscription='all';
  document.querySelectorAll('#subscriptionSources [data-subscription-all],#subscriptionSources [data-subscription-source]').forEach(x=>x.setAttribute('aria-pressed',String(x.hasAttribute('data-subscription-all'))));
  const title=$('#subscriptionTitle');if(title)title.textContent='All subscriptions';
  const root=$('#subscriptionFeed');if(root)root.innerHTML='<p class="meta pad">Loading all subscriptions…</p>';
  if(window.RushNative)RushNative.loadHomeFeed('following');return;
 }
 if(b.hasAttribute('data-search-clear')){
  const input=$('#q');if(input){input.value='';input.focus()}const root=$('#unifiedResults');if(root)root.innerHTML='';return;
 }
 if(b.hasAttribute('data-search-source')){
  const name=b.dataset.sourceName||'';const input=$('#q');if(input){input.value=name;runUnifiedSearch()}return;
 }
 if(b.hasAttribute('data-library-view')&&(b.dataset.libraryView==='liked'||b.dataset.libraryView==='watchlist')){
  libraryView=b.dataset.libraryView;renderLibraryContent();return;
 }
 if(b.hasAttribute('data-player-back')){homeFilter='Music';location.hash='#explore';return}
 if(b.hasAttribute('data-player-toggle')){if(window.RushNative)RushNative.togglePlayback();return}
 if(b.hasAttribute('data-player-prev')){if(window.RushNative)RushNative.skipPrevious();return}
 if(b.hasAttribute('data-player-next')){if(window.RushNative)RushNative.skipNext();return}
 if(b.hasAttribute('data-player-save')){if(window.RushNative&&mediaState.hasItem&&mediaState.isMusic)RushNative.saveLibraryItem(mediaState.sourceId||mediaState.mediaId,mediaState.title||'',mediaState.artist||'',true,mediaState.artwork||'');return}
 if(b.hasAttribute('data-player-queue')){libraryView='queue';location.hash='#library';window.setTimeout(()=>renderLibraryContent(),100);return}
 if(b.hasAttribute('data-player-lyrics-toggle')){
  const panel=$('#playerLyrics');if(panel){panel.hidden=!panel.hidden;if(!panel.hidden&&window.RushNative)RushNative.loadCurrentMusicLyrics()}return;
 }
},true);
window.rushSleepTimerState=state=>{const status=$('#playerStatus');if(status)status.textContent=state.finished?'Sleep timer finished · playback paused':state.active?'Sleep timer · '+state.minutes+' min':'Sleep timer off'};
window.addEventListener('resize',()=>window.rushUpdateVideoPosition&&window.rushUpdateVideoPosition());
document.addEventListener('scroll',()=>window.rushUpdateVideoPosition&&window.rushUpdateVideoPosition(),true);
