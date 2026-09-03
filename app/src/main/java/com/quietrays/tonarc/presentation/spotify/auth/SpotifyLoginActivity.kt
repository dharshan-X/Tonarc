package com.quietrays.tonarc.presentation.spotify.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietrays.tonarc.ui.theme.TonarcTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {

    private val viewModel: SpotifyLoginViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TonarcTheme {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var isLoading by remember { mutableStateOf(true) }
                var pageError by remember { mutableStateOf<String?>(null) }
                var showPasteDialog by remember { mutableStateOf(false) }
                var webViewInstance by remember { mutableStateOf<WebView?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }

                BackHandler(enabled = webViewInstance?.canGoBack() == true) {
                    webViewInstance?.goBack()
                }

                LaunchedEffect(uiState) {
                    when (val state = uiState) {
                        is SpotifyLoginUiState.Success -> {
                            setResult(Activity.RESULT_OK)
                            finish()
                        }
                        is SpotifyLoginUiState.Error -> {
                            snackbarHostState.showSnackbar(state.message)
                        }
                        else -> {}
                    }
                }

                if (showPasteDialog) {
                    SpotifyCookieInputDialog(
                        onDismiss = { showPasteDialog = false },
                        onSubmit = { pastedText ->
                            viewModel.onCookiePasted(pastedText)
                            showPasteDialog = false
                        }
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text("Sign in to Spotify") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    pageError = null
                                    webViewInstance?.reload()
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh"
                                    )
                                }
                                IconButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_LOGIN_URL))
                                        )
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "Open in External Browser"
                                    )
                                }
                                IconButton(onClick = { showPasteDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste Cookie"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Have a Spotify Cookie?",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Paste sp_dc cookie or token directly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { showPasteDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste")
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        SpotifyLoginWebView(
                            onWebViewReady = { webViewInstance = it },
                            onPageLoadingChanged = { loading -> isLoading = loading },
                            onErrorChanged = { pageError = it },
                            onCookiesDetected = { cookies ->
                                viewModel.onCookiesCaptured(cookies)
                            }
                        )

                        if (isLoading || uiState is SpotifyLoginUiState.LoggingIn) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        pageError?.let { error ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                                    .align(Alignment.Center),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "Could not load Spotify Login",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                pageError = null
                                                webViewInstance?.loadUrl(SPOTIFY_LOGIN_URL)
                                            }
                                        ) {
                                            Text("Retry")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(SPOTIFY_LOGIN_URL))
                                                    )
                                                }
                                            }
                                        ) {
                                            Text("Open in Browser")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyCookieInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var cookieText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Paste Spotify Cookie / Token",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste your Spotify sp_dc cookie, Cookie header, or raw token copied from your browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    placeholder = { Text("sp_dc=... or raw token") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    trailingIcon = {
                        if (cookieText.isNotEmpty()) {
                            IconButton(onClick = { cookieText = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                cookieText = clip
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste from Clipboard")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(cookieText) },
                enabled = cookieText.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyLoginWebView(
    onWebViewReady: (WebView) -> Unit,
    onPageLoadingChanged: (Boolean) -> Unit,
    onErrorChanged: (String?) -> Unit,
    onCookiesDetected: (String) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = DESKTOP_USER_AGENT
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (newProgress >= 100) {
                            onPageLoadingChanged(false)
                        }
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageLoadingChanged(true)
                        onErrorChanged(null)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageLoadingChanged(false)

                        val dotSpotifyCookies = cookieManager.getCookie(".spotify.com") ?: ""
                        val accountsCookies = cookieManager.getCookie("https://accounts.spotify.com") ?: ""
                        val openCookies = cookieManager.getCookie("https://open.spotify.com") ?: ""
                        val currentCookies = url?.let { cookieManager.getCookie(it) } ?: ""

                        val allCookieMap = mutableMapOf<String, String>()
                        listOf(dotSpotifyCookies, accountsCookies, openCookies, currentCookies).forEach { cookieStr ->
                            cookieStr.split(";").forEach { pair ->
                                val trimmed = pair.trim()
                                val key = trimmed.substringBefore("=").trim()
                                val value = trimmed.substringAfter("=", "").trim()
                                if (key.isNotEmpty() && value.isNotEmpty()) {
                                    allCookieMap[key] = value
                                }
                            }
                        }
                        val combinedCookies = allCookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
                        if (allCookieMap.containsKey("sp_dc") || combinedCookies.contains("sp_dc=")) {
                            Timber.d("Spotify login cookies captured successfully! Keys: ${allCookieMap.keys}")
                            onCookiesDetected(combinedCookies)
                        }
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            onPageLoadingChanged(false)
                            val desc = error?.description?.toString() ?: "Error loading page"
                            Timber.w("Spotify login WebView main-frame error: $desc")
                            onErrorChanged(desc)
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return false
                        val urlStr = uri.toString()
                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                            return false
                        }
                        // Intercept intent:// or spotify:// or market:// schemes so webview does not error
                        Timber.d("Intercepted external scheme in Spotify login: $urlStr")
                        return true
                    }
                }

                onWebViewReady(this)
                loadUrl(SPOTIFY_LOGIN_URL)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
