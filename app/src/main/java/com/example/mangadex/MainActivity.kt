package com.example.mangadex

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query


@Serializable
data class MangaResponseDto(
    @SerialName("data") val data: List<MangaDataDto> = emptyList()
)

@Serializable
data class MangaDataDto(
    @SerialName("id") val id: String,
    @SerialName("attributes") val attributes: MangaAttributesDto? = null
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
        @Query("limit") limit: Int = 20
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

    return Manga(
        id = this.id,
        title = parsedTitle,
        status = localStatus,
        year = attr?.year ?: 0,
        rating = "8.5", // API не віддає рейтинг у базовому запиті
        author = "MangaDex Author",
        genres = listOf("Манга", attr?.status ?: "Unknown"),
        description = parsedDescription
    )
}


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "manga_settings")

enum class SortOption(val title: String) {
    DEFAULT("За замовчуванням"),
    BY_TITLE("За назвою (A-Z)"),
    BY_YEAR("За роком (спочатку нові)")
}

class SettingsRepository(private val context: Context) {
    companion object {
        val SORT_KEY = stringPreferencesKey("sort_option")
    }

    val sortOptionFlow: Flow<SortOption> = context.dataStore.data.map { preferences ->
        val savedName = preferences[SORT_KEY] ?: SortOption.DEFAULT.name
        try {
            SortOption.valueOf(savedName)
        } catch (e: Exception) {
            SortOption.DEFAULT
        }
    }

    suspend fun saveSortOption(sortOption: SortOption) {
        context.dataStore.edit { preferences ->
            preferences[SORT_KEY] = sortOption.name
        }
    }
}

data class Manga(
    val id: String,
    val title: String,
    val status: String,
    val year: Int,
    val rating: String,
    val author: String,
    val genres: List<String>,
    val description: String
)

val readingStatuses = listOf(
    "Усі",
    "Читаю",
    "Прочитано",
    "Хочу прочитати"
)


data class MangaScreenState(
    val isLoading: Boolean = false,
    val selectedStatus: String = "Усі",
    val mangaList: List<Manga> = emptyList(),
    val sortOption: SortOption = SortOption.DEFAULT
)

class MangaViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    private val _isLoading = MutableStateFlow(false)
    private val _selectedStatus = MutableStateFlow("Усі")
    private val _mangaList = MutableStateFlow<List<Manga>>(emptyList())
    private val _localStatuses = MutableStateFlow<Map<String, String>>(emptyMap())

    val uiState: StateFlow<MangaScreenState> = combine(
        _isLoading,
        _selectedStatus,
        _mangaList,
        settingsRepository.sortOptionFlow
    ) { isLoading, status, list, sort ->
        MangaScreenState(
            isLoading = isLoading,
            selectedStatus = status,
            mangaList = list,
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

    fun fetchMangaFromApi() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = MangaDexNetwork.apiService.getMangaList(limit = 20)
                val domainList = response.data.map { dto ->
                    val savedStatus = _localStatuses.value[dto.id] ?: "Усі"
                    dto.toDomainModel(localStatus = savedStatus)
                }
                _mangaList.value = domainList
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectStatus(status: String) {
        _selectedStatus.value = status
    }

    fun changeMangaStatus(mangaId: String, newStatus: String) {
        _localStatuses.update { current ->
            current + (mangaId to newStatus)
        }
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

    NavHost(
        navController = navController,
        startDestination = "catalog"
    ) {
        composable("catalog") {
            CatalogScreen(
                state = uiState,
                onStatusSelected = { status -> viewModel.selectStatus(status) },
                onItemClick = { mangaId -> navController.navigate("details/$mangaId") },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: MangaScreenState,
    onStatusSelected: (String) -> Unit,
    onItemClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
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
                title = { Text("Полиця манги (MangaDex)") },
                actions = {
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

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (sortedManga.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Манги не знайдено")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = sortedManga,
                        key = { it.id }
                    ) { manga ->
                        MangaCard(
                            manga = manga,
                            onClick = { onItemClick(manga.id) }
                        )
                    }
                }
            }
        }
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

@Composable
fun MangaCard(
    manga: Manga,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverPlaceholder(
                title = manga.title,
                modifier = Modifier.size(width = 88.dp, height = 116.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            CoverPlaceholder(
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