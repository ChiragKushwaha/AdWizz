package rocks.chirag.adshield.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Status { STOPPED, STARTING, RUNNING, ERROR }

data class QueryLog(val domain: String, val blocked: Boolean, val time: Long)

data class Stats(
    val queries: Long = 0,
    val blocked: Long = 0,
    val listSize: Int = 0,
)

/** Process-wide observable state shared by the service, the UI and the Quick Settings tile. */
object VpnState {
    private const val LOG_CAP = 300

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _log = MutableStateFlow<List<QueryLog>>(emptyList())
    val log: StateFlow<List<QueryLog>> = _log.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updatingLists: StateFlow<Boolean> = _updating.asStateFlow()

    fun setStatus(s: Status, error: String? = null) {
        _status.value = s
        _error.value = error
    }

    fun setUpdating(v: Boolean) { _updating.value = v }

    fun setListSize(n: Int) = _stats.update { it.copy(listSize = n) }

    fun resetSession() {
        _stats.update { it.copy(queries = 0, blocked = 0) }
        _log.value = emptyList()
    }

    fun record(domain: String, blocked: Boolean) {
        _stats.update { it.copy(queries = it.queries + 1, blocked = it.blocked + if (blocked) 1 else 0) }
        _log.update { old ->
            // Collapse immediate repeats (apps often fire A + AAAA + HTTPS for the same name).
            if (old.isNotEmpty() && old[0].domain == domain && old[0].blocked == blocked) old
            else (listOf(QueryLog(domain, blocked, System.currentTimeMillis())) + old).take(LOG_CAP)
        }
    }
}
