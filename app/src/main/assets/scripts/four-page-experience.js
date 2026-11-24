/* Four-page navigation and screen composition override the earlier prototype UI. */
let movieResults=[],movieSearchLoaded=false,movieDiscoveryRequested=false,followingFeedRequested=false;
let followingFeedCache={videos:[],music:[],news:[]};
let selectedSubscription='all';
let videoSpeed=1,videoQuality=0,videoCaptions=true;
let currentVideo={id:'',title:'',creator:'',thumbnail:''};
let searchFocusOpen=false;
let subscriptionFilter='All';
let selectedSubscriptionId='all';
let selectedSubscriptionName='All subscriptions';
let exploreCategoryResults={videos:[],music:[],news:[]};
let exploreCategoryQuery='';
let lastExploreMarkupKey='';
let explorePriorityImageAssigned=false;
const LOCAL_LISTS={liked:'rush_liked_items_v1',watchlist:'rush_watchlist_v1',news:'rush_saved_news_v1'};
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
 return '<article class="media-tile"><button class="home-media-open" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'" data-creator-thumbnail="'+esc(x.creatorThumbnail||'')+'" aria-label="Watch '+esc(x.title)+'"><span class="media-art video">'+(thumb?'<img src="'+esc(thumb)+'" alt="" width="190" height="107" decoding="async"'+priority+' onerror="this.hidden=true">':'')+'<span class="media-kind">VIDEO</span><span class="media-play">'+ic('play')+'</span></span></button><span class="media-copy">'+creatorAvatar(creator,x.creatorThumbnail)+'<span><b>'+esc(x.title)+'</b><span>'+esc(creator)+(x.duration?' · '+esc(x.duration):'')+'</span></span></span></article>';
}
function movieTile(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail),creator=x.artist||x.creator||'YouTube';
 const priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 return '<article class="media-tile movie"><button class="home-media-open" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'" data-creator-thumbnail="'+esc(x.creatorThumbnail||'')+'" aria-label="Play '+esc(x.title)+'"><span class="media-art movie">'+(thumb?'<img src="'+esc(thumb)+'" alt="" width="132" height="198" decoding="async"'+priority+' onerror="this.hidden=true">':'')+'<span class="media-kind">FILM</span><span class="media-play">'+ic('play')+'</span></span></button><span class="media-copy"><span><b>'+esc(x.title)+'</b><span>'+esc(creator)+'</span></span></span></article>';
}
function musicTile(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail),artist=x.artist||'Unknown artist';
 const priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 return '<article class="media-tile music"><button class="home-media-open" data-track="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-artist="'+esc(artist)+'" data-thumbnail="'+esc(thumb)+'" aria-label="Play '+esc(x.title)+'"><span class="media-art music">'+(thumb?'<img src="'+esc(thumb)+'" alt="" width="144" height="144" decoding="async"'+priority+' onerror="this.hidden=true">':'')+'<span class="media-kind">AUDIO</span><span class="media-play">'+ic('play')+'</span></span></button><span class="media-copy"><span><b>'+esc(x.title)+'</b><span>'+esc(artist)+(x.duration?' · '+esc(x.duration):'')+'</span></span></span></article>';
}
function newsShelfCard(x,index=0,priorityImage=false){
 const thumb=safeUrl(x.thumbnail);
 const priority=priorityImage?' loading="eager" fetchpriority="high"':' loading="lazy"';
 const saved=localItems('news').some(item=>item.url===x.url);
 return '<article class="news-shelf-card"><button class="news-shelf-open" data-news-url="'+esc(safeUrl(x.url))+'"><img src="'+esc(thumb)+'" alt="" width="108" height="78" decoding="async"'+priority+' referrerpolicy="no-referrer" onerror="this.hidden=true"><span class="news-shelf-copy"><small>'+esc(x.source||'News')+'</small><b>'+esc(x.title)+'</b><span>'+esc(x.date||'Latest report')+'</span></span></button><button class="news-save-button" data-save-news aria-label="'+(saved?'Remove saved story':'Save story')+'" data-url="'+esc(safeUrl(x.url))+'" data-title="'+esc(x.title)+'" data-source="'+esc(x.source||'News')+'" data-thumbnail="'+esc(thumb)+'">'+(saved?'★':'☆')+'</button></article>';
}
function mediaFeedbackMenu(x){
 const id=esc(x.id||''),creator=esc(x.artist||x.creator||'');
 return '<details class="media-feedback"><summary aria-label="More options">⋮</summary><div class="media-feedback-menu"><button data-recommendation-feedback="notInterested" data-id="'+id+'" data-creator="'+creator+'">Not interested</button><button data-recommendation-feedback="recommendChannel" data-id="'+id+'" data-creator="'+creator+'">Don’t recommend channel</button></div></details>';
}
function wideVideoCard(x){
 if(!x)return '';
 const creator=x.artist||x.creator||'YouTube',thumb=safeUrl(x.thumbnail);
 return '<article class="wide-video-card"><button data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'"><span class="wide-video-art">'+(thumb?'<img src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async">':'')+'<span class="media-play">'+ic('play')+'</span></span><span class="wide-video-copy">'+creatorAvatar(creator,x.creatorThumbnail)+'<span><b>'+esc(x.title)+'</b><small>'+esc(creator)+(x.duration?' · '+esc(x.duration):'')+'</small></span></span></button>'+mediaFeedbackMenu(x)+'</article>';
}
function libraryRecordTile(item,shape){
 if(!item)return '';
 const music=!!item.music,thumb=safeUrl(item.thumbnail),id=item.id||'';
 const klass=shape==='movie'?'movie':music?'music':'video';
 const ratio=klass==='movie'?'media-art movie':klass==='music'?'media-art music':'media-art video';
 const creator=item.creator||item.artist||'YouTube';
 const play=music?'<button data-track="'+esc(item.sourceId||id)+'" data-title="'+esc(item.title||'')+'" data-artist="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'">':'<button data-video-id="'+esc(id)+'" data-title="'+esc(item.title||'')+'" data-creator="'+esc(creator)+'" data-thumbnail="'+esc(thumb)+'">';
 return '<article class="media-tile '+(klass==='movie'?'movie':klass==='music'?'music':'')+'">'+play+'<span class="'+ratio+'">'+(thumb?'<img src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async">':'')+'<span class="media-play">'+ic('play')+'</span></span></button><span class="media-copy"><span><b>'+esc(item.title||'Untitled media')+'</b><span>'+esc(creator)+'</span></span></span></article>';
}
function librarySection(title,items,renderer,view){
 if(!items||!items.length)return '';
 return '<section class="library-shelf"><div class="shelf-heading"><h2>'+esc(title)+'</h2><button class="shelf-view-all" data-library-view="'+esc(view)+'">VIEW ALL ›</button></div><div class="shelf-row">'+items.map(renderer).join('')+'</div></section>';
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
 const root=$('#exploreShelves');if(!root)return;
 const videos=feed.videos||[],music=feed.music||[],news=feed.news||[];
 const historyItems=(libraryData.history||[]).slice(0,10).map(id=>{const item=libraryData.items&&libraryData.items[id];return item?Object.assign({id},item):null}).filter(Boolean);
 const historyVideos=historyItems.filter(item=>!item.music);
 const historyMusic=historyItems.filter(item=>item.music);
 const watchLater=localItems('watchlist');
 const recentVideos=followingFeedCache.videos||[];
 const recentMusic=followingFeedCache.music||[];
 const recentNews=followingFeedCache.news||[];
 const categoryVideos=exploreCategoryResults.videos||[];
 const categoryMusic=exploreCategoryResults.music||[];
 const categoryNews=exploreCategoryResults.news||[];
 let html='';
 if(homeFilter==='Movies'){
  const items=movieResults.length?movieResults:categoryVideos;
  root.innerHTML=items.length?shelf('Featured movies',items,movieTile,'MOVIE DISCOVERY'):'<div class="section-empty">Movie search uses discoverable long-form results at the moment. A licensed movie catalogue is not connected.</div>';
  return;
 }
 if(homeFilter==='Videos'){
  html+=shelf('Continue watching',historyVideos,videoTile,'HISTORY');
  if(videos.length)html+=shelf('Recommended videos',videos,videoTile);
  html+=shelf('From your subscriptions',recentVideos,videoTile,'LATEST UPLOADS');
 }else if(homeFilter==='Music'||homeFilter==='Mixes'||homeFilter==='Lo-Fi & Beats'){
  const selection=homeFilter==='Music'?music:(categoryMusic.length?categoryMusic:music);
  html+=shelf('Recently played',historyMusic,musicTile,'LISTEN AGAIN');
  html+='<section class="explore-shelf"><div class="shelf-heading"><h2>'+(homeFilter==='Music'?'Mixes & albums':esc(homeFilter))+'</h2><small>'+selection.length+' TRACKS</small></div><div class="music-grid">'+selection.map(musicTile).join('')+'</div></section>';
 }else if(homeFilter==='News'||homeFilter==='Tech & World'){
  const selection=homeFilter==='News'?news:(categoryNews.length?categoryNews:news);
  html+='<section class="explore-shelf news-column"><div class="shelf-heading"><h2>Top headlines</h2><small>RECENT REPORTING</small></div>'+(selection.length?selection.map(newsShelfCard).join(''):'<div class="section-empty">No headlines yet. Search for a publisher or topic.</div>')+'</section>';
 }else if(homeFilter==='Watch Later'){
  html+=shelf('Watch later',watchLater,(x)=>libraryRecordTile(x,'movie'),'SAVED ON THIS DEVICE');
 }else if(homeFilter==='Recently Uploaded'){
  html+=shelf('Latest from your subscriptions',recentVideos,videoTile,'NEW UPLOADS');
  html+=shelf('Recently published music',recentMusic,musicTile,'FOLLOWING');
  html+='<section class="explore-shelf news-column"><div class="shelf-heading"><h2>Publisher updates</h2></div>'+recentNews.map(newsShelfCard).join('')+'</section>';
 }else if(homeFilter==='All'){
  if(historyVideos.length){
   html+='<section class="explore-shelf continue-shelf"><div class="shelf-heading"><h2>Continue watching</h2><button class="shelf-view-all" data-library-view="history">HISTORY ›</button></div>'+wideVideoCard(historyVideos[0])+'</section>';
   if(historyVideos.length>1)html+=shelf('Recently watched',historyVideos.slice(1),videoTile,'PICK UP WHERE YOU LEFT OFF');
  }
  html+=shelf('Suggested for you',videos,videoTile,'BASED ON YOUR WATCHING');
  if(music.length)html+='<section class="explore-shelf"><div class="shelf-heading"><h2>Mixes & albums</h2><small>YOUR MUSIC</small></div><div class="shelf-row">'+music.map(musicTile).join('')+'</div></section>';
  if(movieResults.length)html+=shelf('Featured movies',movieResults,movieTile,'MOVIE DISCOVERY');
  if(news.length)html+='<section class="explore-shelf news-column"><div class="shelf-heading"><h2>Top headlines</h2><small>NEWS</small></div>'+news.slice(0,5).map(newsShelfCard).join('')+'</section>';
  if(music.length||videos.length){
   const trendingMusic=music.slice(0,2);
   html+='<section class="explore-shelf"><div class="shelf-heading"><h2>Trending now</h2><small>POPULAR PICKS</small></div><div class="trending-split">'+trendingMusic.map(musicTile).join('')+'</div>'+(videos[0]?'<div class="trending-feature">'+wideVideoCard(videos[0])+'</div>':'')+'</section>';
  }
  html+=shelf('From your subscriptions',recentVideos,videoTile,'LATEST UPLOADS');
  html+=shelf('Latest from followed artists',recentMusic,musicTile,'FOLLOWING');
  if(recentNews.length)html+='<section class="explore-shelf news-column"><div class="shelf-heading"><h2>Publisher updates</h2><small>FOLLOWING</small></div>'+recentNews.slice(0,4).map(newsShelfCard).join('')+'</section>';
  html+=topicShelf();
 }else{
  const title=homeFilter;
  if(categoryVideos.length)html+=shelf(title,categoryVideos,videoTile,'VIDEO RESULTS');
  if(categoryMusic.length)html+=shelf('Music for '+title,categoryMusic,musicTile);
  if(categoryNews.length)html+='<section class="explore-shelf news-column"><div class="shelf-heading"><h2>Stories about '+esc(title)+'</h2></div>'+categoryNews.map(newsShelfCard).join('')+'</section>';
  if(!html)html='<div class="section-empty">Finding '+esc(title)+' for your feed…</div>';
 }
 root.innerHTML=html||'<div class="section-empty">Nothing here yet. Search or choose another category.</div>';
}
renderHome = function(){
 const filters=['All','Videos','Music','News','Movies'];
 return '<div class="explore-tabs" role="tablist" aria-label="Explore categories">'+filters.map(x=>'<button class="explore-tab" role="tab" data-home-filter="'+esc(x)+'" aria-selected="'+(homeFilter===x)+'" aria-pressed="'+(homeFilter===x)+'">'+esc(x)+'</button>').join('')+'</div>'+
 '<p class="meta pad home-feed-status" id="homeFeedStatus" aria-live="polite">Your recommendations and subscriptions.</p><div id="exploreShelves"></div>';
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
 return '<section class="search-entry"><label class="search-box">'+ic('search')+'<input id="q" type="search" placeholder="Search videos, music, news and creators" aria-label="Search Rush" autocomplete="off"><button class="ib" type="button" data-search-clear aria-label="Clear search">×</button></label></section>'+
 '<div id="searchIdle"><div class="search-idle-heading">Explore</div><div class="trend-row">'+trending.map(x=>'<button class="trend-chip" data-topic-search="'+esc(x)+'">'+esc(x)+'</button>').join('')+'</div>'+
 '<div class="search-idle-heading">Channels & artists</div><div class="source-rail">'+(prefs.sources.length?prefs.sources.slice(0,16).map(x=>'<button class="source-avatar" data-search-source="'+esc(x.kind)+'" data-source-name="'+esc(x.name)+'"><span class="avatar">'+(safeUrl(x.thumbnail)?'<img src="'+esc(safeUrl(x.thumbnail))+'" alt="" loading="lazy" decoding="async">':esc(creatorInitial(x.name)))+'</span><b>'+esc(x.name)+'</b></button>').join(''):'<span class="meta">Your followed creators will appear here.</span>')+'</div>'+
 '<div class="search-idle-heading">Browse categories</div><div class="trend-row">'+['Videos','Music','Movies','News','Live','Podcasts','Gaming','Technology'].map(x=>'<button class="trend-chip" data-topic-search="'+esc(x)+'">'+esc(x)+'</button>').join('')+'</div>'+
 '<div class="search-idle-heading">Recent searches</div><div class="search-history" id="searchHistory">'+(history.length?history.map(x=>'<button class="search-history-item" data-search-history="'+esc(x)+'">◷ <span>'+esc(x)+'</span></button>').join('')+'<button class="trend-chip" data-search-history-clear>Clear history</button>':'<span class="meta">Your recent searches will appear here.</span>')+'</div></div>'+
 '<div class="chips search-filters"><button class="chip" data-search-filter="all" aria-pressed="true">All</button><button class="chip" data-search-filter="videos" aria-pressed="false">Videos</button><button class="chip" data-search-filter="music" aria-pressed="false">Music</button><button class="chip" data-search-filter="news" aria-pressed="false">News</button><button class="chip" data-search-filter="channels" aria-pressed="false">Channels</button><button class="chip" data-search-filter="playlists" aria-pressed="false">Playlists</button></div>'+
 '<p class="meta pad" id="unifiedStatus">Search all of Rush.</p><div id="unifiedResults"></div><div id="publicPlaylistContent"></div>';
};
renderSubscribedPage = function(){
 const filters=['All','Videos','Music','Movies','News'];
 return '<div class="source-rail subscriptions" id="subscriptionSources"><button class="source-avatar '+(selectedSubscriptionId==='all'?'active':'')+'" data-subscription-all><span class="avatar all-avatar">ALL</span><b>All</b></button>'+
 (prefs.sources.length?prefs.sources.map((x,i)=>'<button class="source-avatar '+(selectedSubscriptionId===x.id?'active':'')+'" data-subscription-source="'+i+'"><span class="avatar">'+(safeUrl(x.thumbnail)?'<img src="'+esc(safeUrl(x.thumbnail))+'" alt="" loading="lazy" decoding="async">':esc(creatorInitial(x.name)))+'</span><b>'+esc(x.name)+'</b></button>').join(''):'<button class="source-avatar" data-open-search><span class="avatar all-avatar">+</span><b>Find</b></button>')+'</div>'+
 '<div class="subscription-tabs">'+filters.map(x=>'<button class="explore-tab '+(subscriptionFilter===x?'selected':'')+'" data-subscription-filter="'+x+'" aria-pressed="'+(subscriptionFilter===x)+'">'+x+'</button>').join('')+'</div>'+
 '<div class="subscription-feed"><div class="subscription-toolbar"><b id="subscriptionTitle">'+esc(selectedSubscriptionName)+'</b><small id="subscriptionStatus">LATEST UPDATES</small></div><div id="subscriptionFeed" class="subscription-results"><p class="meta pad">Loading your subscriptions…</p></div></div>';
};
function subscriptionPost(item,kind){
 const creator=item.artist||item.creator||item.sourceName||item.source||selectedSubscriptionName;
 const thumb=safeUrl(item.thumbnail);
 let body='';
 if(kind==='video')body=videoTile(item);
 else if(kind==='music')body=musicTile(item);
 else body=newsShelfCard(item);
 return '<article class="subscription-post"><div class="subscription-post-source">'+creatorAvatar(creator)+'<span><b>'+esc(creator)+'</b><small>'+esc(item.date||item.duration||'Latest update')+'</small></span></div>'+body+'</article>';
}
function renderSubscribedFeed(data){
 const root=$('#subscriptionFeed');if(!root)return;
 const videos=data.videos||[],music=data.music||[],news=data.news||[];
 let rows=[];
 if(subscriptionFilter==='All'||subscriptionFilter==='Today'||subscriptionFilter==='Videos'){
  rows.push(...videos.map(item=>({item,kind:'video'})));
 }
 if(subscriptionFilter==='All'||subscriptionFilter==='Today'||subscriptionFilter==='Music'){
  rows.push(...music.map(item=>({item,kind:'music'})));
 }
 if(subscriptionFilter==='All'||subscriptionFilter==='Today'||subscriptionFilter==='News'){
  rows.push(...news.map(item=>({item,kind:'news'})));
 }
 if(subscriptionFilter==='Movies'){
  rows.push(...videos.filter(item=>durationSeconds(item.duration)>=900).map(item=>({item,kind:'video'})));
 }
 if(subscriptionFilter==='Today'){
  const today=new Date();today.setHours(0,0,0,0);
  rows=rows.filter(row=>!row.item.date||Date.parse(row.item.date)>=today.getTime());
 }
 root.innerHTML=rows.length?rows.map(row=>subscriptionPost(row.item,row.kind)).join(''):'<div class="section-empty">No recent updates in this filter. Select All or search for more creators.</div>';
 const status=$('#subscriptionStatus');if(status)status.textContent=rows.length+' UPDATES';
}
function durationSeconds(value){
 const parts=String(value||'').split(':').map(Number);
 if(parts.some(Number.isNaN))return 0;
 if(parts.length===3)return parts[0]*3600+parts[1]*60+parts[2];
 if(parts.length===2)return parts[0]*60+parts[1];
 return parts[0]||0;
}
function subscriptionSourceSearch(index){
 const source=prefs.sources[index];if(!source)return;
 selectedSubscriptionId=source.id;selectedSubscriptionName=source.name;
 document.querySelectorAll('[data-subscription-source]').forEach((button,i)=>button.classList.toggle('active',i===index));
 const title=$('#subscriptionTitle');if(title)title.textContent=source.name;
 document.querySelectorAll('#subscriptionSources [data-subscription-all],#subscriptionSources [data-subscription-source]').forEach(x=>x.setAttribute('aria-pressed',String(x.dataset.subscriptionSource!==undefined&&Number(x.dataset.subscriptionSource)===index)));
 const title=$('#subscriptionTitle');if(title)title.textContent=source.name;
 const root=$('#subscriptionFeed');if(root)root.innerHTML='<p class="meta pad">Loading '+esc(source.name)+'…</p>';
 if(!window.RushNative)return;
 if(source.kind==='artist')RushNative.searchMusic(source.name);
 else if(source.kind==='news')RushNative.searchNews(source.name);
 else RushNative.loadChannelUploads(source.id,source.name);
}
renderLibraryPage = function(){
 const history=(libraryData.history||[]).slice(0,12).map(id=>{const item=libraryData.items&&libraryData.items[id];return item?Object.assign({id},item):null}).filter(Boolean);
 const playlists=libraryData.playlists||[];
 const watchlist=localItems('watchlist');
 const savedNews=localItems('news');
 const listRows=[['history','History','◷'],['playlists','Playlists','▤'],['saved','Your videos','▶'],['downloads','Downloads','⇩'],['liked','Liked content','♥'],['watchlist','Watch later','▣'],['news','Saved news','▧']];
 return '<div class="library-profile"><button class="library-profile-main" data-open-profile><span class="avatar">'+esc((rushProfile.displayName||'R').trim().slice(0,1).toUpperCase())+'</span><span class="profile-copy"><b>'+esc(rushProfile.displayName||'Your profile')+'</b><span>'+(rushProfile.handle?'@'+esc(rushProfile.handle):'Local profile')+'</span></span></button><button class="ib" data-open-settings aria-label="Settings">⚙</button></div>'+
 librarySection('History',history.slice(0,8),item=>libraryRecordTile(item),'history')+
 (playlists.length?'<section class="library-shelf"><div class="shelf-heading"><h2>Playlists</h2><button class="shelf-view-all" data-library-view="playlists">VIEW ALL ›</button></div><div class="shelf-row">'+playlists.slice(0,10).map(p=>'<button class="library-playlist-tile" data-play-playlist data-playlist-id="'+esc(p.id)+'"><span class="playlist-cover">'+(p.items&&p.items[0]&&libraryData.items[p.items[0]]&&safeUrl(libraryData.items[p.items[0]].thumbnail)?'<img src="'+esc(safeUrl(libraryData.items[p.items[0]].thumbnail))+'" alt="" loading="lazy">':'♫')+'</span><b>'+esc(p.name)+'</b><small>'+(p.items||[]).length+' tracks</small></button>').join('')+'</div></section>':'')+
 librarySection('Watchlist',watchlist.slice(0,10),item=>libraryRecordTile(item,'movie'),'watchlist')+
 (savedNews.length?'<section class="explore-shelf news-column"><div class="shelf-heading"><h2>Saved news</h2><button class="shelf-view-all" data-library-view="news">VIEW ALL ›</button></div>'+savedNews.slice(0,5).map(item=>newsShelfCard(item)).join('')+'</section>':'')+
 '<section class="library-list">'+listRows.map(([key,label,icon])=>'<button data-library-view="'+key+'"><span class="library-row-icon">'+icon+'</span><b>'+label+'</b><span>›</span></button>').join('')+'</section><div class="library-view-all" id="libraryContent"></div>';
};
const renderOriginalLibraryContent=renderLibraryContent;
renderLibraryContent = function(){
 const root=$('#libraryContent');if(!root)return;
 if(libraryView==='liked'||libraryView==='watchlist'){
  const items=localItems(libraryView);
  document.querySelectorAll('[data-library-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.libraryView===libraryView)));
  root.innerHTML=items.length?items.map(item=>'<div class="local-list-row"><img src="'+esc(safeUrl(item.thumbnail))+'" alt="" onerror="this.hidden=true"><span class="copy"><b>'+esc(item.title)+'</b><span>'+esc(item.creator||'')+'</span></span><button class="chip" data-video-id="'+esc(item.id)+'" data-title="'+esc(item.title)+'" data-creator="'+esc(item.creator||'')+'" data-thumbnail="'+esc(item.thumbnail||'')+'">PLAY</button></div>').join(''):'<div class="empty-state"><b>'+(libraryView==='liked'?'No liked items yet':'Your watchlist is empty')+'</b><p>Use the actions on a video to save it here.</p></div>';
  return;
 }
 if(libraryView==='news'){
  const items=localItems('news');
  document.querySelectorAll('[data-library-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.libraryView===libraryView)));
  root.innerHTML=items.length?'<div class="news-column">'+items.map(newsShelfCard).join('')+'</div>':'<div class="empty-state"><b>No saved stories yet</b><p>Save a news card to keep it here.</p></div>';
  return;
 }
 renderOriginalLibraryContent();
};
renderWatchPage = function(){
 const x=currentVideo;
 const liked=isLocalItem('liked',x.id),saved=isLocalItem('watchlist',x.id),disliked=safeLocalRead('rush_disliked_items_v1').some(item=>item.id===x.id);
 return '<div class="watch-player"><div class="video-player-host" id="videoPlayerHost"></div><div class="watch-buffer-overlay" id="videoBuffering" hidden><span class="buffer-indicator"><i class="buffer-spinner"></i><b>BUFFERING</b></span></div></div>'+
 '<section class="watch-details"><h2 id="watchTitle">'+esc(x.title||'Video loading…')+'</h2>'+
 '<div class="watch-channel">'+creatorAvatar(x.creator)+'<span class="watch-channel-copy"><b>'+esc(x.creator||'Creator')+'</b><span>Channel</span></span><button class="chip" data-follow-kind="channel" data-source-id="'+esc(x.sourceId||'')+'" data-source-name="'+esc(x.creator||'')+'" data-source-thumbnail="'+esc(x.creatorThumbnail||'')+'">Subscribe</button></div>'+
 '<div class="watch-actions"><button class="watch-action '+(liked?'on':'')+'" data-local-like>♥ '+(liked?'Liked':'Like')+'</button><button class="watch-action '+(disliked?'on':'')+'" data-local-dislike>− Dislike</button><button class="watch-action '+(saved?'on':'')+'" data-local-watchlist>＋ '+(saved?'Saved':'Watch later')+'</button><button class="watch-action" data-option-menu="speed">1×</button><button class="watch-action" data-option-menu="quality">Quality</button><button class="watch-action" data-option-menu="captions">CC</button></div>'+
 '<div class="video-options" id="videoOptions" hidden></div><div class="watch-actions secondary"><button class="watch-action" data-recommendation-feedback="notInterested" data-id="'+esc(x.id)+'" data-creator="'+esc(x.creator)+'">Not interested</button><button class="watch-action" data-recommendation-feedback="recommendChannel" data-id="'+esc(x.id)+'" data-creator="'+esc(x.creator)+'">Don’t recommend channel</button><button class="watch-action" data-video-fullscreen>Full screen ↗</button></div>'+
 '<p class="meta" id="videoPlaybackStatus" aria-live="polite">Video controls and playback options are available here.</p></section>'+
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
 if(page==='subscribed'){selectedSubscription='all';selectedSubscriptionId='all';selectedSubscriptionName='All subscriptions';subscriptionFilter='All';}
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
 if(page==='subscribed'&&data&&data.mode==='following'){if(selectedSubscriptionId==='all')renderSubscribedFeed(data);return}
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
 if(page==='explore'&&exploreCategoryQuery===homeFilter&&!['All','Videos','Music','News','Movies','Mixes','Watch Later','Recently Uploaded'].includes(homeFilter)){
  exploreCategoryResults.videos=items||[];renderExploreContent(homeFeedCache);return;
 }
 if(page==='subscribed'&&selectedSubscription!=='all'){
  const root=$('#subscriptionFeed');if(root)root.innerHTML=(items||[]).map(item=>subscriptionPost(item,'video')).join('')||'<p class="meta pad">No videos found for this creator.</p>';return;
 }
 if(page==='watch'){
  const root=$('#watchSuggestions');if(root)root.innerHTML=(items||[]).filter(x=>x.id!==currentVideo.id).slice(0,12).map(videoRow).join('');return;
 }
 if(previousVideoResults)previousVideoResults(items);
};
const previousVideoSearchError=window.rushVideoSearchError;
window.rushVideoSearchError=function(error){
 if(movieDiscoveryRequested){movieDiscoveryRequested=false;movieSearchLoaded=false;if(page==='explore')renderExploreContent(homeFeedCache);return}
 if(page==='explore'&&exploreCategoryQuery===homeFilter){exploreCategoryQuery='';const status=$('#homeFeedStatus');if(status)status.textContent=error.message||'Could not load this category.';renderExploreContent(homeFeedCache);return}
 if(previousVideoSearchError)previousVideoSearchError(error);
};
const previousMusicResults=window.rushMusicSearchResults;
window.rushMusicSearchResults=function(items){
 if(page==='explore'&&exploreCategoryQuery===homeFilter){exploreCategoryResults.music=items||[];renderExploreContent(homeFeedCache);return}
 if(page==='subscribed'&&selectedSubscription!=='all'){const root=$('#subscriptionFeed');if(root)root.innerHTML=(items||[]).map(item=>subscriptionPost(item,'music')).join('');return}
 if(previousMusicResults)previousMusicResults(items);
};
const previousNewsResults=window.rushNewsResults;
window.rushNewsResults=function(items){
 if(page==='explore'&&exploreCategoryQuery===homeFilter){exploreCategoryResults.news=items||[];renderExploreContent(homeFeedCache);return}
 if(page==='subscribed'&&selectedSubscription!=='all'){const root=$('#subscriptionFeed');if(root)root.innerHTML=(items||[]).map(item=>subscriptionPost(item,'news')).join('');return}
 if(previousNewsResults)previousNewsResults(items);
};
window.rushChannelUploadsResults=function(data){
 if(page!=='subscribed'||selectedSubscriptionId==='all'||data.channelId!==selectedSubscriptionId)return;
 const root=$('#subscriptionFeed');if(!root)return;
 const items=data.items||[];
 root.innerHTML=items.length?items.map(item=>subscriptionPost(item,'video')).join(''):'<div class="section-empty">This channel has no recent uploads.</div>';
 const status=$('#subscriptionStatus');if(status)status.textContent=items.length+' VIDEOS';
};
window.rushChannelUploadsError=function(error){
 if(page!=='subscribed'||selectedSubscriptionId==='all'||error.channelId!==selectedSubscriptionId)return;
 const root=$('#subscriptionFeed');if(root)root.innerHTML='<div class="feed-recovery"><b>Could not load '+esc(error.channelName||'this channel')+'</b><p>'+esc(error.message||'Try again in a moment.')+'</p><button class="btn" data-subscription-source-retry>RETRY</button></div>';
};
window.rushUpNextResults=function(items){
 if(page!=='watch')return;
 const root=$('#watchSuggestions');if(root)root.innerHTML=(items||[]).map(videoRow).join('')||'<div class="section-empty">No related videos found yet.</div>';
};
const previousFeedPreferences=window.rushFeedPreferences;
window.rushFeedPreferences=function(data){
 followingFeedRequested=false;
 followingFeedCache={videos:[],music:[],news:[]};
 if(previousFeedPreferences)previousFeedPreferences(data);
};
const previousLibraryData=window.rushLibraryData;
window.rushLibraryData=function(data){libraryData=data||libraryData;if(page==='explore')renderExploreContent(homeFeedCache);else if(page==='library'){const root=$('#v');if(root)root.innerHTML=renderLibraryPage();renderLibraryContent()}};
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
 const handled=b.hasAttribute('data-home-filter')||b.hasAttribute('data-open-library')||b.hasAttribute('data-open-search')||b.hasAttribute('data-open-settings')||b.hasAttribute('data-video-id')||b.hasAttribute('data-track')||b.hasAttribute('data-mini-expand')||b.hasAttribute('data-video-close')||b.hasAttribute('data-video-fullscreen')||b.hasAttribute('data-video-comments')||b.hasAttribute('data-local-like')||b.hasAttribute('data-local-dislike')||b.hasAttribute('data-local-watchlist')||b.hasAttribute('data-subscription-source')||b.hasAttribute('data-subscription-all')||b.hasAttribute('data-subscription-filter')||b.hasAttribute('data-subscription-source-retry')||b.hasAttribute('data-topic-search')||b.hasAttribute('data-search-history')||b.hasAttribute('data-search-clear')||b.hasAttribute('data-search-source')||b.hasAttribute('data-save-news')||b.hasAttribute('data-recommendation-feedback')||b.hasAttribute('data-option-menu')||b.hasAttribute('data-video-speed-choice')||b.hasAttribute('data-video-quality-choice')||b.hasAttribute('data-video-cc-toggle')||b.hasAttribute('data-sleep-timer')||b.hasAttribute('data-sleep-choice')||b.hasAttribute('data-player-back')||b.hasAttribute('data-player-toggle')||b.hasAttribute('data-player-prev')||b.hasAttribute('data-player-next')||b.hasAttribute('data-player-save')||b.hasAttribute('data-player-queue')||b.hasAttribute('data-player-lyrics-toggle')||(b.hasAttribute('data-library-view')&&['liked','watchlist','news'].includes(b.dataset.libraryView));
 if(handled){e.preventDefault();e.stopImmediatePropagation()}
 if(b.hasAttribute('data-home-filter')){
  homeFilter=b.dataset.homeFilter||'All';
  exploreCategoryQuery='';exploreCategoryResults={videos:[],music:[],news:[]};
  document.querySelectorAll('[data-home-filter]').forEach(x=>{x.setAttribute('aria-pressed',String(x===b));x.setAttribute('aria-selected',String(x===b))});
  const root=$('#exploreShelves');
  if(homeFilter==='Movies'&&window.RushNative&&!movieSearchLoaded){
   movieResults=[];movieDiscoveryRequested=true;
   if(root)root.innerHTML='<div class="category-loading"><i class="buffer-spinner"></i><span>Finding movies…</span></div>';
   RushNative.searchVideos('full length movie film');
  }else if(['Mixes','Live','Podcasts','Gaming','Trending','Sci-Fi & Cinema','Lo-Fi & Beats','Tech & World'].includes(homeFilter)){
   exploreCategoryQuery=homeFilter;
   if(root)root.innerHTML='<div class="category-loading"><i class="buffer-spinner"></i><span>Finding '+esc(homeFilter)+'…</span></div>';
   if(window.RushNative){
    if(homeFilter==='Mixes')RushNative.searchMusic('music mixes');
    else if(homeFilter==='Lo-Fi & Beats')RushNative.searchMusic('lofi beats');
    else if(homeFilter==='Tech & World')RushNative.searchNews('technology world news');
    else if(homeFilter==='Sci-Fi & Cinema')RushNative.searchVideos('science fiction cinema movie');
    else RushNative.searchVideos(({Live:'live streams',Podcasts:'podcasts',Gaming:'gaming videos',Trending:'trending videos today'})[homeFilter]||homeFilter);
   }
  }else renderExploreContent(homeFeedCache);
  return;
 }
 if(b.hasAttribute('data-open-library')){location.hash='#library';return}
 if(b.hasAttribute('data-open-search')){location.hash='#search';return}
 if(b.hasAttribute('data-open-settings')){location.hash='#settings';return}
 if(b.hasAttribute('data-subscription-all')){
  selectedSubscriptionId='all';selectedSubscription='all';selectedSubscriptionName='All subscriptions';
  document.querySelectorAll('[data-subscription-source]').forEach(x=>x.classList.remove('active'));
  b.classList.add('active');const title=$('#subscriptionTitle');if(title)title.textContent=selectedSubscriptionName;
  const root=$('#subscriptionFeed');if(root)root.innerHTML='<p class="meta pad">Loading all subscriptions…</p>';
  if(window.RushNative)RushNative.loadHomeFeed('following');return;
 }
 if(b.hasAttribute('data-subscription-filter')){
  subscriptionFilter=b.dataset.subscriptionFilter||'All';
  document.querySelectorAll('[data-subscription-filter]').forEach(x=>{x.classList.toggle('selected',x===b);x.setAttribute('aria-pressed',String(x===b))});
  if(selectedSubscriptionId==='all')renderSubscribedFeed(followingFeedCache);
  else subscriptionSourceSearch(prefs.sources.findIndex(x=>x.id===selectedSubscriptionId));
  return;
 }
 if(b.hasAttribute('data-subscription-source-retry')){
  const index=prefs.sources.findIndex(x=>x.id===selectedSubscriptionId);if(index>=0)subscriptionSourceSearch(index);return;
 }
 if(b.hasAttribute('data-save-news')){
  const item={url:b.dataset.url||'',title:b.dataset.title||'',source:b.dataset.source||'News',thumbnail:b.dataset.thumbnail||''};
  const items=localItems('news'),index=items.findIndex(x=>x.url===item.url);
  if(index>=0)items.splice(index,1);else items.unshift(item);
  safeLocalWrite(LOCAL_LISTS.news,items.slice(0,80));
  b.textContent=index>=0?'☆':'★';b.setAttribute('aria-label',index>=0?'Save story':'Remove saved story');return;
 }
 if(b.hasAttribute('data-recommendation-feedback')){
  const action=b.dataset.recommendationFeedback||'',id=b.dataset.id||'',creator=b.dataset.creator||'';
  if(window.RushNative)RushNative.setRecommendationFeedback(id,creator,action);
  if(action==='notInterested')b.closest('.media-tile,.wide-video-card,.subscription-post')?.remove();
  if(action==='recommendChannel')b.closest('.media-tile,.wide-video-card,.subscription-post')?.remove();
  return;
 }
 if(b.hasAttribute('data-video-id')){
  currentVideo={id:b.dataset.videoId||'',title:b.dataset.title||'Video',creator:b.dataset.creator||'',thumbnail:b.dataset.thumbnail||'',creatorThumbnail:b.dataset.creatorThumbnail||'',sourceId:b.dataset.sourceId||''};
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
 if(b.hasAttribute('data-local-dislike')){
  const list=safeLocalRead('rush_disliked_items_v1');
  const index=list.findIndex(x=>x.id===currentVideo.id);
  if(index>=0)list.splice(index,1);else list.unshift({id:currentVideo.id,title:currentVideo.title,creator:currentVideo.creator,thumbnail:currentVideo.thumbnail});
  safeLocalWrite('rush_disliked_items_v1',list.slice(0,100));
  if(window.RushNative)RushNative.setRecommendationFeedback(currentVideo.id,currentVideo.creator,index>=0?'like':'dislike');
  b.classList.toggle('on',index<0);b.textContent=index>=0?'− Dislike':'− Disliked';return;
 }
 if(b.hasAttribute('data-local-like')||b.hasAttribute('data-local-watchlist')){
  const kind=b.hasAttribute('data-local-like')?'liked':'watchlist';
  toggleLocalItem(kind,{id:currentVideo.id,title:currentVideo.title,creator:currentVideo.creator,thumbnail:currentVideo.thumbnail});
  if(window.RushNative)RushNative.setRecommendationFeedback(currentVideo.id,currentVideo.creator,isLocalItem(kind,currentVideo.id)?'like':'notInterested');
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
