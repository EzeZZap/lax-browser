package com.javzzap.laxbrowser.domain

/** Engine work that cannot be expressed as state and must run after session reconciliation. */
data class ImportSummary(val imported: Int, val skipped: Int)

sealed interface EngineCommand {
    data class Load(val tabId: String, val url: String) : EngineCommand
    data class GoBack(val tabId: String) : EngineCommand
    data class GoForward(val tabId: String) : EngineCommand
    data class Reload(val tabId: String) : EngineCommand

    /** Site zoom levels changed; open pages re-apply theirs. */
    data object RefreshZoom : EngineCommand
    data class Stop(val tabId: String) : EngineCommand
    data class ClearContainerData(val containerId: String) : EngineCommand
    data class SetDesktopMode(val tabId: String, val enabled: Boolean) : EngineCommand
}

enum class RejectReason {
    NOT_FOUND,
    INVALID_URL,
    INVALID_NAME,
    LAST_SPACE,
    CONTAINER_IN_USE,
    CONTAINER_MISSING,
    DUPLICATE,
    NOT_ALLOWED,
    NO_ACTIVE_SPACE,
}

sealed interface Outcome {
    data object Ok : Outcome
    data class Created(val id: String) : Outcome
    data class AlreadyExists(val id: String) : Outcome
    data class Rejected(val reason: RejectReason) : Outcome
}

/**
 * The result of a domain operation. [discardSessionState] lists tabs whose saved Gecko history
 * must be dropped (for example when their container changes).
 */
data class Reduction(
    val state: BrowserState,
    val commands: List<EngineCommand> = emptyList(),
    val discardSessionState: Set<String> = emptySet(),
    val outcome: Outcome = Outcome.Ok,
) {
    val isRejected: Boolean get() = outcome is Outcome.Rejected

    companion object {
        fun reject(state: BrowserState, reason: RejectReason) =
            Reduction(state, outcome = Outcome.Rejected(reason))
    }
}

sealed interface ContainerChoice {
    data class New(val name: String, val color: Int) : ContainerChoice
    data class Existing(val containerId: String) : ContainerChoice
}

data class ContainerUsage(
    val spaces: List<Space>,
    val tabs: List<Tab>,
    val webApps: List<WebApp>,
) {
    val isUnused: Boolean get() = spaces.isEmpty() && tabs.isEmpty() && webApps.isEmpty()
}
