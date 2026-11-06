package com.nexauren.pdfmax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ToolCardSpec(
    val id: String,
    val pt: String,
    val en: String,
    val detailPt: String,
    val detailEn: String,
    val group: Int,
    val icon: ImageVector,
    val mark: String
)

@Composable
fun ToolsCatalogScreen(
    english: Boolean,
    onCreate: () -> Unit,
    onCompress: () -> Unit,
    onMerge: () -> Unit,
    onScan: () -> Unit,
    onOpenTool: (String) -> Unit,
    onFiles: () -> Unit
) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    val categories = if (english) listOf("All", "Edit", "Organize", "Convert", "Security", "Scan") else listOf("Todos", "Editar", "Organizar", "Converter", "Segurança", "Digitalizar")
    val tools = remember(english) {
        listOf(
            ToolCardSpec("create", "Criar PDF", "Create PDF", "Transforma fotografias em páginas PDF", "Turn photos into PDF pages", 5, Icons.Default.AddPhotoAlternate, "PHOTO"),
            ToolCardSpec("scan", "Digitalizar documento", "Scan document", "Captura documentos com a câmara", "Capture documents with your camera", 5, Icons.Default.DocumentScanner, "CAMERA"),
            ToolCardSpec("compress", "Comprimir PDF", "Compress PDF", "Regrava e compara o tamanho real", "Rebuild and compare the actual file size", 2, Icons.Default.Compress, "OPTIMIZE"),
            ToolCardSpec("merge", "Juntar PDFs", "Merge PDFs", "Combina vários ficheiros pela ordem escolhida", "Combine files in your chosen order", 2, Icons.Default.MergeType, "MULTI-FILE"),
            ToolCardSpec("extract_pages", "Extrair páginas", "Extract pages", "Cria um PDF apenas com as páginas indicadas", "Create a PDF with only the selected pages", 2, Icons.Default.ContentCut, "PAGE RANGE"),
            ToolCardSpec("remove_pages", "Remover páginas", "Remove pages", "Retira páginas sem alterar as restantes", "Remove pages while preserving the others", 2, Icons.Default.DeleteSweep, "CLEAN UP"),
            ToolCardSpec("rotate", "Rodar páginas", "Rotate pages", "Roda todas as páginas em 90°, 180° ou 270°", "Rotate all pages by 90°, 180° or 270°", 2, Icons.Default.RotateRight, "ORIENTATION"),
            ToolCardSpec("watermark", "Marca de água", "Watermark", "Adiciona uma marca de água em todas as páginas", "Add a watermark to every page", 1, Icons.Default.BrandingWatermark, "IDENTITY"),
            ToolCardSpec("add_text", "Adicionar texto", "Add text", "Sobrepõe uma nota ao documento", "Overlay a note on the document", 1, Icons.Default.TextFields, "ANNOTATION"),
            ToolCardSpec("page_numbers", "Numerar páginas", "Page numbers", "Insere numeração automática", "Insert automatic page numbering", 1, Icons.Default.FormatListNumbered, "PAGINATION"),
            ToolCardSpec("metadata", "Propriedades PDF", "PDF properties", "Edita título e autor do documento", "Edit document title and author", 1, Icons.Default.EditNote, "DOCUMENT INFO"),
            ToolCardSpec("extract_text", "Extrair texto", "Extract text", "Exporta o texto selecionável para TXT", "Export selectable text to TXT", 3, Icons.Default.TextSnippet, "TXT EXPORT"),
            ToolCardSpec("to_images", "PDF para imagens", "PDF to images", "Exporta páginas para um pacote ZIP", "Export pages into a ZIP package", 3, Icons.Default.Image, "PNG EXPORT"),
            ToolCardSpec("protect", "Proteger PDF", "Protect PDF", "Define uma palavra-passe de abertura", "Set an opening password", 4, Icons.Default.Lock, "PASSWORD"),
            ToolCardSpec("unlock", "Desproteger PDF", "Unlock PDF", "Remove a proteção com a palavra-passe correta", "Remove protection with the correct password", 4, Icons.Default.LockOpen, "UNLOCK")
        )
    }
    val visible = tools.filter { category == 0 || it.group == category }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (english) "Tools & Features" else "Ferramentas e funções", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (english) "A focused workspace for every PDF task" else "Uma área própria para cada tarefa PDF", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = onFiles) { Icon(Icons.Default.FolderOpen, if (english) "My files" else "Meus ficheiros") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            categories.forEachIndexed { index, label ->
                FilterChip(
                    selected = category == index,
                    onClick = { category = index },
                    label = { Text(label, maxLines = 1) },
                    leadingIcon = if (category == index) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp)) }) else null
                )
            }
        }
        Spacer(Modifier.height(13.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            items(visible, key = { it.id }) { tool ->
                val click = when (tool.id) {
                    "create" -> onCreate
                    "compress" -> onCompress
                    "merge" -> onMerge
                    "scan" -> onScan
                    else -> ({ onOpenTool(tool.id) })
                }
                Card(
                    onClick = click,
                    modifier = Modifier.fillMaxWidth().height(164.dp),
                    shape = RoundedCornerShape(21.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.align(Alignment.TopEnd).size(108.dp).offset(x = 25.dp, y = (-24).dp)
                                .background(Brush.radialGradient(listOf(Color(0x4425C9F2), Color.Transparent)))
                        )
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(47.dp).background(
                                        Brush.linearGradient(listOf(Color(0xFF18CBE3), Color(0xFF287CE8))),
                                        RoundedCornerShape(15.dp)
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                            }
                            Spacer(Modifier.height(13.dp))
                            Text(if (english) tool.en else tool.pt, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(if (english) tool.detailEn else tool.detailPt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Text(tool.mark, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                        }
                    }
                }
            }
        }
    }
}
