package com.ragclient.android.navigation

object Routes {
    /**
     * The placeholder name must equal the `navArgument` name, or the value in
     * the URL is filed under `documentId` while the view model reads `scope`
     * and only ever sees the default.
     */
    const val CHAT_PATTERN = "chat/{conversationId}?scope={scope}"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val VIEWER_PATTERN = "viewer/{documentId}/{page}"
    const val STAGED_VIEWER_PATTERN = "viewerStaged/{directory}/{name}"

    private const val NO_SCOPE = "none"

    /**
     * `chat/{conversationId}` accepts a real Room id or one of these three
     * sentinels, which is how "new", "resume" and "the thread for this
     * document" share a single destination.
     */
    const val CHAT_NEW = "new"
    const val CHAT_LATEST_ID = "latest"
    const val CHAT_FOR_DOCUMENT = "scoped"

    /** Cold start resumes the most recent thread. */
    val CHAT_LATEST: String = chat(CHAT_LATEST_ID)

    fun chat(conversationId: String, documentScope: String? = null): String =
        "chat/$conversationId?scope=${documentScope ?: NO_SCOPE}"

    /** The document's own thread — resumed, not re-created on every tap. */
    fun chatForDocument(documentId: String): String = chat(CHAT_FOR_DOCUMENT, documentId)

    fun newChatInDocument(documentId: String): String = chat(CHAT_NEW, documentId)

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
