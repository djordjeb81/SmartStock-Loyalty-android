package com.smartstock.loyalty

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartstock.loyalty.databinding.ActivityPdfViewerBinding
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_TITLE = "extra_title"
    }

    private lateinit var b: ActivityPdfViewerBinding

    private var pageCount = 0
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(b.root)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        b.toolbar.title = if (title.isBlank()) "Uputstvo" else title
        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnPrev.setOnClickListener {
            if (currentPage > 0) {
                b.pdfView.jumpTo(currentPage - 1, true)
            }
        }

        b.btnNext.setOnClickListener {
            if (currentPage < pageCount - 1) {
                b.pdfView.jumpTo(currentPage + 1, true)
            }
        }

        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(this, "PDF fajl nije pronađen.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        openPdf(file)
    }

    private fun openPdf(file: File) {
        b.progressBar.visibility = View.VISIBLE
        b.pdfView.visibility = View.INVISIBLE
        b.bottomBar.visibility = View.INVISIBLE

        b.pdfView.fromFile(file)
            .defaultPage(0)
            .enableSwipe(true)
            .swipeHorizontal(true)
            .enableDoubletap(true)
            .pageSnap(true)
            .pageFling(true)
            .spacing(8)
            .autoSpacing(true)
            .pageFitPolicy(com.github.barteksc.pdfviewer.util.FitPolicy.WIDTH)
            .onLoad { count ->
                pageCount = count
                currentPage = 0
                b.progressBar.visibility = View.GONE
                b.pdfView.visibility = View.VISIBLE
                b.bottomBar.visibility = View.VISIBLE
                updatePagerUi()
            }
            .onPageChange { page, count ->
                currentPage = page
                pageCount = count
                updatePagerUi()
            }
            .onError {
                b.progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Greška pri otvaranju PDF-a.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            .onPageError { _, _ ->
                Toast.makeText(
                    this,
                    "Greška pri učitavanju stranice.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .load()
    }

    private fun updatePagerUi() {
        val shownPage = currentPage + 1
        b.tvPageIndicator.text = "$shownPage / $pageCount"

        b.btnPrev.isEnabled = currentPage > 0
        b.btnNext.isEnabled = currentPage < pageCount - 1

        b.btnPrev.alpha = if (b.btnPrev.isEnabled) 1f else 0.4f
        b.btnNext.alpha = if (b.btnNext.isEnabled) 1f else 0.4f
    }
}