package com.javzzap.laxbrowser.engine.gecko

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.javzzap.laxbrowser.domain.AutoplayPolicy
import com.javzzap.laxbrowser.domain.UrlInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/** Site permissions Lax asks about or lists. */
enum class SitePermission {
    LOCATION, NOTIFICATIONS, CAMERA, MICROPHONE, DRM, AUTOPLAY_AUDIBLE, AUTOPLAY_INAUDIBLE, PERSISTENT_STORAGE,
    STORAGE_ACCESS, LOCAL_NETWORK, LOCAL_DEVICE,
}

/** A request waiting for the user. Hosts come from Gecko's view of the requesting page. */
sealed class PermissionRequest {
    abstract val tabId: String
    internal var answered = false

    abstract fun deny()

    /** Android runtime permissions Gecko needs (for example location before a site may use it). */
    class Android(override val tabId: String, val permissions: List<String>, private val onResult: (Boolean) -> Unit) : PermissionRequest() {
        fun result(granted: Boolean) = onResult(granted)
        override fun deny() = onResult(false)
    }

    class Content(
        override val tabId: String,
        val host: String,
        val permission: SitePermission,
        val thirdPartyHost: String?,
        val isPrivate: Boolean,
        private val onAnswer: (Boolean) -> Unit,
    ) : PermissionRequest() {
        fun answer(allow: Boolean) = onAnswer(allow)
        override fun deny() = onAnswer(false)
    }

    data class MediaDevice(val id: String, val name: String, val isFrontCamera: Boolean)

    /** Camera and/or microphone (getUserMedia). Gecko does not remember these; Lax asks every time. */
    class Media(
        override val tabId: String,
        val host: String,
        val cameras: List<MediaDevice>,
        val microphones: List<MediaDevice>,
        private val onAnswer: (camera: String?, microphone: String?) -> Unit,
        private val onDeny: () -> Unit,
    ) : PermissionRequest() {
        fun allow(cameraId: String?, microphoneId: String?) = onAnswer(cameraId, microphoneId)
        override fun deny() = onDeny()
    }
}

/** A permission Gecko stored for a site. [contextId] identifies the container it applies to. */
data class SitePermissionEntry(
    val host: String,
    val uri: String,
    val contextId: String?,
    val permission: SitePermission,
    val allowed: Boolean,
    internal val raw: ContentPermission,
)

/**
 * Answers GeckoView permission requests. Gecko stores the answers to content permission requests
 * per site and container (private-browsing answers last only for the private session), so a site
 * is asked once; [list] and [set] manage the stored answers.
 */
class SitePermissionsHandler(
    context: Context,
    private val runtime: () -> GeckoRuntime,
    private val autoplayPolicy: () -> AutoplayPolicy,
) {
    private val appContext = context.applicationContext
    private val queue = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val requests: StateFlow<List<PermissionRequest>> = queue.asStateFlow()

    private fun add(request: PermissionRequest) {
        queue.value = queue.value + request
    }

    private fun remove(request: PermissionRequest): Boolean {
        if (request.answered) return false
        request.answered = true
        queue.value = queue.value - request
        return true
    }

    fun cancelFor(tabId: String) {
        queue.value.filter { it.tabId == tabId }.forEach { it.deny() }
    }

    fun delegateFor(tabId: () -> String?): PermissionDelegate = object : PermissionDelegate {
        private fun id() = tabId().orEmpty()

        override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<String>?, callback: PermissionDelegate.Callback) {
            val missing = permissions.orEmpty().filter {
                ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) {
                callback.grant()
                return
            }
            lateinit var request: PermissionRequest.Android
            request = PermissionRequest.Android(id(), missing) { granted ->
                if (remove(request)) if (granted) callback.grant() else callback.reject()
            }
            add(request)
        }

        override fun onContentPermissionRequest(session: GeckoSession, perm: ContentPermission): GeckoResult<Int>? {
            if (perm.value != ContentPermission.VALUE_PROMPT) return GeckoResult.fromValue(perm.value)
            val kind = kindOf(perm.permission) ?: return GeckoResult.fromValue(ContentPermission.VALUE_PROMPT)
            when (kind) {
                SitePermission.AUTOPLAY_AUDIBLE, SitePermission.AUTOPLAY_INAUDIBLE -> return GeckoResult.fromValue(autoplayValue(kind))
                SitePermission.PERSISTENT_STORAGE -> return GeckoResult.fromValue(
                    if (perm.privateMode) ContentPermission.VALUE_DENY else ContentPermission.VALUE_ALLOW,
                )
                else -> Unit
            }
            val result = GeckoResult<Int>()
            lateinit var request: PermissionRequest.Content
            request = PermissionRequest.Content(
                tabId = id(),
                host = UrlInput.displayHost(perm.uri),
                permission = kind,
                thirdPartyHost = perm.thirdPartyOrigin?.let(UrlInput::displayHost),
                isPrivate = perm.privateMode,
            ) { allow ->
                if (remove(request)) result.complete(if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY)
            }
            add(request)
            return result
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<PermissionDelegate.MediaSource>?,
            audio: Array<PermissionDelegate.MediaSource>?,
            callback: PermissionDelegate.MediaCallback,
        ) {
            val cameras = video.orEmpty().filter { it.source == PermissionDelegate.MediaSource.SOURCE_CAMERA }
            val microphones = audio.orEmpty().filter { it.source == PermissionDelegate.MediaSource.SOURCE_MICROPHONE }
            if (cameras.isEmpty() && microphones.isEmpty()) {
                callback.reject()
                return
            }
            lateinit var request: PermissionRequest.Media
            request = PermissionRequest.Media(
                tabId = id(),
                host = UrlInput.displayHost(uri),
                cameras = cameras.map { PermissionRequest.MediaDevice(it.id, it.name.orEmpty(), it.name.orEmpty().contains("front", ignoreCase = true)) },
                microphones = microphones.map { PermissionRequest.MediaDevice(it.id, it.name.orEmpty(), false) },
                onAnswer = { cameraId, microphoneId ->
                    if (remove(request)) {
                        val camera = cameras.firstOrNull { it.id == cameraId }
                        val microphone = microphones.firstOrNull { it.id == microphoneId }
                        if (camera == null && microphone == null) callback.reject() else callback.grant(camera, microphone)
                    }
                },
                onDeny = { if (remove(request)) callback.reject() },
            )
            add(request)
        }
    }

    private fun autoplayValue(kind: SitePermission): Int {
        val allow = when (autoplayPolicy()) {
            AutoplayPolicy.ALLOW_ALL -> true
            AutoplayPolicy.BLOCK_ALL -> false
            AutoplayPolicy.BLOCK_AUDIBLE -> kind == SitePermission.AUTOPLAY_INAUDIBLE
        }
        return if (allow) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY
    }

    /** Stored permissions of normal browsing, for the site permissions screen. */
    suspend fun list(): List<SitePermissionEntry> {
        val all = runtime().storageController.allPermissions.await().orEmpty()
        return all.mapNotNull { perm ->
            val kind = kindOf(perm.permission) ?: return@mapNotNull null
            if (perm.privateMode || perm.value == ContentPermission.VALUE_PROMPT) return@mapNotNull null
            SitePermissionEntry(
                host = UrlInput.displayHost(perm.uri),
                uri = perm.uri,
                contextId = perm.contextId,
                permission = kind,
                allowed = perm.value == ContentPermission.VALUE_ALLOW,
                raw = perm,
            )
        }
    }

    /** Allows or blocks a stored permission; null removes it so the site is asked again. */
    fun set(entry: SitePermissionEntry, allowed: Boolean?) {
        val value = when (allowed) {
            null -> ContentPermission.VALUE_PROMPT
            true -> ContentPermission.VALUE_ALLOW
            false -> ContentPermission.VALUE_DENY
        }
        runtime().storageController.setPermission(entry.raw, value)
    }

    /** Forgets every site's autoplay answer so a new default applies everywhere. */
    suspend fun resetAutoplay() {
        list().filter { it.permission == SitePermission.AUTOPLAY_AUDIBLE || it.permission == SitePermission.AUTOPLAY_INAUDIBLE }
            .forEach { set(it, null) }
    }

    private fun kindOf(permission: Int): SitePermission? = when (permission) {
        PermissionDelegate.PERMISSION_GEOLOCATION -> SitePermission.LOCATION
        PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> SitePermission.NOTIFICATIONS
        PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> SitePermission.DRM
        PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> SitePermission.AUTOPLAY_AUDIBLE
        PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> SitePermission.AUTOPLAY_INAUDIBLE
        PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> SitePermission.PERSISTENT_STORAGE
        PermissionDelegate.PERMISSION_STORAGE_ACCESS -> SitePermission.STORAGE_ACCESS
        PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS -> SitePermission.LOCAL_NETWORK
        PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS -> SitePermission.LOCAL_DEVICE
        else -> null
    }
}
