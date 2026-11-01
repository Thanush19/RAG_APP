package com.ragclient.android.navigation

object Routes {
    const val CHAT_PATTERN = "chat/{conversationId}?scope={documentId}"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val VIEWER_PATTERN = "viewer/{documentId}/{page}"

    private const val NO_SCOPE = "none"

    /** Cold start resumes the most recent thread. */
    val CHAT_LATEST: String = chat("latest")

    fun chat(conversationId: String, documentScope: String? = null): String =
        "chat/$conversationId?scope=${documentScope ?: NO_SCOPE}"

    fun viewer(documentId: String, page: Int) = "viewer/$documentId/$page"

    fun scopeOf(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it != NO_SCOPE }
}
