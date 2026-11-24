package com.rush.app

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MainActivity:ComponentActivity(){
 private var pipModeActive=false
 private var pipEligible=false
 private var pipAspectRatio=android.util.Rational(16,9)
 private lateinit var webView:android.webkit.WebView
 private lateinit var webViewAssetLoader:WebViewAssetLoader
 private lateinit var rushWebBridge: RushWebBridge
 private lateinit var rootLayout: android.widget.FrameLayout
 @androidx.media3.common.util.UnstableApi
 private var nativeVideoView: androidx.media3.ui.PlayerView? = null
 private var observedNativeVideoPlayer: androidx.media3.common.Player? = null
 private var nativeVideoBufferListener: androidx.media3.common.Player.Listener? = null
  @Volatile private var nativeVideoRequested=false
 private var nativeVideoFullscreen=false
 private var previousRequestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
 private var fullscreenExitButton:android.widget.TextView?=null
 private var videoErrorDialog:android.app.AlertDialog?=null
 private var webPageLoaded=false
 private var mainFrameLoadFailed=false
 private var startupOverlay:android.view.View?=null
 private var pendingOpenDownloads=false
 private val notificationPermissionLauncher=registerForActivityResult(
  androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
 ){ granted ->
  if(!granted)android.widget.Toast.makeText(this,"Downloads will continue, but Android notifications are disabled.",android.widget.Toast.LENGTH_LONG).show()
 }
 fun requestDownloadNotificationPermission(){
  runOnUiThread { requestDownloadNotificationPermissionOnMainThread() }
 }
 private fun requestDownloadNotificationPermissionOnMainThread(){
  if(android.os.Build.VERSION.SDK_INT<33)return
  val permission=android.Manifest.permission.POST_NOTIFICATIONS
  if(androidx.core.content.ContextCompat.checkSelfPermission(this,permission)==android.content.pm.PackageManager.PERMISSION_GRANTED)return
  val prefs=getSharedPreferences("rush_prefs",MODE_PRIVATE)
  val requestedBefore=prefs.getBoolean("notification_permission_requested",false)
  when{
   androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(this,permission)->{
    android.app.AlertDialog.Builder(this)
     .setTitle("Download notifications")
     .setMessage("Allow notifications to see download progress and completion while Rush works in the background.")
     .setPositiveButton("Continue"){_,_->notificationPermissionLauncher.launch(permission)}
     .setNegativeButton("Not now",null)
     .show()
   }
   !requestedBefore->{
    prefs.edit().putBoolean("notification_permission_requested",true).apply()
    notificationPermissionLauncher.launch(permission)
   }
   else->android.app.AlertDialog.Builder(this)
    .setTitle("Notifications are turned off")
    .setMessage("You can enable Rush notifications in Android settings. Playback and downloads can still work without notification permission.")
    .setPositiveButton("Open settings"){_,_->openApplicationSettings()}
    .setNegativeButton("Not now",null)
    .show()
  }
 }
 fun openApplicationSettings(){
  runCatching {
   startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    android.net.Uri.fromParts("package",packageName,null)))
  }.onFailure {
   android.widget.Toast.makeText(this,"Open Rush in Android app settings to manage permissions.",android.widget.Toast.LENGTH_LONG).show()
  }
 }
 fun openPictureInPictureSettings(){
  runCatching {
   startActivity(android.content.Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS",
    android.net.Uri.fromParts("package",packageName,null)))
  }.recoverCatching {
   startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    android.net.Uri.fromParts("package",packageName,null)))
  }
 }
 private fun openDownloadsFromNotification(){
  if(!webPageLoaded){pendingOpenDownloads=true;return}
  webView.evaluateJavascript("window.rushOpenDownloads ? window.rushOpenDownloads() : (location.hash='#library')",null)
 }
 @androidx.media3.common.util.UnstableApi
 fun showNativeVideo(player: androidx.media3.common.Player, fullscreen: Boolean = false) {
  nativeVideoRequested=true
  runOnUiThread {
   val view=nativeVideoView?:androidx.media3.ui.PlayerView(this).apply {
    useController=true
    controllerAutoShow=true
    setShowSubtitleButton(true)
    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
    setKeepContentOnPlayerReset(true)
    setBackgroundColor(android.graphics.Color.BLACK)
    elevation=resources.displayMetrics.density*8
   }.also { nativeVideoView=it }
   view.player=player
   if(observedNativeVideoPlayer!==player){
    nativeVideoBufferListener?.let { listener->observedNativeVideoPlayer?.removeListener(listener) }
    val listener=object:androidx.media3.common.Player.Listener{
     override fun onPlaybackStateChanged(playbackState:Int){
      publishVideoBuffering(playbackState==androidx.media3.common.Player.STATE_BUFFERING)
     }
     override fun onPlayerError(error:androidx.media3.common.PlaybackException){
      publishVideoBuffering(false)
      runOnUiThread { webView.evaluateJavascript("window.rushVideoBufferingError&&window.rushVideoBufferingError('Playback failed. Check your connection and try again.')",null) }
      showVideoPlaybackError(player)
     }
    }
    observedNativeVideoPlayer=player
    nativeVideoBufferListener=listener
    player.addListener(listener)
   }
   if(view.parent==null)rootLayout.addView(view,android.widget.FrameLayout.LayoutParams(1,1))
   setPipEligible(true)
   if(fullscreen)setNativeVideoFullscreen(true) else updateNativeVideoPosition()
  }
 }
 fun positionNativeVideo(left:Double,top:Double,width:Double,height:Double,mini:Boolean) {
  if(!nativeVideoRequested||nativeVideoFullscreen)return
  if(width<20||height<20)return
  runOnUiThread {
   val view=nativeVideoView?:return@runOnUiThread
   if(nativeVideoFullscreen||!nativeVideoRequested)return@runOnUiThread
   // CSS pixels from getBoundingClientRect must be converted to Android view pixels.
   val scale=resources.displayMetrics.density*webView.scale.coerceAtLeast(0.5f)
   val w=(width*scale).toInt().coerceAtLeast(1)
   val h=(height*scale).toInt().coerceAtLeast(1)
   val params=android.widget.FrameLayout.LayoutParams(w,h,android.view.Gravity.TOP or android.view.Gravity.START)
   params.leftMargin=webView.left+(left*scale).toInt()
   params.topMargin=webView.top+(top*scale).toInt()
   view.layoutParams=params
   view.visibility=android.view.View.VISIBLE
   view.bringToFront()
   if(mini)updatePipAspectRatio(w,h)
   setPipEligible(true)
  }
 }
 private fun showVideoPlaybackError(player:androidx.media3.common.Player){
  runOnUiThread {
   if(isFinishing||isDestroyed||!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))return@runOnUiThread
   val item=player.currentMediaItem
   val videoId=item?.mediaId.orEmpty()
   val title=item?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Video" }
   val creator=item?.mediaMetadata?.artist?.toString().orEmpty()
   val thumbnail=item?.mediaMetadata?.artworkUri?.toString().orEmpty()
   videoErrorDialog?.dismiss()
   videoErrorDialog=android.app.AlertDialog.Builder(this)
    .setTitle("Video playback interrupted")
    .setMessage("Rush could not continue this stream. Check your connection and retry.")
    .setPositiveButton("Retry"){_,_->
     if(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))){
      rushWebBridge.playVideo(videoId,title,creator,thumbnail)
     }else{
      runCatching { player.prepare();player.play() }
     }
    }
    .setNegativeButton("Close"){_,_->rushWebBridge.closeVideo()}
    .create()
    .also { dialog->
     dialog.setOnDismissListener { if(videoErrorDialog===dialog)videoErrorDialog=null }
     dialog.show()
    }
  }
 }
 private fun publishVideoBuffering(buffering:Boolean){
  if(!::webView.isInitialized)return
  runOnUiThread {
   if(webPageLoaded)webView.evaluateJavascript("window.rushVideoBuffering&&window.rushVideoBuffering($buffering)",null)
  }
 }
 fun updateNativeVideoPosition() {
  if(!nativeVideoRequested||nativeVideoFullscreen)return
  runOnUiThread {
   if(!nativeVideoRequested||nativeVideoFullscreen)return@runOnUiThread
   if(::webView.isInitialized)webView.evaluateJavascript("window.rushUpdateVideoPosition&&window.rushUpdateVideoPosition()",null)
  }
 }

 fun setNativeVideoFullscreen(enabled:Boolean) {
  runOnUiThread {
   if(nativeVideoFullscreen==enabled)return@runOnUiThread
   nativeVideoFullscreen=enabled
   val density=resources.displayMetrics.density
   if(enabled) {
    previousRequestedOrientation=requestedOrientation
    requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    val insetsController=WindowInsetsControllerCompat(window,window.decorView)
    insetsController.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
    nativeVideoView?.apply {
     layoutParams=android.widget.FrameLayout.LayoutParams(
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT)
     visibility=android.view.View.VISIBLE
     bringToFront()
    }
    val exit=fullscreenExitButton?:android.widget.TextView(this).apply {
     text="×"
     textSize=30f
     gravity=android.view.Gravity.CENTER
     setTextColor(android.graphics.Color.WHITE)
     background=android.graphics.drawable.GradientDrawable().apply {
      setColor(0xCC111111.toInt())
      cornerRadius=24f*density
     }
     elevation=16f*density
     setOnClickListener { setNativeVideoFullscreen(false) }
    }.also { fullscreenExitButton=it; rootLayout.addView(it) }
    val size=(52*density).toInt()
    exit.layoutParams=android.widget.FrameLayout.LayoutParams(size,size,
     android.view.Gravity.TOP or android.view.Gravity.END).apply {
      topMargin=(18*density).toInt()
      marginEnd=(16*density).toInt()
     }
    exit.visibility=android.view.View.VISIBLE
    exit.bringToFront()
   } else {
    fullscreenExitButton?.visibility=android.view.View.GONE
    WindowInsetsControllerCompat(window,window.decorView).show(WindowInsetsCompat.Type.systemBars())
    requestedOrientation=previousRequestedOrientation
    webView.post { updateNativeVideoPosition() }
   }
  }
 }
 fun hideNativeVideo() {
  nativeVideoRequested=false
  setPipEligible(false)
  if(nativeVideoFullscreen)setNativeVideoFullscreen(false)
  runOnUiThread { nativeVideoView?.visibility=android.view.View.GONE }
 }
 private fun buildPipParams():android.app.PictureInPictureParams {
  val builder=android.app.PictureInPictureParams.Builder().setAspectRatio(pipAspectRatio)
  if(android.os.Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(pipEligible)
  nativeVideoView?.let { view ->
   val sourceRect=android.graphics.Rect()
   if(view.isShown&&view.getGlobalVisibleRect(sourceRect)&&!sourceRect.isEmpty)builder.setSourceRectHint(sourceRect)
  }
  return builder.build()
 }
 fun setPipEligible(eligible:Boolean){
  pipEligible=eligible
  setPictureInPictureParams(buildPipParams())
 }
 fun updatePipAspectRatio(width:Int,height:Int){
  if(width<=0||height<=0)return
  val ratio=(width.toFloat()/height).coerceIn(1f/2.39f,2.39f)
  pipAspectRatio=android.util.Rational((ratio*1000).toInt().coerceAtLeast(1),1000)
  if(pipEligible)setPictureInPictureParams(buildPipParams())
 }
 fun enterPlayerPip(){
  if(pipEligible)enterPictureInPictureMode(buildPipParams())
 }
 override fun onUserLeaveHint(){
  super.onUserLeaveHint()
  if(android.os.Build.VERSION.SDK_INT<31&&pipEligible)enterPlayerPip()
 }
 private fun createRushWebView():android.webkit.WebView {
  return android.webkit.WebView(this).apply{
   setBackgroundColor(android.graphics.Color.rgb(14,14,14))
   isVerticalScrollBarEnabled=false
   isHorizontalScrollBarEnabled=false
   overScrollMode=android.view.View.OVER_SCROLL_NEVER
   settings.javaScriptEnabled=true
   settings.domStorageEnabled=true
   settings.loadsImagesAutomatically=true
   settings.blockNetworkImage=false
   settings.cacheMode=android.webkit.WebSettings.LOAD_DEFAULT
   settings.allowFileAccess=false
   settings.allowContentAccess=false
   settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
   settings.javaScriptCanOpenWindowsAutomatically=false
   settings.setSupportMultipleWindows(false)
   settings.safeBrowsingEnabled=true
   // Follow Android's text scaling preference and allow pinch zoom for accessibility.
   settings.textZoom=(resources.configuration.fontScale*100f).toInt().coerceAtLeast(50)
   settings.setSupportZoom(true)
   settings.builtInZoomControls=true
   settings.displayZoomControls=false
   if(android.os.Build.VERSION.SDK_INT>=26){
    setRendererPriorityPolicy(android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT,true)
   }
   webViewClient=object:android.webkit.WebViewClient(){
    override fun shouldInterceptRequest(
     view:android.webkit.WebView,
     request:android.webkit.WebResourceRequest
    ):android.webkit.WebResourceResponse? = webViewAssetLoader.shouldInterceptRequest(request.url)
    override fun shouldOverrideUrlLoading(
     view:android.webkit.WebView,
     request:android.webkit.WebResourceRequest
    ):Boolean {
     val uri=request.url
     return !(uri.scheme.equals("https",true)&&uri.host.equals("rush.local",true))
    }
    override fun onRenderProcessGone(
     view:android.webkit.WebView,
     detail:android.webkit.RenderProcessGoneDetail
    ):Boolean {
     if(view!==webView){
      (view.parent as? android.view.ViewGroup)?.removeView(view)
      view.destroy()
      return true
     }
     val route=view.url?.substringAfter('#')?.takeIf {
      it.matches(Regex("[A-Za-z0-9_-]{1,40}"))
     } ?: "explore"
     webPageLoaded=false
     nativeVideoRequested=false
     runCatching { rushWebBridge.release() }
     nativeVideoView?.let { playerView->
      playerView.player=null
      (playerView.parent as? android.view.ViewGroup)?.removeView(playerView)
     }
     nativeVideoView=null
     view.stopLoading()
     view.removeJavascriptInterface("RushNative")
     (view.parent as? android.view.ViewGroup)?.removeView(view)
     view.destroy()
     webView=createRushWebView()
     rootLayout.addView(webView,android.widget.FrameLayout.LayoutParams(
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
      android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
     webView.setOnScrollChangeListener { _,_,_,_,_-> updateNativeVideoPosition() }
     webView.loadUrl("https://rush.local/assets/rush.html#$route")
     return true
    }
    override fun onReceivedSslError(
     view:android.webkit.WebView,
     handler:android.webkit.SslErrorHandler,
     error:android.net.http.SslError
    ){handler.cancel()}
    override fun onPageStarted(view:android.webkit.WebView,url:String?,favicon:android.graphics.Bitmap?){
     super.onPageStarted(view,url,favicon)
     mainFrameLoadFailed=false
     showStartupOverlay("Loading Rush…")
    }
    override fun onReceivedError(
     view:android.webkit.WebView,
     request:android.webkit.WebResourceRequest,
     error:android.webkit.WebResourceError
    ){
     super.onReceivedError(view,request,error)
     if(request.isForMainFrame){
      mainFrameLoadFailed=true
      showPageLoadFailure()
     }
    }
    override fun onReceivedHttpError(
     view:android.webkit.WebView,
     request:android.webkit.WebResourceRequest,
     response:android.webkit.WebResourceResponse
    ){
     super.onReceivedHttpError(view,request,response)
     if(request.isForMainFrame&&response.statusCode>=400){
      mainFrameLoadFailed=true
      showPageLoadFailure()
     }
    }
    override fun onPageFinished(view:android.webkit.WebView,url:String?){
     super.onPageFinished(view,url)
     webPageLoaded=!mainFrameLoadFailed
     if(mainFrameLoadFailed)showPageLoadFailure() else hideStartupOverlay()
     if(RushPlaybackService.activeInstance?.hasCurrentMediaItem()==true){
      view.evaluateJavascript("if(window.RushNative)RushNative.loadCurrentPlayback()",null)
     }
     if(pendingOpenDownloads||intent.getBooleanExtra("open_downloads",false)){
      pendingOpenDownloads=false
      intent.removeExtra("open_downloads")
      openDownloadsFromNotification()
     }
    }
   }
   webChromeClient=android.webkit.WebChromeClient()
   rushWebBridge=RushWebBridge(this@MainActivity,this)
   addJavascriptInterface(rushWebBridge,"RushNative")
  }
 }
 private fun showStartupOverlay(message:String="Preparing Rush…"){
  if(!::rootLayout.isInitialized)return
  startupOverlay?.let { rootLayout.removeView(it) }
  val panel=android.widget.LinearLayout(this).apply{
   orientation=android.widget.LinearLayout.VERTICAL
   gravity=android.view.Gravity.CENTER
   setPadding((28*resources.displayMetrics.density).toInt(),(24*resources.displayMetrics.density).toInt(),(28*resources.displayMetrics.density).toInt(),(24*resources.displayMetrics.density).toInt())
   setBackgroundColor(android.graphics.Color.rgb(11,16,20))
  }
  panel.addView(android.widget.TextView(this).apply{
   text="rush"
   textSize=38f
   typeface=android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD)
   setTextColor(android.graphics.Color.rgb(215,250,94))
   gravity=android.view.Gravity.CENTER
  },android.widget.LinearLayout.LayoutParams(-2,-2))
  panel.addView(android.widget.ProgressBar(this).apply{isIndeterminate=true},android.widget.LinearLayout.LayoutParams(
   (42*resources.displayMetrics.density).toInt(),(42*resources.displayMetrics.density).toInt()).apply{topMargin=(26*resources.displayMetrics.density).toInt()})
  panel.addView(android.widget.TextView(this).apply{
   text=message;textSize=12f;setTextColor(android.graphics.Color.rgb(168,178,185));gravity=android.view.Gravity.CENTER
  },android.widget.LinearLayout.LayoutParams(-2,-2).apply{topMargin=(14*resources.displayMetrics.density).toInt()})
  startupOverlay=panel
  rootLayout.addView(panel,android.widget.FrameLayout.LayoutParams(-1,-1))
  panel.bringToFront()
 }
 private fun showPageLoadFailure(){
  if(!::rootLayout.isInitialized)return
  startupOverlay?.let { rootLayout.removeView(it) }
  val panel=android.widget.LinearLayout(this).apply{
   orientation=android.widget.LinearLayout.VERTICAL
   gravity=android.view.Gravity.CENTER
   setPadding(34,34,34,34)
   setBackgroundColor(android.graphics.Color.rgb(11,16,20))
  }
  panel.addView(android.widget.TextView(this).apply{
   text="Couldn’t load Rush"
   textSize=22f;setTextColor(android.graphics.Color.WHITE);gravity=android.view.Gravity.CENTER
  })
  panel.addView(android.widget.TextView(this).apply{
   text="Check your connection and try again."
   textSize=13f;setTextColor(android.graphics.Color.LTGRAY);gravity=android.view.Gravity.CENTER
  },android.widget.LinearLayout.LayoutParams(-1,-2).apply{topMargin=12})
  panel.addView(android.widget.Button(this).apply{
   text="RETRY"
   setOnClickListener { mainFrameLoadFailed=false;webView.reload() }
  },android.widget.LinearLayout.LayoutParams(-2,-2).apply{topMargin=18})
  startupOverlay=panel
  rootLayout.addView(panel,android.widget.FrameLayout.LayoutParams(-1,-1))
  panel.bringToFront()
 }
 private fun hideStartupOverlay(){
  startupOverlay?.let { rootLayout.removeView(it) }
  startupOverlay=null
 }
 private fun showStartupFailure(error:Throwable){
  if(!::rootLayout.isInitialized)return
  startupOverlay?.let { rootLayout.removeView(it) }
  val panel=android.widget.LinearLayout(this).apply{
   orientation=android.widget.LinearLayout.VERTICAL
   gravity=android.view.Gravity.CENTER
   setPadding(36,36,36,36)
   setBackgroundColor(android.graphics.Color.rgb(11,16,20))
  }
  panel.addView(android.widget.TextView(this).apply{
   text="Rush could not start"
   textSize=23f;setTextColor(android.graphics.Color.WHITE);gravity=android.view.Gravity.CENTER
  })
  panel.addView(android.widget.TextView(this).apply{
   text="Android System WebView could not initialize. Update Android System WebView and try again."
   textSize=13f;setTextColor(android.graphics.Color.LTGRAY);gravity=android.view.Gravity.CENTER
  },android.widget.LinearLayout.LayoutParams(-1,-2).apply{topMargin=18})
  panel.addView(android.widget.Button(this).apply{
   text="RESTART RUSH"
   setOnClickListener {
    val launch=packageManager.getLaunchIntentForPackage(packageName)
    launch?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    if(launch!=null){startActivity(launch);finish()}else recreate()
   }
  },android.widget.LinearLayout.LayoutParams(-2,-2).apply{topMargin=22})
  startupOverlay=panel
  rootLayout.addView(panel,android.widget.FrameLayout.LayoutParams(-1,-1))
  panel.bringToFront()
  android.util.Log.e("RushStartup","WebView startup failed",error)
 }
 private fun attachRushWebView(){
  if(isFinishing||isDestroyed||::webView.isInitialized)return
  webViewAssetLoader=WebViewAssetLoader.Builder()
   .setDomain("rush.local")
   .addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(this))
   .build()
  webView=createRushWebView()
  rootLayout.addView(webView,android.widget.FrameLayout.LayoutParams(-1,-1))
  webView.setOnScrollChangeListener { _,_,_,_,_-> updateNativeVideoPosition() }
  webView.loadUrl("https://rush.local/assets/rush.html")
 }
 @SuppressLint("SetJavaScriptEnabled")
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  WindowCompat.setDecorFitsSystemWindows(window,false)
  if(android.os.Build.VERSION.SDK_INT>=29)window.isNavigationBarContrastEnforced=false
  WindowInsetsControllerCompat(window,window.decorView).apply {
   isAppearanceLightStatusBars=false
   isAppearanceLightNavigationBars=false
  }
  rootLayout=android.widget.FrameLayout(this).apply {
   setBackgroundColor(android.graphics.Color.rgb(11,16,20))
   setOnApplyWindowInsetsListener { view, windowInsets ->
    val safeInsets=WindowInsetsCompat.toWindowInsetsCompat(windowInsets,view)
     .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    view.setPadding(safeInsets.left,safeInsets.top,safeInsets.right,safeInsets.bottom)
    windowInsets
   }
  }
  setContentView(rootLayout)
  androidx.core.view.ViewCompat.requestApplyInsets(rootLayout)
  showStartupOverlay()
  RushApplication.instance.webViewStartupReady.whenComplete { _, error ->
   runOnUiThread {
    if(error==null)attachRushWebView() else showStartupFailure(error)
   }
  }
  onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true){
   override fun handleOnBackPressed(){
    if(nativeVideoFullscreen){setNativeVideoFullscreen(false);return}
    if(!::webView.isInitialized){finish();return}
    webView.evaluateJavascript("window.rushHandleBack ? window.rushHandleBack() : false"){handled->
     if(handled!="true"){
      if(webView.canGoBack())webView.goBack()
      else webView.evaluateJavascript("location.hash='#explore'",null)
     }
    }
   }
  })
 }
 override fun onPause(){
  if(::webView.isInitialized){
   webView.onPause()
   webView.pauseTimers()
  }
  super.onPause()
 }
 override fun onResume(){
  super.onResume()
  if(::webView.isInitialized){
   webView.onResume()
   webView.resumeTimers()
   if(webPageLoaded)webView.evaluateJavascript("window.rushAppForeground&&window.rushAppForeground()",null)
   if(webPageLoaded&&RushPlaybackService.activeInstance?.hasCurrentMediaItem()==true)
    webView.evaluateJavascript("if(window.RushNative)RushNative.loadCurrentPlayback()",null)
  }
 }
 override fun onStop(){
  // Allow PiP to keep its video; elsewhere only actively playing audio may continue.
  if(!pipModeActive&&!isInPictureInPictureMode){
   RushPlaybackService.activeInstance?.enforceBackgroundPlaybackPolicy()
   if(::rushWebBridge.isInitialized)rushWebBridge.release()
  }
  super.onStop()
 }
 override fun onNewIntent(intent:android.content.Intent){
  super.onNewIntent(intent)
  setIntent(intent)
  if(intent.getBooleanExtra("open_downloads",false)){
   intent.removeExtra("open_downloads")
   openDownloadsFromNotification()
  }
 }
 override fun onPictureInPictureModeChanged(isInPictureInPictureMode:Boolean,newConfig:android.content.res.Configuration){
  super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
  pipModeState.value=isInPictureInPictureMode
 }
 @androidx.media3.common.util.UnstableApi
 override fun onDestroy(){
  if(::rushWebBridge.isInitialized)rushWebBridge.release()
  nativeVideoView?.player=null
  nativeVideoView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
  nativeVideoView=null
  if(::webView.isInitialized){webView.stopLoading();webView.destroy()}
  super.onDestroy()
 }
}
