const $=s=>document.querySelector(s);
const RM=matchMedia('(prefers-reduced-motion: reduce)').matches;
const IC={
 home:'M3 11l9-8 9 8v10h-6v-6H9v6H3z',
 vid:'M4 5h16v14H4zM10 9l5 3-5 3z',
 music:'M9 18V5l11-2v13M9 18a3 3 0 11-6 0 3 3 0 016 0zM20 16a3 3 0 11-6 0 3 3 0 016 0z',
 news:'M4 4h13v16H4zM17 8h3v10a2 2 0 01-2 2M7 8h7M7 12h7M7 16h4',
 search:'M11 4a7 7 0 100 14 7 7 0 000-14zM20 20l-4-4',
 play:'M7 4l13 8-13 8z', pause:'M7 5h3v14H7zM14 5h3v14h-3z',
 arrow:'M5 12h14M13 6l6 6-6 6', follow:'M12 5v14M5 12h14', library:'M4 4h13v16H4zM7 8h7M7 12h7M7 16h4M19 7v13'
};
const FILL=['play','pause'];
const ic=n=>'<svg class="ic'+(FILL.includes(n)?' f':'')+'" viewBox="0 0 24 24" aria-hidden="true"><path d="'+(IC[n]||IC.arrow)+'"/></svg>';
const PAL=['#FF6B3D','#4D7DFF','#D8FF3F','#35E0E0','#B58CFF','#3DDC84'];
const TOPICS=['Science','Technology','AI and machine learning','Programming','Cybersecurity','History','Geography','Education','Languages','Literature','Books','Philosophy','Psychology','Mathematics','Space','Engineering','Economics','Business','Startups','Personal finance','Investing','Politics','Current affairs','World news','Environment','Climate','Health','Medicine','Fitness','Sports','Football','Basketball','Cricket','Esports','Gaming','Game development','Design','Art','Photography','Architecture','Fashion','Beauty','Makeup','Cooking','Food','Travel','Cars','Motorcycles','Nature','Wildlife','Pets','Gardening','Home improvement','Music','Music production','Classical music','Hip-hop','K-pop','Film analysis','Anime','Manga','Podcasts','Documentaries','Vlogs','Comedy','Memes','Viral clips','Celebrity gossip','Pranks','Internet drama','Rage bait','Reality TV','Shorts','ASMR','Luxury','Shopping','Product reviews','Relationships','Parenting','Lifestyle','Meditation','Self-improvement','Romance','Horror','Internet culture','Streaming','Live streams','Board games','Collecting','Crafts'];
const esc=v=>String(v==null?'':v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const safeUrl=v=>/^https:\/\/[^\s"'<>]+$/i.test(String(v||''))?String(v):'';
let prefs={topics:['Science','Technology','History','Education','Design','Engineering','Economics','Culture','Environment','Sports','Music','Health','Language','Business','Art','Documentaries'],sources:[]};
let page='home',homeTab='fy',homeFilter='All',homeFeedCache={videos:[],music:[],news:[]},unifiedMode='all',unifiedQuery='',mediaState={hasItem:false,isMusic:false,isVideo:false,isPlaying:false,title:'',artist:'',artwork:''};
const unifiedData={videos:[],music:[],news:[],channels:[],playlists:[]};
let publicPlaylistData=null;
const selectedTopics=()=>prefs.topics||[];
function loadPrefs(){
 try{if(window.RushNative){const raw=RushNative.getFeedPreferences();if(raw)prefs=JSON.parse(raw)}}catch(e){}
}
function saveTopics(){
 const selected=[...document.querySelectorAll('[data-topic][aria-pressed="true"]')].map(b=>b.dataset.topic);
 if(!selected.length){const s=$('#topicStatus');if(s)s.textContent='Choose at least one topic.';return}
 if(window.RushNative)RushNative.saveFeedTopics(JSON.stringify(selected));
}
function sourceExists(kind,id){return prefs.sources.some(s=>s.kind===kind&&s.id===id)}
function sourceFor(kind,id){return prefs.sources.find(s=>s.kind===kind&&s.id===id)}
function sourceButton(kind,id,name,label,thumbnail=''){
 const found=sourceExists(kind,id);
 return '<button class="chip source-follow" data-follow-kind="'+esc(kind)+'" data-source-id="'+esc(id)+'" data-source-name="'+esc(name)+'" data-source-thumbnail="'+esc(safeUrl(thumbnail))+'">'+(found?'FOLLOWING':(label||'FOLLOW'))+'</button>';
}
function channelCard(x){
 const thumb=safeUrl(x.thumbnail);
 return '<article class="source-card"><img src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async" onerror="this.hidden=true"><div class="source-info"><b>'+esc(x.name)+'</b><span>'+esc(x.subscribers||'YouTube channel')+'</span><p>'+esc(x.description||'')+'</p><div class="source-actions">'+sourceButton('channel',x.id,x.name,'FOLLOW',x.thumbnail)+'</div></div></article>';
}

function mediaActions(x,isMusic){
 const id=esc(x.id),title=esc(x.title),creator=esc(isMusic?(x.artist||x.creator):(x.artist||x.creator)),thumb=esc(safeUrl(x.thumbnail));
 return '<div class="result-actions"><button class="chip" data-save-item data-id="'+id+'" data-title="'+title+'" data-creator="'+creator+'" data-music="'+isMusic+'" data-thumbnail="'+thumb+'">SAVE</button>'+
  '<button class="chip" data-queue-item data-id="'+id+'" data-title="'+title+'" data-creator="'+creator+'" data-music="'+isMusic+'" data-thumbnail="'+thumb+'">QUEUE</button>'+
  '<button class="chip" data-download-media data-id="'+id+'" data-title="'+title+'" data-creator="'+creator+'" data-music="'+isMusic+'">DOWNLOAD</button>'+
  (isMusic?'<button class="chip" data-download-mp3 data-id="'+id+'" data-title="'+title+'" data-creator="'+creator+'">MP3</button>':'')+'</div>';
}
function playlistRow(x){
 return '<article class="source-card"><img src="'+esc(safeUrl(x.thumbnail))+'" alt="" loading="lazy" decoding="async" onerror="this.hidden=true"><div class="source-info"><b>'+esc(x.title)+'</b><span>'+esc(x.creator||'YouTube')+' · '+esc(x.videoCount||'Playlist')+'</span><div class="result-actions"><button class="chip" data-public-playlist-open data-playlist-id="'+esc(x.id)+'" data-playlist-title="'+esc(x.title)+'">VIEW PLAYLIST</button></div></div></article>';
}
function videoRow(x){
 const thumb=safeUrl(x.thumbnail);
 return '<div class="result-wrap"><button class="row n" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(x.artist||x.creator)+'" data-thumbnail="'+esc(thumb)+'"><img src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async" onerror="this.hidden=true"><div><b>'+esc(x.title)+'</b><span>'+esc(x.artist||x.creator||'YouTube')+' · '+esc(x.duration||'')+'</span></div>'+ic('play')+'</button>'+mediaActions(x,false)+'</div>';
}
function musicRow(x){
 return '<div class="result-wrap">'+videoLikeMusic(x)+'<div class="result-actions">'+sourceButton('artist',x.artist,x.artist,'FOLLOW ARTIST')+'</div>'+mediaActions(x,true)+'</div>';
}
function musicHistoryRow(x){
 return '<div class="result-wrap history-row">'+videoLikeMusic(x)+'</div>';
}
function videoLikeMusic(x){
 const thumb=safeUrl(x.thumbnail);
 return '<button class="row n" data-track="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-artist="'+esc(x.artist)+'" data-thumbnail="'+esc(thumb)+'"><img src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async" onerror="this.hidden=true"><div><b>'+esc(x.title)+'</b><span>'+esc(x.artist||'Unknown artist')+' · '+esc(x.duration||'')+'</span></div>'+ic('play')+'</button>';
}
function newsCard(x){
 const thumb=safeUrl(x.thumbnail),publisher=x.source||'News publisher';
 const image='<span class="news-thumb-wrap"><span class="news-thumb-fallback">NEWS</span>'+(thumb?'<img class="news-thumb" src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async" referrerpolicy="no-referrer" onerror="this.hidden=true">':'')+'</span>';
 return '<article class="news-card"><button class="news-open" data-news-url="'+esc(safeUrl(x.url))+'"><span class="news-copy"><span class="news-meta">'+esc(publisher)+' · '+esc(x.date||'')+'</span><b>'+esc(x.title)+'</b><p>'+esc(x.summary||'Open the article for the full report.')+'</p></span>'+image+'</button><div class="result-actions">'+sourceButton('news',publisher,publisher,'FOLLOW PUBLISHER')+'</div></article>';
}
function section(title,body){
 return '<section class="feed-section"><h2>'+title+'</h2>'+(body||'<p class="meta pad">Nothing to show yet.</p>')+'</section>';
}
function pageTop(title,kicker){
 return '<header class="pt"><h1>'+title+'</h1><em class="stk">'+kicker+'</em></header>';
}
function renderHome(){
 const filters=['All','Videos','Music','News'];
 return pageTop('Home','your feed')+
 '<div class="seg" role="tablist"><i></i><button role="tab" data-tab="fy" aria-selected="'+(homeTab==='fy')+'">For you</button><button role="tab" data-tab="fo" aria-selected="'+(homeTab==='fo')+'">Following</button></div>'+
 '<div class="home-tools"><div class="home-categories" role="group" aria-label="Filter home feed">'+filters.map(label=>'<button class="home-filter" data-home-filter="'+label+'" aria-pressed="'+(homeFilter===label)+'">'+label+'</button>').join('')+'</div>'+
 '<div class="feed-actions"><button class="chip" data-open-topics>YOUR TOPICS</button><button class="chip" data-open-search>FIND CHANNELS & SOURCES</button></div></div>'+
 '<p class="meta pad home-feed-status" id="homeFeedStatus" aria-live="polite">Building your feed…</p><div id="homeFeedResults"></div>';
}
function renderVideoPage(){
 return pageTop('Videos','full-screen playback')+
 '<section class="video-controls"><div class="video-player-status"><p class="meta pad video-playback-status" id="videoPlaybackStatus" aria-live="polite" hidden></p><button class="chip" data-video-resume hidden>OPEN VIDEO PLAYER</button></div>'+
 '<h2>Find videos</h2><label class="si">'+ic('search')+'<input id="videoQuery" type="search" placeholder="Search videos, creators and topics" aria-label="Search videos" autocomplete="off"></label><button class="btn" data-video-search>SEARCH YOUTUBE</button><p class="meta pad" id="videoStatus">Choose a result to open the full-screen player. Search remains broad across YouTube.</p><div id="videoResults"></div></section>';
}

function renderMusicPage(){
 return pageTop('Music','your listening taste')+
 '<section class="music-now" id="musicNowPlaying"><div class="music-now-art"><img id="musicNowArtwork" src="" alt="Current track artwork"><span id="musicNowFallback">RUSH<br>MUSIC</span></div><div class="music-now-copy"><span class="stk">NOW PLAYING</span><h3 id="musicNowTitle">No music playing</h3><p id="musicNowArtist">Choose a track to start listening.</p><p class="meta" id="musicStatus">Your player will appear here when a track starts.</p><button class="btn k" data-open-player>OPEN PLAYER</button><button class="btn o" data-music-toggle aria-label="Play or pause music">'+ic('play')+' PLAY / PAUSE</button><div class="result-actions"><button class="chip" data-current-download>DOWNLOAD</button><button class="chip" data-current-mp3>DOWNLOAD MP3</button><button class="chip" data-current-save>SAVE</button></div></div></section>'+
 '<h2>Picked for your taste</h2><p class="meta pad" id="musicSuggestionStatus">Using your listening history and followed musicians.</p><div id="musicSuggestions"></div>'+
 '<h2>Discover music</h2><section class="music-find"><label class="si">'+ic('search')+'<input id="musicQuery" type="search" placeholder="Search songs, artists or albums" aria-label="Search music" autocomplete="off"></label><button class="btn" data-music-search>SEARCH MUSIC</button><div id="musicResults"></div></section>'+
 '<section><h2>Browse genres</h2><div class="gen">'+['Pop','Hip-hop','Electronic','Rock','Jazz','Ambient','Classical','Folk'].map((t,i)=>'<button class="cat" data-music-term="'+t+'" style="background:'+PAL[(i+1)%PAL.length]+'"><b>'+t+'</b><i aria-hidden="true">→</i></button>').join('')+'</div></section>'+
 '<section><h2>Recently played</h2><div id="musicRecent"><p class="meta pad">Your listening history will appear here.</p></div></section>';
}
function renderPlayerPage(){
 return '<section class="player-screen"><div class="player-head"><button class="chip" data-player-back>← MUSIC</button><span class="stk">NOW PLAYING</span></div>'+
 '<div class="player-art"><img id="playerArtwork" alt="Track artwork" hidden><span id="playerArtFallback">RUSH MUSIC</span></div>'+
 '<div class="player-track"><div class="player-track-copy"><h2 id="playerTitle">Choose a track</h2><p id="playerArtist">Your next favourite song starts here.</p><p class="meta" id="playerStatus" aria-live="polite">Nothing is playing yet.</p></div><button class="ib" id="playerSave" data-player-save aria-label="Save current track" disabled>♡</button></div>'+
 '<div class="player-progress"><input id="playerSeek" type="range" min="0" max="1000" step="1" value="0" disabled aria-label="Seek through track"><div class="player-time"><span id="playerElapsed">0:00</span><span id="playerDuration">0:00</span></div></div>'+
 '<div class="player-controls"><button class="ib" data-player-prev aria-label="Previous track" disabled>|◀</button><button class="ib p player-play" id="playerToggle" data-player-toggle aria-label="Play or pause" disabled>'+ic('play')+'</button><button class="ib" data-player-next aria-label="Next track" disabled>▶|</button></div>'+
 '<div class="player-utilities"><button class="chip" id="playerLyricsToggle" data-player-lyrics-toggle aria-expanded="false" disabled>SHOW LYRICS</button><button class="chip" id="playerModeToggle" data-player-watch-video hidden>WATCH VIDEO</button><button class="chip" data-player-queue>UP NEXT</button></div>'+
 '<section class="player-lyrics" id="playerLyrics" hidden><h2>Lyrics</h2><p class="meta" id="playerLyricsStatus" aria-live="polite">Open lyrics for the current track.</p><div class="lyrics-panel" id="playerLyricsPanel"></div></section></section>';
}
function renderNewsPage(){
 return pageTop('News','useful reporting')+
 '<section class="news-controls"><div class="chips news-chips">'+['Personalized','World','Technology','Science','Business','Sports','Culture','Health'].map((t,i)=>'<button class="chip" data-news-category="'+t+'" aria-pressed="'+(i===0)+'">'+t+'</button>').join('')+'</div><label class="si">'+ic('search')+'<input id="newsQuery" type="search" placeholder="Search headlines and publishers" aria-label="Search news" autocomplete="off"></label><button class="btn" data-news-search>SEARCH NEWS</button><p class="meta pad" id="newsStatus">Ranking reporting from your topics and followed publishers…</p><div id="newsResults"></div></section>';
}
function renderSearchPage(){
 return pageTop('Search','open discovery')+
 '<label class="si">'+ic('search')+'<input id="q" type="search" placeholder="Search videos, music, news, channels, playlists or artists" aria-label="Search all Rush" autocomplete="off"></label><button class="btn" data-unified-search>SEARCH ALL SOURCES</button>'+
 '<div class="chips search-filters"><button class="chip" data-search-filter="all" aria-pressed="true">All</button><button class="chip" data-search-filter="videos" aria-pressed="false">Videos</button><button class="chip" data-search-filter="music" aria-pressed="false">Music</button><button class="chip" data-search-filter="news" aria-pressed="false">News</button><button class="chip" data-search-filter="channels" aria-pressed="false">Channels</button><button class="chip" data-search-filter="playlists" aria-pressed="false">Playlists</button></div>'+
 '<p class="meta pad" id="unifiedStatus">Search is broad. Recommendation rules do not restrict these results.</p><div id="unifiedResults"></div><div id="publicPlaylistContent"></div>';
}
function filterTopicNames(topics,query){
 const q=String(query||'').trim().toLocaleLowerCase();
 return topics.filter(topic=>!q||topic.toLocaleLowerCase().includes(q));
}
function updateTopicOptions(){
 const input=$('#topicQuery'),query=input?input.value:'';
 const visible=new Set(filterTopicNames(TOPICS,query));
 const buttons=[...document.querySelectorAll('[data-topic]')];
 let shown=0;
 buttons.forEach(button=>{const show=visible.has(button.dataset.topic);button.hidden=!show;if(show)shown++});
 const empty=$('#topicNoResults');if(empty)empty.hidden=shown>0;
 const status=$('#topicStatus');
 if(status)status.textContent=buttons.filter(b=>b.getAttribute('aria-pressed')==='true').length+' selected · '+TOPICS.length+' interests';
}
function renderTopicsPage(){
 return pageTop('Personalise','your interests and followed sources')+
 '<p class="pad meta">Choose subjects you care about. Recommendations prioritise useful, informative material; every subject remains available in Search.</p>'+
 '<label class="si">'+ic('search')+'<input id="topicQuery" type="search" maxlength="80" placeholder="Search interests" aria-label="Search interests" autocomplete="off"></label>'+
 '<div class="meta pad" id="topicStatus" aria-live="polite">'+selectedTopics().length+' selected · '+TOPICS.length+' interests</div>'+
 '<div class="topics-grid" id="topicOptions">'+TOPICS.map(t=>'<button class="chip topic-chip" data-topic="'+esc(t)+'" aria-pressed="'+selectedTopics().includes(t)+'">'+esc(t)+'</button>').join('')+'</div>'+
 '<p class="meta pad" id="topicNoResults" aria-live="polite" hidden>No interests match that search.</p>'+
 '<div class="feed-actions"><button class="btn k" data-save-topics>SAVE INTERESTS</button></div>'+
 '<h2>Followed sources</h2><p class="meta pad">Follow any channel, publisher or musician. Followed YouTube channels can all supply For You candidates; productive and informative items rank higher.</p><div id="followedSources"></div>';
}
const R={home:renderHome,videos:renderVideoPage,music:renderMusicPage,player:renderPlayerPage,news:renderNewsPage,search:renderSearchPage,topics:renderTopicsPage,library:renderLibraryPage};

let libraryView='saved',libraryData={saved:[],history:[],queue:[],playlists:[],items:{}},downloadData={items:[],count:0,bytes:0};
function renderLibraryPage(){
 return pageTop('Library','saved offline')+
 '<div class="chips library-tabs">'+[['saved','Saved'],['history','History'],['playlists','Playlists'],['queue','Queue'],['downloads','Downloads']].map(([key,label])=>'<button class="chip" data-library-view="'+key+'" aria-pressed="'+(libraryView===key)+'">'+label+'</button>').join('')+'</div>'+
 '<p class="meta pad" id="libraryStatus">Your collection is stored on this device.</p><div id="libraryContent"></div>';
}
function libraryMediaRow(id,kind,playlistId){
 const item=libraryData.items&&libraryData.items[id];if(!item)return '';
 const isMusic=!!item.music,rawThumb=item.thumbnail||'',thumb=/^https:\/\/[^\s"'<>]+$/i.test(rawThumb)?rawThumb:'';
 const play='<button class="chip" data-play-library-item data-id="'+esc(id)+'" data-music="'+isMusic+'">PLAY</button>';
 const queue='<button class="chip" data-queue-item data-id="'+esc(id)+'" data-title="'+esc(item.title)+'" data-creator="'+esc(item.creator)+'" data-music="'+isMusic+'" data-thumbnail="'+esc(thumb)+'">QUEUE</button>';
 const save=kind==='saved'?'<button class="chip" data-unsave-item data-id="'+esc(id)+'">REMOVE</button>':'<button class="chip" data-save-item data-id="'+esc(id)+'" data-title="'+esc(item.title)+'" data-creator="'+esc(item.creator)+'" data-music="'+isMusic+'" data-thumbnail="'+esc(thumb)+'">SAVE</button>';
 const history=kind==='history'?'<button class="chip" data-remove-history data-id="'+esc(id)+'">REMOVE HISTORY</button>':'';
 const removePlaylist=kind==='playlist'?'<button class="chip" data-remove-playlist-item data-playlist-id="'+esc(playlistId||'')+'" data-id="'+esc(id)+'">REMOVE FROM PLAYLIST</button>':'';
 const playlistButtons=(libraryData.playlists||[]).map(p=>'<button class="chip" data-playlist-add data-playlist-id="'+esc(p.id)+'" data-id="'+esc(id)+'" data-title="'+esc(item.title)+'" data-creator="'+esc(item.creator)+'" data-music="'+isMusic+'" data-thumbnail="'+esc(thumb)+'">+ '+esc(p.name)+'</button>').join('');
 return '<article class="library-item"><div class="library-item-main"><img class="a library-art" src="'+esc(thumb)+'" alt="" loading="lazy"><div class="library-copy"><b>'+esc(item.title||'Untitled media')+'</b><span>'+esc(item.creator||'YouTube')+' · '+(isMusic?'MUSIC':'VIDEO')+'</span></div></div><div class="result-actions">'+play+queue+save+history+removePlaylist+'</div>'+(playlistButtons?'<div class="result-actions playlist-quick-add">'+playlistButtons+'</div>':'')+'</article>';
}
function renderLibraryContent(){
 const root=$('#libraryContent');if(!root)return;
 document.querySelectorAll('[data-library-view]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.libraryView===libraryView)));
 let html='';
 if(libraryView==='saved'||libraryView==='history'){
  const ids=libraryView==='saved'?libraryData.saved:libraryData.history;
  html=ids.length?ids.map(id=>libraryMediaRow(id,libraryView)).join(''):'<div class="empty-state"><b>'+(libraryView==='saved'?'No saved items yet':'No playback history yet')+'</b><p>'+(libraryView==='saved'?'Use SAVE on a video or music result to keep it here.':'Play a video or track and it will appear here.')+'</p></div>';
  if(libraryView==='history'&&ids.length)html='<div class="feed-actions"><button class="chip" data-clear-history>CLEAR HISTORY</button></div>'+html;
 }else if(libraryView==='playlists'){
  html='<div class="feed-actions"><input class="library-input" id="newPlaylistName" maxlength="80" placeholder="New playlist name" aria-label="New playlist name"><button class="btn k" data-create-playlist>CREATE PLAYLIST</button></div>';
  html+=(libraryData.playlists||[]).length?(libraryData.playlists||[]).map(p=>{
   const tracks=(p.items||[]).map(id=>libraryMediaRow(id,'playlist',p.id)).join('');
   return '<section class="library-playlist"><div class="playlist-heading"><div><b>'+esc(p.name)+'</b><span>'+(p.items||[]).length+' items</span></div><div class="result-actions"><button class="chip" data-play-playlist data-playlist-id="'+esc(p.id)+'">PLAY ALL</button><button class="chip" data-delete-playlist data-playlist-id="'+esc(p.id)+'">DELETE</button></div></div>'+(tracks||'<p class="meta pad">Add saved items to this playlist from Saved or History.</p>')+'</section>';
  }).join(''):'<div class="empty-state"><b>No playlists</b><p>Create a local playlist, then add saved videos and music to it.</p></div>';
 }else if(libraryView==='queue'){
  const ids=libraryData.queue||[];
  html=ids.length?'<div class="feed-actions"><button class="btn k" data-play-library-queue>PLAY QUEUE · '+ids.length+'</button><button class="chip" data-clear-queue>CLEAR QUEUE</button></div>'+ids.map((id,index)=>{
   const item=libraryData.items&&libraryData.items[id];if(!item)return '';
   return '<article class="library-queue-item"><span>'+(index+1)+'.</span><div class="library-copy"><b>'+esc(item.title||'Untitled media')+'</b><span>'+esc(item.creator||'')+'</span></div><button class="chip" data-move-queue data-from="'+index+'" data-to="'+Math.max(0,index-1)+'" '+(index===0?'disabled':'')+'>↑</button><button class="chip" data-move-queue data-from="'+index+'" data-to="'+Math.min(ids.length-1,index+1)+'" '+(index===ids.length-1?'disabled':'')+'>↓</button><button class="chip" data-remove-queue data-index="'+index+'">×</button></article>';
  }).join(''):'<div class="empty-state"><b>Your queue is empty</b><p>Use QUEUE on any video or music result to add it to Up next.</p></div>';
 }else{
  const items=downloadData.items||[];
  html='<p class="meta pad">'+(downloadData.count||0)+' downloads · '+formatBytes(downloadData.bytes||0)+' used in private app storage</p>';
  html+=items.length?items.map(item=>{
   const active=['RUNNING','ENQUEUED','BLOCKED'].includes(item.state);
   const status=item.complete?'Ready offline':item.state==='RUNNING'?(item.stage||('Downloading · '+item.progress+'%')):active?'Queued':(['FAILED','CANCELLED'].includes(item.state)?'Interrupted · resume to continue':'Preparing');
   return '<article class="library-item"><div class="library-item-main"><div class="a library-art"></div><div class="library-copy"><b>'+esc(item.title)+'</b><span>'+esc(item.creator)+' · '+(item.isAudio?'AUDIO':'VIDEO')+' · '+esc(item.quality)+' · .'+esc(item.extension)+'</span><em>'+esc(status)+'</em>'+(active?'<div class="download-track"><i style="width:'+Math.max(0,Math.min(100,+item.progress||0))+'%"></i></div>':'')+'</div></div><div class="result-actions">'+(item.complete?'<button class="chip" data-play-downloaded data-download-id="'+esc(item.id)+'">PLAY OFFLINE</button>':active?'<button class="chip" data-cancel-download data-download-id="'+esc(item.id)+'">CANCEL</button>':'<button class="chip" data-resume-download data-download-id="'+esc(item.id)+'">RESUME</button>')+'<button class="chip" data-delete-download data-download-id="'+esc(item.id)+'">DELETE</button></div></article>';
  }).join(''):'<div class="empty-state"><b>No offline downloads</b><p>Save videos and music for offline playback using the DOWNLOAD action on a result.</p></div>';
 }
 root.innerHTML=html;
}
function formatBytes(bytes){
 const n=Math.max(0,Number(bytes)||0);if(n<1024)return n+' B';const units=['KB','MB','GB'];let value=n/1024,index=0;while(value>=1024&&index<units.length-1){value/=1024;index++}return value.toFixed(1)+' '+units[index];
}
function loadLibrary(){
 if(!window.RushNative)return;
 RushNative.loadLibrary();RushNative.loadDownloads();
}
function refreshLibrary(){
 if(page==='library')loadLibrary();
}

const NAV=[['explore','Explore','home'],['search','Search','search'],['subscribed','Subscribed','follow'],['library','Library','library']];
$('#nav').innerHTML='<i class="pill"></i>'+NAV.map(([key,label,icon])=>'<a href="#'+key+'" data-k="'+key+'">'+ic(icon)+'<span>'+label+'</span></a>').join('');
function navPosition(){
 const active=$('#nav a[aria-current]'),nav=$('#nav');if(!active)return;
 nav.style.setProperty('--lx',active.offsetLeft+2+'px');nav.style.setProperty('--lw',active.offsetWidth-4+'px');
}
function indicator(){
 const seg=$('.seg'),button=seg&&seg.querySelector('[aria-selected="true"]');
 if(button){seg.style.setProperty('--tx',button.offsetLeft+'px');seg.style.setProperty('--tw',button.offsetWidth+'px')}
}
function renderSources(){
 const root=$('#followedSources');if(!root)return;
 root.innerHTML=prefs.sources.length?prefs.sources.map(s=>{
  return '<article class="source-card"><div class="source-info"><b>'+esc(s.name)+'</b><span>'+esc(s.kind)+' · Following</span><div class="source-actions">'+
   '<button class="chip" data-unfollow-kind="'+esc(s.kind)+'" data-source-id="'+esc(s.id)+'">UNFOLLOW</button></div></div></article>';
 }).join(''):'<p class="meta pad">You are not following any sources yet. Search for channels, publishers or musicians to add them.</p>';
}

function applyPrefs(data){
 if(data&&data.topics&&data.sources)prefs=data;
 const root=$('#topicOptions');
 if(root)root.querySelectorAll('[data-topic]').forEach(b=>b.setAttribute('aria-pressed',selectedTopics().includes(b.dataset.topic)));
 const status=$('#topicStatus');if(status)status.textContent=selectedTopics().length+' selected · '+TOPICS.length+' interests';
 renderSources();updateTopicOptions();
}
function loadFeedPreferences(){
 try{if(window.RushNative){const raw=RushNative.getFeedPreferences();if(raw)applyPrefs(JSON.parse(raw))}}catch(e){}
}
function go(){
 page=(location.hash||'#home').slice(1);if(!R[page])page='home';
 $('#v').innerHTML=R[page]();
 if(page==='topics')updateTopicOptions();
 const activePage=page==='player'?'music':page;
 document.querySelectorAll('#nav a').forEach(a=>a.dataset.k===activePage?a.setAttribute('aria-current','page'):a.removeAttribute('aria-current'));
 $('.app').classList.toggle('player-open',page==='player');
 $('#sc').scrollTop=0;navPosition();indicator();loadFeedPreferences();
 if(window.RushNative){
  RushNative.loadCurrentPlayback();
  if(page==='home')loadHomeFeed();
  if(page!=='videos')RushNative.hideVideo();
  if(page==='music'){RushNative.loadMusicHistory();RushNative.loadMusicSuggestions()}
  if(page==='player')RushNative.loadCurrentMusicLyrics();
  if(page==='news')RushNative.loadPersonalizedNews();
  if(page==='library')loadLibrary();
 }
}
function loadHomeFeed(){
 const status=$('#homeFeedStatus');if(status)status.textContent=homeTab==='fy'?'Finding a mix of videos, music and news for you…':'Loading posts from sources you follow…';
 if(window.RushNative)RushNative.loadHomeFeed(homeTab==='fy'?'forYou':'following');
}
function homeVideoTile(x,featured){
 const thumb=safeUrl(x.thumbnail);
 return '<article class="home-tile home-video-tile'+(featured?' home-video-feature':'')+'"><button class="home-media-open" data-video-id="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-creator="'+esc(x.artist||x.creator)+'" data-thumbnail="'+esc(thumb)+'" aria-label="Play '+esc(x.title)+'"><span class="home-media-art home-video-art">'+(thumb?'<img src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async" onerror="this.hidden=true">':'')+'<span class="home-media-kind">VIDEO</span><span class="home-play-mark">'+ic('play')+'</span></span><span class="home-tile-copy"><b>'+esc(x.title)+'</b><span>' +esc(x.artist||x.creator||'YouTube')+(x.duration?' · '+esc(x.duration):'')+'</span></span></button>'+mediaActions(x,false)+'</article>';
}
function homeMusicTile(x){
 const thumb=safeUrl(x.thumbnail);
 return '<article class="home-tile home-music-tile"><button class="home-media-open" data-track="'+esc(x.id)+'" data-title="'+esc(x.title)+'" data-artist="'+esc(x.artist||'')+'" data-thumbnail="'+esc(thumb)+'" aria-label="Play '+esc(x.title)+'"><span class="home-media-art home-music-art">'+(thumb?'<img src="'+esc(thumb)+'" alt="" loading="lazy" decoding="async" onerror="this.hidden=true">':'')+'<span class="home-media-kind">MUSIC</span><span class="home-play-mark">'+ic('play')+'</span></span><span class="home-tile-copy"><b>'+esc(x.title)+'</b><span>'+esc(x.artist||'Unknown artist')+(x.duration?' · '+esc(x.duration):'')+'</span></span></button>'+mediaActions(x,true)+'</article>';
}
function renderFeedResults(data){
 if(page!=='home')return;
 const expectedMode=homeTab==='fy'?'forYou':'following';
 if(data&&data.mode&&data.mode!==expectedMode)return;
 const feed=data||{videos:[],music:[],news:[]};
 homeFeedCache=feed;
 const root=$('#homeFeedResults'),status=$('#homeFeedStatus');if(!root)return;
 const allVideos=feed.videos||[],allMusic=feed.music||[],allNews=feed.news||[];
 const videos=homeFilter==='Music'||homeFilter==='News'?[]:allVideos;
 const music=homeFilter==='Videos'||homeFilter==='News'?[]:allMusic;
 const news=homeFilter==='Videos'||homeFilter==='Music'?[]:allNews;
 const tiles=[];
 if(videos.length)tiles.push(homeVideoTile(videos[0],true));
 if(music.length)tiles.push(homeMusicTile(music[0]));
 if(news.length)tiles.push('<div class="home-news-slot">'+newsCard(news[0])+'</div>');
 videos.slice(1).forEach(item=>tiles.push(homeVideoTile(item,false)));
 music.slice(1).forEach(item=>tiles.push(homeMusicTile(item)));
 news.slice(1).forEach(item=>tiles.push('<div class="home-news-slot">'+newsCard(item)+'</div>'));
 const count=videos.length+music.length+news.length;
 root.innerHTML=count?'<section class="home-feed-section"><div class="home-section-heading"><h2>'+(homeFilter==='All'?(homeTab==='fy'?'Picked for you':'From your subscriptions'):homeFilter)+'</h2><span>'+count+' ITEMS</span></div><div class="home-bento">'+tiles.join('')+'</div></section>':'<div class="empty-state"><b>'+(homeFilter==='All'?(feed.message||'Your feed is taking shape.'):'Nothing in '+esc(homeFilter)+' yet.')+'</b><p>'+(homeFilter==='All'?'Follow channels, musicians or publishers and choose topics to shape your feed. Search stays open to every kind of content.':'Try another category or explore all recommendations.')+'</p><button class="btn k" data-open-search>FIND SOURCES</button></div>';
 if(status)status.textContent=data.message||((allVideos.length+allMusic.length+allNews.length)+' items · '+(homeTab==='fy'?'For You':'Following')+' feed');
}
function renderChannels(items){
 const root=$('#channelResults');if(!root)return;
 root.innerHTML=items.map(channelCard).join('')||'<p class="meta pad">No channels found. Try the channel name or handle.</p>';
}
function renderSearchResults(){
 const root=$('#unifiedResults');if(!root||page!=='search')return;
 const groups={
  videos:unifiedData.videos,
  music:unifiedData.music,
  news:unifiedData.news,
  channels:unifiedData.channels,
  playlists:unifiedData.playlists
 };
 const names={videos:'Videos',music:'Music',news:'News',channels:'YouTube channels',playlists:'Public YouTube playlists'};
 const renderers={videos:items=>items.map(videoRow).join(''),music:items=>items.map(musicRow).join(''),news:items=>items.map(newsCard).join(''),channels:items=>items.map(channelCard).join(''),playlists:items=>items.map(playlistRow).join('')};
 root.innerHTML=Object.keys(groups).filter(k=>unifiedMode==='all'||unifiedMode===k).map(k=>{
  const items=groups[k];return section(names[k]+' · '+items.length,renderers[k](items));
 }).join('')||'<p class="meta pad">No results in this category.</p>';
 const total=Object.values(groups).reduce((sum,items)=>sum+items.length,0);
 const status=$('#unifiedStatus');if(status)status.textContent=total+' results · search keeps broader discovery open';
}
function runUnifiedSearch(){
 const q=$('#q')?.value.trim();if(!q)return;
 unifiedQuery=q;Object.keys(unifiedData).forEach(k=>unifiedData[k]=[]);
 const status=$('#unifiedStatus');if(status)status.textContent='Searching YouTube, YouTube Music and news sources…';
 $('#unifiedResults').innerHTML='';
 const publicRoot=$('#publicPlaylistContent');if(publicRoot)publicRoot.innerHTML='';
 publicPlaylistData=null;
 if(window.RushNative){RushNative.searchVideos(q);RushNative.searchMusic(q);RushNative.searchNews(q);RushNative.searchChannels(q);RushNative.searchPlaylists(q)}
}
function updateMini(){
 const mini=$('#mini');if(!mini)return;
 const show=!!mediaState.hasItem&&!(!mediaState.title);
 mini.classList.toggle('on',show);mini.inert=!show;
 $('.app').classList.toggle('has-mini',show);
 if(!show)return;
 $('#mt').textContent=mediaState.title||'Now playing';
 $('#ms').textContent=(mediaState.isMusic?'MUSIC':(mediaState.isVideo?'VIDEO':'MEDIA'))+(mediaState.artist?' · '+mediaState.artist:'');
 const a=$('#ma'),img=a&&a.querySelector('img'),artwork=mediaState.artwork||'';
 if(img){
  if(/^https:\/\/[^\s"'<>]+$/i.test(artwork)){img.src=artwork;img.hidden=false}
  else{img.removeAttribute('src');img.hidden=true}
 }
 $('#mp').innerHTML=ic(mediaState.isPlaying?'pause':'play');
}
function updateCurrentPlayback(data){
 mediaState=Object.assign({},mediaState,data||{});updateMini();
 if(page==='videos'){const resume=$('[data-video-resume]');if(resume)resume.hidden=!(mediaState.hasItem&&mediaState.isVideo)}
 if(page==='videos'||page==='music'){
  const currentDownload=$('[data-current-download]'),currentMp3=$('[data-current-mp3]'),currentSave=$('[data-current-save]');
  if(currentDownload)currentDownload.disabled=!mediaState.hasItem||!!mediaState.isOffline;
  if(currentMp3)currentMp3.disabled=!mediaState.hasItem||!mediaState.isMusic||!!mediaState.isOffline;
  if(currentSave)currentSave.disabled=!mediaState.hasItem;
 }
 if(page==='music'){
  const isMusic=mediaState.hasItem&&mediaState.isMusic;
  const title=$('#musicNowTitle'),artist=$('#musicNowArtist'),status=$('#musicStatus'),img=$('#musicNowArtwork'),box=img&&img.parentElement;
  if(title)title.textContent=isMusic?(mediaState.title||'Unknown track'):'No music playing';
  if(artist)artist.textContent=isMusic?(mediaState.artist||'Unknown artist'):'Choose a track to start listening.';
  if(status)status.textContent=isMusic?(mediaState.isPlaying?'PLAYING':'PAUSED'):'Your player will appear here when a track starts.';
  if(img&&box){const artwork=safeUrl(mediaState.artwork);img.onerror=()=>{img.removeAttribute('src');box.classList.remove('has-art')};if(isMusic&&artwork){img.src=artwork;box.classList.add('has-art')}else{img.removeAttribute('src');box.classList.remove('has-art')}}
  const toggle=$('[data-music-toggle]');if(toggle)toggle.innerHTML=ic(isMusic&&mediaState.isPlaying?'pause':'play')+(isMusic&&mediaState.isPlaying?' PAUSE':' PLAY');
 }
 renderPlayerState();
}
function renderPlayerProgress(position,duration){
 const seek=$('#playerSeek');if(!seek)return;
 const total=Math.max(0,Number(duration)||0),current=Math.max(0,Number(position)||0);
 seek.max=String(Math.max(total,1000));seek.value=String(Math.min(current,total||current));seek.disabled=!mediaState.hasItem||!mediaState.isMusic||total<=0;
 const elapsed=$('#playerElapsed'),length=$('#playerDuration');
 if(elapsed)elapsed.textContent=formatTrackTime(current);if(length)length.textContent=formatTrackTime(total);
}
function formatTrackTime(milliseconds){
 const seconds=Math.floor(Math.max(0,Number(milliseconds)||0)/1000);return Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');
}
function renderPlayerState(){
 if(page!=='player')return;
 const isMusic=!!mediaState.hasItem&&!!mediaState.isMusic;
 const isVideo=!!mediaState.hasItem&&!!mediaState.isVideo;
 const hasMedia=isMusic||isVideo;
 const title=$('#playerTitle'),artist=$('#playerArtist'),status=$('#playerStatus'),img=$('#playerArtwork'),fallback=$('#playerArtFallback');
 if(title)title.textContent=hasMedia?(mediaState.title||'Unknown track'):'Choose a track';
 if(artist)artist.textContent=hasMedia?(mediaState.artist||'Unknown artist'):'Search or browse music, then select a track.';
 if(status)status.textContent=hasMedia?(mediaState.isPlaying?(isMusic?'PLAYING':'VIDEO MODE · PLAYING'):'PAUSED'):'Nothing is playing yet.';
 if(img&&fallback){
  const artwork=safeUrl(mediaState.artwork);
  if(hasMedia&&artwork){img.onerror=()=>{img.hidden=true;fallback.hidden=false};img.src=artwork;img.hidden=false;fallback.hidden=true}
  else{img.removeAttribute('src');img.hidden=true;fallback.hidden=false}
 }
 const toggle=$('#playerToggle');if(toggle){toggle.innerHTML=ic(isMusic&&mediaState.isPlaying?'pause':'play');toggle.disabled=!isMusic}
 const previous=$('[data-player-prev]'),next=$('[data-player-next]');
 if(previous)previous.disabled=!isMusic;if(next)next.disabled=!isMusic;
 const lyrics=$('[data-player-lyrics-toggle]');if(lyrics)lyrics.disabled=!isMusic;
 const save=$('#playerSave');if(save)save.disabled=!isMusic;
 const modeToggle=$('#playerModeToggle');
 if(modeToggle){
  modeToggle.hidden=!hasMedia;
  modeToggle.disabled=!hasMedia;
  if(isMusic){
   modeToggle.removeAttribute('data-player-listen-audio');
   modeToggle.setAttribute('data-player-watch-video','');
   modeToggle.textContent='WATCH VIDEO';
  }else if(isVideo){
   modeToggle.removeAttribute('data-player-watch-video');
   modeToggle.setAttribute('data-player-listen-audio','');
   modeToggle.textContent='LISTEN TO AUDIO';
  }else{
   modeToggle.removeAttribute('data-player-watch-video');
   modeToggle.removeAttribute('data-player-listen-audio');
  }
 }
 renderPlayerProgress(mediaState.position,mediaState.duration);
}
function renderPreferencePage(){
 applyPrefs(prefs);renderSources();updateTopicOptions();
}
function handleFollow(kind,id,name,productive,thumbnail=''){
 if(!window.RushNative)return;
 RushNative.followSource(kind,id,name,!!productive,thumbnail||'');
}
function refreshCurrentPage(){
 if(page==='home'||page==='explore')loadHomeFeed();
 if(page==='topics')renderPreferencePage();
 if(page==='search')renderSearchResults();
 if(page==='subscribed'){
  const root=$('#v');if(root)root.innerHTML=renderSubscribedPage();
  if(window.RushNative)RushNative.loadHomeFeed('following');
 }
 if(page==='library')renderLibraryContent();
}
document.addEventListener('input',e=>{if(e.target&&e.target.id==='topicQuery')updateTopicOptions()});
document.addEventListener('click',e=>{
 const b=e.target.closest('button');if(!b)return;
 if(b.id==='topicsShortcut'||b.hasAttribute('data-open-topics')||b.id==='profileShortcut'){location.hash='#topics';return}
 if(b.dataset.libraryView){
  libraryView=b.dataset.libraryView;renderLibraryContent();
  if(window.RushNative){RushNative.loadLibrary();if(libraryView==='downloads')RushNative.loadDownloads()}
  return
 }
 if(b.hasAttribute('data-create-playlist')){const name=$('#newPlaylistName')?.value.trim();if(name&&window.RushNative)RushNative.createLibraryPlaylist(name);return}
 if(b.hasAttribute('data-clear-history')){if(window.RushNative)RushNative.clearPlaybackHistory();return}
 if(b.hasAttribute('data-clear-queue')){if(window.RushNative)RushNative.clearPlaybackQueue();return}
 if(b.hasAttribute('data-play-library-queue')){if(window.RushNative)RushNative.playLibraryQueue();return}
 if(b.hasAttribute('data-play-library-item')){if(window.RushNative)RushNative.playLibraryItem(b.dataset.id);return}
 if(b.hasAttribute('data-play-playlist')){if(window.RushNative)RushNative.playLibraryPlaylist(b.dataset.playlistId);return}
 if(b.hasAttribute('data-save-item')){const d=b.dataset;if(window.RushNative)RushNative.saveLibraryItem(d.id,d.title||'',d.creator||'',d.music==='true',d.thumbnail||'');return}
 if(b.hasAttribute('data-unsave-item')){if(window.RushNative)RushNative.removeSavedLibraryItem(b.dataset.id);return}
 if(b.hasAttribute('data-remove-history')){if(window.RushNative)RushNative.removeHistoryItem(b.dataset.id);return}
 if(b.hasAttribute('data-remove-playlist-item')){if(window.RushNative)RushNative.removeLibraryItemFromPlaylist(b.dataset.playlistId,b.dataset.id);return}
 if(b.hasAttribute('data-queue-item')){const d=b.dataset;if(window.RushNative)RushNative.queueLibraryItem(d.id,d.title||'',d.creator||'',d.music==='true',d.thumbnail||'');return}
 if(b.hasAttribute('data-playlist-add')){const d=b.dataset;if(window.RushNative)RushNative.addLibraryItemToPlaylist(d.playlistId,d.id,d.title||'',d.creator||'',d.music==='true',d.thumbnail||'');return}
 if(b.hasAttribute('data-delete-playlist')){if(window.RushNative)RushNative.deleteLibraryPlaylist(b.dataset.playlistId);return}
 if(b.hasAttribute('data-remove-queue')){if(window.RushNative)RushNative.removeQueueItem(Number(b.dataset.index));return}
 if(b.hasAttribute('data-move-queue')){if(window.RushNative)RushNative.moveQueueItem(Number(b.dataset.from),Number(b.dataset.to));return}
 if(b.hasAttribute('data-download-media')||b.hasAttribute('data-download-mp3')||b.hasAttribute('data-download-id')){const d=b.dataset;if(b.hasAttribute('data-download-id'))return;if(window.RushNative){RushNative.downloadMedia(d.id,d.title||'',d.creator||'',d.music==='true',b.hasAttribute('data-download-mp3'))}return}
 if(b.hasAttribute('data-play-downloaded')){if(window.RushNative)RushNative.playDownloaded(b.dataset.downloadId);return}
 if(b.hasAttribute('data-cancel-download')){if(window.RushNative)RushNative.cancelDownload(b.dataset.downloadId);return}
 if(b.hasAttribute('data-resume-download')){if(window.RushNative)RushNative.resumeDownload(b.dataset.downloadId);return}
 if(b.hasAttribute('data-delete-download')){if(window.RushNative)RushNative.deleteDownload(b.dataset.downloadId);return}
 if(b.hasAttribute('data-current-download')||b.hasAttribute('data-current-mp3')||b.hasAttribute('data-current-save')){const d=mediaState;if(!d.hasItem||!d.mediaId)return;if(window.RushNative){if(b.hasAttribute('data-current-save'))RushNative.saveLibraryItem(d.sourceId||d.mediaId,d.title||'',d.artist||'',!!d.isMusic,d.artwork||'');else if(!d.isOffline)RushNative.downloadMedia(d.sourceId||d.mediaId,d.title||'',d.artist||'',!!d.isMusic,b.hasAttribute('data-current-mp3'))}return}
 if(b.hasAttribute('data-open-search')){location.hash='#search';return}
 if(b.dataset.homeFilter){homeFilter=b.dataset.homeFilter;document.querySelectorAll('[data-home-filter]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.homeFilter===homeFilter)));renderFeedResults(homeFeedCache);return}
 if(b.dataset.tab){homeTab=b.dataset.tab;$('#v').innerHTML=R.home();indicator();loadHomeFeed();return}
 if(b.hasAttribute('data-save-topics')){saveTopics();return}
 if(b.dataset.topic){const on=b.getAttribute('aria-pressed')!=='true';b.setAttribute('aria-pressed',String(on));updateTopicOptions();return}
 if(b.hasAttribute('data-unified-search')){runUnifiedSearch();return}
 if(b.dataset.searchFilter){unifiedMode=b.dataset.searchFilter;document.querySelectorAll('[data-search-filter]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));const publicRoot=$('#publicPlaylistContent');if(publicRoot)publicRoot.innerHTML='';renderSearchResults();return}
 if(b.hasAttribute('data-public-playlist-open')){
  const root=$('#publicPlaylistContent');if(root)root.innerHTML='<p class="meta pad">Loading playlist…</p>';
  if(window.RushNative)RushNative.browsePublicPlaylist(b.dataset.playlistId,b.dataset.playlistTitle||'YouTube playlist');
  return
 }
 if(b.hasAttribute('data-queue-public-playlist')){
  const items=(publicPlaylistData?.items||[]).slice(0,50).map(x=>({id:x.id,title:x.title,creator:x.creator,thumbnail:x.thumbnail}));
  if(window.RushNative&&items.length)RushNative.queuePublicPlaylist(publicPlaylistData.title||'YouTube playlist',JSON.stringify(items));
  return
 }
 if(b.hasAttribute('data-video-search')){const q=$('#videoQuery')?.value.trim();if(q&&window.RushNative){$('#videoStatus').textContent='Searching YouTube…';$('#videoResults').innerHTML='';RushNative.searchVideos(q)}return}
 if(b.hasAttribute('data-channel-search')){const q=$('#channelQuery')?.value.trim();if(q&&window.RushNative){$('#channelStatus').textContent='Searching channels…';$('#channelResults').innerHTML='';RushNative.searchChannels(q)}return}
 if(b.hasAttribute('data-music-search')){const q=$('#musicQuery')?.value.trim();if(q&&window.RushNative){$('#musicStatus').textContent='Searching YouTube Music…';$('#musicResults').innerHTML='';RushNative.searchMusic(q)}return}
 if(b.dataset.musicTerm){const input=$('#musicQuery');if(input)input.value=b.dataset.musicTerm;if(window.RushNative)RushNative.searchMusic(b.dataset.musicTerm);return}
 if(b.hasAttribute('data-news-search')){const q=$('#newsQuery')?.value.trim();if(q&&window.RushNative)RushNative.searchNews(q);return}
 if(b.dataset.newsCategory){document.querySelectorAll('[data-news-category]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));if(b.dataset.newsCategory==='Personalized')RushNative.loadPersonalizedNews();else if(window.RushNative)RushNative.loadNews(b.dataset.newsCategory);return}
 if(b.dataset.followKind){if(sourceExists(b.dataset.followKind,b.dataset.sourceId))RushNative.unfollowSource(b.dataset.followKind,b.dataset.sourceId);else handleFollow(b.dataset.followKind,b.dataset.sourceId,b.dataset.sourceName,false,b.dataset.sourceThumbnail||'');return}
 if(b.dataset.unfollowKind){if(window.RushNative)RushNative.unfollowSource(b.dataset.unfollowKind,b.dataset.sourceId);return}
 if(b.dataset.videoId){const d=b.dataset;const status=$('#videoPlaybackStatus');if(status){status.hidden=false;status.textContent='Preparing video…'}if(window.RushNative)RushNative.playVideo(d.videoId,d.title,d.creator,d.thumbnail);return}
  if(b.hasAttribute('data-video-resume')){if(window.RushNative)RushNative.showCurrentVideo();return}
 if(b.dataset.track){const d=b.dataset;const status=$('#musicStatus');if(status)status.textContent='Preparing track…';if(window.RushNative)RushNative.playMusic(d.track,d.title,d.artist,d.thumbnail);location.hash='#player';return}
 if(b.dataset.newsUrl){if(window.RushNative)RushNative.openNews(b.dataset.newsUrl);return}
 if(b.hasAttribute('data-video-fullscreen')){if(window.RushNative)RushNative.setVideoFullscreen(true);return}
 if(b.hasAttribute('data-video-comments')){if(window.RushNative)RushNative.openNews('https://www.youtube.com/watch?v='+encodeURIComponent(currentVideo.id)+'#comments');return}
 if(b.hasAttribute('data-video-close')){if(window.RushNative)RushNative.hideVideo();return}
 if(b.hasAttribute('data-open-player')){location.hash='#player';return}
  if(b.hasAttribute('data-player-back')){location.hash='#music';return}
  if(b.hasAttribute('data-player-watch-video')){
   const d=mediaState;
   if(d.hasItem&&d.isMusic&&d.mediaId&&window.RushNative)RushNative.playVideo(d.mediaId,d.title||'',d.artist||'',d.artwork||'');
   return;
  }
  if(b.hasAttribute('data-player-listen-audio')){
   const d=mediaState;
   if(d.hasItem&&d.isVideo&&d.mediaId&&window.RushNative)RushNative.playMusic(d.mediaId,d.title||'',d.artist||'',d.artwork||'');
   return;
  }
  if(b.hasAttribute('data-player-back')){homeFilter='Music';location.hash='#explore';return}
 if(b.hasAttribute('data-player-back')){homeFilter='Music';location.hash='#explore';return}
 if(b.hasAttribute('data-player-toggle')){if(window.RushNative)RushNative.togglePlayback();return}
  if(b.hasAttribute('data-player-prev')){if(window.RushNative)RushNative.skipPrevious();return}
  if(b.hasAttribute('data-player-next')){if(window.RushNative)RushNative.skipNext();return}
  if(b.hasAttribute('data-player-save')){if(window.RushNative&&mediaState.hasItem&&mediaState.isMusic)RushNative.saveLibraryItem(mediaState.sourceId||mediaState.mediaId,mediaState.title||'',mediaState.artist||'',true,mediaState.artwork||'');return}
  if(b.hasAttribute('data-player-queue')){libraryView='queue';location.hash='#library';return}
  if(b.hasAttribute('data-player-lyrics-toggle')){
   const panel=$('#playerLyrics'),toggle=$('#playerLyricsToggle');
   if(panel){panel.hidden=!panel.hidden;if(toggle){toggle.setAttribute('aria-expanded',String(!panel.hidden));toggle.textContent=panel.hidden?'SHOW LYRICS':'HIDE LYRICS'}if(!panel.hidden&&window.RushNative)RushNative.loadCurrentMusicLyrics()}
   return
  }
  if(b.hasAttribute('data-music-toggle')){if(mediaState.hasItem&&mediaState.isMusic&&window.RushNative)RushNative.togglePlayback();return}
 if(b.dataset.act==='toggle'){if(window.RushNative)RushNative.togglePlayback();return}
 if(b.dataset.act==='open'){if(mediaState.isMusic)location.hash='#player';else{location.hash='#videos';if(window.RushNative)RushNative.showCurrentVideo()}return}
});
document.addEventListener('change',e=>{
  if(e.target&&e.target.id==='playerSeek'&&window.RushNative)RushNative.seekPlaybackTo(Number(e.target.value)||0);
 });
 document.addEventListener('keydown',e=>{
 if(e.key!=='Enter')return;
 if(e.target.id==='q'){e.preventDefault();runUnifiedSearch()}
 if(e.target.id==='videoQuery'){e.preventDefault();const b=$('[data-video-search]');if(b)b.click()}
 if(e.target.id==='channelQuery'){e.preventDefault();const b=$('[data-channel-search]');if(b)b.click()}
 if(e.target.id==='musicQuery'){e.preventDefault();const b=$('[data-music-search]');if(b)b.click()}
 if(e.target.id==='newsQuery'){e.preventDefault();const b=$('[data-news-search]');if(b)b.click()}
});
window.rushFeedPreferences=data=>{applyPrefs(data);refreshCurrentPage()};
window.rushLibraryData=data=>{libraryData=data||libraryData;if(page==='library')renderLibraryContent()};
window.rushLibraryError=error=>{const status=$('#libraryStatus');if(status)status.textContent=error.message||'Library action failed.'};
window.rushDownloadsData=data=>{downloadData=data||downloadData;if(page==='library'&&libraryView==='downloads')renderLibraryContent()};
window.rushDownloadsError=error=>{const status=$('#libraryStatus');if(status)status.textContent=error.message||'Could not load downloads.'};
window.rushDownloadAction=state=>{const status=$('#libraryStatus');if(status)status.textContent=state.message||state.title||state.state;loadLibrary()};
window.rushQueuePlayback=state=>{const status=$('#libraryStatus');if(status)status.textContent=state.message||('Playing '+(state.count||0)+' queued items')};
window.rushOpenDownloads=()=>{libraryView='downloads';if(page==='library'){renderLibraryContent();if(window.RushNative)RushNative.loadDownloads()}else location.hash='#library'};
window.rushFeedPreferencesError=error=>{const t=$('#topicStatus');if(t)t.textContent=error.message||'Could not save preferences.'};
window.rushCurrentPlayback=updateCurrentPlayback;
window.rushHomeFeedResults=renderFeedResults;
window.rushChannelSearchResults=items=>{renderChannels(items);if(page==='search'){unifiedData.channels=items;renderSearchResults()}};
window.rushPlaylistSearchResults=items=>{unifiedData.playlists=items||[];renderSearchResults()};
window.rushPlaylistSearchError=error=>{const s=$('#unifiedStatus');if(s)s.textContent=error.message||'Playlist search failed.'};
window.rushPublicPlaylistResults=data=>{
 publicPlaylistData=data||{items:[]};
 const root=$('#publicPlaylistContent');if(!root||page!=='search')return;
 const items=publicPlaylistData.items||[];
 root.innerHTML='<section class="feed-section"><h2>'+esc(publicPlaylistData.title||'YouTube playlist')+' · '+items.length+'</h2><div class="feed-actions"><button class="chip" data-queue-public-playlist>QUEUE FIRST '+Math.min(50,items.length)+'</button><span class="meta">Up to 100 videos loaded from this public playlist.</span></div>'+(items.length?items.map(videoRow).join(''):'<p class="meta pad">No playable videos were found in this playlist.</p>')+'</section>';
};
window.rushPublicPlaylistError=error=>{const root=$('#publicPlaylistContent');if(root)root.innerHTML='<p class="meta pad">'+esc(error.message||'Could not open this playlist.')+'</p>'};
window.rushPlaylistQueued=data=>{const s=$('#unifiedStatus');if(s)s.textContent=(data.count||0)+' playlist videos added to your local queue. Open Library → Queue to play them.'};

window.rushChannelSearchError=error=>{const s=$('#channelStatus')||$('#unifiedStatus');if(s)s.textContent=error.message||'Channel search failed.'};
window.rushVideoSearchResults=items=>{
 unifiedData.videos=items;renderSearchResults();
 const root=$('#videoResults');if(root)root.innerHTML=items.map(videoRow).join('')||'<p class="meta pad">No videos found. Try another search.</p>';
 const status=$('#videoStatus');if(status)status.textContent=items.length+' videos found';
};
window.rushVideoSearchError=error=>{const s=$('#videoStatus')||$('#unifiedStatus');if(s)s.textContent=error.message||'Video search failed.'};
window.rushVideoPlaybackState=state=>{
 const status=$('#videoPlaybackStatus');
 if(status){status.hidden=false;status.textContent=state.state==='playing'?'NOW PLAYING · '+(state.title||'Video'):state.message||state.state}
 if(state.state==='playing'&&window.RushNative)RushNative.loadCurrentPlayback();
};
window.rushVideoCaptions=lines=>{const root=$('#videoCaptions');if(root)root.textContent=lines.join(' ')};
window.rushMusicSearchResults=items=>{
 unifiedData.music=items;renderSearchResults();
 const root=$('#musicResults');if(root)root.innerHTML=items.map(musicRow).join('')||'<p class="meta pad">No tracks found. Try another search.</p>';
 const status=$('#musicStatus');if(status)status.textContent=items.length+' tracks found';
};
window.rushMusicSearchError=error=>{const s=$('#musicStatus')||$('#unifiedStatus');if(s)s.textContent=error.message||'Music search failed.'};
window.rushMusicHistory=items=>{const root=$('#musicRecent');if(root)root.innerHTML=items.slice(0,8).map(musicHistoryRow).join('')||'<p class="meta pad">No tracks played yet.</p>'};
window.rushMusicSuggestionResults=data=>{
 const items=data.items||[],root=$('#musicSuggestions'),status=$('#musicSuggestionStatus');
 if(root)root.innerHTML=items.map(musicRow).join('')||'<p class="meta pad">'+esc(data.message||'Play music or follow an artist to establish your taste profile.')+'</p>';
 if(status)status.textContent=data.message||'Ranked using followed musicians and recent listening history.';
};
window.rushMusicPlaybackState=state=>{
 const status=$('#musicStatus');if(status&&page==='music')status.textContent=state.state==='playing'?'PLAYING · '+(state.title||'track'):state.state==='loading'?'LOADING · '+(state.title||'track'):state.message||state.state;
 const playerStatus=$('#playerStatus');if(playerStatus&&page==='player')playerStatus.textContent=state.state==='playing'?'PLAYING · '+(state.title||'track'):state.message||state.state;
 if(state.state==='playing'||state.state==='paused'||state.state==='error')if(window.RushNative){RushNative.loadCurrentPlayback();if(page==='player'&&state.state==='playing')RushNative.loadCurrentMusicLyrics()}
};
window.rushMusicLyricsLoading=()=>{const s=$('#playerLyricsStatus');if(s)s.textContent='Loading lyrics…'};
window.rushMusicLyricsError=error=>{const s=$('#playerLyricsStatus');if(s)s.textContent=error.message||'Lyrics unavailable.';const root=$('#playerLyricsPanel');if(root)root.innerHTML=''};
window.rushMusicLyrics=data=>{
 const status=$('#playerLyricsStatus'),root=$('#playerLyricsPanel');if(!root)return;
 const lines=(data.synced||'').split(/\r?\n/).map(line=>{
  const matches=[...line.matchAll(/\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g)];
  const text=line.slice(matches.length?matches[matches.length-1].index+matches[matches.length-1][0].length:0).trim();
  return matches.map(m=>({time:(+m[1]*60)+(+m[2])+(Number((m[3]||'').padEnd(3,'0'))/1000),text})).filter(x=>x.text);
 }).flat().sort((a,b)=>a.time-b.time);
 if(lines.length){root.innerHTML=lines.map((x,i)=>'<button class="lyric-line" data-lyric-index="'+i+'">'+esc(x.text)+'</button>').join('');window._rushLyrics=lines;if(status)status.textContent='Synced lyrics · '+(data.title||'')}
 else if(data.plain){root.textContent=data.plain;window._rushLyrics=[];if(status)status.textContent='Lyrics · not timed'}
 else{root.textContent='';window._rushLyrics=[];if(status)status.textContent='No lyrics available for this track.'}
};
window.rushNewsLoading=state=>{const s=$('#newsStatus');if(s)s.textContent='Loading: '+state.query+'…'};
window.rushNewsError=error=>{const s=$('#newsStatus')||$('#unifiedStatus');if(s)s.textContent=error.message||'Could not load news.'};
window.rushNewsResults=items=>{
 unifiedData.news=items;renderSearchResults();
 const root=$('#newsResults');if(root)root.innerHTML=items.map(newsCard).join('')||'<p class="meta pad">No articles found.</p>';
 const status=$('#newsStatus');if(status)status.textContent=items.length+' articles · ranked by your topics and publisher follows';
};
window.rushMusicLyricsLoading=()=>{
 window._rushLyrics=[];
 const status=$('#playerLyricsStatus'),root=$('#playerLyricsPanel');
 if(status)status.textContent='Loading lyrics…';
 if(root)root.replaceChildren();
};
setInterval(()=>{
 if(page==='library'&&libraryView==='downloads'&&window.RushNative)RushNative.loadDownloads();
},2000);
setInterval(()=>{
 if(page!=='player'||!window._rushLyrics?.length||!window.RushNative||!mediaState.isMusic)return;
 const pos=RushNative.musicPosition()/1000;let active=-1;
 window._rushLyrics.forEach((x,i)=>{if(x.time<=pos)active=i});
 document.querySelectorAll('.lyric-line').forEach((line,i)=>{
  const on=i===active;line.classList.toggle('active',on);
  if(on&&line.dataset.visible!=='1'){line.dataset.visible='1';line.scrollIntoView({block:'nearest',behavior:RM?'auto':'smooth'})}
  else if(!on)line.dataset.visible='0';
 });
},350);
setInterval(()=>{if(page==='player'&&window.RushNative&&mediaState.hasItem&&mediaState.isMusic)renderPlayerProgress(RushNative.musicPosition(),RushNative.musicDuration())},1000);
window.addEventListener('hashchange',()=>go());
window.addEventListener('resize',()=>{navPosition();indicator()});
