let rushProfile={displayName:"",handle:"",profileComplete:false};
let onboardingTopics=new Set(["Science","Technology","Music","Design"]);
let permissionSnapshot={};
let rushRefreshAt=0;
function readProfile(){
 try{
  if(window.RushNative){const value=JSON.parse(RushNative.getUserProfile()||"{}");if(value&&value.profileComplete)return value}
 }catch(error){}
 try{return JSON.parse(localStorage.getItem("rush_profile_v1")||"{}")}catch(error){return {}}
}
function persistProfile(name,handle){
 const value={displayName:name.trim().slice(0,40),handle:handle.trim().replace(/^@/,"").slice(0,24),profileComplete:true};
 let saved=false;
 try{if(window.RushNative)saved=!!RushNative.saveUserProfile(value.displayName,value.handle)}catch(error){}
 if(!saved){try{localStorage.setItem("rush_profile_v1",JSON.stringify(value));saved=true}catch(error){}}
 if(saved)rushProfile=value;
 return saved;
}
function renderProfileSetup(){
 const topics=TOPICS.filter((x,i)=>["Science","Technology","Music","Design"].includes(x));
 const all=TOPICS.slice(0,36);
 return '<section class="onboarding-screen"><div class="onboarding-brand"><span class="wm">rush<i></i></span><span class="stk">PERSONAL MEDIA SPACE</span></div>'+
 '<div class="onboarding-copy"><span class="onboarding-step">01 / PROFILE</span><h1>Make Rush yours.</h1><p>Set up a local profile so your feed can start with your interests. No Rush account is created.</p></div>'+
 '<label class="onboarding-field"><span>DISPLAY NAME</span><input id="profileName" type="text" maxlength="40" autocomplete="nickname" placeholder="What should we call you?" value="'+esc(rushProfile.displayName||"")+'"></label>'+
 '<label class="onboarding-field"><span>HANDLE <em>OPTIONAL</em></span><input id="profileHandle" type="text" maxlength="24" autocapitalize="none" autocomplete="off" placeholder="your.handle" value="'+esc(rushProfile.handle||"")+'"></label>'+
 '<div class="onboarding-topic-head"><b>Choose a few interests</b><span>EDIT ANYTIME IN SETTINGS</span></div><div class="onboarding-topics">'+all.map(x=>'<button type="button" class="onboarding-topic" data-onboarding-topic="'+esc(x)+'" aria-pressed="'+onboardingTopics.has(x)+'">'+esc(x)+'</button>').join("")+'</div>'+
 '<p class="onboarding-error" id="onboardingError" role="alert" hidden></p><button class="btn onboarding-continue" data-profile-complete>BUILD MY FEED <span>↗</span></button>'+
 '<p class="onboarding-footnote">Your profile, saved items and preferences stay on this device.</p></section>';
}
function renderProfilePage(){
 const name=esc(rushProfile.displayName||"Rush listener"),handle=rushProfile.handle?"@"+esc(rushProfile.handle):"Local profile";
 return pageTop("Profile","your identity")+
 '<section class="profile-hero"><span class="profile-avatar-large">'+esc((rushProfile.displayName||"R").trim().slice(0,1).toUpperCase())+'</span><div><h2>'+name+'</h2><p>'+handle+'</p><small>STORED ON THIS DEVICE</small></div></section>'+
 '<div class="settings-group"><button class="settings-row" data-edit-profile><span class="settings-icon">✎</span><span><b>Edit profile</b><small>Change your display name and handle</small></span><i>›</i></button><button class="settings-row" data-open-settings><span class="settings-icon">⚙</span><span><b>App settings</b><small>Permissions, playback and privacy</small></span><i>›</i></button></div>';
}
function renderEditProfilePage(){
 return pageTop("Edit profile","local profile")+
 '<section class="settings-section"><label class="onboarding-field"><span>DISPLAY NAME</span><input id="profileName" type="text" maxlength="40" value="'+esc(rushProfile.displayName||"")+'"></label>'+
 '<label class="onboarding-field"><span>HANDLE <em>OPTIONAL</em></span><input id="profileHandle" type="text" maxlength="24" value="'+esc(rushProfile.handle||"")+'"></label>'+
 '<p class="permission-note">Your profile is stored only on this device.</p><div class="feed-actions"><button class="btn k" data-profile-save>SAVE PROFILE</button><button class="chip" data-cancel-profile>CANCEL</button></div></section>';
}
function renderSettingsPage(){
 const p=permissionSnapshot||{};
 const notify=p.notificationsEnabled?"Enabled":p.notificationsBlockedInSystemSettings?"Blocked in Android settings":p.notificationRuntimePermissionRequired?"Not enabled":"Not required on this Android version";
 const notifyAction=p.notificationsBlockedInSystemSettings?"OPEN ANDROID SETTINGS":"ALLOW NOTIFICATIONS";
 return pageTop("Settings","app controls")+
 '<section class="settings-section"><div class="settings-section-title"><b>Permissions</b><span>RUSH ONLY ASKS WHEN NEEDED</span></div>'+
 '<div class="permission-card"><span class="settings-icon">♧</span><div><b>Notifications</b><small>'+esc(notify)+' · download and playback updates</small></div><button class="chip" data-notification-settings>'+(p.notificationsEnabled?"MANAGE":notifyAction)+'</button></div>'+
 '<div class="permission-note">Rush does not need access to your camera, microphone, contacts, location or shared storage for its current features. Downloads are stored inside app storage.</div>'+
 '<div class="permission-card"><span class="settings-icon">▣</span><div><b>Picture-in-Picture</b><small>Allow video to float over other apps</small></div><button class="chip" data-pip-settings>OPEN SETTINGS</button></div>'+
 '<div class="permission-card"><span class="settings-icon">⚙</span><div><b>Android app permissions</b><small>Review every permission granted to Rush</small></div><button class="chip" data-app-settings>MANAGE</button></div></section>'+
 '<section class="settings-section"><div class="settings-section-title"><b>Personalisation</b></div>'+
 '<button class="settings-row" data-open-profile><span class="settings-icon">◉</span><span><b>Profile</b><small>'+esc(rushProfile.displayName||"Local profile")+'</small></span><i>›</i></button>'+
 '<button class="settings-row" data-open-interests><span class="settings-icon">⌕</span><span><b>Interests and subscriptions</b><small>Control the topics that shape Explore</small></span><i>›</i></button></section>'+
 '<section class="settings-section"><div class="settings-section-title"><b>Playback</b></div>'+
 '<div class="settings-row informational"><span class="settings-icon">♫</span><span><b>Background audio</b><small>Playback controls are available through the Android media notification.</small></span></div>'+
 '<div class="settings-row informational"><span class="settings-icon">◫</span><span><b>Picture-in-Picture</b><small>Video can float inside Rush and use Android PiP when you leave the app, subject to system settings.</small></span></div></section>'+
 '<section class="settings-section"><div class="settings-section-title"><b>Privacy and information</b></div>'+
 '<button class="settings-row" data-open-privacy><span class="settings-icon">ⓘ</span><span><b>Privacy information</b><small>What is stored locally and what leaves your device</small></span><i>›</i></button>'+
 '<div class="settings-row informational"><span class="settings-icon">ⓘ</span><span><b>App version</b><small>Rush 1.0.0 · Android</small></span></div></section>';
}
function renderPrivacyPage(){
 return pageTop("Privacy","how Rush handles data")+
 '<section class="privacy-copy"><h2>Stored on this device</h2><p>Your display name and optional handle, selected interests, followed sources, search history, saved items, playlists and playback history are stored locally by Rush.</p>'+
 '<h2>Requests to content providers</h2><p>When you search, load recommendations or open a story, Rush sends the relevant query or request to the content provider needed to return that result. Provider services may receive standard connection information such as your IP address and device request metadata.</p>'+
 '<h2>Permissions</h2><p>Rush uses internet access for content discovery and playback. Notification permission is optional and requested for playback or download updates. Camera, microphone, contacts, location and shared-storage permissions are not required by the current app features.</p>'+
 '<h2>Control</h2><p>You can review Android permissions in Settings. Clearing Rush app data removes the local profile, preferences and locally saved application data.</p>'+
 '<p class="privacy-disclaimer">This is an in-app disclosure draft. Before publishing, replace it with a public privacy policy containing the developer’s legal contact details and a complete audit of the final app and SDK behaviour.</p></section>';
}
function refreshPermissionStatus(){
 try{permissionSnapshot=window.RushNative?JSON.parse(RushNative.getPermissionStatus()||"{}"):{}}
 catch(error){permissionSnapshot={}}
}
function renderNotifications(){
 const sheet=$("#notificationsSheet");if(sheet)sheet.hidden=false;
 document.body.classList.add("sheet-open");
 const close=sheet&&sheet.querySelector("[data-close-sheet]");if(close)close.focus();
}
function closeNotifications(){
 const sheet=$("#notificationsSheet");if(sheet)sheet.hidden=true;
 document.body.classList.remove("sheet-open");
 const bell=$("#notificationBell");if(bell)bell.focus();
}
function showProfileSetup(){
 page="setup";
 const app=$(".app");if(app)app.classList.add("setup-open");
 const nav=$("#nav");if(nav)nav.hidden=true;
 const top=$("#topAppBar");if(top)top.hidden=true;
 const root=$("#v");if(root)root.innerHTML=renderProfileSetup();
 const sc=$("#sc");if(sc)sc.scrollTop=0;
}
function startRush(){
 loadPrefs();
 rushProfile=readProfile();
 const route=(location.hash||"").replace(/^#/,"");
 if(!rushProfile.profileComplete||!rushProfile.displayName){
  if(location.hash!=="#setup")history.replaceState(null,"","#setup");
  showProfileSetup();
  return;
 }
 if(!route||route==="setup")history.replaceState(null,"","#explore");
 refreshPermissionStatus();
 go();
}
const goBeforeProfile=go;
go=function(){
 const route=(location.hash||"#explore").slice(1);
 if(route==="setup"){showProfileSetup();return}
 const app=$(".app");if(app)app.classList.remove("setup-open");
 const nav=$("#nav");if(nav)nav.hidden=false;
 const top=$("#topAppBar");if(top)top.hidden=false;
 if(route==="settings")refreshPermissionStatus();
 if(route==="profile")rushProfile=readProfile();
 if(route==="privacy"){const root=$("#v");if(root)root.innerHTML=renderPrivacyPage();page="privacy";return}
 if(route==="profile"){const root=$("#v");if(root)root.innerHTML=renderProfilePage();page="profile";return}
 if(route==="edit-profile"){const root=$("#v");if(root)root.innerHTML=renderEditProfilePage();page="edit-profile";return}
 if(route==="settings"){const root=$("#v");if(root)root.innerHTML=renderSettingsPage();page="settings";return}
 goBeforeProfile();
 const root=$("#v");if(root){root.classList.remove("screen-enter");requestAnimationFrame(()=>root.classList.add("screen-enter"))}
};
function saveOnboarding(){
 const name=$("#profileName")?.value.trim()||"";
 const handle=$("#profileHandle")?.value.trim()||"";
 const error=$("#onboardingError");
 if(!name){if(error){error.textContent="Enter a display name to continue.";error.hidden=false}return}
 if(!onboardingTopics.size){if(error){error.textContent="Choose at least one interest.";error.hidden=false}return}
 if(!persistProfile(name,handle)){if(error){error.textContent="Could not save the profile on this device.";error.hidden=false}return}
 if(window.RushNative)RushNative.saveFeedTopics(JSON.stringify([...onboardingTopics]));
 prefs.topics=[...onboardingTopics];
 history.replaceState(null,"","#explore");
 rushProfile=readProfile();
 go();
}
document.addEventListener("click",event=>{
 const button=event.target.closest("button");if(!button)return;
 const profileAction=button.hasAttribute("data-profile-complete")||button.hasAttribute("data-onboarding-topic")||button.hasAttribute("data-open-notifications")||button.hasAttribute("data-close-sheet")||button.hasAttribute("data-sheet-subscriptions")||button.hasAttribute("data-notification-settings")||button.hasAttribute("data-app-settings")||button.hasAttribute("data-pip-settings")||button.hasAttribute("data-open-settings")||button.hasAttribute("data-edit-profile")||button.hasAttribute("data-open-profile")||button.hasAttribute("data-open-interests")||button.hasAttribute("data-open-privacy")||button.hasAttribute("data-profile-save")||button.hasAttribute("data-cancel-profile")||button.hasAttribute("data-video-retry")||button.hasAttribute("data-feed-retry");
 if(!profileAction)return;
 event.preventDefault();event.stopImmediatePropagation();
 if(button.hasAttribute("data-video-retry")){
  if(window.RushNative&&currentVideo.id)RushNative.playVideo(currentVideo.id,currentVideo.title,currentVideo.creator,currentVideo.thumbnail);
  return;
 }
 if(button.hasAttribute("data-feed-retry")){
  if(window.RushNative){RushNative.loadHomeFeed("forYou");RushNative.loadHomeFeed("following")}
  return;
 }
 if(button.hasAttribute("data-profile-complete")){saveOnboarding();return}
 if(button.hasAttribute("data-onboarding-topic")){
  const topic=button.dataset.onboardingTopic;if(onboardingTopics.has(topic))onboardingTopics.delete(topic);else onboardingTopics.add(topic);
  button.setAttribute("aria-pressed",String(onboardingTopics.has(topic)));return;
 }
 if(button.hasAttribute("data-open-notifications")){renderNotifications();return}
 if(button.hasAttribute("data-close-sheet")){closeNotifications();return}
 if(button.hasAttribute("data-sheet-subscriptions")){closeNotifications();location.hash="#subscribed";return}
 if(button.hasAttribute("data-notification-settings")){
  refreshPermissionStatus();
  if(permissionSnapshot.notificationsEnabled||permissionSnapshot.notificationsBlockedInSystemSettings)window.RushNative.openApplicationSettings();
  else window.RushNative.requestNotificationPermission();
  return;
 }
 if(button.hasAttribute("data-app-settings")){window.RushNative.openApplicationSettings();return}
 if(button.hasAttribute("data-pip-settings")){window.RushNative.openPictureInPictureSettings();return}
 if(button.hasAttribute("data-open-settings")){location.hash="#settings";return}
 if(button.hasAttribute("data-edit-profile")){location.hash="#edit-profile";return}
 if(button.hasAttribute("data-open-profile")){location.hash="#profile";return}
 if(button.hasAttribute("data-open-interests")){location.hash="#topics";return}
 if(button.hasAttribute("data-open-privacy")){location.hash="#privacy";return}
 if(button.hasAttribute("data-cancel-profile")){location.hash="#profile";return}
 if(button.hasAttribute("data-profile-save")){
  const name=$("#profileName")?.value.trim()||"",handle=$("#profileHandle")?.value.trim()||"";
  if(!name)return;
  if(persistProfile(name,handle)){location.hash="#profile"}return;
 }
},true);
document.addEventListener("keydown",event=>{
 if(event.key==="Escape"){
  if(!$("#notificationsSheet")?.hidden){closeNotifications();event.preventDefault();return}
  if(page==="search"&&document.activeElement?.id==="q"){document.activeElement.blur();searchFocusOpen=false;event.preventDefault();return}
 }
});
window.rushHandleBack=function(){
 if(!$("#notificationsSheet")?.hidden){closeNotifications();return true}
 if(page==="search"&&document.activeElement?.id==="q"){
  document.activeElement.blur();searchFocusOpen=false;return true;
 }
 if(page==="privacy"||page==="profile"||page==="edit-profile"){location.hash="#library";return true}
 if(page==="settings"){location.hash="#library";return true}
 if(page==="topics"){location.hash="#settings";return true}
 if(page==="watch"){location.hash="#explore";return true}
 if(page==="player"){location.hash="#explore";return true}
 if(page==="explore"){return false}
 if(page!=="setup"){location.hash="#explore";return true}
 return false;
};
window.rushAppForeground=function(force){
 const now=Date.now();
 if(!force&&now-rushRefreshAt<150000)return;
 if(page==="settings"){refreshPermissionStatus();const settingsRoot=$("#v");if(settingsRoot)settingsRoot.innerHTML=renderSettingsPage();return}
 if(!window.RushNative||!["explore","subscribed"].includes(page))return;
 rushRefreshAt=now;
 if(page==="explore"){RushNative.loadHomeFeed("forYou");RushNative.loadHomeFeed("following")}
 if(page==="subscribed")RushNative.loadHomeFeed("following");
};
window.rushHomeFeedLoading=function(state){
 if(page==="explore"){
  const root=$("#exploreShelves");
  if(root&&!root.children.length)root.innerHTML='<section class="explore-shelf"><div class="shelf-heading"><h2>Building your mix</h2><small>LOADING YOUR FEED</small></div><div class="skeleton-shelf">'+Array.from({length:3},()=>'<div class="skeleton-tile"><i></i><b></b><span></span></div>').join("")+'</div><div class="skeleton-news"><i></i><span></span></div></section>';
  const status=$("#homeFeedStatus");if(status)status.textContent="Getting the latest from your sources…";
 }
};
window.rushHomeFeedError=function(error){
 const message=error.message||"Feed refresh failed.";
 const status=$("#homeFeedStatus");if(status)status.textContent=message+" Showing your last saved picks where available.";
 const root=$("#exploreShelves");
 const cachedCount=(homeFeedCache.videos||[]).length+(homeFeedCache.music||[]).length+(homeFeedCache.news||[]).length;
 if(root&&!cachedCount)root.innerHTML='<div class="feed-recovery"><b>Could not reach your feed</b><p>'+esc(message)+'</p><button class="btn" data-feed-retry>TRY AGAIN</button></div>';
};
window.rushVideoPreparation=function(){
 const overlay=$("#videoBuffering");if(!overlay)return;
 overlay.hidden=false;
 overlay.innerHTML='<span class="buffer-indicator"><i class="buffer-spinner"></i><b>CONNECTING TO VIDEO</b></span>';
};
window.rushVideoBuffering=function(buffering){
 const overlay=$("#videoBuffering");if(overlay)overlay.hidden=!buffering;
 const status=$("#videoPlaybackStatus");if(status&&buffering)status.textContent="Buffering video…";
};
window.rushVideoBufferingError=function(message){
 const overlay=$("#videoBuffering");
 if(overlay){overlay.hidden=false;overlay.innerHTML='<span class="buffer-indicator"><i class="buffer-error-mark">!</i><b>PLAYBACK INTERRUPTED</b><button class="btn" data-video-retry>TRY AGAIN</button></span>';}
 const status=$("#videoPlaybackStatus");if(status)status.textContent=message||"Playback failed. Check your connection and try again.";
};
window.rushMusicBuffering=function(state){
 const buffering=typeof state==="boolean"?state:!!(state&&state.buffering);
 const bufferState=window.rushMusicBufferState||(window.rushMusicBufferState={preparing:false,buffering:false});
 bufferState.buffering=buffering;
 const indicator=$("#musicBuffering");if(indicator)indicator.hidden=!(bufferState.preparing||bufferState.buffering);
 const status=$("#playerStatus");if(status&&buffering)status.textContent="BUFFERING AUDIO…";
};
window.setInterval(()=>{
 if(document.visibilityState==="visible"&&["explore","subscribed"].includes(page))window.rushAppForeground();
},180000);
