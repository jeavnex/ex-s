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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

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
    onDiagnostics: () -> Unit,
    onClearLogs: () -> Unit,
    onShareLogs: () -> Unit,
) {
    MaterialTheme {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color(0xFF173357), Ink), center = Offset(900f, 80f), radius = 1500f)
            )
        ) {
            when (state.screen) {
                AppScreen.CATALOG -> CatalogScreen(state, onSection, onOpen, onSearch, onLogin, onRefresh, onUpdate, onDiagnostics)
                AppScreen.DETAIL -> DetailScreen(state, onPlay, onBack)
                AppScreen.DIAGNOSTICS -> DiagnosticsScreen(state, onBack, onClearLogs, onShareLogs)
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
    onDiagnostics: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp || maxHeight < 500.dp
        if (compact) PhoneCatalog(state, onSection, onOpen, onSearch, onLogin, onRefresh, onUpdate, onDiagnostics)
        else TvCatalog(state, onSection, onOpen, onSearch, onLogin, onRefresh, onUpdate, onDiagnostics)
    }
}

@Composable
private fun PhoneCatalog(
    state: ExsState,
    onSection: (CatalogSection) -> Unit,
    onOpen: (MediaCard) -> Unit,
    onSearch: (String) -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(start = 16.dp, top = 14.dp, end = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Brand(Modifier.weight(1f), compact = true)
                CompactAction(if (state.loggedIn) "Аккаунт" else "Войти", onLogin)
                Spacer(Modifier.width(7.dp))
                VersionBadge(state, onUpdate)
                Spacer(Modifier.width(7.dp))
                CompactAction("⚙", onDiagnostics)
            }
            Spacer(Modifier.height(14.dp))
            SearchBox(state.query, onSearch)
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogSection.entries.forEach { section ->
                    SectionChip(section.label, state.section == section) { onSection(section) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(state.section.label, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.section == CatalogSection.FAVORITES) "Закладки аккаунта EX-FS" else "Выберите фильм или сериал", color = Muted, fontSize = 13.sp)
                }
                Text("${state.cards.size}", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            CatalogGrid(state, onOpen, onRefresh, compact = true, modifier = Modifier.weight(1f))
        }
        PhoneNavigation(state.section, onSection)
    }
}

@Composable
private fun TvCatalog(
    state: ExsState,
    onSection: (CatalogSection) -> Unit,
    onOpen: (MediaCard) -> Unit,
    onSearch: (String) -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(start = 26.dp, top = 22.dp, end = 28.dp, bottom = 20.dp)) {
        TvNavigation(state, onSection, onLogin, onUpdate, onDiagnostics)
        Spacer(Modifier.width(26.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchBox(state.query, onSearch, Modifier.weight(1f))
                FocusPill("↻ Обновить", false, onUpdate)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatalogSection.entries.filterNot { it.primary }.forEach { section ->
                    SectionChip(section.label, state.section == section) { onSection(section) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(state.section.label, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(if (state.section == CatalogSection.FAVORITES) "Закладки вашего аккаунта EX-FS" else "Стрелки — выбор, OK — открыть", color = Muted, fontSize = 14.sp)
                }
                Text("${state.cards.size} материалов", color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            CatalogGrid(state, onOpen, onRefresh, compact = false, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier) {
        Text("EX-S", color = Color.White, fontSize = if (compact) 29.sp else 34.sp, fontWeight = FontWeight.Black)
        Text("КИНО БЕЗ ШУМА", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun TvNavigation(state: ExsState, onSection: (CatalogSection) -> Unit, onLogin: () -> Unit, onUpdate: () -> Unit, onDiagnostics: () -> Unit) {
    val first = remember { FocusRequester() }
    Column(Modifier.width(180.dp).fillMaxHeight()) {
        Brand(Modifier.padding(start = 10.dp, bottom = 24.dp))
        CatalogSection.entries.filter { it.primary }.forEachIndexed { index, section ->
            NavItem(
                icon = when (section) {
                    CatalogSection.HOME -> "⌂"
                    CatalogSection.MOVIES -> "▶"
                    CatalogSection.SERIES -> "▤"
                    CatalogSection.CARTOONS -> "◇"
                    CatalogSection.SHOWS -> "▣"
                    CatalogSection.FAVORITES -> "♥"
                    else -> "•"
                },
                label = section.label,
                selected = state.section == section,
                onClick = { onSection(section) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(if (state.loggedIn) state.accountName.ifBlank { "ВХОД ВЫПОЛНЕН" } else "АККАУНТ EX-FS", color = Muted, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp, bottom = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        NavItem(if (state.loggedIn) "●" else "○", if (state.loggedIn) "Аккаунт" else "Войти", false, onLogin)
        Spacer(Modifier.height(6.dp))
        VersionBadge(state, onUpdate)
        Spacer(Modifier.height(6.dp))
        NavItem("⚙", "Диагностика", false, onDiagnostics)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun PhoneNavigation(selected: CatalogSection, onSection: (CatalogSection) -> Unit) {
    val destinations = listOf(
        Triple(CatalogSection.HOME, "⌂", "Главная"),
        Triple(CatalogSection.MOVIES, "▶", "Фильмы"),
        Triple(CatalogSection.SERIES, "▤", "Сериалы"),
        Triple(CatalogSection.CARTOONS, "◇", "Мультфильмы"),
        Triple(CatalogSection.FAVORITES, "♥", "Избранное"),
    )
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF101D2D)).padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        destinations.forEach { (section, icon, label) ->
            val active = selected == section
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onSection(section) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(icon, color = if (active) Cyan else Muted, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(label, color = if (active) Color.White else Muted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(46.dp).onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)
            .background(if (selected || focused) Cyan else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = if (selected || focused) Ink else Muted, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        Text(label, color = if (selected || focused) Ink else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CompactAction(label: String, onClick: () -> Unit) = Text(
    label,
    color = Color.White,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(Panel).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
)

@Composable
private fun VersionBadge(state: ExsState, onClick: () -> Unit) {
    val status = when (state.updateStatus) {
        UpdateStatus.CHECKING -> "проверка…"
        UpdateStatus.CURRENT -> "актуальна"
        UpdateStatus.OUTDATED -> "обновить до ${state.latestVersion}"
        UpdateStatus.UNAVAILABLE -> "проверить"
    }
    val color = when (state.updateStatus) {
        UpdateStatus.CURRENT -> Color(0xFF6EE7A8)
        UpdateStatus.OUTDATED -> Color(0xFFFFC857)
        else -> Muted
    }
    Column(
        Modifier.clip(RoundedCornerShape(11.dp)).background(Panel).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("EX-S ${BuildConfig.VERSION_NAME}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(status, color = color, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, onClick: () -> Unit) = Text(
    label,
    color = if (selected) Ink else Color.White,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Cyan else Panel).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
)

@Composable
private fun CatalogGrid(state: ExsState, onOpen: (MediaCard) -> Unit, onRefresh: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    if (!state.loading && state.cards.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(if (state.section == CatalogSection.FAVORITES) "Войдите в аккаунт или добавьте фильмы в закладки на EX-FS" else "В этом разделе пока ничего не найдено", onRefresh)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (compact) 132.dp else 145.dp),
            modifier = modifier,
            contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 18.dp else 22.dp),
        ) { items(state.cards, key = { it.url }) { MediaPoster(it, onOpen) } }
    }
}

@Composable
private fun SearchBox(value: String, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
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
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable()
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
        val compact = maxWidth < 700.dp || maxHeight < 500.dp
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
                PlayerButtons(detail, onPlay)
                if (detail.card.meta.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    InfoChip(detail.card.meta)
                }
                if (detail.facts.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(detail.facts, color = Muted, fontSize = 14.sp) }
                Spacer(Modifier.height(16.dp))
                Text(detail.description, color = Color(0xFFE1E8F0), fontSize = 16.sp, lineHeight = 23.sp)
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
                    Spacer(Modifier.height(16.dp))
                    PlayerButtons(detail, onPlay)
                    Spacer(Modifier.height(20.dp))
                    Text(detail.description, color = Color(0xFFE1E8F0), fontSize = 17.sp, lineHeight = 25.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                    if (detail.facts.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(detail.facts, color = Muted, fontSize = 14.sp, maxLines = 3) }
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
private fun DiagnosticsScreen(state: ExsState, onBack: () -> Unit, onClear: () -> Unit, onShare: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val report = remember(state.logs) {
        buildString {
            appendLine("EX-S ${BuildConfig.VERSION_NAME}")
            appendLine("Экран: ${state.screen}; раздел: ${state.section.label}")
            appendLine("--- ЖУРНАЛ ---")
            append(state.logs.joinToString("\n"))
        }
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusPill("← Назад", false, onBack, compact = true)
            Text("Диагностика EX-S", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("Откройте фильм и плеер, вернитесь сюда, затем нажмите «Копировать» или «Отправить». Пароли и cookies в журнал не записываются.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xCC050B13), RoundedCornerShape(12.dp)).padding(12.dp)) {
            SelectionContainer {
                Text(
                    if (state.logs.isEmpty()) "Журнал пока пуст." else report,
                    color = Color(0xFFD8E4F0), fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusPill("Копировать", true, { clipboard.setText(AnnotatedString(report)) }, compact = true)
            FocusPill("Отправить", false, onShare, compact = true)
            FocusPill("Очистить", false, onClear, compact = true)
        }
    }
}

@Composable
fun PlayerChrome(
    state: ExsState,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onRotate: () -> Unit,
) {
    var chromeVisible by remember(state.detail?.card?.url) { mutableStateOf(true) }
    LaunchedEffect(state.playerReady) {
        if (state.playerReady) {
            delay(1800)
            chromeVisible = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (!state.playerReady) {
            Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Cyan)
                Spacer(Modifier.height(16.dp))
                Text("Готовим плеер…", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Страница сайта скрыта", color = Muted, fontSize = 13.sp)
            }
        }
        if (chromeVisible) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xB307111F)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                PlayerControl("← Назад", onBack)
                Text(state.detail?.card?.title.orEmpty(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                PlayerControl("Скрыть", { chromeVisible = false })
                PlayerControl("↻ Повернуть", onRotate)
            }
        } else {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { PlayerControl("Меню", { chromeVisible = true }) }
        }
        if (state.playerReady && chromeVisible) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).background(Color(0xD9101D2D), RoundedCornerShape(16.dp)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerControl("−10", onSeekBack)
                PlayerControl("▶ / ❚❚", onToggle, accent = true)
                PlayerControl("+10", onSeekForward)
            }
        }
    }
}

@Composable
private fun PlayerControl(label: String, onClick: () -> Unit, accent: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = if (accent || focused) Ink else Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.focusable().clip(RoundedCornerShape(11.dp))
            .background(if (accent || focused) Cyan else Color(0xFF24354A)).clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 11.dp),
    )
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
