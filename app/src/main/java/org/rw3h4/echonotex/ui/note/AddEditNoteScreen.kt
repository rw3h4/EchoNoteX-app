package org.rw3h4.echonotex.ui.note

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.rw3h4.echonotex.R
import org.rw3h4.echonotex.data.local.model.Note
import org.rw3h4.echonotex.ui.theme.DarkBlue
import org.rw3h4.echonotex.ui.theme.LightBlue
import org.rw3h4.echonotex.ui.theme.LightPurple
import org.rw3h4.echonotex.ui.theme.OffWhite
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

    val bottomSheetState = rememberModalBottomSheetState()
    var showCategoryBottomSheet by remember { mutableStateOf(false) }

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
                val mergedTextFieldValue = TextFieldValue(mergedText, selection = TextRange(cursorPosition))
                newParts[indexToDelete - 1] = previousPart.copy(value = mergedTextFieldValue)
                newParts.removeAt(indexToDelete + 1)
                newParts.removeAt(indexToDelete)
                focusedPartId = previousPart.id
            }
            else -> {
                newParts.removeAt(indexToDelete)
                val newPreviousPart = newParts.getOrNull(indexToDelete - 1)
                val currentPart = newParts.getOrNull(indexToDelete)
                if (newPreviousPart is EditContentPart.Text && currentPart is EditContentPart.Text) {
                    val mergedText = newPreviousPart.value.text + currentPart.value.text
                    val cursorPosition = newPreviousPart.value.text.length
                    val mergedTextFieldValue = TextFieldValue(mergedText, selection = TextRange(cursorPosition))
                    newParts[indexToDelete - 1] = newPreviousPart.copy(value = mergedTextFieldValue)
                    newParts.removeAt(indexToDelete)
                    focusedPartId = newPreviousPart.id
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(OffWhite, LightPurple.copy(alpha = 0.2f))
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (existingNote == null) "Create Note" else "Edit Note",
                            color = DarkBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateUp,
                            modifier = Modifier
                                .background(OffWhite.copy(alpha = 0.8f), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close",
                                tint = DarkBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            // The bottomBar is removed from here
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()), // Apply only top padding from scaffold
                state = rememberLazyListState(),
                // Add content padding at the bottom for the floating bar
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ModernTitleSection(
                        title = title,
                        onTitleChange = { title = it }
                    )
                }

                item {
                    ModernCategorySection(
                        selectedCategory = selectedCategory,
                        onCategoryClick = { showCategoryBottomSheet = true }
                    )
                }

                items(contentParts, key = { it.id }) { part ->
                    when (part) {
                        is EditContentPart.Text -> {
                            ModernTextEditor(
                                part = part,
                                onTextChange = { newValue ->
                                    contentParts = contentParts.map {
                                        if (it.id == part.id) {
                                            (it as EditContentPart.Text).copy(value = newValue)
                                        } else {
                                            it
                                        }
                                    }
                                },
                                onFocusChanged = { isFocused ->
                                    if (isFocused) focusedPartId = part.id
                                },
                                onBackspaceAtStart = {
                                    val index = contentParts.indexOf(part)
                                    val previousPart = contentParts.getOrNull(index - 1)
                                    if (previousPart != null) {
                                        handleDeleteAction(previousPart.id)
                                    }
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
                                        if (p.id == part.id) {
                                            (p as EditContentPart.Image).copy(sizeFraction = newSizeFraction)
                                        } else {
                                            p
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // The action bar is now placed inside the Box, aligned to the bottom
        StyledBottomActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            onSaveClick = {
                val finalHtml = convertCContentPartsToHtml(contentParts)
                onSave(title, finalHtml, selectedCategory?.name ?: "None")
            },
            onAttachImageClick = onLaunchGallery,
            onTakePhotoClick = onLaunchCamera,
            canSave = title.isNotBlank()
        )
    }

    if (showCategoryBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategoryBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = OffWhite,
            dragHandle = {
                Surface(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 32.dp, height = 4.dp),
                    color = DarkBlue.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp)
                ) {}
            }
        ) {
            CategorySelectionBottomSheet(
                categories = predefinedCategories,
                existingCategories = categories.map { it.name },
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = category
                    showCategoryBottomSheet = false
                }
            )
        }
    }
}

@Composable
fun ModernTitleSection(
    title: String,
    onTitleChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Title",
                style = MaterialTheme.typography.labelLarge,
                color = DarkBlue.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                textStyle = TextStyle(
                    color = DarkBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            "Enter note title...",
                            color = DarkBlue.copy(alpha = 0.4f),
                            fontSize = 18.sp
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ModernCategorySection(
    selectedCategory: CategoryItem?,
    onCategoryClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCategoryClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OffWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = DarkBlue.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (selectedCategory != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = selectedCategory.icon,
                            contentDescription = null,
                            tint = selectedCategory.color,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = selectedCategory.name,
                            color = DarkBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = "Select category",
                        color = DarkBlue.copy(alpha = 0.4f),
                        fontSize = 16.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Select category",
                tint = DarkBlue.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ModernTextEditor(
    part: EditContentPart.Text,
    onTextChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onBackspaceAtStart: () -> Unit,
    focusRequester: FocusRequester
) {
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
                    part.value.selection.end == 0) {
                    onBackspaceAtStart()
                    return@onKeyEvent true
                }
                false
            },
        textStyle = TextStyle(
            color = DarkBlue,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        decorationBox = { innerTextField ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OffWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (part.value.text.isEmpty()) {
                        Text(
                            "Start writing your note...",
                            color = DarkBlue.copy(alpha = 0.4f),
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
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
                    "Delete Image",
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
                            val newSizeFraction = (part.sizeFraction +
                                    (dragAmount.x / boxSize)).coerceIn(0.3f, 1.0f)
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
}

@Composable
fun CategorySelectionBottomSheet(
    categories: List<CategoryItem>,
    existingCategories: List<String>,
    selectedCategory: CategoryItem?,
    onCategorySelected: (CategoryItem) -> Unit
) {
    var showCreateNew by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text(
            text = "Select Category",
            style = MaterialTheme.typography.headlineSmall,
            color = DarkBlue,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(categories) { category ->
                CategoryChip(
                    category = category,
                    isSelected = selectedCategory?.id == category.id,
                    onClick = { onCategorySelected(category) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (existingCategories.isNotEmpty()) {
            Text(
                text = "Your Categories",
                style = MaterialTheme.typography.titleMedium,
                color = DarkBlue,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(existingCategories.filter { categoryName ->
                    categories.none { it.name.equals(categoryName, ignoreCase = true) }
                }) { categoryName ->
                    CustomCategoryItem(
                        name = categoryName,
                        isSelected = selectedCategory?.name == categoryName,
                        onClick = {
                            onCategorySelected(
                                CategoryItem("custom", categoryName, Icons.Default.Label, DarkBlue)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        AnimatedContent(
            targetState = showCreateNew,
            transitionSpec = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeIn() togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut()
            },
            label = "create_category"
        ) { showCreate ->
            if (showCreate) {
                Column {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkBlue,
                            focusedLabelColor = DarkBlue,
                            cursorColor = DarkBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newCategoryName.isNotBlank()) {
                                    onCategorySelected(
                                        CategoryItem("new", newCategoryName.trim(), Icons.Default.Label, DarkBlue)
                                    )
                                }
                                keyboardController?.hide()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showCreateNew = false
                                newCategoryName = ""
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = DarkBlue
                            ),
                            border = BorderStroke(1.dp, DarkBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    onCategorySelected(
                                        CategoryItem("new", newCategoryName.trim(), Icons.Default.Label, DarkBlue)
                                    )
                                }
                                keyboardController?.hide()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkBlue),
                            shape = RoundedCornerShape(12.dp),
                            enabled = newCategoryName.isNotBlank()
                        ) {
                            Text("Create", color = OffWhite)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { showCreateNew = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightPurple,
                        contentColor = DarkBlue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Category", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun CategoryChip(
    category: CategoryItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .scale(animatedScale)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) category.color.copy(alpha = 0.2f) else OffWhite
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, category.color)
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = category.name,
                color = DarkBlue,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CustomCategoryItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) LightPurple else OffWhite
        ),
        border = if (isSelected) {
            BorderStroke(1.dp, DarkBlue)
        } else null,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Label,
                contentDescription = null,
                tint = DarkBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                color = DarkBlue,
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = DarkBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


@Composable
fun StyledBottomActionBar(
    onSaveClick: () -> Unit,
    onAttachImageClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    canSave: Boolean,
    modifier: Modifier = Modifier // Add modifier parameter
) {
    Box(
        modifier = modifier // Use the passed modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(250.dp)
                .height(60.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = DarkBlue,
                disabledContainerColor = DarkBlue.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(onClick = onTakePhotoClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.attach_photo),
                        contentDescription = "Take Photo",
                        tint = LightBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = onAttachImageClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.attach_image),
                        contentDescription = "Attach Image",
                        tint = LightBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(onClick = onSaveClick, enabled = canSave) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Note",
                        tint = if (canSave) LightBlue else LightBlue.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}