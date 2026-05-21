package com.secondream.keeper.ui.screens

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.secondream.keeper.R
import androidx.compose.ui.res.stringResource
import com.secondream.keeper.data.model.Attachment
import com.secondream.keeper.data.model.Note
import com.secondream.keeper.data.model.KeepColors
import com.secondream.keeper.viewmodel.NavigationScreen
import com.secondream.keeper.viewmodel.NoteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteApp(viewModel: NoteViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredNotes by viewModel.filteredNotes.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()
    val isGoogleConnected by viewModel.isGoogleConnected.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Dialog note editing state
    var selectedNoteForEdit by remember { mutableStateOf<Note?>(null) }
    var isCreatingNewNote by remember { mutableStateOf(false) }
    var initialLaunchType by remember { mutableStateOf<String?>(null) }

    // Intercept deletion confirmation dialog targets
    var noteToTrash by remember { mutableStateOf<Note?>(null) }
    var noteToDeletePermanently by remember { mutableStateOf<Note?>(null) }

    val themeIsDark = isSystemInDarkThemeCustom(viewModel)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                // Keep Drawer Header User Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.take(2).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.drawer_greeting, userName),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.drawer_sub),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Standard Keep screens
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Note, stringResource(R.string.screen_notes)) },
                    label = { Text(stringResource(R.string.screen_notes)) },
                    selected = currentScreen is NavigationScreen.Notes,
                    onClick = {
                        viewModel.navigateTo(NavigationScreen.Notes)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Checklist, stringResource(R.string.screen_reminders)) },
                    label = { Text(stringResource(R.string.screen_reminders)) },
                    selected = currentScreen is NavigationScreen.Reminders,
                    onClick = {
                        viewModel.navigateTo(NavigationScreen.Reminders)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Divider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp))

                // Dynamic Labels Drawer Items
                if (allLabels.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.dynamic_labels_header),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    allLabels.forEach { label ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Outlined.Label, label) },
                            label = { Text(label) },
                            selected = currentScreen is NavigationScreen.Label && (currentScreen as NavigationScreen.Label).label == label,
                            onClick = {
                                viewModel.navigateTo(NavigationScreen.Label(label))
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp))
                }

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Archive, stringResource(R.string.screen_archive)) },
                    label = { Text(stringResource(R.string.screen_archive)) },
                    selected = currentScreen is NavigationScreen.Archive,
                    onClick = {
                        viewModel.navigateTo(NavigationScreen.Archive)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, stringResource(R.string.screen_trash)) },
                    label = { Text(stringResource(R.string.screen_trash)) },
                    selected = currentScreen is NavigationScreen.Trash,
                    onClick = {
                        viewModel.navigateTo(NavigationScreen.Trash)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, stringResource(R.string.screen_settings)) },
                    label = { Text(stringResource(R.string.screen_settings)) },
                    selected = currentScreen is NavigationScreen.Settings,
                    onClick = {
                        viewModel.navigateTo(NavigationScreen.Settings)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                // Keep Style Top search header
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "Open Menu")
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                // Dynamic search text box
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search icon",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 16.sp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        decorationBox = { innerTextField ->
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.search_hint),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        fontSize = 16.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                }
                            }

                            // View list layout toggler + avatar
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.toggleLayoutView() }) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                        contentDescription = stringResource(R.string.toggle_grid)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))

                                // Profile Avatar Circle
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (themeIsDark) MaterialTheme.colorScheme.primary else Color(0xFFA8C7FA))
                                        .clickable { viewModel.navigateTo(NavigationScreen.Settings) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userName.take(1).uppercase(),
                                        color = if (themeIsDark) MaterialTheme.colorScheme.onPrimary else Color(0xFF062E6F),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Show bottom edit drawer trigger bar only on notes screen to resemble Keep's bottom bar perfectly
                if (currentScreen is NavigationScreen.Notes) {
                    val activeNotesList by viewModel.activeNotes.collectAsState(emptyList())
                    BottomAppBar(
                        actions = {
                            Text(
                                text = if (activeNotesList.isEmpty()) "Nessuna nota presente" else "${activeNotesList.size} " + (if (activeNotesList.size == 1) "nota" else "note"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (themeIsDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color(0xFF0F172A).copy(alpha = 0.6f),
                                    letterSpacing = 0.2.sp
                                ),
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { isCreatingNewNote = true },
                                containerColor = Color(0xFFFFCA28),
                                contentColor = Color(0xFF1A1A1A),
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 12.dp
                                ),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.take_note),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(84.dp),
                        containerColor = if (themeIsDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF2F4FC),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    )
                }
            }
        ) { paddingValues ->
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                     scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)))
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                label = "screen_transition"
            ) { screen ->
                if (screen is NavigationScreen.Settings) {
                    SettingsView(viewModel = viewModel)
                } else {
                    // Main Note list view or empty state view
                    if (filteredNotes.isEmpty()) {
                        EmptyStateView(screen)
                    } else {
                        // Separate pinned notes and other notes to resemble Google Keep perfectly!
                        val pinnedList = filteredNotes.filter { it.isPinned }
                        val othersList = filteredNotes.filter { !it.isPinned }

                        if (isGridView) {
                            // Render Stunning staggered grid
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalItemSpacing = 8.dp
                            ) {
                                if (pinnedList.isNotEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Text(
                                            stringResource(R.string.pinned_ideas),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(pinnedList, key = { it.id }) { note ->
                                        NoteItemCard(
                                            note = note,
                                            themeIsDark = themeIsDark,
                                            isGoogleConnected = isGoogleConnected,
                                            onClick = { selectedNoteForEdit = note },
                                            onPinClick = { viewModel.togglePinNote(note.id) },
                                            onTrashClick = { if (screen is NavigationScreen.Trash) noteToDeletePermanently = note else noteToTrash = note },
                                            onArchiveClick = { if (note.isArchived) viewModel.unarchiveNote(note.id) else viewModel.archiveNote(note.id) }
                                        )
                                    }
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Text(
                                            stringResource(R.string.others),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 4.dp)
                                        )
                                    }
                                }

                                items(othersList, key = { it.id }) { note ->
                                    NoteItemCard(
                                        note = note,
                                        themeIsDark = themeIsDark,
                                        isGoogleConnected = isGoogleConnected,
                                        onClick = { selectedNoteForEdit = note },
                                        onPinClick = { viewModel.togglePinNote(note.id) },
                                        onTrashClick = { if (screen is NavigationScreen.Trash) noteToDeletePermanently = note else noteToTrash = note },
                                        onArchiveClick = { if (note.isArchived) viewModel.unarchiveNote(note.id) else viewModel.archiveNote(note.id) }
                                    )
                                }
                            }
                        } else {
                            // Linear Vertical list view with animations
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (pinnedList.isNotEmpty()) {
                                    item {
                                        Text(
                                            stringResource(R.string.pinned_ideas),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(pinnedList, key = { it.id }) { note ->
                                        NoteItemCard(
                                            note = note,
                                            themeIsDark = themeIsDark,
                                            isGoogleConnected = isGoogleConnected,
                                            onClick = { selectedNoteForEdit = note },
                                            onPinClick = { viewModel.togglePinNote(note.id) },
                                            onTrashClick = { if (screen is NavigationScreen.Trash) noteToDeletePermanently = note else noteToTrash = note },
                                            onArchiveClick = { if (note.isArchived) viewModel.unarchiveNote(note.id) else viewModel.archiveNote(note.id) }
                                        )
                                    }
                                    item {
                                        Text(
                                            stringResource(R.string.others),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp)
                                        )
                                    }
                                }

                                items(othersList, key = { it.id }) { note ->
                                    NoteItemCard(
                                        note = note,
                                        themeIsDark = themeIsDark,
                                        isGoogleConnected = isGoogleConnected,
                                        onClick = { selectedNoteForEdit = note },
                                        onPinClick = { viewModel.togglePinNote(note.id) },
                                        onTrashClick = { if (screen is NavigationScreen.Trash) noteToDeletePermanently = note else noteToTrash = note },
                                        onArchiveClick = { if (note.isArchived) viewModel.unarchiveNote(note.id) else viewModel.archiveNote(note.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Creator sliding overlay trigger
    AnimatedVisibility(
        visible = isCreatingNewNote || selectedNoteForEdit != null,
        enter = slideInVertically(
            initialOffsetY = { (it * 0.12f).toInt() },
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 240)) + scaleIn(initialScale = 0.95f, animationSpec = androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { (it * 0.10f).toInt() },
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)) + scaleOut(targetScale = 0.95f, animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    ) {
        NoteDetailView(
            note = selectedNoteForEdit,
            viewModel = viewModel,
            initialLaunchType = if (isCreatingNewNote) initialLaunchType else null,
            onDismiss = {
                selectedNoteForEdit = null
                isCreatingNewNote = false
                initialLaunchType = null
            }
        )
    }

    // 1. Move to Trash Confirmation Dialog
    if (noteToTrash != null) {
        val note = noteToTrash!!
        AlertDialog(
            onDismissRequest = { noteToTrash = null },
            title = { Text(text = stringResource(R.string.delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(text = stringResource(R.string.delete_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.trashNote(note.id)
                        noteToTrash = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(R.string.delete_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToTrash = null }) {
                    Text(text = stringResource(R.string.delete_confirm_cancel))
                }
            }
        )
    }

    // 2. Permanent Delete Confirmation Dialog
    if (noteToDeletePermanently != null) {
        val note = noteToDeletePermanently!!
        AlertDialog(
            onDismissRequest = { noteToDeletePermanently = null },
            title = { Text(text = stringResource(R.string.delete_confirm_title_permanent), fontWeight = FontWeight.Bold) },
            text = { Text(text = stringResource(R.string.delete_confirm_msg_permanent)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNotePermanently(note)
                        noteToDeletePermanently = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(R.string.delete_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDeletePermanently = null }) {
                    Text(text = stringResource(R.string.delete_confirm_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteItemCard(
    note: Note,
    themeIsDark: Boolean,
    isGoogleConnected: Boolean,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onTrashClick: () -> Unit,
    onArchiveClick: () -> Unit
) {
    val cardBackground by animateColorAsState(
        targetValue = KeepColors.getColorForHex(note.colorHex, themeIsDark),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = KeepColors.getContentColorForHex(note.colorHex, themeIsDark),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = getBorderColorForHex(note.colorHex, themeIsDark),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_border"
    )

    val checklist = note.getChecklist()
    val attachments = note.getAttachments()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.6.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Card visual image highlights
            val firstImg = attachments.find { it.type == "image" }
            if (firstImg != null) {
                val modelToLoad: Any = if (firstImg.uri.startsWith("file://")) {
                    java.io.File(firstImg.uri.removePrefix("file://"))
                } else if (firstImg.uri.startsWith("content://")) {
                    android.net.Uri.parse(firstImg.uri)
                } else {
                    firstImg.uri
                }
                AsyncImage(
                    model = modelToLoad,
                    contentDescription = firstImg.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .padding(bottom = 8.dp)
                )
            }

            // Title + Pin tray
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onPinClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin notes",
                            tint = if (note.isPinned) Color(0xFFFF9100) else contentColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body content summary preview
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    fontSize = 13.sp,
                    color = contentColor.copy(alpha = 0.82f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Checklist preview list
            if (checklist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    checklist.take(4).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (item.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "Todo Preview",
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.text,
                                fontSize = 12.sp,
                                color = if (item.checked) contentColor.copy(alpha = 0.4f) else contentColor,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (checklist.size > 4) {
                        Text(
                            text = stringResource(R.string.more_checklist_items, checklist.size - 4),
                            fontSize = 11.sp,
                            color = contentColor.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, top = 2.dp)
                        )
                    }
                }
            }

            // Compact dynamic labels tray
            if (note.getLabelsList().isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    note.getLabelsList().forEach { label ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(contentColor.copy(alpha = 0.07f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor
                            )
                        }
                    }
                }
            }

            // Attachments counters badge
            val nonImgCount = attachments.filter { it.type != "image" }.size
            if (attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attachment icon",
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${attachments.size} Attachment${if (attachments.size > 1) "s" else ""}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                }
            }

            // Quick hover utility bar (Delete / Trash, Archive)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Archive / Unarchive icon
                IconButton(
                    onClick = onArchiveClick,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (note.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = if (note.isArchived) "Sposta nelle note" else "Archivia",
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Delete trash icon shortcut
                IconButton(
                    onClick = onTrashClick,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Trash Note",
                        tint = contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(screen: NavigationScreen) {
    val info = when (screen) {
        is NavigationScreen.Notes -> Pair(Icons.Outlined.Lightbulb, stringResource(R.string.empty_notes))
        is NavigationScreen.Reminders -> Pair(Icons.Outlined.Checklist, stringResource(R.string.empty_reminders))
        is NavigationScreen.Archive -> Pair(Icons.Outlined.Archive, stringResource(R.string.empty_archive))
        is NavigationScreen.Trash -> Pair(Icons.Outlined.Delete, stringResource(R.string.empty_trash))
        is NavigationScreen.Label -> Pair(Icons.Outlined.Label, stringResource(R.string.empty_label))
        is NavigationScreen.Settings -> Pair(Icons.Outlined.Settings, stringResource(R.string.empty_settings))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = info.first,
            contentDescription = "Empty notes state icon",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = info.second,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            fontSize = 16.sp
        )
    }
}

@Composable
fun AnimatedBottomBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bottom_action_scale"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(vertical = 6.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(120)
            isPressed = false
        }
    }
}

fun getBorderColorForHex(hex: String, isDark: Boolean): Color {
    if (isDark) {
        return Color.White.copy(alpha = 0.08f)
    }
    return when (hex.uppercase()) {
        "#FFFF8D", "#FFF475" -> Color(0xFFFEF08A).copy(alpha = 0.6f) // Yellow-200
        "#B388FF", "#D7AEFB" -> Color(0xFFE9D5FF).copy(alpha = 0.6f) // Purple-200
        "#CCFF90" -> Color(0xFFBBF7D0).copy(alpha = 0.6f) // Green-200
        "#80D8FF", "#AECBFA" -> Color(0xFFBFDBFE).copy(alpha = 0.6f) // Blue-200
        "#FF8A80", "#FF9E9E" -> Color(0xFFFECACA).copy(alpha = 0.6f) // Coral-secondary
        "#FFD180" -> Color(0xFFFED7AA).copy(alpha = 0.6f) // Orange-200
        "#A7FFEB" -> Color(0xFF99F6E4).copy(alpha = 0.6f) // Teal-200
        else -> Color(0xFFE2E8F0).copy(alpha = 0.6f) // Default soft slate/grey border
    }
}

