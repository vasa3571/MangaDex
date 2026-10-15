package com.example.mangadex

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

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

val initialMangaList = listOf(
    Manga(
        id = "one_piece",
        title = "One Piece",
        status = "Читаю",
        year = 1997,
        rating = "9.2",
        author = "Ейічіро Ода",
        genres = listOf("Пригоди", "Фентезі", "Екшн"),
        description = "Манга розповідає про пригоди Манкі Д. Луффі та його команди, які вирушають у море на пошуки легендарного скарбу One Piece."
    ),
    Manga(
        id = "naruto",
        title = "Naruto",
        status = "Прочитано",
        year = 1999,
        rating = "8.5",
        author = "Масаші Кішімото",
        genres = listOf("Екшн", "Пригоди", "Ніндзя"),
        description = "Історія Наруто Узумакі — юного ніндзя, який прагне стати Хокаґе та здобути повагу свого селища."
    ),
    Manga(
        id = "berserk",
        title = "Berserk",
        status = "Хочу прочитати",
        year = 1989,
        rating = "9.4",
        author = "Кентаро Міура",
        genres = listOf("Темне фентезі", "Драма", "Екшн"),
        description = "Темне фентезі про воїна Ґатса, який мандрує жорстоким світом і протистоїть небезпечним ворогам."
    ),
    Manga(
        id = "attack_on_titan",
        title = "Attack on Titan",
        status = "Читаю",
        year = 2009,
        rating = "9.0",
        author = "Хаджіме Ісаяма",
        genres = listOf("Екшн", "Драма", "Фентезі"),
        description = "Людство живе за величезними стінами, що захищають його від титанів. Ерен та його друзі намагаються розкрити таємниці навколишнього світу."
    ),
    Manga(
        id = "death_note",
        title = "Death Note",
        status = "Прочитано",
        year = 2003,
        rating = "8.6",
        author = "Цуґумі Оба",
        genres = listOf("Трилер", "Містика", "Детектив"),
        description = "Старшокласник знаходить загадковий зошит, який змінює його життя, і вступає в інтелектуальне протистояння з детективом."
    ),
    Manga(
        id = "demon_slayer",
        title = "Demon Slayer",
        status = "Хочу прочитати",
        year = 2016,
        rating = "8.7",
        author = "Койохару Ґотоґе",
        genres = listOf("Екшн", "Фентезі", "Пригоди"),
        description = "Танджіро вирушає в небезпечну подорож, щоб урятувати свою сестру та протистояти демонам."
    )
)

// -------------------- СТАН ЕКРАНА --------------------

data class MangaScreenState(
    val selectedStatus: String = "Усі",
    val mangaList: List<Manga> = initialMangaList
)

// -------------------- VIEWMODEL --------------------

class MangaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MangaScreenState())

    val uiState: StateFlow<MangaScreenState> =
        _uiState.asStateFlow()

    fun selectStatus(status: String) {
        _uiState.update { current ->
            current.copy(selectedStatus = status)
        }
    }

    fun changeMangaStatus(mangaId: String, newStatus: String) {
        _uiState.update { current ->
            current.copy(
                mangaList = current.mangaList.map { manga ->
                    if (manga.id == mangaId) {
                        manga.copy(status = newStatus)
                    } else {
                        manga
                    }
                }
            )
        }
    }
}

// -------------------- ACTIVITY --------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
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
                onStatusSelected = { status ->
                    viewModel.selectStatus(status)
                },
                onItemClick = { mangaId ->
                    navController.navigate("details/$mangaId")
                }
            )
        }

        composable(
            route = "details/{mangaId}",
            arguments = listOf(
                navArgument("mangaId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val mangaId =
                backStackEntry.arguments?.getString("mangaId")

            val manga = uiState.mangaList.find {
                it.id == mangaId
            }

            if (manga != null) {
                MangaDetailsScreen(
                    manga = manga,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onStatusChange = { newStatus ->
                        viewModel.changeMangaStatus(
                            mangaId = manga.id,
                            newStatus = newStatus
                        )
                    }
                )
            } else {
                NotFoundScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: MangaScreenState,
    onStatusSelected: (String) -> Unit,
    onItemClick: (String) -> Unit
) {
    val filteredManga = if (state.selectedStatus == "Усі") {
        state.mangaList
    } else {
        state.mangaList.filter {
            it.status == state.selectedStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Полиця манги") }
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
                        onClick = {
                            onStatusSelected(status)
                        },
                        label = { Text(status) }
                    )
                }
            }

            if (filteredManga.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Манги з таким статусом немає")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredManga,
                        key = { it.id }
                    ) { manga ->
                        MangaCard(
                            manga = manga,
                            onClick = {
                                onItemClick(manga.id)
                            }
                        )
                    }
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
                Text("Рік: ${manga.year}")
                Text("Рейтинг: ${manga.rating}/10")

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

            Text(
                text = "Автор: ${manga.author}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text("Рік випуску: ${manga.year}")
            Text("Рейтинг: ${manga.rating}/10")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Жанри",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(manga.genres.joinToString(", "))

            Spacer(modifier = Modifier.height(16.dp))

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
                Button(
                    onClick = {
                        statusMenuExpanded = true
                    }
                ) {
                    Text(manga.status)
                }

                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = {
                        statusMenuExpanded = false
                    }
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