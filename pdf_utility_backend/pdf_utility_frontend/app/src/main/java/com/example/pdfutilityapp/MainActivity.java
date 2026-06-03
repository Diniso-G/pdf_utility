package com.example.pdfutilityapp;

import android.content.Intent;
//import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.cardview.widget.CardView;

//import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
//import androidx.core.graphics.Insets;
//import androidx.core.view.ViewCompat;
//import androidx.core.view.WindowInsetsCompat;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import okhttp3.*;

import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
//import com.tom_roush.pdfbox.pdmodel.font.PDType1CFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;

public class MainActivity extends AppCompatActivity {

    //private static final int PICK_PDF = 1;
    private static final int PICK_PDF_MULTIPLE_PDF = 2;
    private Uri[] selectedPDFUris = new Uri[0];
    private List<File> splitPages = new ArrayList<>();

    private CardView btnSelect, btnUpload, btnCompressNo, btnMerge, btnSplit ;
    private TextView tvResult;
    private LinearLayout layoutSplitPages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());

        btnSelect = findViewById(R.id.btnSelect);
        //btnUpload = findViewById(R.id.btnUpload);
        btnCompressNo = findViewById(R.id.btnCompressNo);
        btnMerge = findViewById(R.id.btnMerge);
        btnSplit = findViewById(R.id.btnSplit);

        tvResult = findViewById(R.id.tvResult);
        layoutSplitPages = findViewById(R.id.layoutSplitPages);

        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select PDF/s"), PICK_PDF_MULTIPLE_PDF);
        });


        btnCompressNo.setOnClickListener(v-> {
            if (selectedPDFUris.length > 0 ){
                for (Uri uri : selectedPDFUris){
                    try{
                        compressNoPDF(getFileFromUri(uri));
                    } catch (IOException e) {
                        tvResult.setText("Error: " + e.getMessage());
                    }
                }
            } else {
                tvResult.setText("Please select PDF/s first");
            }
        });

        btnMerge.setOnClickListener(v->{
            if (selectedPDFUris.length>0) {
                try {
                    File[] files = new File[selectedPDFUris.length];
                    for (int i = 0; i < selectedPDFUris.length; i++) {
                        files[i] = getFileFromUri(selectedPDFUris[i]);
                    }
                    mergePDF(files);
                } catch (IOException e) {
                    tvResult.setText("Merge error: " + e.getMessage());
                }
            }else{
                tvResult.setText("Please select PDF/s first");
            }
        });

        btnSplit.setOnClickListener(v->{
            if (selectedPDFUris.length > 0){
                for (Uri uri : selectedPDFUris) {
                    try {
                        splitPDF(getFileFromUri(uri));
                    } catch (IOException e) {
                        tvResult.setText("Error: " + e.getMessage());
                    }
                }
            } else {
                tvResult.setText("Please select PDF/s first");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode ==PICK_PDF_MULTIPLE_PDF){
            if(data.getClipData() != null){
                int count = data.getClipData().getItemCount();
                selectedPDFUris = new Uri[count];

                for (int i = 0; i< count; i++){
                    selectedPDFUris[i] = data.getClipData().getItemAt(i).getUri();
                }
            } else if (data.getData() != null){
                selectedPDFUris = new Uri[] {data.getData()};
            }
            tvResult.setText(selectedPDFUris.length + "PDF(s) selected.");

        }
    }


    private File getFileFromUri(Uri uri) throws IOException {
        File tempFile = new File(getCacheDir(), "upload_" + System.currentTimeMillis() + ".pdf");

        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }
    private void openPDF(File pdfFile){
        Uri pdfUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider",
                pdfFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(pdfUri, "application/pdf");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{
            startActivity(intent);
        } catch (Exception e) {
            tvResult.setText("No PDF " + e.getMessage());
        }
    }

    private void compressNoPDF(File pdfFile) {
        File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File output = new File(downloadDir, "numbered_" + pdfFile.getName());
        PDDocument doc = null;

        try{
            doc = PDDocument.load(pdfFile);
        } catch (Exception e) {
            e.printStackTrace();
            tvResult.setText("Failed to load PDF: "+ e.getMessage());
            return;
        }
        try {
            int totalPages = doc.getNumberOfPages();
            for (int i=0; i< totalPages;i++){
                PDPage page = doc.getPage(i);
                try (PDPageContentStream content = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)){
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA_BOLD, 12);
                    content.newLineAtOffset(page.getMediaBox().getWidth() - 50, 20);
                    content.showText(String.valueOf(i + 1));
                    content.endText();
                }
            }
            doc.save(output);
            tvResult.setText("Page numbers added: "+ output.getAbsolutePath());
            openPDF(output);
        } catch (Exception e){
            tvResult.setText("Unexpected Error" + e.getMessage());
        } finally{
            if (doc !=null){
                try {doc.close(); } catch (Exception ignored){}
            }
        }
    }

    private void mergePDF(File[] pdfFiles){
        File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File output = new File(downloadDir, "merged.pdf");
        try{
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(output.getAbsolutePath());
            for (File f : pdfFiles){
                merger.addSource(f);
            }
            merger.mergeDocuments(null);
            tvResult.setText("PDFs merged: "+ output.getAbsolutePath());
            openPDF(output);
        } catch (Exception e){
            tvResult.setText("Error merging PDFs: "+ e.getMessage());
        }
    }

    private void splitPDF(File pdfFile){
        splitPages.clear();
        layoutSplitPages.removeAllViews();
        try (PDDocument doc = PDDocument.load(pdfFile)){
            int totalPages = doc.getNumberOfPages();

            for (int i=0; i<totalPages; i++){
                File output = new File(getCacheDir(), "split_page_" + (i + 1 )+ "_" + System.currentTimeMillis() + ".pdf");
                try (PDDocument single = new PDDocument()){
                    single.importPage(doc.getPage(i));
                    single.save(output);
                }
                splitPages.add(output);
            }
            tvResult.setText("PDF split into "+ totalPages+ " pages in cache");
            for (int i = 0; i< splitPages.size(); i++){
                File pageFile = splitPages.get(i);
                Button btnPage = new Button(this);
                btnPage.setText("Page " + (i +1));
                btnPage.setOnClickListener(v-> openPDF(pageFile));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(8, 0, 8, 0);
                btnPage.setLayoutParams(params);
                layoutSplitPages.addView(btnPage);
            }
        } catch (Exception e ){
            tvResult.setText("Unexpected error " + e.getMessage());
        }
    }
}
