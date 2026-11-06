package com.smartcare.app

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.os.Build
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.security.MessageDigest

private data class CleanCandidate(
    val uri: Uri,
    val name: String,
    val size: Long,
    val kind: String,
    val note: String,
    val selected: Boolean = false
)

@Composable
fun CleanerPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val candidates = remember { mutableStateListOf<CleanCandidate>() }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Faça uma análise para localizar arquivos grandes, possíveis duplicados e fotos possivelmente desfocadas.") }
    var hasPermission by remember { mutableStateOf(false) }

    fun scanFiles() {
        scanning = true
        message = "Analisando fotos e vídeos…"
        scope.launch {
            val found = withContext(Dispatchers.IO) { findCandidates(context) }
            candidates.clear()
            candidates.addAll(found)
            scanning = false
            message = if (found.isEmpty()) "Análise concluída. Nenhum candidato encontrado nas mídias permitidas." else "Análise concluída: ${found.size} itens para revisar. Nada foi apagado."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.any { it }
        if (hasPermission) scanFiles()
        else message = "Permissão de fotos e vídeos não concedida. Você pode liberar o acesso nas configurações."
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            message = "Exclusão confirmada pelo Android."
            scanFiles()
        } else {
            message = "Nenhum arquivo foi excluído. A confirmação foi cancelada."
        }
    }

    fun requestScan() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) scanFiles() else permissionLauncher.launch(missing.toTypedArray())
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("LIMPEZA INTELIGENTE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Você escolhe o que será excluído. O Smart Care nunca apaga arquivos sozinho.", style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Análise de arquivos", fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { requestScan() }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (scanning) "Analisando…" else "Analisar fotos e vídeos")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Cache de aplicativos", fontWeight = FontWeight.SemiBold)
                Text("Por segurança, o Android não permite que um app limpe diretamente o cache privado de outros aplicativos. Abra as configurações para revisar o armazenamento de cada app.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                    context.startActivity(intent)
                }, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar armazenamento dos aplicativos") }
            }
        }
        if (candidates.isNotEmpty()) {
            val selected = candidates.filter { it.selected }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${candidates.size} itens encontrados", fontWeight = FontWeight.SemiBold)
                Text("${selected.size} selecionados", color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        for (index in candidates.indices) {
                            candidates[index] = candidates[index].copy(selected = true)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Selecionar todos") }
                Button(
                    onClick = {
                        for (index in candidates.indices) {
                            candidates[index] = candidates[index].copy(selected = false)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Limpar seleção") }
            }
            Button(
                onClick = {
                    val uris = selected.map { it.uri }
                    if (uris.isEmpty()) {
                        message = "Selecione pelo menos um arquivo."
                    } else if (Build.VERSION.SDK_INT >= 30) {
                        val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
                        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } else if (Build.VERSION.SDK_INT == 29) {
                        message = "No Android 10, a exclusão precisa de uma confirmação específica do sistema. Use a Galeria ou o app Arquivos para concluir com segurança."
                    } else {
                        var deleted = 0
                        uris.forEach { uri ->
                            try { if (context.contentResolver.delete(uri, null, null) > 0) deleted++ }
                            catch (_: SecurityException) { }
                        }
                        message = "$deleted arquivo(s) excluído(s)."
                        scanFiles()
                    }
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Excluir selecionados (com confirmação)") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates, key = { it.uri.toString() }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(
                                checked = item.selected,
                                onCheckedChange = { checked ->
                                    val index = candidates.indexOfFirst { it.uri == item.uri }
                                    if (index >= 0) candidates[index] = candidates[index].copy(selected = checked)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text("${item.kind} • ${formatBytes(item.size)}", style = MaterialTheme.typography.bodySmall)
                                Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun findCandidates(context: android.content.Context): List<CleanCandidate> {
    val resolver = context.contentResolver
    val rows = mutableListOf<CleanCandidate>()
    val firstDuplicateUris = mutableMapOf<String, Uri>()
    var blurChecks = 0
    val sources = listOf(
        Triple(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Imagem", MediaStore.Images.Media._ID),
        Triple(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Vídeo", MediaStore.Video.Media._ID)
    )
    for ((collection, kind, idColumn) in sources) {
        val projection = arrayOf(idColumn, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE)
        try {
            resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.SIZE} DESC")?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(idColumn)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext() && rows.size < 1200) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Arquivo sem nome"
                    val size = cursor.getLong(sizeIndex)
                    if (size <= 0L) continue
                    val uri = ContentUris.withAppendedId(collection, id)
                    val key = "${name.lowercase(Locale.ROOT)}|$size"
                    val firstUri = firstDuplicateUris.putIfAbsent(key, uri)
                    val notes = mutableListOf<String>()
                    if (size >= 100L * 1024L * 1024L) notes.add("Arquivo grande (100 MB ou mais)")
                    if (firstUri != null) {
                        if (size <= 250L * 1024L * 1024L) {
                            val firstHash = sha256(resolver, firstUri)
                            val currentHash = sha256(resolver, uri)
                            notes.add(
                                when {
                                    firstHash != null && currentHash != null && firstHash == currentHash ->
                                        "Duplicado confirmado: conteúdo idêntico"
                                    firstHash != null && currentHash != null ->
                                        "Mesmo nome e tamanho, mas conteúdo diferente"
                                    else -> "Possível duplicado: não foi possível comparar o conteúdo"
                                }
                            )
                        } else {
                            notes.add("Possível duplicado: mesmo nome e tamanho (arquivo grande demais para comparar)")
                        }
                    }
                    if (kind == "Imagem" && blurChecks < 120 && size < 80L * 1024L * 1024L) {
                        blurChecks++
                        if (looksPossiblyBlurry(resolver, uri)) {
                            notes.add("Possivelmente desfocada (avaliação automática)")
                        }
                    }
                    if (notes.isNotEmpty()) rows.add(CleanCandidate(uri, name, size, kind, notes.joinToString(" • ")))
                }
            }
        } catch (_: SecurityException) {
            // Acesso parcial ou negado: os arquivos não autorizados são ignorados.
        } catch (_: Exception) {
            // Um arquivo indisponível não interrompe a análise dos demais.
        }
    }
    return rows.sortedByDescending { it.size }
}


private fun sha256(resolver: android.content.ContentResolver, uri: Uri): String? {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }
}

private fun looksPossiblyBlurry(resolver: android.content.ContentResolver, uri: Uri): Boolean {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth < 160 || bounds.outHeight < 160) return false
        val sample = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 180)
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, sample) } ?: return false
        val score = edgeSharpness(bitmap)
        bitmap.recycle()
        score < 9.0
    } catch (_: Exception) {
        false
    }
}

private fun edgeSharpness(bitmap: Bitmap): Double {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 3 || height < 3) return 0.0
    var total = 0L
    var count = 0
    for (y in 1 until height - 1 step 2) {
        for (x in 1 until width - 1 step 2) {
            val center = bitmap.getPixel(x, y)
            val right = bitmap.getPixel(x + 1, y)
            val down = bitmap.getPixel(x, y + 1)
            fun gray(c: Int) = (((c shr 16) and 255) * 30 + ((c shr 8) and 255) * 59 + (c and 255) * 11) / 100
            total += kotlin.math.abs(gray(center) - gray(right)) + kotlin.math.abs(gray(center) - gray(down))
            count += 2
        }
    }
    return if (count == 0) 0.0 else total.toDouble() / count
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1073741824.0)
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1048576.0)
        else -> "%.0f KB".format(bytes / 1024.0)
    }
}
