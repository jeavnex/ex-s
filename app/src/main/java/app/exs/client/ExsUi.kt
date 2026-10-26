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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

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
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(140.dp)) {
                Text("EX-S", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("КИНО БЕЗ ШУМА", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CatalogSection.entries.forEach { section ->
                    FocusPill(section.label, state.section == section) { onSection(section) }
                }
            }
            Spacer(Modifier.width(12.dp))
            FocusPill(if (state.loggedIn) state.accountName.ifBlank { "Аккаунт" } else "Войти", false, onLogin)
            Spacer(Modifier.width(8.dp))
            FocusPill("Обновить EX-S", false, onUpdate)
        }
        Spacer(Modifier.height(22.dp))
        SearchBox(state.query, onSearch)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(state.section.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (state.section == CatalogSection.FAVORITES) "Закладки вашего аккаунта EX-FS" else "Выберите карточку стрелками пульта и нажмите OK",
                    color = Muted, fontSize = 14.sp,
                )
            }
            Text("${state.cards.size} материалов", color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        if (!state.loading && state.cards.isEmpty()) {
            EmptyState(if (state.section == CatalogSection.FAVORITES) "Войдите в аккаунт или добавьте фильмы в закладки на EX-FS" else "В этом разделе пока ничего не найдено", onRefresh)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(state.cards, key = { it.url }) { MediaPoster(it, onOpen) }
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
    Column(
        Modifier.width(160.dp).onFocusChanged { focused = it.isFocused }.focusable()
            .clickable { onOpen(item) },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.69f).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF17283B))
                .border(if (focused) 4.dp else 1.dp, if (focused) Cyan else Color(0x334A6480), RoundedCornerShape(14.dp))
        ) {
            AsyncImage(model = item.poster, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xC9000000)), startY = 300f)))
            if (item.badge.isNotBlank()) {
                Text(item.badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Violet, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp))
            }
            if (focused) {
                Text("▶", color = Ink, fontSize = 22.sp, modifier = Modifier.background(Cyan, RoundedCornerShape(50)).padding(horizontal = 15.dp, vertical = 10.dp))
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
    Box(Modifier.fillMaxSize()) {
        AsyncImage(model = detail.card.poster, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().drawBehind { })
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Ink, Color(0xF207111F), Color(0x9907111F)))))
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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (detail.players.ifEmpty { listOf(PlayerChoice(0, "Смотреть")) }).forEach { player ->
                        FocusPill(if (player.index == 0) "▶ ${player.label}" else player.label, player.index == 0) { onPlay(player.index) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Во время просмотра: OK — пауза, ←/→ — перемотка, Назад — выйти", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(42.dp))
            AsyncImage(model = detail.card.poster, contentDescription = detail.card.title, contentScale = ContentScale.Crop,
                modifier = Modifier.width(260.dp).aspectRatio(.69f).clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0x445BD6FF), RoundedCornerShape(22.dp)))
        }
    }
}

@Composable private fun InfoChip(text: String) = Text(text, color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color(0x5535D4FF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))

@Composable
private fun FocusPill(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label, color = if (selected || focused) Ink else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
            .background(if (selected || focused) Cyan else Panel, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected || focused) Cyan else Color(0x334A6480), RoundedCornerShape(12.dp))
            .padding(horizontal = 17.dp, vertical = 12.dp),
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
