package com.example.mangadex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


data class Manga(
    val id: String,
    val title: String,
    val status: String,
    val year: Int,
    val rating: String,
    val imageRes: Int
)

val initialMangaList = listOf(
    Manga(
        id = "1",
        title = "One Piece",
        status = "Читаю",
        year = 1997,
        rating = "9.2",
        imageRes = R.drawable.one_piece
    ),
    Manga(
        id = "2",
        title = "Naruto",
        status = "Прочитано",
        year = 1999,
        rating = "8.5",
        imageRes = R.drawable.naruto
    ),
    Manga(
        id = "3",
        title = "Berserk",
        status = "Хочу прочитати",
        year = 1989,
        rating = "9.4",
        imageRes = R.drawable.berserk
    ),
    Manga(
        id = "4",
        title = "Attack on Titan",
        status = "Читаю",
        year = 2009,
        rating = "9.0",
        imageRes = R.drawable.attack_on_titan
    ),
    Manga(
        id = "5",
        title = "Death Note",
        status = "Прочитано",
        year = 2003,
        rating = "8.6",
        imageRes = R.drawable.death_note
    ),
    Manga(
        id = "6",
        title = "Demon Slayer",
        status = "Хочу прочитати",
        year = 2016,
        rating = "8.7",
        imageRes = R.drawable.demon_slayer
    )
)

data class MangaScreenState(
    val selectedStatus: String = "Усі",
    val mangaList: List<Manga> = initialMangaList
)

class MangaViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        MangaScreenState()
    )

    val uiState: StateFlow<MangaScreenState> =
        _uiState.asStateFlow()

    fun selectStatus(status: String) {
        _uiState.value = _uiState.value.copy(
            selectedStatus = status
        )
    }

    fun changeMangaStatus(
        mangaId: String,
        newStatus: String
    ) {

        val updatedList = _uiState.value.mangaList.map { manga ->
            if (manga.id == mangaId) {
                manga.copy(
                    status = newStatus
                )
            } else {
                manga
            }
        }
        _uiState.value = _uiState.value.copy(
            mangaList = updatedList
        )
    }
}

class MainActivity : ComponentActivity() {
    private val mangaViewModel: MangaViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    MangaApp(
                        viewModel = mangaViewModel
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaApp(
    viewModel: MangaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val statuses = listOf(
        "Усі",
        "Читаю",
        "Прочитано",
        "Хочу прочитати"
    )

    val filteredManga = if (uiState.selectedStatus == "Усі") {
        uiState.mangaList
    } else {
        uiState.mangaList.filter { manga ->
            manga.status == uiState.selectedStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Полиця манги")
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            StatusSwitcher(
                options = statuses,
                selectedOption = uiState.selectedStatus,
                onOptionSelected = { status ->
                    viewModel.selectStatus(status)
                }
            )

            if (filteredManga.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Манги з таким статусом немає",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(16.dp)
                ) {


                    item {
                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )
                    }

                    items(
                        items = filteredManga,
                        key = { manga ->
                            manga.id
                        }
                    ) { manga ->
                        MangaCard(
                            manga = manga,
                            statuses = statuses.drop(1),
                            onStatusChanged = { newStatus ->
                                viewModel.changeMangaStatus(
                                    mangaId = manga.id,
                                    newStatus = newStatus
                                )
                            }
                        )
                    }


                    item {
                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusSwitcher(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 8.dp
            ),

        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        options.forEach { option ->
            FilterChip(
                selected = option == selectedOption,
                onClick = {
                    onOptionSelected(option)
                },
                label = {
                    Text(option)
                }
            )
        }
    }
}

@Composable
fun MangaCard(
    manga: Manga,
    statuses: List<String>,
    onStatusChanged: (String) -> Unit

) {

    val menuState = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }


    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Image(
                    painter = painterResource(
                        id = manga.imageRes
                    ),
                    contentDescription = manga.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )


                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                ) {


                    Surface(
                        modifier = Modifier.padding(8.dp),
                        shape = MaterialTheme.shapes.small
                    ) {

                        TextButton(
                            onClick = {
                                menuState.value = true
                            }
                        ) {
                            Text(manga.status)
                        }
                    }


                    DropdownMenu(
                        expanded = menuState.value,
                        onDismissRequest = {
                            menuState.value = false
                        }
                    ) {

                        statuses.forEach { status ->
                            DropdownMenuItem(
                                text = {
                                    Text(status)
                                },
                                onClick = {
                                    onStatusChanged(status)
                                    menuState.value = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = manga.title,
                style =
                    MaterialTheme.typography.headlineSmall
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            // -------------------------------------------------
            // ХАРАКТЕРИСТИКИ
            // -------------------------------------------------

            Row(

                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {


                Text(
                    text = "Рік: ${manga.year}",
                    style =
                        MaterialTheme.typography.bodyMedium
                )


                Text(
                    text = "Рейтинг: ${manga.rating}",
                    style =
                        MaterialTheme.typography.bodyMedium
                )
            }


            Spacer(
                modifier = Modifier.height(6.dp)
            )


            Text(
                text = "Статус: ${manga.status}",
                style =
                    MaterialTheme.typography.bodyMedium
            )
        }
    }
}