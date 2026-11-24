package com.rush.app

import android.app.Application
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class RushApplication : Application() {
    val webViewStartupReady = CompletableFuture<Boolean>()
    private val webViewStartupExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            val config = WebViewStartUpConfig.Builder(webViewStartupExecutor).build()
            WebViewCompat.startUpWebView(
                applicationContext,
                config,
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) {
                        webViewStartupReady.complete(true)
                    }

                    override fun onError(error: WebViewStartupException) {
                        webViewStartupReady.completeExceptionally(error)
                    }
                }
            )
        } catch (error: Throwable) {
            webViewStartupReady.completeExceptionally(error)
        }
    }

    override fun onTerminate() {
        webViewStartupExecutor.shutdown()
        super.onTerminate()
    }

    companion object {
        @Volatile
        lateinit var instance: RushApplication
            private set
    }
}
