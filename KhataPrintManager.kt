package com.example.app.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import androidx.core.content.FileProvider
import com.example.app.models.Bill
import com.example.app.models.BillItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "KhataPrintManager"

// ─── A4 at 72 dpi (PDF points) ───────────────────────────────────────────────
private const val A4_WIDTH_PT = 595
private const val A4_HEIGHT_PT = 842

// ─── Layout constants ─────────────────────────────────────────────────────────
private const val MARGIN_H = 36f          // horizontal margin
private const val MARGIN_V = 40f          // vertical margin
private const val COL_QTY_X = 320f
private const val COL_RATE_X = 380f
private const val COL_TAX_X = 450f
private const val COL_TOTAL_X = 520f
private const val RIGHT_EDGE = (A4_WIDTH_PT - MARGIN_H)

// ─── Colours ──────────────────────────────────────────────────────────────────
private val COLOR_PRIMARY = Color.parseColor("#1A237E")   // indigo-900
private val COLOR_ACCENT  = Color.parseColor("#3949AB")   // indigo-600
private val COLOR_LIGHT   = Color.parseColor("#E8EAF6")   // indigo-50
private val COLOR_GRAY    = Color.parseColor("#757575")
private val COLOR_LINE    = Color.parseColor("#BDBDBD")

/**
 * KhataPrintManager
 *
 * Generates standard A4 PDF invoices and prints them via Android's native
 * [PrintManager].
 *
 * ──────────────────────────────────────────────────────────────────────────
 * STORAGE STRATEGY — no runtime permissions required
 * ──────────────────────────────────────────────────────────────────────────
 *  • Files are saved to [Context.getFilesDir]/invoices/  (app-internal storage).
 *    This directory is private to the app and never requires WRITE_EXTERNAL_STORAGE.
 *  • Sharing with external apps (e.g. WhatsApp, Gmail) is done through a
 *    [FileProvider] URI, which grants temporary read access without any
 *    storage permission on the receiver side.
 *  • On Android 10+ (API 29+) scoped storage is enforced, so writing to
 *    internal storage is always the safest cross-version approach.
 * ──────────────────────────────────────────────────────────────────────────
 */
class KhataPrintManager(private val context: Context) {

    /** Directory where all generated PDFs are stored (internal, no permission). */
    private val invoiceDir: File
        get() = File(context.filesDir, "invoices").also { it.mkdirs() }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Send the [bill] to the system print dialog.
     *
     * @param jobName   Title shown in the print queue.
     */
    fun printBill(bill: Bill, jobName: String = "Invoice ${bill.invoiceNumber}") {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = A4PrintDocumentAdapter(bill)
        val attributes = buildA4PrintAttributes()
        printManager.print(jobName, adapter, attributes)
        Log.i(TAG, "Print job '$jobName' submitted to PrintManager")
    }

    /**
     * Generate a PDF for [bill] and save it to internal app storage.
     *
     * @return The saved [File], or null if an error occurred.
     */
    fun savePdf(bill: Bill): File? {
        val outFile = invoiceFile(bill)
        val doc = buildPdfDocument(bill)
        return try {
            FileOutputStream(outFile).use { doc.writeTo(it) }
            Log.i(TAG, "Invoice PDF saved → ${outFile.absolutePath}")
            outFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save PDF: ${e.message}", e)
            null
        } finally {
            doc.close()
        }
    }

    /**
     * Returns a [Uri] suitable for sharing the invoice via Intent.
     * Uses [FileProvider] — no storage permission needed by the receiving app.
     *
     * Call [savePdf] first, or pass the [File] returned by it.
     *
     * @param authority  Must match the <provider android:authorities> in your
     *                   AndroidManifest.xml, e.g. "com.example.app.fileprovider"
     */
    fun shareableUri(file: File, authority: String): Uri =
        FileProvider.getUriForFile(context, authority, file)

    /**
     * Build a share [Intent] that lets the user pick an app to share the invoice.
     */
    fun buildShareIntent(bill: Bill, authority: String): Intent? {
        val file = savePdf(bill) ?: return null
        val uri = shareableUri(file, authority)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Invoice ${bill.invoiceNumber}")
            putExtra(Intent.EXTRA_TEXT, "Please find invoice ${bill.invoiceNumber} attached.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Return all previously generated invoice PDFs from internal storage.
     */
    fun listSavedInvoices(): List<File> =
        invoiceDir.listFiles { f -> f.extension == "pdf" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /**
     * Delete the PDF for a specific [bill], if it exists.
     */
    fun deletePdf(bill: Bill): Boolean =
        invoiceFile(bill).let { if (it.exists()) it.delete() else false }

    // ─── Internal helpers ────────────────────────────────────────────────────

    private fun invoiceFile(bill: Bill): File =
        File(invoiceDir, "invoice_${bill.invoiceNumber.sanitizeFilename()}.pdf")

    private fun buildA4PrintAttributes(): PrintAttributes =
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("khata_res", "300dpi", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

    // ─── PDF rendering ───────────────────────────────────────────────────────

    internal fun buildPdfDocument(bill: Bill): PdfDocument {
        val doc = PdfDocument()
        val renderer = InvoiceRenderer(bill)

        // Paginate: each page gets a fresh canvas at A4 size
        var pageNumber = 1
        var itemsRendered = 0
        while (itemsRendered < bill.items.size || pageNumber == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageNumber).create()
            val page = doc.startPage(pageInfo)
            val (rendered, done) = renderer.drawPage(
                canvas = page.canvas,
                pageNumber = pageNumber,
                itemOffset = itemsRendered,
                isLastPage = false     // we'll determine inside
            )
            itemsRendered += rendered
            doc.finishPage(page)
            if (done) break
            pageNumber++
        }
        return doc
    }

    // ─── PrintDocumentAdapter ────────────────────────────────────────────────

    private inner class A4PrintDocumentAdapter(private val bill: Bill) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder("invoice_${bill.invoiceNumber}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, newAttributes != oldAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onWriteCancelled(); return }
            val doc = buildPdfDocument(bill)
            try {
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: IOException) {
                Log.e(TAG, "onWrite failed: ${e.message}", e)
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InvoiceRenderer  —  all drawing logic lives here
// ─────────────────────────────────────────────────────────────────────────────

private class InvoiceRenderer(private val bill: Bill) {

    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // Pre-built paints
    private val pTitle = buildPaint(20f, COLOR_PRIMARY, bold = true)
    private val pMeta  = buildPaint(8f, COLOR_GRAY)
    private val pHeader= buildPaint(8f, Color.WHITE, bold = true)
    private val pBody  = buildPaint(8f, Color.BLACK)
    private val pBold  = buildPaint(8f, Color.BLACK, bold = true)
    private val pSmall = buildPaint(7f, COLOR_GRAY)
    private val pTotal = buildPaint(10f, Color.WHITE, bold = true)
    private val pAccent= buildPaint(8f, COLOR_PRIMARY, bold = true)

    private val pLine = Paint().apply {
        color = COLOR_LINE; strokeWidth = 0.5f; style = Paint.Style.STROKE
    }
    private val pFillPrimary = Paint().apply { color = COLOR_PRIMARY; style = Paint.Style.FILL }
    private val pFillAccent  = Paint().apply { color = COLOR_ACCENT;  style = Paint.Style.FILL }
    private val pFillLight   = Paint().apply { color = COLOR_LIGHT;   style = Paint.Style.FILL }
    private val pFillWhite   = Paint().apply { color = Color.WHITE;   style = Paint.Style.FILL }

    /**
     * Draw one page of the invoice.
     *
     * @return Pair(itemsRenderedThisPage, isDone)
     */
    fun drawPage(
        canvas: Canvas,
        pageNumber: Int,
        itemOffset: Int,
        @Suppress("UNUSED_PARAMETER") isLastPage: Boolean
    ): Pair<Int, Boolean> {
        var y = MARGIN_V

        // ── Header band ─────────────────────────────────────────────────────
        if (pageNumber == 1) {
            y = drawHeader(canvas, y)
            y = drawBillToSection(canvas, y)
            y = drawTableHeader(canvas, y)
        } else {
            y = drawContinuedHeader(canvas, y)
            y = drawTableHeader(canvas, y)
        }

        // ── Line items ───────────────────────────────────────────────────────
        val (rendered, done) = drawLineItems(canvas, bill.items, itemOffset, y)
        y += rendered * 18f   // approximate — drawLineItems returns count

        // ── Footer (only on last page) ────────────────────────────────────
        if (done) {
            val footerTopY = (A4_HEIGHT_PT - MARGIN_V - 110).toFloat()
            drawTotalsAndFooter(canvas, footerTopY)
        }

        drawPageNumber(canvas, pageNumber)
        return Pair(rendered, done)
    }

    // ─── Section: Logo + Invoice title ───────────────────────────────────────

    private fun drawHeader(canvas: Canvas, startY: Float): Float {
        // Top colour band
        canvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), 70f, pFillPrimary)

        // Company / app name
        canvas.drawText("KHATA", MARGIN_H, 38f, pTitle.also { it.color = Color.WHITE; it.textSize = 22f })
        canvas.drawText("invoicing", MARGIN_H, 54f, buildPaint(9f, Color.WHITE).also { it.alpha = 180 })

        // Invoice label (right-aligned)
        val invLabel = "TAX INVOICE"
        canvas.drawText(invLabel, RIGHT_EDGE - pTitle.measureText(invLabel), 32f,
            buildPaint(14f, Color.WHITE, bold = true))
        canvas.drawText("# ${bill.invoiceNumber}", RIGHT_EDGE - pMeta.measureText("# ${bill.invoiceNumber}"), 48f,
            buildPaint(9f, Color.WHITE))
        canvas.drawText(
            "Date: ${dateFormatter.format(Date(bill.createdAt))}",
            RIGHT_EDGE - pMeta.measureText("Date: ${dateFormatter.format(Date(bill.createdAt))}"),
            62f, buildPaint(8f, Color.WHITE).also { it.alpha = 200 }
        )

        return 78f
    }

    private fun drawContinuedHeader(canvas: Canvas, startY: Float): Float {
        canvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), 30f, pFillPrimary)
        canvas.drawText("Invoice ${bill.invoiceNumber} — continued", MARGIN_H, 20f,
            buildPaint(9f, Color.WHITE))
        return 38f
    }

    // ─── Section: Bill To ────────────────────────────────────────────────────

    private fun drawBillToSection(canvas: Canvas, startY: Float): Float {
        var y = startY + 8f

        canvas.drawText("BILL TO", MARGIN_H, y, pAccent)
        y += 13f
        canvas.drawText(bill.customer.displayName, MARGIN_H, y, pBold.also { it.textSize = 10f })
        y += 12f

        val addressLines = bill.customer.formattedAddress.lines()
        for (line in addressLines) {
            canvas.drawText(line, MARGIN_H, y, pBody)
            y += 11f
        }
        if (bill.customer.email.isNotBlank()) {
            canvas.drawText(bill.customer.email, MARGIN_H, y, pBody)
            y += 11f
        }
        if (bill.customer.phone.isNotBlank()) {
            canvas.drawText(bill.customer.phone, MARGIN_H, y, pBody)
            y += 11f
        }

        // Due date — right side
        bill.dueAt?.let { due ->
            val dueStr = "Due: ${dateFormatter.format(Date(due))}"
            canvas.drawText(dueStr,
                RIGHT_EDGE - pBody.measureText(dueStr),
                startY + 13f, pBold.also { it.textSize = 8f })
        }

        canvas.drawLine(MARGIN_H, y + 4f, RIGHT_EDGE, y + 4f, pLine)
        return y + 10f
    }

    // ─── Section: Table header row ───────────────────────────────────────────

    private fun drawTableHeader(canvas: Canvas, startY: Float): Float {
        val rowH = 14f
        canvas.drawRect(MARGIN_H, startY, RIGHT_EDGE, startY + rowH, pFillAccent)
        val textY = startY + rowH - 4f
        canvas.drawText("#",          MARGIN_H + 2f,   textY, pHeader)
        canvas.drawText("Item",       MARGIN_H + 14f,  textY, pHeader)
        canvas.drawText("Qty",        COL_QTY_X,       textY, pHeader)
        canvas.drawText("Rate",       COL_RATE_X,      textY, pHeader)
        canvas.drawText("Tax%",       COL_TAX_X,       textY, pHeader)
        canvas.drawText("Amount",     COL_TOTAL_X,     textY, pHeader)
        return startY + rowH + 2f
    }

    // ─── Section: Line items ─────────────────────────────────────────────────

    /**
     * Draw as many items as fit on the remaining page height.
     *
     * Reserve space for a footer (~130 pt) on the last page.
     * @return Pair(count rendered, allItemsDone)
     */
    private fun drawLineItems(
        canvas: Canvas,
        items: List<BillItem>,
        offset: Int,
        startY: Float
    ): Pair<Int, Boolean> {
        val maxY = A4_HEIGHT_PT - MARGIN_V - 130f   // leave room for totals
        var y = startY
        var count = 0

        for (i in offset until items.size) {
            val rowH = 16f
            if (y + rowH > maxY) break

            val item = items[i]
            val rowNum = i + 1
            val isEven = rowNum % 2 == 0
            if (isEven) canvas.drawRect(MARGIN_H, y, RIGHT_EDGE, y + rowH, pFillLight)

            val textY = y + rowH - 5f
            canvas.drawText("$rowNum", MARGIN_H + 2f, textY, pSmall)
            canvas.drawText(item.name.take(30), MARGIN_H + 14f, textY, pBody)
            if (item.description.isNotBlank()) {
                canvas.drawText(item.description.take(40), MARGIN_H + 14f, textY + 8f,
                    pSmall.also { it.textSize = 6.5f })
            }
            canvas.drawText(item.formattedQuantity, COL_QTY_X, textY, pBody)
            canvas.drawText(item.formattedUnitPrice, COL_RATE_X, textY, pBody)
            canvas.drawText("${item.taxRatePercent.toInt()}%", COL_TAX_X, textY, pBody)

            val totalStr = item.formattedLineTotal
            canvas.drawText(totalStr, RIGHT_EDGE - pBody.measureText(totalStr), textY, pBody)

            canvas.drawLine(MARGIN_H, y + rowH, RIGHT_EDGE, y + rowH, pLine.also { it.alpha = 80 })
            y += rowH
            count++
        }
        val done = (offset + count) >= items.size
        return Pair(count, done)
    }

    // ─── Section: Totals + Notes + Footer ────────────────────────────────────

    private fun drawTotalsAndFooter(canvas: Canvas, startY: Float) {
        var y = startY

        // Divider
        canvas.drawLine(MARGIN_H, y, RIGHT_EDGE, y, pLine)
        y += 10f

        // Subtotal row
        val labelX = 400f
        fun drawSummaryRow(label: String, value: String, paint: Paint = pBody, bold: Boolean = false) {
            canvas.drawText(label, labelX, y, if (bold) pBold else pSmall)
            val valPaint = if (bold) pBold else pBody
            canvas.drawText(value, RIGHT_EDGE - valPaint.measureText(value), y, valPaint)
        }

        drawSummaryRow("Subtotal", bill.formattedSubtotal)
        y += 11f
        if (bill.totalDiscount > 0) {
            drawSummaryRow("Discount", "-%.2f".format(bill.totalDiscount))
            y += 11f
        }
        drawSummaryRow("Tax", bill.formattedTotalTax)
        y += 11f

        // Grand total band
        canvas.drawRect(390f, y, RIGHT_EDGE, y + 18f, pFillPrimary)
        canvas.drawText("TOTAL", 396f, y + 12f, pTotal)
        val gtStr = bill.formattedGrandTotal
        canvas.drawText(gtStr, RIGHT_EDGE - 4f - pTotal.measureText(gtStr), y + 12f, pTotal)
        y += 24f

        // Notes
        if (bill.notes.isNotBlank()) {
            canvas.drawText("Notes", MARGIN_H, y, pAccent.also { it.textSize = 7f })
            y += 10f
            wrapText(bill.notes, MARGIN_H, y, RIGHT_EDGE - MARGIN_H, pSmall, canvas).let { y = it }
            y += 4f
        }

        // T&C
        if (bill.termsAndConditions.isNotBlank()) {
            canvas.drawText("Terms & Conditions", MARGIN_H, y, pAccent.also { it.textSize = 7f })
            y += 10f
            wrapText(bill.termsAndConditions, MARGIN_H, y, RIGHT_EDGE - MARGIN_H, pSmall, canvas)
        }
    }

    private fun drawPageNumber(canvas: Canvas, pageNumber: Int) {
        val text = "Page $pageNumber"
        canvas.drawText(text,
            (A4_WIDTH_PT / 2f) - pSmall.measureText(text) / 2f,
            (A4_HEIGHT_PT - 12).toFloat(), pSmall)
    }

    // ─── Utilities ───────────────────────────────────────────────────────────

    /** Simple word-wrap helper. Returns the Y position after the last line. */
    private fun wrapText(
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        canvas: Canvas
    ): Float {
        var y = startY
        val words = text.split(" ")
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth) {
                canvas.drawText(line, x, y, paint)
                y += 10f
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) { canvas.drawText(line, x, y, paint); y += 10f }
        return y
    }

    private fun buildPaint(
        textSize: Float,
        color: Int,
        bold: Boolean = false
    ) = Paint().apply {
        this.color = color
        this.textSize = textSize
        this.isAntiAlias = true
        if (bold) this.typeface = Typeface.DEFAULT_BOLD
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension utilities
// ─────────────────────────────────────────────────────────────────────────────

/** Replace characters not safe in filenames with underscores. */
private fun String.sanitizeFilename(): String =
    replace(Regex("[^a-zA-Z0-9._-]"), "_")
