package com.quietrays.tonarc.data.network.youtube

/**
 * Shared holder expected by InnertubeParser when resolving player JS URLs for signature deobfuscation.
 */
object InnertubeApiService {
    @Volatile
    var cachedPlayerJsUrl: String? = null
}
