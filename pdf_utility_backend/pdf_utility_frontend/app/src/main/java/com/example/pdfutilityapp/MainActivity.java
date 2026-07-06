package com.example.pdfutilityapp;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
//import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
//import com.tom_roush.pdfbox.pdmodel.font.PDType1CFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_WORD_DOC = 3;
    private static final String BASE_URL = "http://localhost:3000"; //Use the IPv4 Address of the pc that uses same as device, "http://10.0.2.2:3000"only works on emulator
    private static final int PICK_PDF_MULTIPLE_PDF = 2;

    private Uri[] selectedPDFUris = new Uri[0];
    private Uri selectedWordUri;
    private List<File> splitPages = new ArrayList<>();

    private CardView btnSelect, btnCompressNo, btnMerge, btnSplit, btnSelectWord, btnConvertWord;
    private TextView tvResult;
    private LinearLayout layoutSplitPages;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

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
        btnSelectWord = findViewById(R.id.btnSelectWord);
        btnConvertWord = findViewById(R.id.btnConvertWord);

        tvResult = findViewById(R.id.tvResult);
        layoutSplitPages = findViewById(R.id.layoutSplitPages);

        cleanupCacheDir();

        btnSelect.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select PDF/s"), PICK_PDF_MULTIPLE_PDF);
        });


        btnCompressNo.setOnClickListener(v-> {
            if (selectedPDFUris.length == 0 ){
                tvResult.setText("Please select PDF/s first");
                return;
            }
            runWithProgress("Adding page numbers...", () -> { //update to use runWithProgress
                for (Uri uri : selectedPDFUris){
                    try{
                        compressNoPDF(getFileFromUri(uri));
                    } catch (IOException e) {
                        tvResult.setText("Error: " + e.getMessage());
                    }
                }
            });
        });

        btnMerge.setOnClickListener(v->{
            if (selectedPDFUris.length ==0) {
                tvResult.setText("Please select PDF/s first");
                return;
            }
            runWithProgress("Merging PDFs...", () -> {
                try {
                    File[] files = new File[selectedPDFUris.length];
                    for (int i = 0; i < selectedPDFUris.length; i++) {
                        files[i] = getFileFromUri(selectedPDFUris[i]);
                    }
                    mergePDF(files);
                } catch (IOException e) {
                    tvResult.setText("Merge error: " + e.getMessage());
                }
            });
        });

        btnSplit.setOnClickListener(v->{
            if (selectedPDFUris.length == 0){
                tvResult.setText("Please select PDF/s first");
                return;
            }
            runWithProgress("Splitting PDF...", () -> {
                for (Uri uri : selectedPDFUris) {
                    try {
                        splitPDF(getFileFromUri(uri));
                    } catch (IOException e) {
                        tvResult.setText("Error: " + e.getMessage());
                    }
                }
            });
        });

        //NEW FEATURE FOR WORD CONVERTION
        btnSelectWord.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select Word document"), PICK_WORD_DOC);
        });

        btnConvertWord.setOnClickListener(v->{
            if (selectedWordUri == null){
                tvResult.setText("Please select a Word document first");
                return;
            }
            runWithProgress("Converting Word document to PDF...", this::convertWordToPDF);
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
        else if (requestCode == PICK_WORD_DOC) {
            if (data.getData() != null) {
                selectedWordUri= data.getData();
                tvResult.setText("Word document selected.");
            }
        }
    }

    //THE NEW FEATURE FUNCTION HELPS IN THREADING FOR LARGE PDFS
    private interface Task {
        void run();
    }

    private void runWithProgress(String message, Task task) {
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(message);
        dialog.setCancelable(false);
        dialog.show();

        executor.execute(() -> {
            try {
                task.run();
            }
            finally {
                runOnUiThread(dialog::dismiss);
            }
        });
    }

    private void postResult(String message) {
        runOnUiThread(() -> tvResult.setText(message));
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
        runOnUiThread(() -> {
            try{
                startActivity(intent);
            } catch (Exception e) {
                tvResult.setText("No PDF viewer app found: " + e.getMessage());
            }
        });
    }

    private Uri savedPdfDownloads(File sourceFile, String displayName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri itemUri = getContentResolver().insert(collection, values);
            if (itemUri == null) throw new IOException("Could not create MediaStore entry");

            try (OutputStream out = getContentResolver().openOutputStream(itemUri);
                 InputStream in = new FileInputStream(sourceFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return itemUri;
        }
        else {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File output = new File(downloadDir, displayName);

            try (InputStream in = new FileInputStream(sourceFile);
                 OutputStream out = new FileOutputStream(output)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return Uri.fromFile(output);
        }
    }

    //Delete the leftover files to clear space
    private void cleanupCacheDir() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles((dir, name) -> name.startsWith("upload_") || name.startsWith("split_page_") || name.startsWith("word_"));
        if (files != null) {
            for (File fl : files) {
                fl.delete();
            }
        }
        splitPages.clear();
    }
    private void compressNoPDF(File pdfFile) {
        //File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File output = new File(getCacheDir(), "numbered_" + pdfFile.getName());

        try (PDDocument doc = PDDocument.load(pdfFile)){
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
            Uri savedUri = savedPdfDownloads(output, output.getName());
            postResult("Page numbers added: "+ savedUri);
            openSavedPDF(savedUri, output);
        } catch (Exception e){
            postResult("Unexpected Error" + e.getMessage());
        }

    }

    private void mergePDF(File[] pdfFiles){
       // File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File output = new File(getCacheDir(), "merged_" + System.currentTimeMillis() + ".pdf");
        try{
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(output.getAbsolutePath());
            for (File fl : pdfFiles){
                merger.addSource(fl);
            }
            merger.mergeDocuments(null);
            Uri savedUri = savedPdfDownloads(output, "merged.pdf");
            postResult("PDFs merged: "+ savedUri);
            openSavedPDF(savedUri, output);
        } catch (Exception e){
            postResult("Error merging PDFs: "+ e.getMessage());
        }
    }

    private void splitPDF(File pdfFile){
        List<File> pages = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdfFile)){
            int totalPages = doc.getNumberOfPages();

            for (int i=0; i<totalPages; i++){
                File output = new File(getCacheDir(), "split_page_" + (i + 1 ) + "_" + System.currentTimeMillis() + ".pdf");
                try (PDDocument single = new PDDocument()){
                    single.importPage(doc.getPage(i));
                    single.save(output);
                }
                pages.add(output);
            }
            postResult("PDF split into "+ totalPages+ " pages in cache");
            runOnUiThread(()-> {
                splitPages.clear();
                splitPages.addAll(pages);
                layoutSplitPages.removeAllViews();
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
            });
        }
        catch (Exception e ){
            postResult("Unexpected error " + e.getMessage());
        }
    }

    private void openSavedPDF(Uri savedUri, File cacheCopy) {
        openPDF(cacheCopy);
    }

    private void convertWordToPDF(){
        try {
            File wordFile = new File(getCacheDir(), "word_" + System.currentTimeMillis() + ".docx");
            try (InputStream in = getContentResolver().openInputStream(selectedWordUri);
                OutputStream out = new FileOutputStream(wordFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            String fileID = uploadWordFile(wordFile);
            if (fileID == null) return;

            String resultID = requestConversion(fileID);
            if (resultID == null) return;

            File pdfOutput = downloadResult(resultID);
            if (pdfOutput == null) return;

            Uri savedUri = savedPdfDownloads(pdfOutput, pdfOutput.getName());
            postResult("Word document converted to PDF: " + savedUri);
            openSavedPDF(savedUri, pdfOutput);
        }
        catch (Exception err) {
            postResult("Conversion error: " + err.getMessage());
        }
    }

    private String uploadWordFile(File wordFile) throws IOException, org.json.JSONException {
        RequestBody fileBody = RequestBody.create(wordFile, MediaType.parse("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        RequestBody uploadBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", wordFile.getName(), fileBody).build();
        Request request = new Request.Builder().url(BASE_URL + "/api/pdf/upload").post(uploadBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                postResult("Upload failed- is the server running? (code " + response.code() + ")");
                return null;
            }
            return new JSONObject(response.body().string()).getString("fileID");
        }
        catch (IOException err) {
            postResult("Could not reach the server. Check its running and BASE_URI is correct: "+ err.getMessage());
            return null;
        }
    }

    private String requestConversion(String fileID) throws org.json.JSONException {
        JSONObject payload = new JSONObject();
        payload.put("fileID", fileID);
        payload.put("operation", "docxToPDF");

        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder().url(BASE_URL + "/api/pdf/process").post(body).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                postResult("Processing failed (code " + response.code() + ")");
                return null;
            }
            return new JSONObject(response.body().string()).getString("resultID");
        } catch (Exception err) {
            postResult("Processing request failed: " + err.getMessage());
            return null;
        }
    }

    private File downloadResult(String resultID) {
        Request request = new Request.Builder().url(BASE_URL + "/api/pdf/open/" + resultID).get().build();
        File pdfOutput = new File(getCacheDir(), "converted_" + System.currentTimeMillis() + ".pdf");

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                postResult("Download failed (code " + response.code() + ")");
                return null;
            }
            try ( InputStream body = response.body().byteStream();
                OutputStream out = new FileOutputStream(pdfOutput)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = body.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return pdfOutput;
        } catch (Exception err) {
            postResult("Download failed: " + err.getMessage());
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
