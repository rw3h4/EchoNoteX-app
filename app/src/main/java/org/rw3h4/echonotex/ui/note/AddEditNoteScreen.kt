package org.rw3h4.echonotex.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.rw3h4.echonotex.R
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.ui.theme.*
import org.rw3h4.echonotex.viewmodel.AddEditNoteViewModel
import java.util.UUID

data class CategoryItem(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteScreen(
    viewModel: AddEditNoteViewModel,
    existingNote: Note?,
    initialContent: String?,
    onSave: (title: String, content: String, category: String) -> Unit,
    onNavigateUp: () -> Unit,
    onLaunchGallery: () -> Unit,
    onLaunchCamera: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CategoryItem?>(null) }
    val categories by viewModel.allCategories.observeAsState(listOf())
    var contentParts by remember { mutableStateOf<List<EditContentPart>>(emptyList()) }
    var focusedPartId by remember { mutableStateOf<UUID?>(null) }
    val focusRequesters = remember { mutableStateMapOf<UUID, FocusRequester>() }

    var isExpanded by remember { mutableStateOf(false) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    val anyDialogVisible = showNewCategoryDialog || showImageSourceDialog

    val predefinedCategories = remember {
        listOf(
            CategoryItem("work", "Work", Icons.Default.Work, DarkBlue),
            CategoryItem("personal", "Personal", Icons.Default.Person, LightBlue),
            CategoryItem("ideas", "Ideas", Icons.Default.Lightbulb, LightPurple),
            CategoryItem("shopping", "Shopping", Icons.Default.ShoppingCart, LightBlue),
            CategoryItem("health", "Health", Icons.Default.Favorite, DarkBlue),
            CategoryItem("travel", "Travel", Icons.Default.Flight, LightPurple),
            CategoryItem("food", "Food", Icons.Default.Restaurant, LightBlue),
            CategoryItem("finance", "Finance", Icons.Default.AccountBalance, DarkBlue)
        )
    }

    val customCategories = remember(categories, predefinedCategories) {
        categories
            .map { it.name }
            .filter { name -> predefinedCategories.none { it.name.equals(name, ignoreCase = true) } }
    }

    LaunchedEffect(focusedPartId) {
        if (focusedPartId != null) {
            focusRequesters[focusedPartId]?.requestFocus()
        }
    }

    fun handleDeleteAction(partId: UUID) {
        val indexToDelete = contentParts.indexOfFirst { it.id == partId }
        if (indexToDelete == -1) return
        val newParts = contentParts.toMutableList()
        val partToDelete = newParts[indexToDelete]
        val previousPart = newParts.getOrNull(indexToDelete - 1)
        val nextPart = newParts.getOrNull(indexToDelete + 1)
        when {
            partToDelete is EditContentPart.Image && previousPart is EditContentPart.Text && nextPart is EditContentPart.Text -> {
                val mergedText = previousPart.value.text + nextPart.value.text
                val cursorPosition = previousPart.value.text.length
                val merged = TextFieldValue(mergedText, selection = TextRange(cursorPosition))
                newParts[indexToDelete - 1] = previousPart.copy(value = merged)
                newParts.removeAt(indexToDelete + 1)
                newParts.removeAt(indexToDelete)
                focusedPartId = previousPart.id
            }
            else -> {
                newParts.removeAt(indexToDelete)
                val newPrevious = newParts.getOrNull(indexToDelete - 1)
                val current = newParts.getOrNull(indexToDelete)
                if (newPrevious is EditContentPart.Text && current is EditContentPart.Text) {
                    val mergedText = newPrevious.value.text + current.value.text
                    val cursorPosition = newPrevious.value.text.length
                    val merged = TextFieldValue(mergedText, selection = TextRange(cursorPosition))
                    newParts[indexToDelete - 1] = newPrevious.copy(value = merged)
                    newParts.removeAt(indexToDelete)
                    focusedPartId = newPrevious.id
                } else {
                    focusedPartId = newParts.getOrNull(indexToDelete - 1)?.id
                }
            }
        }
        if (newParts.isEmpty() || newParts.last() is EditContentPart.Image) {
            newParts.add(EditContentPart.Text(value = TextFieldValue("")))
        }
        contentParts = newParts
    }

    LaunchedEffect(existingNote, categories, initialContent) {
        if (existingNote != null) {
            title = existingNote.title
            val categoryName = categories.find { it.id == existingNote.categoryId }?.name ?: "None"
            selectedCategory = predefinedCategories.find { it.name.equals(categoryName, ignoreCase = true) }
                ?: CategoryItem("custom", categoryName, Icons.Default.Label, DarkBlue)
            if (contentParts.isEmpty()) {
                contentParts = parseHtmlToContentPart(existingNote.content)
            }
        } else {
            if (contentParts.isEmpty()) {
                val initialText = initialContent ?: ""
                contentParts = listOf(EditContentPart.Text(value = TextFieldValue(initialText)))
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.imageToInsert.collect { uri ->
            val newParts = contentParts.toMutableList()
            val index = contentParts.indexOfFirst { it.id == focusedPartId }.coerceIn(0, newParts.size - 1)
            val focusedPart = newParts.getOrNull(index)
            if (focusedPart is EditContentPart.Text) {
                val cursorPosition = focusedPart.value.selection.start
                val textBefore = focusedPart.value.text.substring(0, cursorPosition)
                val textAfter = focusedPart.value.text.substring(cursorPosition)
                newParts[index] = focusedPart.copy(value = TextFieldValue(textBefore))
                val imagePart = EditContentPart.Image(uri = uri)
                val textAfterPart = EditContentPart.Text(value = TextFieldValue(textAfter, selection = TextRange(0)))
                newParts.add(index + 1, imagePart)
                newParts.add(index + 2, textAfterPart)
                focusedPartId = textAfterPart.id
            } else {
                val insertPos = if (index != -1) index + 1 else newParts.size
                val imagePart = EditContentPart.Image(uri = uri)
                val textAfterPart = EditContentPart.Text(value = TextFieldValue(""))
                newParts.add(insertPos, imagePart)
                newParts.add(insertPos + 1, textAfterPart)
                focusedPartId = textAfterPart.id
            }
            contentParts = newParts
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .blur(if (anyDialogVisible) 10.dp else 0.dp),
            containerColor = OffWhite,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (existingNote == null) "New Note" else "Edit Note",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = TextPrimary
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val finalHtml = convertCContentPartsToHtml(contentParts)
                                onSave(title, finalHtml, selectedCategory?.name ?: "None")
                            },
                            enabled = title.isNotBlank()
                        ) {
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (title.isNotBlank()) TextPrimary else TextDim
                                )
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = OffWhite
                    )
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { /* TODO: AI create */ },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightPurple,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sparkle),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Button(
                        onClick = { showImageSourceDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightPurple,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.attach_image),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Image",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (title.isEmpty()) {
                            Text(
                                text = "Note title...",
                                style = TextStyle(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDim
                                )
                            )
                        }
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            textStyle = TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    if (!isExpanded) {
                        Button(
                            onClick = { isExpanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LightPurple,
                                contentColor = TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .height(40.dp)
                        ) {
                            Text(
                                text = selectedCategory?.name ?: "Add Tag",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    } else {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .height(44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = { showNewCategoryDialog = true },
                                    label = {
                                        Text(
                                            text = "+ New",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = LightPurple.copy(alpha = 0.5f),
                                        labelColor = TextPrimary
                                    ),
                                    border = null
                                )
                            }
                            items(predefinedCategories) { category ->
                                val isSelected = selectedCategory?.id == category.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategory = category
                                        isExpanded = false
                                    },
                                    label = { Text(text = category.name, color = TextPrimary) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LightPurple,
                                        containerColor = Color.LightGray.copy(alpha = 0.2f),
                                        labelColor = TextPrimary,
                                        selectedLabelColor = TextPrimary
                                    ),
                                    border = null
                                )
                            }
                            items(customCategories) { name ->
                                val isSelected = selectedCategory?.name == name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategory = CategoryItem("custom", name, Icons.Default.Label, DarkBlue)
                                        isExpanded = false
                                    },
                                    label = { Text(text = name, color = TextPrimary) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LightPurple,
                                        containerColor = Color.LightGray.copy(alpha = 0.2f),
                                        labelColor = TextPrimary,
                                        selectedLabelColor = TextPrimary
                                    ),
                                    border = null
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(contentParts, key = { it.id }) { part ->
                    when (part) {
                        is EditContentPart.Text -> {
                            FlatTextEditor(
                                part = part,
                                onTextChange = { newValue ->
                                    contentParts = contentParts.map {
                                        if (it.id == part.id) (it as EditContentPart.Text).copy(value = newValue) else it
                                    }
                                },
                                onFocusChanged = { isFocused ->
                                    if (isFocused) focusedPartId = part.id
                                },
                                onBackspaceAtStart = {
                                    val index = contentParts.indexOf(part)
                                    val previousPart = contentParts.getOrNull(index - 1)
                                    if (previousPart != null) handleDeleteAction(previousPart.id)
                                },
                                focusRequester = focusRequesters.getOrPut(part.id) { FocusRequester() }
                            )
                        }
                        is EditContentPart.Image -> {
                            ModernImageEditor(
                                part = part,
                                onDelete = { handleDeleteAction(part.id) },
                                onResize = { newSizeFraction ->
                                    contentParts = contentParts.map { p ->
                                        if (p.id == part.id) (p as EditContentPart.Image).copy(sizeFraction = newSizeFraction) else p
                                    }
                                }
                            )
                        }
                    }
                }

            }
        }

        if (showImageSourceDialog) {
            ImageSourceDialog(
                onLaunchCamera = {
                    showImageSourceDialog = false
                    onLaunchCamera()
                },
                onLaunchGallery = {
                    showImageSourceDialog = false
                    onLaunchGallery()
                },
                onDismiss = { showImageSourceDialog = false }
            )
        }

        if (showNewCategoryDialog) {
            NewCategoryDialog(
                newCategoryName = newCategoryName,
                onNameChange = { newCategoryName = it },
                onConfirm = {
                    if (newCategoryName.isNotBlank()) {
                        selectedCategory = CategoryItem("new", newCategoryName.trim(), Icons.Default.Label, DarkBlue)
                        isExpanded = false
                        showNewCategoryDialog = false
                        newCategoryName = ""
                    }
                },
                onDismiss = {
                    showNewCategoryDialog = false
                    newCategoryName = ""
                }
            )
        }
    }
}

@Composable
fun FlatTextEditor(
    part: EditContentPart.Text,
    onTextChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onBackspaceAtStart: () -> Unit,
    focusRequester: FocusRequester
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (part.value.text.isEmpty()) {
            Text(
                text = "Start writing...",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = TextSecondary.copy(alpha = 0.5f)
                )
            )
        }
        BasicTextField(
            value = part.value,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) }
                .onKeyEvent { event ->
                    if (event.key == Key.Backspace &&
                        part.value.selection.start == 0 &&
                        part.value.selection.end == 0
                    ) {
                        onBackspaceAtStart()
                        return@onKeyEvent true
                    }
                    false
                },
            textStyle = TextStyle(
                color = TextSecondary,
                fontSize = 18.sp,
                lineHeight = 28.sp
            )
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun ModernImageEditor(
    part: EditContentPart.Image,
    onDelete: () -> Unit,
    onResize: (Float) -> Unit
) {
    var boxSize by remember { mutableStateOf(0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth(part.sizeFraction)
            .onSizeChanged { boxSize = it.width.toFloat() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            AsyncImage(
                model = part.uri,
                contentDescription = "Note Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Image",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val newSizeFraction = (part.sizeFraction + (dragAmount.x / boxSize)).coerceIn(0.3f, 1.0f)
                            onResize(newSizeFraction)
                        }
                    },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(id = R.drawable.ic_resize),
                        contentDescription = "Resize Image",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun ImageSourceDialog(
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = OffWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add Image",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onLaunchCamera,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightPurple,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.attach_photo),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Take Photo",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Button(
                        onClick = onLaunchGallery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LightPurple,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.attach_image),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Select from Gallery",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Cancel",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewCategoryDialog(
    newCategoryName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = OffWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "New Category",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val focusRequester = remember { FocusRequester() }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.LightGray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            if (newCategoryName.isEmpty()) {
                                Text(
                                    text = "Category name...",
                                    style = TextStyle(fontSize = 18.sp, color = TextDim)
                                )
                            }
                            BasicTextField(
                                value = newCategoryName,
                                onValueChange = onNameChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = TextStyle(fontSize = 18.sp, color = TextPrimary),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    capitalization = KeyboardCapitalization.Words
                                ),
                                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                                singleLine = true
                            )
                        }
                    }

                    LaunchedEffect(Unit) { focusRequester.requestFocus() }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirm,
                            enabled = newCategoryName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LightBlue,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}
