package com.example.dualfm

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Node(val file: File, val size: Long)
data class Tile(val node: Node, val x: Float, val y: Float, val w: Float, val h: Float)

private fun worst(row: List<Pair<Node, Double>>, side: Double): Double {
    val s = row.sumOf { it.second }
    val mx = row.maxOf { it.second }
    val mn = row.minOf { it.second }
    return maxOf(side * side * mx / (s * s), (s * s) / (side * side * mn))
}

/** Squarified treemap layout. */
fun squarify(nodes: List<Node>, x: Float, y: Float, w: Float, h: Float): List<Tile> {
    val total = nodes.sumOf { it.size }.toDouble()
    if (total <= 0.0 || w <= 0f || h <= 0f) return emptyList()
    val area = w.toDouble() * h
    val items = nodes.filter { it.size > 0 }.sortedByDescending { it.size }
        .map { it to it.size / total * area }
    val out = ArrayList<Tile>()
    var rx = x.toDouble(); var ry = y.toDouble()
    var rw = w.toDouble(); var rh = h.toDouble()
    var i = 0
    while (i < items.size && rw > 0.5 && rh > 0.5) {
        val side = minOf(rw, rh)
        var row = listOf(items[i])
        var best = worst(row, side)
        var j = i + 1
        while (j < items.size) {
            val cand = row + items[j]
            val wv = worst(cand, side)
            if (wv <= best) { row = cand; best = wv; j++ } else break
        }
        val rowArea = row.sumOf { it.second }
        if (rw >= rh) {
            val colW = rowArea / rh
            var cy = ry
            for ((n, a) in row) {
                val hh = a / colW
                out += Tile(n, rx.toFloat(), cy.toFloat(), colW.toFloat(), hh.toFloat())
                cy += hh
            }
            rx += colW; rw -= colW
        } else {
            val rowH = rowArea / rw
            var cx = rx
            for ((n, a) in row) {
                val ww = a / rowH
                out += Tile(n, cx.toFloat(), ry.toFloat(), ww.toFloat(), rowH.toFloat())
                cx += ww
            }
            ry += rowH; rh -= rowH
        }
        i += row.size
    }
    return out
}

@Composable
fun DiskMapScreen(start: File, onClose: () -> Unit, onOpen: (File) -> Unit) {
    var dir by remember { mutableStateOf(start) }
    var nodes by remember { mutableStateOf<List<Node>?>(null) }

    LaunchedEffect(dir) {
        nodes = null
        nodes = withContext(Dispatchers.IO) {
            (dir.listFiles() ?: emptyArray<File>())
                .map { Node(it, FileOps.sizeOf(it)) }
                .filter { it.size > 0 }
                .sortedByDescending { it.size }
                .take(60)
        }
    }

    BackHandler {
        val p = dir.parentFile
        if (dir != start && p != null) dir = p else onClose()
    }

    val storage = remember { Environment.getExternalStorageDirectory() }
    val total = storage.totalSpace
    val used = total - storage.freeSpace

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("✕ Close") }
            TextButton(onClick = { val p = dir.parentFile; if (dir != start && p != null) dir = p }) {
                Text("⬆ Up")
            }
            Text(dir.absolutePath, Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        if (total > 0) {
            Column(Modifier.padding(horizontal = 12.dp)) {
                LinearProgressIndicator(
                    progress = { used.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Used ${FileOps.fmtSize(used)} of ${FileOps.fmtSize(total)}", fontSize = 12.sp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(4.dp)) {
            val list = nodes
            val w = maxWidth.value
            val h = maxHeight.value
            if (list == null) {
                Text("Calculating sizes…", Modifier.align(Alignment.Center))
            } else if (list.isEmpty()) {
                Text("Empty", Modifier.align(Alignment.Center))
            } else {
                val tiles = remember(list, w, h) { squarify(list, 0f, 0f, w, h) }
                tiles.forEachIndexed { idx, t ->
                    val isDir = t.node.file.isDirectory
                    val color = if (isDir) Color.hsv((idx * 47 % 360).toFloat(), 0.45f, 0.88f)
                    else Color.hsv((idx * 47 % 360).toFloat(), 0.12f, 0.85f)
                    Box(
                        Modifier
                            .offset(t.x.dp, t.y.dp)
                            .size(t.w.coerceAtLeast(0f).dp, t.h.coerceAtLeast(0f).dp)
                            .padding(1.dp)
                            .background(color)
                            .clickable { if (isDir) dir = t.node.file }
                            .padding(3.dp)
                    ) {
                        if (t.w > 44 && t.h > 30) {
                            Text(
                                (if (isDir) "📁 " else "") + t.node.file.name + "\n" + FileOps.fmtSize(t.node.size),
                                fontSize = 10.sp, color = Color.Black, maxLines = 3,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }

        TextButton(onClick = { onOpen(dir) }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Open this folder in active pane")
        }
    }
}
