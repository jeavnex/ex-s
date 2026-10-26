package app.exs.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val Ink = Color(0xFF07111F)
private val Panel = Color(0xE6162436)
private val Cyan = Color(0xFF35D4FF)
private val Violet = Color(0xFF8B5CF6)
private val Muted = Color(0xFFA8B6C8)

@Composable
fun ExsApp(
    state: ExsState,
    onSection: (CatalogSection) -> Unit,
    onOpen: (MediaCard) -> Unit,
    onSearch: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: () -> Unit,
) {
    MaterialTheme {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFF173357), Ink), center = Offset(900f, 80f), radius = 1500f)
            )
        ) {
            when (state.screen) {
                AppScreen.CATALOG -> CatalogScreen(state, onSection, onOpen, onSearch, onLogin, onRefresh, onUpdate)
                AppScreen.DETAIL -> DetailScreen(state, onPlay, onBack)
                AppScreen.PLAYER, AppScreen.LOGIN -> Unit
            }
            if (state.loading && state.screen != AppScreen.PLAYER) LoadingLayer()
            state.error?.let { ErrorBanner(it, onRefresh) }
        }
    }
}

@Composable
private fun CatalogScreen(
    state: ExsState,
    onSection: (CatalogSection) -> Unit,
    onOpen: (MediaCard) -> Unit,
    onSearch: (String) -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 14.dp else 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EX-S", color = Color.White, fontSize = if (compact) 29.sp else 32.sp, fontWeight = FontWeight.Black)
                    Text("КИНО БЕЗ ШУМА", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                FocusPill(if (state.loggedIn) "Аккаунт" else "Войти", false, onLogin, compact = compact)
                Spacer(Modifier.width(7.dp))
                FocusPill("Обновить", false, onUpdate, compact = compact)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogSection.entries.forEach { section -> FocusPill(section.label, state.section == section, onClick = { onSection(section) }) }
            }
            Spacer(Modifier.height(16.dp))
            SearchBox(state.query, onSearch)
            Spacer(Modifier.height(18.dp))
            if (compact) {
                Text(state.section.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${state.cards.size} материалов", color = Muted, fontSize = 13.sp)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(state.section.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(if (state.section == CatalogSection.FAVORITES) "Закладки вашего аккаунта EX-FS" else "Выберите карточку стрелками пульта и нажмите OK", color = Muted, fontSize = 14.sp)
                    }
                    Text("${state.cards.size} материалов", color = Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (!state.loading && state.cards.isEmpty()) {
                EmptyState(if (state.section == CatalogSection.FAVORITES) "Войдите в аккаунт или добавьте фильмы в закладки на EX-FS" else "В этом разделе пока ничего не найдено", onRefresh)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (compact) 132.dp else 150.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) { items(state.cards, key = { it.url }) { MediaPoster(it, onOpen) } }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onSearch: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color(0xFF203A57) else Panel)
            .border(if (focused) 2.dp else 1.dp, if (focused) Cyan else Color(0x334A6480), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = Cyan, fontSize = 28.sp)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it; if (it.length >= 2 || it.isEmpty()) onSearch(it) },
            modifier = Modifier.weight(1f).focusable(interactionSource = interaction),
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Cyan),
            singleLine = true,
            decorationBox = { inner -> if (text.isEmpty()) Text("Поиск фильма или сериала", color = Muted, fontSize = 18.sp); inner() },
        )
    }
}

@Composable
private fun MediaPoster(item: MediaCard, onOpen: (MediaCard) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var imageFailed by remember(item.poster) { mutableStateOf(false) }
    Column(
        Modifier.width(160.dp).onFocusChanged { focused = it.isFocused }.focusable()
            .clickable { onOpen(item) },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.69f).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF17283B))
                .border(if (focused) 4.dp else 1.dp, if (focused) Cyan else Color(0x334A6480), RoundedCornerShape(14.dp))
        ) {
            Text("EX-S", color = Color(0x556B829B), fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
            if (!imageFailed) PosterImage(item.poster, item.title, Modifier.fillMaxSize()) { imageFailed = true }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xC9000000)), startY = 300f)))
            if (item.badge.isNotBlank()) {
                Text(item.badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Violet, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
            }
            if (focused) {
                Text("▶", color = Ink, fontSize = 22.sp, modifier = Modifier.align(Alignment.Center).background(Cyan, RoundedCornerShape(50)).padding(horizontal = 15.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.meta.isNotBlank()) Text(item.meta, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailScreen(state: ExsState, onPlay: (Int) -> Unit, onBack: () -> Unit) {
    val detail = state.detail ?: return
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10243B), Ink)))) {
        val compact = maxWidth < 700.dp
        if (compact) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                FocusPill("← Назад", false, onBack)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Top) {
                    PosterImage(detail.card.poster, detail.card.title, Modifier.width(112.dp).aspectRatio(.69f).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(detail.card.title, color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                        if (detail.originalTitle.isNotBlank()) Text(detail.originalTitle, color = Muted, fontSize = 15.sp)
                        if (detail.rating.isNotBlank()) { Spacer(Modifier.height(8.dp)); InfoChip("★ ${detail.rating}") }
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (detail.card.meta.isNotBlank()) InfoChip(detail.card.meta)
                if (detail.facts.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(detail.facts, color = Muted, fontSize = 14.sp) }
                Spacer(Modifier.height(16.dp))
                Text(detail.description, color = Color(0xFFE1E8F0), fontSize = 16.sp, lineHeight = 23.sp)
                Spacer(Modifier.height(22.dp))
                PlayerButtons(detail, onPlay)
                Spacer(Modifier.height(26.dp))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).widthIn(max = 760.dp)) {
                    FocusPill("← Назад", false, onBack)
                    Spacer(Modifier.height(30.dp))
                    Text(detail.card.title, color = Color.White, fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black)
                    if (detail.originalTitle.isNotBlank()) Text(detail.originalTitle, color = Muted, fontSize = 19.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (detail.rating.isNotBlank()) InfoChip("★ ${detail.rating}")
                        if (detail.card.meta.isNotBlank()) InfoChip(detail.card.meta)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(detail.description, color = Color(0xFFE1E8F0), fontSize = 17.sp, lineHeight = 25.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    if (detail.facts.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(detail.facts, color = Muted, fontSize = 14.sp, maxLines = 3) }
                    Spacer(Modifier.height(28.dp))
                    PlayerButtons(detail, onPlay)
                    Spacer(Modifier.height(12.dp))
                    Text("Во время просмотра: OK — пауза, ←/→ — перемотка, Назад — выйти", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(42.dp))
                PosterImage(detail.card.poster, detail.card.title, Modifier.width(260.dp).aspectRatio(.69f).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0x445BD6FF), RoundedCornerShape(22.dp)))
            }
        }
    }
}

@Composable
private fun PlayerButtons(detail: MediaDetail, onPlay: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        detail.players.ifEmpty { listOf(PlayerChoice(0, "Смотреть")) }.forEachIndexed { position, player ->
            FocusPill(if (position == 0) "▶ ${player.label}" else player.label, position == 0, onClick = { onPlay(player.index) })
        }
    }
}

@Composable
private fun PosterImage(url: String, title: String?, modifier: Modifier, onError: () -> Unit = {}) {
    val request = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build()
    AsyncImage(model = request, contentDescription = title, modifier = modifier.background(Color(0xFF17283B)), contentScale = ContentScale.Crop, onError = { onError() })
}

@Composable private fun InfoChip(text: String) = Text(text, color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color(0x5535D4FF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))

@Composable
private fun FocusPill(label: String, selected: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label, color = if (selected || focused) Ink else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
            .background(if (selected || focused) Cyan else Panel, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected || focused) Cyan else Color(0x334A6480), RoundedCornerShape(12.dp))
            .padding(horizontal = if (compact) 12.dp else 17.dp, vertical = if (compact) 10.dp else 12.dp),
    )
}

@Composable private fun LoadingLayer() = Box(Modifier.fillMaxSize().background(Color(0x8807111F)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Cyan) }

@Composable
private fun ErrorBanner(message: String, retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF7F1D1D)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = Color.White, modifier = Modifier.weight(1f))
        Text("Повторить", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = retry).padding(8.dp))
    }
}

@Composable
private fun EmptyState(message: String, retry: () -> Unit) = Column(Modifier.fillMaxWidth().padding(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(message, color = Muted, fontSize = 18.sp)
    Spacer(Modifier.height(16.dp))
    FocusPill("Повторить", false, retry)
}
