package com.quietrays.tonarc.presentation.spotify.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quietrays.tonarc.ui.theme.TonarcTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class SpotifyLoginActivity : ComponentActivity() {

    private val viewModel: SpotifyLoginViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TonarcTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var isLoading by remember { mutableStateOf(true) }
                var showPasteDialog by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }

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
                            onPageLoadingChanged = { loading -> isLoading = loading },
                            onCookiesDetected = { cookies ->
                                viewModel.onCookiesCaptured(cookies)
                            }
                        )

                        if (isLoading || uiState is SpotifyLoginUiState.LoggingIn) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
    onPageLoadingChanged: (Boolean) -> Unit,
    onCookiesDetected: (String) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageLoadingChanged(false)

                        val cookieManager = CookieManager.getInstance()
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
                }

                loadUrl("https://accounts.spotify.com/en/login")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
