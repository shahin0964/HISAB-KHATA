package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.DuePaymentEntity
import com.example.ui.components.formatBengaliNumber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfReceiptGenerator {

    fun generateReceiptPdf(context: Context, payment: DuePaymentEntity): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size in points
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val isReceivable = payment.direction == "RECEIVABLE"

        // Background
        canvas.drawColor(Color.WHITE)

        // Header Banner Background (Primary Blue or Slate)
        paint.color = Color.parseColor("#1E3A8A") // Dark Navy Blue
        canvas.drawRect(0f, 0f, 595f, 100f, paint)

        // App Brand Name
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("হিসাব খাতা", 30f, 45f, paint)

        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#93C5FD")
        canvas.drawText("ডিজিটাল পেমেন্ট রসিদ - Hisab Khata", 30f, 68f, paint)

        // Receipt Unique ID & Date Box (Right aligned header text)
        paint.color = Color.WHITE
        paint.textSize = 10f
        canvas.drawText("রসিদ নং: ${payment.receiptNumber}", 380f, 45f, paint)
        canvas.drawText("তারিখ: ${payment.paymentDate} ${payment.paymentTime}", 380f, 65f, paint)

        // Title Box (পাওনা পরিশোধ রসিদ vs দেনা পরিশোধ রসিদ)
        paint.color = if (isReceivable) Color.parseColor("#15803D") else Color.parseColor("#B91C1C")
        canvas.drawRoundRect(30f, 120f, 565f, 165f, 12f, 12f, paint)

        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val titleText = if (isReceivable) "পাওনা পরিশোধ রসিদ (MONEY RECEIVED)" else "দেনা পরিশোধ রসিদ (MONEY PAID)"
        canvas.drawText(titleText, 45f, 148f, paint)

        // Wording Box Statement
        paint.color = if (isReceivable) Color.parseColor("#F0FDF4") else Color.parseColor("#FEF2F2")
        canvas.drawRoundRect(30f, 180f, 565f, 235f, 12f, 12f, paint)

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = if (isReceivable) Color.parseColor("#86EFAC") else Color.parseColor("#FCA5A5")
        }
        canvas.drawRoundRect(30f, 180f, 565f, 235f, 12f, 12f, borderPaint)

        paint.color = if (isReceivable) Color.parseColor("#166534") else Color.parseColor("#991B1B")
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val statementText = if (isReceivable) {
            "আপনার নিকট হতে ${formatBengaliNumber(payment.paymentAmount)} গ্রহণ করা হলো।"
        } else {
            "আপনাকে ${formatBengaliNumber(payment.paymentAmount)} পরিশোধ করা হলো।"
        }
        canvas.drawText(statementText, 45f, 213f, paint)

        // Table / Breakdown Header
        paint.color = Color.parseColor("#334155")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("হিসাব সংক্রান্ত বিবরণী:", 30f, 270f, paint)

        var currentY = 300f
        val lineSpacing = 32f

        val details = listOf(
            Pair(if (isReceivable) "যার কাছ থেকে টাকা গ্রহণ করেছি:" else "যাকে টাকা প্রদান করেছি:", payment.personName),
            Pair("হিসাবের দিক (Direction):", if (isReceivable) "আমার কাছে পাওনা (RECEIVABLE)" else "আমার দেনা (PAYABLE)"),
            Pair("আগের বকেয়া (Previous Balance):", formatBengaliNumber(payment.previousBalance)),
            Pair("এইবার পরিশোধ (Current Payment):", formatBengaliNumber(payment.paymentAmount)),
            Pair("পরিশোধের পর বাকি (Remaining):", formatBengaliNumber(payment.remainingBalance)),
            Pair("পেমেন্ট মাধ্যম (Payment Method):", payment.paymentMethod),
            Pair("পরিশোধের স্ট্যাটাস (Status):", payment.status),
            Pair("মন্তব্য / নোট (Note):", payment.note.ifBlank { "N/A" })
        )

        paint.textSize = 12f
        details.forEachIndexed { index, pair ->
            // Row Background (Alternate)
            if (index % 2 == 0) {
                val bgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
                canvas.drawRect(30f, currentY - 20f, 565f, currentY + 10f, bgPaint)
            }

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#475569")
            canvas.drawText(pair.first, 40f, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = when {
                pair.first.contains("পরিশোধের পর বাকি") -> Color.parseColor("#0284C7")
                pair.first.contains("এইবার পরিশোধ") -> if (isReceivable) Color.parseColor("#16A34A") else Color.parseColor("#DC2626")
                else -> Color.parseColor("#0F172A")
            }
            canvas.drawText(pair.second, 290f, currentY, paint)

            currentY += lineSpacing
        }

        // Horizontal Separator Line
        paint.color = Color.parseColor("#E2E8F0")
        canvas.drawLine(30f, currentY + 10f, 565f, currentY + 10f, paint)

        // Signature Section
        currentY += 100f
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT

        // Left Signature line
        canvas.drawLine(50f, currentY, 200f, currentY, paint)
        canvas.drawText(if (isReceivable) "গ্রহীতার স্বাক্ষর" else "প্রদানকারীর স্বাক্ষর", 75f, currentY + 18f, paint)

        // Right Signature line
        canvas.drawLine(395f, currentY, 545f, currentY, paint)
        canvas.drawText(if (isReceivable) "প্রদানকারীর স্বাক্ষর" else "গ্রহীতার স্বাক্ষর", 420f, currentY + 18f, paint)

        // Footer Note
        paint.textSize = 10f
        paint.color = Color.parseColor("#94A3B8")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("এটি হিসাব খাতা অ্যাপ দ্বারা স্বয়ংক্রিয়ভাবে প্রস্তুতকৃত একটি ডিজিটাল পরিশোধ রসিদ।", 120f, 810f, paint)

        pdfDocument.finishPage(page)

        return try {
            val file = File(context.cacheDir, "Receipt_${payment.receiptNumber}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun shareReceiptPdf(context: Context, payment: DuePaymentEntity) {
        val file = generateReceiptPdf(context, payment)
        if (file == null || !file.exists()) {
            Toast.makeText(context, "রসিদ ফাইল তৈরি করা যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                if (payment.direction == "RECEIVABLE") "পাওনা পরিশোধ রসিদ - ${payment.personName}" else "দেনা পরিশোধ রসিদ - ${payment.personName}"
            )
            putExtra(
                Intent.EXTRA_TEXT,
                if (payment.direction == "RECEIVABLE") {
                    "আপনার নিকট হতে ${formatBengaliNumber(payment.paymentAmount)} গ্রহণ করা হলো। রসিদ নং: ${payment.receiptNumber}"
                } else {
                    "আপনাকে ${formatBengaliNumber(payment.paymentAmount)} পরিশোধ করা হলো। রসিদ নং: ${payment.receiptNumber}"
                }
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "রসিদ শেয়ার করুন"))
    }

    fun shareReceiptText(context: Context, payment: DuePaymentEntity) {
        val isReceivable = payment.direction == "RECEIVABLE"
        val text = buildString {
            append(if (isReceivable) "🧾 পাওনা পরিশোধ রসিদ\n" else "🧾 দেনা পরিশোধ রসিদ\n")
            append("----------------------------------\n")
            append("রসিদ নং: ${payment.receiptNumber}\n")
            append("তারিখ: ${payment.paymentDate} ${payment.paymentTime}\n\n")
            if (isReceivable) {
                append("আপনার নিকট হতে ${formatBengaliNumber(payment.paymentAmount)} গ্রহণ করা হলো।\n\n")
            } else {
                append("আপনাকে ${formatBengaliNumber(payment.paymentAmount)} পরিশোধ করা হলো।\n\n")
            }
            append("ব্যক্তির নাম: ${payment.personName}\n")
            append("আগের বকেয়া: ${formatBengaliNumber(payment.previousBalance)}\n")
            append("এইবার পরিশোধ: ${formatBengaliNumber(payment.paymentAmount)}\n")
            append("অবশিষ্ট বাকি: ${formatBengaliNumber(payment.remainingBalance)}\n")
            append("পেমেন্ট মাধ্যম: ${payment.paymentMethod}\n")
            append("স্ট্যাটাস: ${payment.status}\n")
            if (payment.note.isNotBlank()) {
                append("নোট: ${payment.note}\n")
            }
            append("----------------------------------\n")
            append("ধন্যবাদ, হিসাব খাতা।")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "রসিদের বিবরণ শেয়ার করুন"))
    }

    fun printReceiptPdf(context: Context, payment: DuePaymentEntity) {
        val pdfFile = generateReceiptPdf(context, payment)
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(context, "প্রিন্ট করার জন্য রসিদ ফাইল তৈরি করা যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "প্রিন্ট সার্ভিস পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val jobName = "Receipt_${payment.receiptNumber}"
        printManager.print(
            jobName,
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build()

                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        FileInputStream(pdfFile).use { input ->
                            FileOutputStream(destination?.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback?.onWriteFailed(e.message)
                    }
                }
            },
            PrintAttributes.Builder().build()
        )
    }
}
