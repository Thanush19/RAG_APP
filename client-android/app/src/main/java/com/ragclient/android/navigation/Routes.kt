package com.ragclient.android.navigation

object Routes {
    const val CHAT_PATTERN = "chat/{conversationId}?scope={documentId}"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val VIEWER_PATTERN = "viewer/{documentId}/{page}"
    const val STAGED_VIEWER_PATTERN = "viewerStaged/{directory}/{name}"

    private const val NO_SCOPE = "none"

    /** Cold start resumes the most recent thread. */
    val CHAT_LATEST: String = chat("latest")

    fun chat(conversationId: String, documentScope: String? = null): String =
        "chat/$conversationId?scope=${documentScope ?: NO_SCOPE}"

    fun viewer(documentId: String, page: Int) = "viewer/$documentId/$page"

    /**
     * Preview of a file that is staged locally and not on the server yet. The
     * staging layout is `uploads/<millis>/<sanitised name>`, so both segments
     * are url-safe and need no encoding.
     */
    fun stagedViewer(stagedPath: String): String? {
        val name = stagedPath.substringAfterLast('/')
        val directory = stagedPath.substringBeforeLast('/').substringAfterLast('/')
        return if (name.isBlank() || directory.isBlank()) {
            null
        } else {
            "viewerStaged/$directory/$name"
        }
    }

    fun scopeOf(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it != NO_SCOPE }
}
