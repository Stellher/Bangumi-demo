package com.example.bangumioauthdemo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

data class Resource<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null
)

data class SubjectScreenData(
    val subject: JSONObject,
    val episodes: List<EpisodeCard>,
    val cast: List<PersonCard>,
    val staff: List<PersonCard>,
    val related: List<AnimeCard>,
    val collection: JSONObject?
)

class BangumiBrowseViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BangumiRepository()
    private val episodeDiscussionApi = EpisodeDiscussionApi()
    val authorization = OAuthSessionManager.session
    val authState = OAuthSessionManager.refreshState

    var trending by mutableStateOf(Resource<List<AnimeCard>>())
        private set
    var latest by mutableStateOf(Resource<List<AnimeCard>>())
        private set
    var calendar by mutableStateOf(Resource<List<BroadcastDay>>())
        private set
    var seasonal by mutableStateOf(Resource<List<AnimeCard>>())
        private set
    var search by mutableStateOf(Resource<List<AnimeCard>>())
        private set
    var peopleSearch by mutableStateOf(Resource<List<PersonCard>>())
        private set
    var library by mutableStateOf(Resource<List<CollectionCard>>())
        private set
    var profile by mutableStateOf(Resource<AccountCard>())
        private set
    var subject by mutableStateOf(Resource<SubjectScreenData>())
        private set
    var person by mutableStateOf(Resource<JSONObject>())
        private set
    var activeEpisode by mutableStateOf<EpisodeCard?>(null)
        private set
    var episodeComments by mutableStateOf(Resource<List<EpisodeComment>>())
        private set

    var selectedSubject by mutableStateOf<Int?>(null)
        private set
    var selectedPerson by mutableStateOf<Pair<Int, Boolean>?>(null)
        private set
    var actionBusy by mutableStateOf(false)
        private set
    var actionFeedback by mutableStateOf<String?>(null)
        private set
    var seasonYear by mutableStateOf(LocalDate.now().year)
        private set
    var seasonQuarter by mutableStateOf((LocalDate.now().monthValue - 1) / 3 + 1)
        private set
    var seasonCategory by mutableIntStateOf(1)
        private set
    var selectedCollectionType by mutableStateOf<Int?>(null)
        private set

    init { OAuthSessionManager.initialize(app) }

    fun dismissFeedback() { actionFeedback = null }
    fun announce(message: String) { actionFeedback = message }

    fun loadDiscover(force: Boolean = false) {
        if ((trending.data != null || trending.loading) && !force) return
        trending = Resource(loading = true)
        latest = Resource(loading = true)
        viewModelScope.launch {
            try {
                trending = Resource(data = repo.browse(type = 2, sort = "rank", limit = 24))
            } catch (e: Exception) { trending = Resource(error = e.message ?: "无法加载动画排行榜") }
        }
        viewModelScope.launch {
            try {
                latest = Resource(data = repo.browse(type = 2, sort = "date", limit = 24))
            } catch (e: Exception) { latest = Resource(error = e.message ?: "无法加载最新动画") }
        }
        loadCalendar()
    }

    fun loadCalendar(force: Boolean = false) {
        if ((calendar.data != null || calendar.loading) && !force) return
        calendar = Resource(loading = true)
        viewModelScope.launch {
            try { calendar = Resource(data = repo.calendar()) }
            catch (e: Exception) { calendar = Resource(error = e.message ?: "获取每日放送失败") }
        }
    }

    fun selectSeason(year: Int, quarter: Int) {
        seasonYear = year.coerceIn(2000, LocalDate.now().year + 2)
        seasonQuarter = quarter.coerceIn(1, 4)
        loadSeason(force = true)
    }

    fun selectSeasonCategory(category: Int) {
        if (seasonCategory == category) return
        require(category in listOf(0, 1, 2, 3, 5))
        seasonCategory = category
        loadSeason(force = true)
    }

    fun loadSeason(force: Boolean = false) {
        if ((seasonal.data != null || seasonal.loading) && !force) return
        val year = seasonYear
        val quarter = seasonQuarter
        val category = seasonCategory
        seasonal = Resource(loading = true)
        viewModelScope.launch {
            try {
                val result = repo.season(year, quarter, category)
                if (seasonYear == year && seasonQuarter == quarter && seasonCategory == category) {
                    seasonal = Resource(data = result)
                }
            } catch (e: Exception) { seasonal = Resource(error = e.message ?: "季度新番加载失败") }
        }
    }

    fun searchSubjects(text: String, type: Int? = null) {
        if (text.trim().isBlank()) {
            search = Resource()
            return
        }
        search = Resource(loading = true)
        viewModelScope.launch {
            try { search = Resource(data = repo.search(text, type)) }
            catch (e: Exception) { search = Resource(error = e.message ?: "搜索失败") }
        }
    }

    fun searchPeople(text: String, characters: Boolean) {
        if (text.trim().isBlank()) {
            peopleSearch = Resource()
            return
        }
        peopleSearch = Resource(loading = true)
        viewModelScope.launch {
            try {
                peopleSearch = Resource(data = repo.searchPeople(text, characters))
            } catch (e: Exception) {
                peopleSearch = Resource(error = e.message ?: "人物搜索失败")
            }
        }
    }

    fun loadAccount(force: Boolean = false) {
        if (authorization.value == null) {
            profile = Resource(error = "请先登录 Bangumi")
            return
        }
        if ((profile.data != null || profile.loading) && !force) return
        profile = Resource(loading = true)
        viewModelScope.launch {
            try { profile = Resource(data = repo.account()) }
            catch (e: Exception) { profile = Resource(error = e.message ?: "用户信息加载失败") }
        }
    }

    fun selectCollectionType(type: Int?) {
        selectedCollectionType = type
        loadLibrary(force = true)
    }

    fun loadLibrary(force: Boolean = false) {
        if (authorization.value == null) {
            library = Resource(error = "登录后查看你的收藏")
            return
        }
        if ((library.data != null || library.loading) && !force) return
        library = Resource(loading = true)
        viewModelScope.launch {
            try {
                var account = profile.data
                if (account == null) {
                    account = repo.account()
                    profile = Resource(data = account)
                }
                library = Resource(data = repo.collections(
                    username = account.username,
                    type = selectedCollectionType
                ))
            } catch (e: Exception) { library = Resource(error = e.message ?: "收藏加载失败") }
        }
    }

    fun openSubject(id: Int) {
        closeEpisodeDiscussion()
        selectedPerson = null
        selectedSubject = id
        loadSubject(id)
    }

    fun loadSubject(id: Int) {
        subject = Resource(loading = true)
        viewModelScope.launch {
            try {
                val detail = async { repo.detail(id) }
                val episodes = async { runCatching { repo.episodes(id) }.getOrDefault(emptyList()) }
                val cast = async { runCatching { repo.characters(id) }.getOrDefault(emptyList()) }
                val staff = async { runCatching { repo.staff(id) }.getOrDefault(emptyList()) }
                val related = async { runCatching { repo.related(id) }.getOrDefault(emptyList()) }
                val username = profile.data?.username
                    ?: if (authorization.value != null) runCatching { repo.account().username }.getOrNull() else null
                val collection = if (!username.isNullOrBlank()) {
                    runCatching { repo.userCollection(username, id) }.getOrNull()
                } else null
                val result = SubjectScreenData(
                    detail.await(), episodes.await(), cast.await(), staff.await(),
                    related.await(), collection
                )
                if (selectedSubject == id) subject = Resource(data = result)
            } catch (e: Exception) {
                if (selectedSubject == id) subject = Resource(error = e.message ?: "无法加载条目")
            }
        }
    }

    fun closeSubject() {
        closeEpisodeDiscussion()
        selectedSubject = null
        subject = Resource()
    }

    /** Read a chapter's public comments separately from its viewing progress. */
    fun openEpisodeDiscussion(episode: EpisodeCard) {
        if (episode.id <= 0) return
        activeEpisode = episode
        loadEpisodeComments(episode.id)
    }

    fun refreshEpisodeDiscussion() {
        activeEpisode?.let { loadEpisodeComments(it.id) }
    }

    private fun loadEpisodeComments(episodeId: Int) {
        episodeComments = Resource(loading = true)
        viewModelScope.launch {
            try {
                val result = episodeDiscussionApi.comments(episodeId)
                if (activeEpisode?.id == episodeId) episodeComments = Resource(data = result)
            } catch (e: Exception) {
                if (activeEpisode?.id == episodeId) {
                    episodeComments = Resource(error = e.message ?: "读取章节吐槽失败")
                }
            }
        }
    }

    fun closeEpisodeDiscussion() {
        activeEpisode = null
        episodeComments = Resource()
    }

    fun openPerson(id: Int, isCharacter: Boolean) {
        selectedPerson = id to isCharacter
        person = Resource(loading = true)
        viewModelScope.launch {
            try { person = Resource(data = repo.person(id, isCharacter)) }
            catch (e: Exception) { person = Resource(error = e.message ?: "加载人物失败") }
        }
    }

    fun closePerson() {
        selectedPerson = null
        person = Resource()
    }

    fun updateCollection(id: Int, edit: CollectionEdit, after: () -> Unit = {}) {
        if (actionBusy) return
        actionBusy = true
        actionFeedback = null
        viewModelScope.launch {
            try {
                repo.setCollection(id, edit)
                actionFeedback = "收藏已同步至 Bangumi"
                after()
                if (selectedSubject == id) loadSubject(id)
                if (authorization.value != null) loadLibrary(force = true)
            } catch (e: Exception) {
                actionFeedback = "保存失败：" + (e.message ?: "网络异常")
            } finally { actionBusy = false }
        }
    }

    fun toggleEpisode(episodeId: Int, completed: Boolean) {
        if (actionBusy) return
        actionBusy = true
        viewModelScope.launch {
            try {
                repo.setEpisodeCompleted(episodeId, completed)
                actionFeedback = if (completed) "章节已标记为看过" else "章节进度已重置"
            } catch (e: Exception) {
                actionFeedback = "章节保存失败：" + (e.message ?: "网络错误")
            } finally { actionBusy = false }
        }
    }
}
