package com.example.miprimeraapp

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

data class ExperienceFeedback(val id: String, val reportTitle: String, val comment: String, val createdAt: Long)
enum class FeedbackDelivery { PENDING, SENT }

/** Conectar aquí el cliente HTTP. SENT sólo debe devolverse tras confirmación del servidor. */
interface ExperienceFeedbackRepository {
    suspend fun submit(feedback: ExperienceFeedback): FeedbackDelivery
}

/** Cola persistente local; no simula una entrega al servidor. Conserva IDs para un futuro envío idempotente. */
class PendingExperienceFeedbackRepository(context: Context) : ExperienceFeedbackRepository {
    private val preferences = context.applicationContext.getSharedPreferences("experience_feedback", Context.MODE_PRIVATE)
    override suspend fun submit(feedback: ExperienceFeedback): FeedbackDelivery = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val pending = JSONArray(preferences.getString("pending", "[]"))
            if ((0 until pending.length()).none { pending.getJSONObject(it).getString("id") == feedback.id }) {
                pending.put(JSONObject().put("id", feedback.id).put("reportTitle", feedback.reportTitle)
                    .put("comment", feedback.comment).put("createdAt", feedback.createdAt))
                check(preferences.edit().putString("pending", pending.toString()).commit())
            }
        }
        FeedbackDelivery.PENDING
    }
    private companion object { val lock = Any() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportActions(title: String, area: AreaGeometry, modifier: Modifier = Modifier,
    repository: ExperienceFeedbackRepository? = null) {
    val context = LocalContext.current
    val store = remember { context.getSharedPreferences("experience_feedback", Context.MODE_PRIVATE) }
    val feedbackRepository = repository ?: remember { PendingExperienceFeedbackRepository(context) }
    var open by rememberSaveable { mutableStateOf(false) }
    var comments by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(store.getString("draft", "") ?: "") }
    var submissionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.report_pdf_saved)
    val error = stringResource(R.string.report_action_error)
    val shareTitle = stringResource(R.string.report_share)
    val queued = stringResource(R.string.report_feedback_pending)
    val sent = stringResource(R.string.report_feedback_sent)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { writeReportPdf(context, title, area, it) }
                        ?: error("No output stream")
                }
                message = saved
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { message = error }
            finally { busy = false }
        }
    }
    IconButton(onClick = { open = true; comments = false; message = null }, modifier = modifier
        .background(Color.White.copy(alpha = .92f), CircleShape)) {
        ActionIcon("more", stringResource(R.string.report_actions))
    }
    if (open) ModalBottomSheet(onDismissRequest = { if (!busy) open = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Color(0xFFE1F8F9), scrimColor = Color.Black.copy(alpha = .10f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFCCE2E3), width = 76.dp) }) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp).padding(top = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (comments) {
                Text(stringResource(R.string.report_feedback_title), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(stringResource(R.string.report_feedback_hint), color = Color(0xFF6D8183), fontSize = 14.sp)
                OutlinedTextField(value = draft, onValueChange = {
                    if (it.length <= 2000) { draft = it; store.edit().putString("draft", it).apply() }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 7,
                    label = { Text(stringResource(R.string.report_feedback_label)) },
                    supportingText = { Text("${draft.length}/2000") })
                Button(onClick = { scope.launch {
                    busy = true
                    try {
                        val delivery = feedbackRepository.submit(ExperienceFeedback(submissionId, title, draft.trim(), System.currentTimeMillis()))
                        message = if (delivery == FeedbackDelivery.SENT) sent else queued
                        draft = ""; store.edit().remove("draft").apply()
                        submissionId = UUID.randomUUID().toString()
                        comments = false
                    } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (_: Exception) { message = error }
                    finally { busy = false }
                } }, enabled = draft.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.report_feedback_save))
                }
                TextButton(onClick = { comments = false }, enabled = !busy) { Text(stringResource(R.string.report_actions_back)) }
            } else {
                ActionRow("share", stringResource(R.string.report_share), !busy) {
                    scope.launch {
                        busy = true
                        try {
                            val file = withContext(Dispatchers.IO) {
                                val directory = File(context.cacheDir, "reports").apply { mkdirs() }
                                File(directory, "reporte-${UUID.randomUUID()}.pdf").also { output ->
                                    output.outputStream().use { writeReportPdf(context, title, area, it) }
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                clipData = ClipData.newRawUri("Reporte", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, shareTitle))
                        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                        catch (_: Exception) { message = error }
                        finally { busy = false }
                    }
                }
                ActionRow("comment", stringResource(R.string.report_feedback_title), !busy) { comments = true; message = null }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { save.launch("Reporte-${title.take(60).replace(Regex("[^\\p{L}\\p{N} -]"), "")}.pdf") },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0085FF), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(3.dp)) {
                    Text(stringResource(R.string.report_download), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = Color(0xFF31575E), fontSize = 14.sp) }
        }
    }
}

@Composable
private fun ActionRow(kind: String, title: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ActionIcon(kind, null)
        Text(title, color = Color(0xFF828B8B), fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun ActionIcon(kind: String, description: String?) {
    val modifier = if (description == null) Modifier else Modifier.semantics { contentDescription = description }
    Canvas(modifier.size(24.dp)) {
        val color = Color(0xFF828B8B); val w = size.width; val h = size.height
        fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, Offset(w*x, h*y), Offset(w*a, h*b), 2.dp.toPx(), StrokeCap.Round)
        when (kind) {
            "more" -> listOf(.22f, .5f, .78f).forEach { drawCircle(Color(0xFF0085FF), 1.8.dp.toPx(), Offset(w*it, h*.5f)) }
            "share" -> {
                val p = Path().apply { moveTo(w*.14f,h*.8f); lineTo(w*.14f,h*.57f); quadraticTo(w*.14f,h*.35f,w*.38f,h*.35f); lineTo(w*.88f,h*.35f) }
                drawPath(p,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round));line(.64f,.12f,.88f,.35f);line(.88f,.35f,.64f,.58f)
            }
            else -> {
                val p=Path().apply { moveTo(w*.12f,h*.14f);lineTo(w*.88f,h*.14f);lineTo(w*.88f,h*.72f);lineTo(w*.34f,h*.72f);lineTo(w*.12f,h*.9f);close() }
                drawPath(p,color,style=Stroke(2.dp.toPx()));line(.34f,.43f,.66f,.43f);line(.5f,.27f,.5f,.59f)
            }
        }
    }
}

/** Exportación paginada del contenido del lector y de la geometría real, sin red ni permisos de almacenamiento. */
internal fun writeReportPdf(context: Context, title: String, area: AreaGeometry, output: OutputStream) {
    val document = PdfDocument()
    val blue = android.graphics.Color.rgb(0,133,255)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    fun newPage() {
        page?.let { document.finishPage(it) }
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
        paint.typeface = Typeface.DEFAULT; paint.textSize = 9f; paint.color = android.graphics.Color.GRAY
        page!!.canvas.drawText("Vital Oceans · $pageNumber",42f,810f,paint)
        y = 48f
    }
    fun text(value: String, heading: Boolean = false) {
        if (y > 730f) newPage()
        paint.typeface = if (heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textSize = if (heading) 17f else 11f
        paint.color = if (heading) blue else android.graphics.Color.DKGRAY
        val lineHeight = if (heading) 24f else 17f
        for (paragraph in value.split('\n')) {
            var remaining = paragraph.trim()
            if (remaining.isEmpty()) { y += 10f; continue }
            while (remaining.isNotEmpty()) {
                if (y + lineHeight > 778f) {
                    newPage()
                    paint.typeface = if (heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    paint.textSize = if (heading) 17f else 11f
                    paint.color = if (heading) blue else android.graphics.Color.DKGRAY
                }
                var count = paint.breakText(remaining,true,511f,null).coerceAtLeast(1)
                if (count < remaining.length) remaining.lastIndexOf(' ',count-1).takeIf { it > 0 }?.let { count = it }
                page!!.canvas.drawText(remaining.take(count),42f,y,paint)
                remaining = remaining.drop(count).trimStart(); y += lineHeight
            }
        }
        y += 12f
    }
    try {
        newPage()
        text(title, true)
        text(reportCoordinates(area.center))
        text(String.format(Locale.US,"Superficie: %.2f km² · Perímetro: %.2f km", area.squareMeters/1_000_000,area.perimeterMeters/1000))
        val vertices = area.vertices
        val west=vertices.minOf { it.longitude }; val east=vertices.maxOf { it.longitude }
        val south=vertices.minOf { it.latitude }; val north=vertices.maxOf { it.latitude }
        val scale=minOf(460.0/(east-west).coerceAtLeast(.000001),150.0/(north-south).coerceAtLeast(.000001))
        val path=android.graphics.Path()
        vertices.forEachIndexed { index,p ->
            val x=(67+(p.longitude-west)*scale).toFloat(); val py=(y+160-(p.latitude-south)*scale).toFloat()
            if(index==0) path.moveTo(x,py) else path.lineTo(x,py)
        }
        path.close(); paint.color=blue;paint.style=Paint.Style.STROKE;paint.strokeWidth=2f
        page!!.canvas.drawPath(path,paint);paint.style=Paint.Style.FILL;y+=190f
        text(context.getString(R.string.report_export_geometry))
        ReportTab.entries.forEach { tab ->
            text(context.getString(tab.label),true)
            reportBlocks(tab).forEach { block ->
                text(context.getString(block.title),true);text(context.getString(block.body))
                if (block.statuses) listOf(R.string.report_cr,R.string.report_en,R.string.report_vu,R.string.report_nt,
                    R.string.report_lc,R.string.report_dd,R.string.report_ne).forEach { text(context.getString(it)) }
            }
        }
        page?.let { document.finishPage(it) };page=null
        document.writeTo(output)
    } finally { document.close() }
}
