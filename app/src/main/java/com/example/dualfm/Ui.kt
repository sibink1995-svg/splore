package com.example.dualfm

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ViewMode { LIST, COMPACT, GRID }

class PaneState(start: File) {
    var dir by mutableStateOf(start)
    var items by mutableStateOf<List<File>>(emptyList())
    var selected by mutableStateOf<Set<File>>(emptySet())
    var mode by mutableStateOf(ViewMode.LIST)
    var showHidden by mutableStateOf(false)

    init { refresh() }

    fun refresh() {
        items = FileOps.list(dir, showHidden)
        selected = selected.filter { it.exists() }.toSet()
    }

    fun open(d: File) {
        dir = d
        selected = emptySet()
        refresh()
    }

    fun up() { dir.parentFile?.let { open(it) } }

    fun toggle(f: File) {
        selected = if (f in selected) selected - f else selected + f
    }
}

sealed interface DialogState {
    data class Rename(val file: File) : DialogState
    data object NewFolder : DialogState
    data class Delete(val files: List<File>) : DialogState
}

fun openFile(ctx: Context, f: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(ctx, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun FileManagerApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { Environment.getExternalStorageDirectory() }
    val panes = remember { listOf(PaneState(root), PaneState(root)) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val active = pagerState.currentPage
    var diskMap by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val src = panes[active]
    val dst = panes[1 - active]

    fun refreshAll() { panes.forEach { it.refresh() } }

    fun runOp(okMsg: String, block: () -> Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { block() }
            src.selected = emptySet()
            refreshAll()
            Toast.makeText(ctx, if (ok) okMsg else "Some operations failed", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = !diskMap && src.dir != root && src.dir.parentFile != null) { src.up() }

    if (diskMap) {
        DiskMapScreen(
            start = src.dir,
            onClose = { diskMap = false },
            onOpen = { src.open(it); diskMap = false }
        )
    } else {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DualFM", Modifier.padding(horizontal = 8.dp), fontSize = 18.sp)
                TextButton(onClick = { diskMap = true }) { Text("🗺 Disk map") }
                TextButton(onClick = {
                    src.mode = ViewMode.values()[(src.mode.ordinal + 1) % ViewMode.values().size]
                }) { Text(when (src.mode) { ViewMode.LIST -> "☰ List"; ViewMode.COMPACT -> "≡ Compact"; ViewMode.GRID -> "▦ Grid" }) }
                TextButton(onClick = {
                    panes.forEach { it.showHidden = !it.showHidden; it.refresh() }
                }) { Text(if (src.showHidden) "👁 Hidden: on" else "👁 Hidden: off") }
                TextButton(onClick = { refreshAll() }) { Text("⟳") }
            }

            // One pane at a time - swipe left/right to switch
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                Pane(
                    state = panes[page],
                    isActive = true,
                    onActivate = {},
                    onOpenFile = { openFile(ctx, it) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Pane indicator + copy/move target
            Row(
                Modifier.fillMaxWidth()
                    .clickable { scope.launch { pagerState.animateScrollToPage(1 - active) } }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (active == 0) "● ○" else "○ ●", fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Target: ${dst.dir.absolutePath}   ⇄ swipe",
                    Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottom action bar
            val has = src.selected.isNotEmpty()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(enabled = has, onClick = {
                    val t = src.selected.toList(); val d = dst.dir
                    runOp("Copied") { t.map { FileOps.copy(it, d) }.all { it } }
                }) { Text("Copy ➜") }
                TextButton(enabled = has, onClick = {
                    val t = src.selected.toList(); val d = dst.dir
                    runOp("Moved") { t.map { FileOps.move(it, d) }.all { it } }
                }) { Text("Move ➜") }
                TextButton(enabled = src.selected.size == 1, onClick = {
                    dialog = DialogState.Rename(src.selected.first())
                }) { Text("Rename") }
                TextButton(onClick = { dialog = DialogState.NewFolder }) { Text("New folder") }
                TextButton(enabled = has, onClick = {
                    dialog = DialogState.Delete(src.selected.toList())
                }) { Text("Delete") }
                TextButton(enabled = has, onClick = { src.selected = emptySet() }) { Text("Clear") }
            }
        }
    }

    when (val d = dialog) {
        is DialogState.Rename -> InputDialog("Rename", d.file.name, { n ->
            dialog = null
            runOp("Renamed") {
                n.isNotBlank() && !n.contains('/') && d.file.renameTo(File(d.file.parentFile, n))
            }
        }, { dialog = null })

        DialogState.NewFolder -> {
            val base = src.dir
            InputDialog("New folder", "", { n ->
                dialog = null
                runOp("Folder created") { n.isNotBlank() && !n.contains('/') && File(base, n).mkdirs() }
            }, { dialog = null })
        }

        is DialogState.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete") },
            text = { Text("Delete ${d.files.size} item(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    runOp("Deleted") { d.files.map { FileOps.delete(it) }.all { it } }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )

        null -> {}
    }
}

@Composable
fun InputDialog(title: String, initial: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun Pane(
    state: PaneState,
    isActive: Boolean,
    onActivate: () -> Unit,
    onOpenFile: (File) -> Unit,
    modifier: Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier.padding(2.dp)
            .border(2.dp, if (isActive) accent else Color.Transparent, RoundedCornerShape(6.dp))
    ) {
        // Header with UP button
        Row(
            Modifier.fillMaxWidth().clickable { onActivate() }.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onActivate(); state.up() }) { Text("⬆ Up") }
            Text(
                state.dir.absolutePath, Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp
            )
            if (state.selected.isNotEmpty()) Text("  ✔ ${state.selected.size}", fontSize = 13.sp)
        }

        val click: (File) -> Unit = { f ->
            onActivate()
            if (state.selected.isNotEmpty()) state.toggle(f)
            else if (f.isDirectory) state.open(f)
            else onOpenFile(f)
        }
        val longClick: (File) -> Unit = { f -> onActivate(); state.toggle(f) }

        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Empty") }
        } else if (state.mode == ViewMode.GRID) {
            LazyVerticalGrid(columns = GridCells.Adaptive(84.dp), modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.absolutePath }) { f ->
                    GridCell(f, f in state.selected, click, longClick)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.absolutePath }) { f ->
                    FileRow(f, f in state.selected, state.mode == ViewMode.LIST, click, longClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    f: File, selected: Boolean, detailed: Boolean,
    onClick: (File) -> Unit, onLong: (File) -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bg)
            .combinedClickable(onClick = { onClick(f) }, onLongClick = { onLong(f) })
            .padding(horizontal = 8.dp, vertical = if (detailed) 8.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (f.isDirectory) "📁" else "📄", fontSize = if (detailed) 22.sp else 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            if (detailed) {
                val info = remember(f.absolutePath, f.lastModified()) {
                    val right = if (f.isDirectory) "${f.list()?.size ?: 0} items" else FileOps.fmtSize(f.length())
                    FileOps.fmtDate(f.lastModified()) + "  ·  " + right
                }
                Text(info, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridCell(f: File, selected: Boolean, onClick: (File) -> Unit, onLong: (File) -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column(
        Modifier.padding(2.dp).background(bg, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = { onClick(f) }, onLongClick = { onLong(f) })
            .padding(6.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (f.isDirectory) "📁" else "📄", fontSize = 32.sp)
        Text(
            f.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp, textAlign = TextAlign.Center
        )
    }
}
