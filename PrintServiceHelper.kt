package com.example.app.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import com.example.app.models.Bill
import com.example.app.models.BillItem
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "PrintServiceHelper"

private const val PAGE_WIDTH_PT = 612
private const val PAGE_HEIGHT_PT = 792
private const val MARGIN = 40f
private const val LINE_HEIGHT = 20f

class PrintServiceHelper(private val context: Context) {

    private val printManager: PrintManager by lazy {
        context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    }

    fun printBill(bill: Bill, jobName: String = "Bill #${bill.id.take(8)}") {
        val adapter = BillPrintAdapter(context, bill)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
            .setResolution(PrintAttributes.Resolution("default", "Default", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, adapter, attributes)
        Log.d(TAG, "Print job '$jobName' submitted")
    }

    fun generatePdfDocument(bill: Bill): PdfDocument {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
        val page = document.startPage(pageInfo)

        drawBillOnCanvas(page.canvas, bill)

        document.finishPage(page)
        Log.d(TAG, "PDF document generated for bill ${bill.id}")
        return document
    }

    fun savePdfToFile(bill: Bill, outputPath: String): Boolean {
        val document = generatePdfDocument(bill)
        return try {
            FileOutputStream(outputPath).use { stream ->
                document.writeTo(stream)
            }
            Log.d(TAG, "PDF saved to $outputPath")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to save PDF: ${e.message}", e)
            false
        } finally {
            document.close()
        }
    }

    private fun drawBillOnCanvas(canvas: Canvas, bill: Bill) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        var y = MARGIN + 30f

        canvas.drawText("INVOICE", MARGIN, y, titlePaint)
        y += LINE_HEIGHT * 2

        canvas.drawText("Bill To:", MARGIN, y, headerPaint)
        y += LINE_HEIGHT
        canvas.drawText(bill.customer.displayName, MARGIN, y, bodyPaint)
        y += LINE_HEIGHT
        if (bill.customer.email.isNotBlank()) {
            canvas.drawText(bill.customer.email, MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
        }
        if (bill.customer.phone.isNotBlank()) {
            canvas.drawText(bill.customer.phone, MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
        }
        if (bill.customer.address.isNotBlank()) {
            canvas.drawText(bill.customer.address, MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
        }

        y += LINE_HEIGHT
        canvas.drawLine(MARGIN, y, PAGE_WIDTH_PT - MARGIN, y, linePaint)
        y += LINE_HEIGHT

        canvas.drawText("Item", MARGIN, y, headerPaint)
        canvas.drawText("Qty", 300f, y, headerPaint)
        canvas.drawText("Unit Price", 370f, y, headerPaint)
        canvas.drawText("Total", 480f, y, headerPaint)
        y += LINE_HEIGHT / 2
        canvas.drawLine(MARGIN, y, PAGE_WIDTH_PT - MARGIN, y, linePaint)
        y += LINE_HEIGHT

        for (item in bill.items) {
            canvas.drawText(item.name.take(28), MARGIN, y, bodyPaint)
            canvas.drawText("${item.quantity}", 300f, y, bodyPaint)
            canvas.drawText(item.formattedUnitPrice, 370f, y, bodyPaint)
            canvas.drawText(item.formattedTotal, 480f, y, bodyPaint)
            y += LINE_HEIGHT
        }

        y += LINE_HEIGHT / 2
        canvas.drawLine(MARGIN, y, PAGE_WIDTH_PT - MARGIN, y, linePaint)
        y += LINE_HEIGHT

        canvas.drawText("Subtotal:", 370f, y, headerPaint)
        canvas.drawText("%.2f".format(bill.subtotal), 480f, y, bodyPaint)
        y += LINE_HEIGHT

        canvas.drawText("Tax:", 370f, y, headerPaint)
        canvas.drawText("%.2f".format(bill.totalTax), 480f, y, bodyPaint)
        y += LINE_HEIGHT

        canvas.drawText("Grand Total:", 370f, y, titlePaint.apply { textSize = 16f })
        canvas.drawText(bill.formattedGrandTotal, 480f, y, titlePaint)
        y += LINE_HEIGHT * 2

        if (bill.notes.isNotBlank()) {
            canvas.drawText("Notes:", MARGIN, y, headerPaint)
            y += LINE_HEIGHT
            canvas.drawText(bill.notes, MARGIN, y, bodyPaint)
        }
    }
}

private class BillPrintAdapter(
    private val context: Context,
    private val bill: Bill
) : PrintDocumentAdapter() {

    private val helper = PrintServiceHelper(context)

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: android.os.CancellationSignal?,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("bill_${bill.id.take(8)}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        val document = helper.generatePdfDocument(bill)
        try {
            FileOutputStream(destination.fileDescriptor).use { stream ->
                document.writeTo(stream)
            }
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: IOException) {
            Log.e("BillPrintAdapter", "Failed to write PDF: ${e.message}", e)
            callback.onWriteFailed(e.message)
        } finally {
            document.close()
        }
    }
}
