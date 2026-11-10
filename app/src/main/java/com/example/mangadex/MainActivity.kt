package com.example.mangadex

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.SubcomposeAsyncImage
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.net.UnknownHostException

// -------------------- 1. ТИПИ ПОМИЛОК --------------------

enum class ApiErrorType(val message: String) {
    NO_INTERNET("Немає підключення до інтернету. Перевірте авіарежим або Wi-Fi."),
    SERVER_ERROR("Сервер тимчасово недоступний. Спробуйте пізніше."),
    PARSE_ERROR("Не вдалося обробити відповідь сервера. Оновіть застосунок."),
    UNKNOWN("Сталася невідома помилка. Спробуйте ще раз.")
}

// -------------------- 2. DTO ТА NETWORK --------------------

@Serializable
data class MangaResponseDto(
    @SerialName("data") val data: List<MangaDataDto> = emptyList()
)

@Serializable
data class MangaDataDto(
    @SerialName("id") val id: String,
    @SerialName("attributes") val attributes: MangaAttributesDto? = null,
    @SerialName("relationships") val relationships: List<RelationshipDto>? = emptyList()
)

@Serializable
data class RelationshipDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("attributes") val attributes: RelationshipAttributesDto? = null
)

@Serializable
data class RelationshipAttributesDto(
    @SerialName("fileName") val fileName: String? = null
)

@Serializable
data class MangaAttributesDto(
    @SerialName("title") val title: Map<String, String>? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("description") val description: Map<String, String>? = null
)

interface MangaDexApiService {
    @GET("manga")
    suspend fun getMangaList(
        @Query("limit") limit: Int = 20,
        @Query("title") title: String? = null,
        @Query("includes[]") includes: List<String> = listOf("cover_art")
    ): MangaResponseDto
}

object MangaDexNetwork {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.mangadex.org/")
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val apiService: MangaDexApiService by lazy {
        retrofit.create(MangaDexApiService::class.java)
    }
}

fun MangaDataDto.toDomainModel(localStatus: String = "Усі"): Manga {
    val attr = this.attributes
    val parsedTitle = attr?.title?.get("en")
        ?: attr?.title?.values?.firstOrNull()
        ?: "Без назви"

    val parsedDescription = attr?.description?.get("en")
        ?: attr?.description?.values?.firstOrNull()
        ?: "Опис відсутній"

    val coverFileName = this.relationships
        ?.find { it.type == "cover_art" }
        ?.attributes?.fileName

    val coverUrl = if (!coverFileName.isNullOrEmpty()) {
        "https://uploads.mangadex.org/covers/${this.id}/$coverFileName"
    } else {
        null
    }

    return Manga(
        id = this.id,
        title = parsedTitle,
        status = localStatus,
        year = attr?.year ?: 0,
        rating = "8.5",
        author = "MangaDex Author",
        genres = listOf("Манга", attr?.status ?: "Unknown"),
        description = parsedDescription,
        coverUrl = coverUrl
    )
}

// -------------------- 3. DATASTORE (Зображення Обраного ID) --------------------

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "manga_settings")

enum class SortOption(val title: String) {
    DEFAULT("За замовчуванням"),
    BY_TITLE("За назвою (A-Z)"),
    BY_YEAR("За роком (спочатку нові)")
}

class SettingsRepository(private val context: Context) {
    companion object {
        val SORT_KEY = stringPreferencesKey("sort_option")
        val FAVORITES_KEY = stringSetPreferencesKey("favorite_ids")
    }

    val sortOptionFlow: Flow<SortOption> = context.dataStore.data.map { preferences ->
        val savedName = preferences[SORT_KEY] ?: SortOption.DEFAULT.name
        try {
            SortOption.valueOf(savedName)
        } catch (_: Exception) {
            SortOption.DEFAULT
        }
    }

    val favoriteIdsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[FAVORITES_KEY] ?: emptySet()
    }

    suspend fun saveSortOption(sortOption: SortOption) {
        context.dataStore.edit { preferences ->
            preferences[SORT_KEY] = sortOption.name
        }
    }

    suspend fun toggleFavorite(mangaId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[FAVORITES_KEY] ?: emptySet()
            if (current.contains(mangaId)) {
                preferences[FAVORITES_KEY] = current - mangaId
            } else {
                preferences[FAVORITES_KEY] = current + mangaId
            }
        }
    }
}

// -------------------- 4. DOMAIN MODEL --------------------

data class Manga(
    val id: String,
    val title: String,
    val status: String,
    val year: Int,
    val rating: String,
    val author: String,
    val genres: List<String>,
    val description: String,
    val coverUrl: String? = null
)

val readingStatuses = listOf(
    "Усі",
    "Читаю",
    "Прочитано",
    "Хочу прочитати"
)

// -------------------- 5. STATE & VIEWMODEL --------------------

data class MangaScreenState(
    val isLoading: Boolean = false,
    val errorType: ApiErrorType? = null,
    val searchQuery: String = "",
    val selectedStatus: String = "Усі",
    val mangaList: List<Manga> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val sortOption: SortOption = SortOption.DEFAULT
)

class MangaViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private val _isLoading = MutableStateFlow(false)
    private val _errorType = MutableStateFlow<ApiErrorType?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedStatus = MutableStateFlow("Усі")
    private val _mangaList = MutableStateFlow<List<Manga>>(emptyList())

    private var searchJob: Job? = null

    val uiState: StateFlow<MangaScreenState> = combine(
        combine(_isLoading, _errorType, _searchQuery, ::Triple),
        combine(_selectedStatus, _mangaList, settingsRepository.favoriteIdsFlow, ::Triple),
        settingsRepository.sortOptionFlow
    ) { (isLoading, error, query), (status, list, favorites), sort ->
        MangaScreenState(
            isLoading = isLoading,
            errorType = error,
            searchQuery = query,
            selectedStatus = status,
            mangaList = list,
            favoriteIds = favorites,
            sortOption = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MangaScreenState(isLoading = true)
    )

    init {
        fetchMangaFromApi()
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            delay(500L)
            fetchMangaFromApi()
        }
    }

    fun clearSearchQuery() {
        _searchQuery.value = ""
        fetchMangaFromApi()
    }

    fun fetchMangaFromApi() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorType.value = null
            try {
                val query = _searchQuery.value.trim().ifEmpty { null }
                val response = MangaDexNetwork.apiService.getMangaList(
                    limit = 20,
                    title = query
                )
                val domainList = response.data.map { dto ->
                    dto.toDomainModel(localStatus = "Усі")
                }
                _mangaList.value = domainList
            } catch (_: UnknownHostException) {
                _errorType.value = ApiErrorType.NO_INTERNET
            } catch (_: IOException) {
                _errorType.value = ApiErrorType.NO_INTERNET
            } catch (_: HttpException) {
                _errorType.value = ApiErrorType.SERVER_ERROR
            } catch (_: SerializationException) {
                _errorType.value = ApiErrorType.PARSE_ERROR
            } catch (_: Exception) {
                _errorType.value = ApiErrorType.UNKNOWN
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(mangaId: String) {
        viewModelScope.launch {
            settingsRepository.toggleFavorite(mangaId)
        }
    }

    fun selectStatus(status: String) {
        _selectedStatus.value = status
    }

    fun changeMangaStatus(mangaId: String, newStatus: String) {
        _mangaList.update { currentList ->
            currentList.map { manga ->
                if (manga.id == mangaId) manga.copy(status = newStatus) else manga
            }
        }
    }

    fun updateSortOption(sortOption: SortOption) {
        viewModelScope.launch {
            settingsRepository.saveSortOption(sortOption)
        }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MangaApp()
                }
            }
        }
    }
}

@Composable
fun MangaApp(
    viewModel: MangaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute == "catalog" || currentRoute == "favorites") {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "catalog",
                        onClick = {
                            navController.navigate("catalog") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text("📚", style = MaterialTheme.typography.titleLarge) },
                        label = { Text("Каталог") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == "favorites",
                        onClick = {
                            navController.navigate("favorites") {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text("❤️", style = MaterialTheme.typography.titleLarge) },
                        label = { Text("Добірка") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "catalog",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("catalog") {
                CatalogScreen(
                    state = uiState,
                    onSearchQueryChanged = { query -> viewModel.onSearchQueryChanged(query) },
                    onClearSearch = { viewModel.clearSearchQuery() },
                    onStatusSelected = { status -> viewModel.selectStatus(status) },
                    onItemClick = { mangaId -> navController.navigate("details/$mangaId") },
                    onFavoriteToggle = { mangaId -> viewModel.toggleFavorite(mangaId) },
                    onSettingsClick = { navController.navigate("settings") },
                    onRetryClick = { viewModel.fetchMangaFromApi() },
                    onRefresh = { viewModel.fetchMangaFromApi() }
                )
            }

            composable("favorites") {
                FavoritesScreen(
                    state = uiState,
                    onItemClick = { mangaId -> navController.navigate("details/$mangaId") },
                    onFavoriteToggle = { mangaId -> viewModel.toggleFavorite(mangaId) },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }

            composable("settings") {
                SettingsScreen(
                    currentSortOption = uiState.sortOption,
                    onSortOptionSelected = { sortOption -> viewModel.updateSortOption(sortOption) },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "details/{mangaId}",
                arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mangaId = backStackEntry.arguments?.getString("mangaId")
                val manga = uiState.mangaList.find { it.id == mangaId }

                if (manga != null) {
                    MangaDetailsScreen(
                        manga = manga,
                        isFavorite = uiState.favoriteIds.contains(manga.id),
                        onFavoriteToggle = { viewModel.toggleFavorite(manga.id) },
                        onBackClick = { navController.popBackStack() },
                        onStatusChange = { newStatus ->
                            viewModel.changeMangaStatus(mangaId = manga.id, newStatus = newStatus)
                        }
                    )
                } else {
                    NotFoundScreen(onBackClick = { navController.popBackStack() })
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: MangaScreenState,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onStatusSelected: (String) -> Unit,
    onItemClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onRetryClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Сповіщення увімкнено!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "У доступі до сповіщень відмовлено", Toast.LENGTH_SHORT).show()
        }
    }

    val filteredManga = if (state.selectedStatus == "Усі") {
        state.mangaList
    } else {
        state.mangaList.filter { it.status == state.selectedStatus }
    }

    val sortedManga = when (state.sortOption) {
        SortOption.DEFAULT -> filteredManga
        SortOption.BY_TITLE -> filteredManga.sortedBy { it.title }
        SortOption.BY_YEAR -> filteredManga.sortedByDescending { it.year }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Полиця манги") },
                actions = {
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    Toast.makeText(context, "Сповіщення вже активовані", Toast.LENGTH_SHORT).show()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                Toast.makeText(context, "Сповіщення увімкнено за замовчуванням", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("🔔", style = MaterialTheme.typography.titleLarge)
                    }

                    IconButton(onClick = onSettingsClick) {
                        Text("⚙️", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("Пошук манги...") },
                singleLine = true,
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = onClearSearch) {
                            Text("✕", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("🔍")
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                readingStatuses.forEach { status ->
                    FilterChip(
                        selected = state.selectedStatus == status,
                        onClick = { onStatusSelected(status) },
                        label = { Text(status) }
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.errorType != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Помилка завантаження",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.errorType.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onRetryClick) {
                                Text("Спробувати ще")
                            }
                        }
                    }
                    sortedManga.isEmpty() && !state.isLoading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (state.searchQuery.isNotEmpty()) {
                                Text(
                                    text = "За запитом «${state.searchQuery}» нічого не знайдено",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = onClearSearch) {
                                    Text("Очистити пошук")
                                }
                            } else {
                                Text(
                                    text = if (state.selectedStatus == "Усі") "Список манги порожній" else "Манги з таким статусом немає",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = sortedManga,
                                key = { it.id }
                            ) { manga ->
                                MangaCard(
                                    manga = manga,
                                    isFavorite = state.favoriteIds.contains(manga.id),
                                    onFavoriteToggle = { onFavoriteToggle(manga.id) },
                                    onClick = { onItemClick(manga.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: MangaScreenState,
    onItemClick: (String) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    // Філтруємо мангу за збереженими ID з DataStore
    val favoriteManga = state.mangaList.filter { state.favoriteIds.contains(it.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Особиста добірка") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Text("⚙️", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        if (favoriteManga.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Ваша добірка порожня",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Додайте мангу до обраного, натиснувши на ❤️ у каталозі або на екрані деталей.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = favoriteManga,
                    key = { it.id }
                ) { manga ->
                    MangaCard(
                        manga = manga,
                        isFavorite = true,
                        onFavoriteToggle = { onFavoriteToggle(manga.id) },
                        onClick = { onItemClick(manga.id) }
                    )
                }
            }
        }
    }
}


@Composable
fun MangaCoverImage(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    if (!coverUrl.isNullOrEmpty()) {
        SubcomposeAsyncImage(
            model = coverUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            },
            error = {
                CoverPlaceholder(title = title, modifier = Modifier.fillMaxSize())
            }
        )
    } else {
        CoverPlaceholder(title = title, modifier = modifier)
    }
}

@Composable
fun MangaCard(
    manga: Manga,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MangaCoverImage(
                coverUrl = manga.coverUrl,
                title = manga.title,
                modifier = Modifier.size(width = 88.dp, height = 116.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onFavoriteToggle) {
                        Text(if (isFavorite) "❤️" else "🤍", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text("Статус: ${manga.status}")
                if (manga.year > 0) Text("Рік: ${manga.year}")

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Натисни, щоб переглянути деталі",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailsScreen(
    manga: Manga,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onBackClick: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    var statusMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Деталі манги") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                actions = {
                    IconButton(onClick = onFavoriteToggle) {
                        Text(if (isFavorite) "❤️" else "🤍", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MangaCoverImage(
                coverUrl = manga.coverUrl,
                title = manga.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = manga.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (manga.year > 0) Text("Рік випуску: ${manga.year}")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Опис",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = manga.description,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Статус читання",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Box {
                Button(onClick = { statusMenuExpanded = true }) {
                    Text(manga.status)
                }

                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false }
                ) {
                    readingStatuses
                        .filter { it != "Усі" }
                        .forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status) },
                                onClick = {
                                    onStatusChange(status)
                                    statusMenuExpanded = false
                                }
                            )
                        }
                }
            }
        }
    }
}

@Composable
fun CoverPlaceholder(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Налаштування") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Сортування за замовчуванням",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            SortOption.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortOptionSelected(option) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (option == currentSortOption),
                        onClick = { onSortOptionSelected(option) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = option.title)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotFoundScreen(
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Деталі манги") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Мангу не знайдено",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onBackClick) {
                Text("Повернутися до каталогу")
            }
        }
    }
}