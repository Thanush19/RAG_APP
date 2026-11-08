package com.ragclient.android.data.network

import com.ragclient.android.BuildConfig
import com.ragclient.android.data.api.dto.ApiErrorDto
import java.io.IOException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject

/** Backend failure translated for the UI: human copy plus the machine code. */
data class ApiFailure(
    val message: String,
    val code: String?
)

/**
 * The one message that has to name the origin: a transport failure means the
 * address itself was wrong or unreachable, and without printing it there is
 * nothing to compare against `ipconfig` or the phone's Wi-Fi.
 */
fun unreachableMessage(): String =
    if (BuildConfig.IS_DEV_BUILD) {
        "Can't reach ${BuildConfig.API_BASE_URL}. Is this device on the same network " +
            "as that address, and is the backend running?"
    } else {
        "Can't reach the server. It may still be waking up — wait a moment and try again."
    }

/**
 * Turns backend error envelopes (and transport failures) into UI copy.
 * The error body can only be read once, so callers use a single method.
 */
class ApiErrorMapper @Inject constructor(private val json: Json) {

    fun describe(error: Throwable, fallback: String): ApiFailure = when (error) {
        is HttpException -> {
            val envelope = error.response()?.errorBody()?.string()?.let { body ->
                runCatching { json.decodeFromString<ApiErrorDto>(body) }.getOrNull()
            }
            ApiFailure(
                message = envelope?.message?.takeIf { it.isNotBlank() } ?: httpCopy(error.code()),
                code = envelope?.code?.takeIf { it.isNotBlank() }
            )
        }
        is IOException -> ApiFailure(
            message = unreachableMessage(),
            code = CONNECTION_FAILED
        )
        else -> ApiFailure(message = fallback, code = null)
    }

    private fun httpCopy(status: Int): String = when (status) {
        401, 403 -> "Your session is no longer valid. Please sign in again."
        404 -> "That resource no longer exists."
        in 500..599 -> "The server had a problem (HTTP $status)."
        else -> "Something went wrong (HTTP $status)."
    }

    companion object {
        const val CONNECTION_FAILED = "CONNECTION_FAILED"
    }
}
