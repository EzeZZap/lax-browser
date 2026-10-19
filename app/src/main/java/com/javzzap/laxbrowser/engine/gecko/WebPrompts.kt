package com.javzzap.laxbrowser.engine.gecko

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.javzzap.laxbrowser.data.LoginStore
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import java.io.File
import java.util.UUID

/**
 * A dialog requested by a web page (alert, select menu, date picker, file upload, ...). Text in
 * prompts comes from the page and is untrusted: it is only displayed, never interpreted. Every
 * prompt is answered exactly once; answering again is ignored.
 */
sealed class WebPrompt {
    abstract val tabId: String
    internal var completed = false

    /** Dismisses the prompt without an answer (Cancel, Back, or tapping outside). */
    abstract fun cancel()

    class Alert(override val tabId: String, val title: String?, val message: String?, private val onDone: () -> Unit) : WebPrompt() {
        fun ok() = onDone()
        override fun cancel() = onDone()
    }

    class Confirm(override val tabId: String, val title: String?, val message: String?, private val onAnswer: (Boolean?) -> Unit) : WebPrompt() {
        fun answer(ok: Boolean) = onAnswer(ok)
        override fun cancel() = onAnswer(null)
    }

    class Text(
        override val tabId: String,
        val title: String?,
        val message: String?,
        val defaultValue: String,
        private val onAnswer: (String?) -> Unit,
    ) : WebPrompt() {
        fun submit(text: String) = onAnswer(text)
        override fun cancel() = onAnswer(null)
    }

    class Auth(
        override val tabId: String,
        val title: String?,
        val message: String?,
        val host: String?,
        val passwordOnly: Boolean,
        val defaultUsername: String,
        val insecure: Boolean,
        private val onAnswer: (Pair<String, String>?) -> Unit,
    ) : WebPrompt() {
        fun submit(username: String, password: String) = onAnswer(username to password)
        override fun cancel() = onAnswer(null)
    }

    /** A `<select>` list or menu. Groups are flattened into headers followed by their items. */
    class Choice(
        override val tabId: String,
        val title: String?,
        val message: String?,
        val multiple: Boolean,
        val items: List<ChoiceItem>,
        private val onAnswer: (List<String>?) -> Unit,
    ) : WebPrompt() {
        fun select(ids: List<String>) = onAnswer(ids)
        override fun cancel() = onAnswer(null)
    }

    data class ChoiceItem(
        val id: String,
        val label: String,
        val enabled: Boolean,
        val selected: Boolean,
        val isHeader: Boolean,
        val isSeparator: Boolean,
    )

    class Color(
        override val tabId: String,
        val title: String?,
        val initial: Int,
        val presets: List<Int>,
        private val onAnswer: (Int?) -> Unit,
    ) : WebPrompt() {
        fun pick(color: Int) = onAnswer(color)
        override fun cancel() = onAnswer(null)
    }

    enum class DateTimeType { DATE, MONTH, WEEK, TIME, DATETIME_LOCAL }

    /** Values use the HTML formats: yyyy-MM-dd, yyyy-MM, yyyy-Www, HH:mm, yyyy-MM-ddTHH:mm. */
    class DateTime(
        override val tabId: String,
        val title: String?,
        val type: DateTimeType,
        val value: String?,
        val min: String?,
        val max: String?,
        private val onAnswer: (String?) -> Unit,
    ) : WebPrompt() {
        fun pick(value: String) = onAnswer(value)
        fun clear() = onAnswer("")
        override fun cancel() = onAnswer(null)
    }

    class FilePick(
        override val tabId: String,
        val title: String?,
        val multiple: Boolean,
        val mimeTypes: List<String>,
        val capture: Boolean,
        private val onAnswer: (List<Uri>?) -> Unit,
    ) : WebPrompt() {
        fun pick(uris: List<Uri>) = onAnswer(uris)
        override fun cancel() = onAnswer(null)
    }

    /** Leave a page whose onbeforeunload asks to stay. */
    class BeforeUnload(override val tabId: String, private val onAnswer: (Boolean) -> Unit) : WebPrompt() {
        fun answer(leave: Boolean) = onAnswer(leave)
        override fun cancel() = onAnswer(false)
    }

    /** Resend form data on reload. */
    class Repost(override val tabId: String, private val onAnswer: (Boolean) -> Unit) : WebPrompt() {
        fun answer(resend: Boolean) = onAnswer(resend)
        override fun cancel() = onAnswer(false)
    }

    /** A pop-up window or third-party redirect that was blocked. */
    class Blocked(override val tabId: String, val isRedirect: Boolean, val targetUri: String?, private val onAnswer: (Boolean) -> Unit) : WebPrompt() {
        fun answer(allow: Boolean) = onAnswer(allow)
        override fun cancel() = onAnswer(false)
    }

    /** Offer to save (or update) a login typed into a form. The password itself is never shown. */
    class LoginSave(
        override val tabId: String,
        val host: String,
        val username: String,
        val isUpdate: Boolean,
        private val onAnswer: (LoginSaveAnswer) -> Unit,
    ) : WebPrompt() {
        fun answer(answer: LoginSaveAnswer) = onAnswer(answer)
        override fun cancel() = onAnswer(LoginSaveAnswer.NOT_NOW)
    }

    enum class LoginSaveAnswer { SAVE, NEVER, NOT_NOW }

    /**
     * Saved logins fit a form: pick the one to fill. On sign-up forms Gecko may also suggest a
     * strong [generatedPassword], which fills the form and is offered for saving after submit.
     */
    class LoginSelect(
        override val tabId: String,
        val host: String,
        val usernames: List<String>,
        val generatedPassword: String?,
        private val onAnswer: (Int?) -> Unit,
    ) : WebPrompt() {
        fun pick(index: Int) = onAnswer(index)
        fun useGeneratedPassword() = onAnswer(GENERATED)
        override fun cancel() = onAnswer(null)

        companion object {
            const val GENERATED = -1
        }
    }

    /** navigator.share(); the UI opens the Android share sheet. */
    class Share(override val tabId: String, val title: String?, val text: String?, val url: String?, private val onAnswer: (Boolean) -> Unit) : WebPrompt() {
        fun shared(success: Boolean) = onAnswer(success)
        override fun cancel() = onAnswer(false)
    }
}

/** Turns GeckoView prompts into [WebPrompt]s, queued so only one dialog shows at a time. */
class WebPromptHandler(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val queue = MutableStateFlow<List<WebPrompt>>(emptyList())

    /** Pending prompts; the UI shows the first one. */
    val prompts: StateFlow<List<WebPrompt>> = queue.asStateFlow()

    /** Saved logins, for the save prompt's "never for this site" and update checks. Set by the engine. */
    var loginStore: LoginStore? = null

    /** Private tabs never offer to save logins. Set by the engine. */
    var isPrivateTab: (String) -> Boolean = { false }

    /** Whether saving and filling logins is on. Set by the engine. */
    var loginsEnabled: () -> Boolean = { true }

    /** "tab|origin" where the user closed the login chooser; it is not offered again there. */
    private val loginSelectDismissed = HashSet<String>()

    /** Cancels the prompts of a tab whose session closed. */
    fun cancelFor(tabId: String) {
        queue.value.filter { it.tabId == tabId }.forEach { it.cancel() }
        loginSelectDismissed.removeAll { it.startsWith("$tabId|") }
    }

    /**
     * Queues a prompt. The response is built lazily, so an answer that arrives after the page
     * already closed the prompt is ignored instead of completing it twice.
     */
    private fun <P : BasePrompt> enqueue(
        prompt: P,
        build: (complete: (() -> PromptResponse) -> Unit) -> WebPrompt,
    ): GeckoResult<PromptResponse> {
        val result = GeckoResult<PromptResponse>()
        lateinit var webPrompt: WebPrompt
        val complete: (() -> PromptResponse) -> Unit = { response ->
            if (!webPrompt.completed && !prompt.isComplete) {
                webPrompt.completed = true
                queue.value = queue.value - webPrompt
                result.complete(response())
            }
        }
        webPrompt = build(complete)
        prompt.setDelegate(object : PromptDelegate.PromptInstanceDelegate {
            // The page closed the prompt itself (navigation, script, or timeout).
            override fun onPromptDismiss(prompt: BasePrompt) {
                webPrompt.completed = true
                queue.value = queue.value - webPrompt
            }
        })
        queue.value = queue.value + webPrompt
        return result
    }

    fun delegateFor(tabId: () -> String?): PromptDelegate = object : PromptDelegate {
        private fun id() = tabId().orEmpty()

        override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt) =
            enqueue(prompt) { done -> WebPrompt.Alert(id(), prompt.title, prompt.message) { done { prompt.dismiss() } } }

        override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt) = enqueue(prompt) { done ->
            WebPrompt.Confirm(id(), prompt.title, prompt.message) { ok ->
                done {
                    when (ok) {
                        null -> prompt.dismiss()
                        true -> prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)
                        false -> prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)
                    }
                }
            }
        }

        override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt) = enqueue(prompt) { done ->
            WebPrompt.Text(id(), prompt.title, prompt.message, prompt.defaultValue.orEmpty()) { text ->
                done { if (text == null) prompt.dismiss() else prompt.confirm(text) }
            }
        }

        override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt) = enqueue(prompt) { done ->
            val options = prompt.authOptions
            val passwordOnly = (options.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
            WebPrompt.Auth(
                tabId = id(),
                title = prompt.title,
                message = prompt.message,
                host = options.uri?.toUri()?.host,
                passwordOnly = passwordOnly,
                defaultUsername = options.username.orEmpty(),
                insecure = options.level == PromptDelegate.AuthPrompt.AuthOptions.Level.NONE,
            ) { credentials ->
                done {
                    when {
                        credentials == null -> prompt.dismiss()
                        passwordOnly -> prompt.confirm(credentials.second)
                        else -> prompt.confirm(credentials.first, credentials.second)
                    }
                }
            }
        }

        override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt) = enqueue(prompt) { done ->
            WebPrompt.Choice(
                tabId = id(),
                title = prompt.title,
                message = prompt.message,
                multiple = prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE,
                items = flatten(prompt.choices),
            ) { ids ->
                done {
                    when {
                        ids == null -> prompt.dismiss()
                        prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE -> prompt.confirm(ids.toTypedArray())
                        ids.size == 1 -> prompt.confirm(ids.single())
                        else -> prompt.dismiss()
                    }
                }
            }
        }

        override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt) = enqueue(prompt) { done ->
            WebPrompt.Color(
                tabId = id(),
                title = prompt.title,
                initial = parseColor(prompt.defaultValue) ?: DEFAULT_COLOR,
                presets = prompt.predefinedValues.orEmpty().mapNotNull(::parseColor),
            ) { color -> done { if (color == null) prompt.dismiss() else prompt.confirm(formatColor(color)) } }
        }

        override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt) = enqueue(prompt) { done ->
            val type = when (prompt.type) {
                PromptDelegate.DateTimePrompt.Type.MONTH -> WebPrompt.DateTimeType.MONTH
                PromptDelegate.DateTimePrompt.Type.WEEK -> WebPrompt.DateTimeType.WEEK
                PromptDelegate.DateTimePrompt.Type.TIME -> WebPrompt.DateTimeType.TIME
                PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL -> WebPrompt.DateTimeType.DATETIME_LOCAL
                else -> WebPrompt.DateTimeType.DATE
            }
            WebPrompt.DateTime(id(), prompt.title, type, prompt.defaultValue, prompt.minValue, prompt.maxValue) { value ->
                done { if (value == null) prompt.dismiss() else prompt.confirm(value) }
            }
        }

        override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse> {
            // Folder uploads need a tree picker that GeckoView resolves itself; not offered for now.
            if (prompt.type == PromptDelegate.FilePrompt.Type.FOLDER) return GeckoResult.fromValue(prompt.dismiss())
            return enqueue(prompt) { done ->
                WebPrompt.FilePick(
                    tabId = id(),
                    title = prompt.title,
                    multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE,
                    mimeTypes = prompt.mimeTypes.orEmpty().filter { it.isNotBlank() },
                    capture = prompt.capture != PromptDelegate.FilePrompt.Capture.NONE,
                ) { uris ->
                    if (uris.isNullOrEmpty()) {
                        done { prompt.dismiss() }
                    } else {
                        // Gecko reads uploads as files, so picked documents are copied to the cache first.
                        scope.launch {
                            val files = withContext(Dispatchers.IO) { uris.mapNotNull(::copyToCache) }
                            done {
                                when {
                                    files.isEmpty() -> prompt.dismiss()
                                    prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE -> prompt.confirm(appContext, files.toTypedArray())
                                    else -> prompt.confirm(appContext, files.first())
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt) = enqueue(prompt) { done ->
            WebPrompt.BeforeUnload(id()) { leave -> done { prompt.confirm(if (leave) AllowOrDeny.ALLOW else AllowOrDeny.DENY) } }
        }

        override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt) = enqueue(prompt) { done ->
            WebPrompt.Repost(id()) { resend -> done { prompt.confirm(if (resend) AllowOrDeny.ALLOW else AllowOrDeny.DENY) } }
        }

        override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt) = enqueue(prompt) { done ->
            WebPrompt.Blocked(id(), isRedirect = false, targetUri = prompt.targetUri) { allow ->
                done { prompt.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
            }
        }

        override fun onRedirectPrompt(session: GeckoSession, prompt: PromptDelegate.RedirectPrompt) = enqueue(prompt) { done ->
            WebPrompt.Blocked(id(), isRedirect = true, targetUri = prompt.targetUri) { allow ->
                done { prompt.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
            }
        }

        override fun onLoginSave(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>,
        ): GeckoResult<PromptResponse> {
            val tab = id()
            val option = request.options.firstOrNull()
            val store = loginStore
            val entry = option?.value
            val origin = entry?.origin
            if (option == null || store == null || origin == null || isPrivateTab(tab) || !loginsEnabled()) {
                return GeckoResult.fromValue(request.dismiss())
            }
            val result = GeckoResult<PromptResponse>()
            scope.launch {
                if (runCatching { store.isNeverSave(origin) }.getOrDefault(false)) {
                    result.complete(request.dismiss())
                    return@launch
                }
                val host = origin.toUri().host?.removePrefix("www.") ?: origin
                val username = entry.username.orEmpty()
                val known = runCatching { store.forDomain(host) }.getOrDefault(emptyList())
                    .any { it.guid == entry.guid || (it.origin == origin && it.username == username) }
                result.completeFrom(
                    enqueue(request) { done ->
                        WebPrompt.LoginSave(tab, host, username, isUpdate = known || !entry.guid.isNullOrEmpty()) { answer ->
                            when (answer) {
                                WebPrompt.LoginSaveAnswer.SAVE -> done { request.confirm(option) }
                                WebPrompt.LoginSaveAnswer.NEVER -> {
                                    scope.launch { runCatching { store.neverSave(origin) } }
                                    done { request.dismiss() }
                                }
                                WebPrompt.LoginSaveAnswer.NOT_NOW -> done { request.dismiss() }
                            }
                        }
                    },
                )
            }
            return result
        }

        override fun onLoginSelect(
            session: GeckoSession,
            request: PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>,
        ): GeckoResult<PromptResponse> {
            // The chooser opens for real saved logins and for Gecko's strong password suggestion
            // (not relay addresses), not while saving logins is off, and not again after the user
            // closed it on this site. Private tabs never save logins, so they get no suggestion.
            val tab = id()
            val generatedHint = Autocomplete.SelectOption.Hint.GENERATED
            val options = request.options.filter { option ->
                (option.hint and (generatedHint or Autocomplete.SelectOption.Hint.FIREFOX_RELAY)) == 0 &&
                    !option.value.password.isNullOrEmpty()
            }
            val generated = request.options.firstOrNull { (it.hint and generatedHint) != 0 && !it.value.password.isNullOrEmpty() }
                ?.takeIf { !isPrivateTab(tab) }
            val origin = (options.firstOrNull() ?: generated ?: request.options.firstOrNull())?.value?.origin.orEmpty()
            val key = "$tab|$origin"
            if (!loginsEnabled() || (options.isEmpty() && generated == null) || key in loginSelectDismissed) {
                return GeckoResult.fromValue(request.dismiss())
            }
            val host = origin.toUri().host?.removePrefix("www.").orEmpty()
            return enqueue(request) { done ->
                WebPrompt.LoginSelect(tab, host, options.map { it.value.username.orEmpty() }, generated?.value?.password) { index ->
                    if (index == null) loginSelectDismissed += key
                    done {
                        when {
                            index == WebPrompt.LoginSelect.GENERATED && generated != null -> request.confirm(generated)
                            index == null || index !in options.indices -> request.dismiss()
                            else -> request.confirm(options[index])
                        }
                    }
                }
            }
        }

        override fun onSharePrompt(session: GeckoSession, prompt: PromptDelegate.SharePrompt) = enqueue(prompt) { done ->
            WebPrompt.Share(id(), prompt.title, prompt.text, prompt.uri) { shared ->
                done { prompt.confirm(if (shared) PromptDelegate.SharePrompt.Result.SUCCESS else PromptDelegate.SharePrompt.Result.ABORT) }
            }
        }
    }

    private fun flatten(choices: Array<PromptDelegate.ChoicePrompt.Choice>): List<WebPrompt.ChoiceItem> = choices.flatMap { choice ->
        val items = choice.items
        if (items != null) {
            listOf(WebPrompt.ChoiceItem(choice.id, choice.label, enabled = false, selected = false, isHeader = true, isSeparator = false)) +
                items.map { WebPrompt.ChoiceItem(it.id, it.label, !it.disabled && !choice.disabled, it.selected, isHeader = false, isSeparator = it.separator) }
        } else {
            listOf(WebPrompt.ChoiceItem(choice.id, choice.label, !choice.disabled, choice.selected, isHeader = false, isSeparator = choice.separator))
        }
    }

    /** Copies a picked document into `cacheDir/uploads` and returns its file URI. */
    private fun copyToCache(uri: Uri): Uri? = try {
        val dir = File(appContext.cacheDir, UPLOAD_DIR).apply { mkdirs() }
        val name = displayName(uri)?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.takeIf { it.isNotBlank() } ?: "upload"
        val target = File(File(dir, UUID.randomUUID().toString()).apply { mkdirs() }, name)
        appContext.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
        Uri.fromFile(target)
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the picked file", e)
        null
    }

    private fun displayName(uri: Uri): String? =
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /** Deletes uploads copied in earlier sessions. */
    fun clearUploadCache() {
        scope.launch(Dispatchers.IO) { File(appContext.cacheDir, UPLOAD_DIR).deleteRecursively() }
    }

    companion object {
        private const val TAG = "WebPrompts"
        private const val UPLOAD_DIR = "uploads"
        private const val DEFAULT_COLOR = 0xFF000000.toInt()

        fun parseColor(value: String?): Int? {
            val hex = value?.trim()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
            return hex.toLongOrNull(16)?.let { (it or 0xFF000000L).toInt() }
        }

        fun formatColor(color: Int): String = "#%06x".format(color and 0xFFFFFF)
    }
}
