package com.example.myfirstapplication;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;

public class MLkitActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION = 3000;
    private Uri imageFileUri;
    private ImageView imageView;
    private TextView textViewOutput;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mlkit);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        imageView = findViewById(R.id.imageViewMLKit);
        textViewOutput = findViewById(R.id.textViewMLKit);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String result = extras.getString("result");
            textViewOutput.setText(result);
            Uri uri = Uri.parse(extras.getString("uri"));
            imageView.setImageURI(uri);
        }
    }

    private boolean checkPermission() {
        String permission = android.Manifest.permission.CAMERA;
        boolean grantCamera = ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED;
        if (!grantCamera) {
            ActivityCompat.requestPermissions(this, new String[]{permission},
                    REQUEST_PERMISSION);
        }
        return grantCamera;
    }

    public void openCamera(View view) {
        if (checkPermission() == false)
            return;
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        imageFileUri =
                getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new
                        ContentValues());
        takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageFileUri);
        activityResultLauncher.launch(takePhotoIntent);
    }
    public void loadImage(View view) {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        activityResultLauncher.launch(galleryIntent);

    }

    public void openListView(View view) {
// Get image from the current imageFileUri
        Bitmap bitmap = getBitmapFromUri(imageFileUri);
// Create a unique filename from the current date time
        String currentDateTime = LocalDateTime.now().toString();
        String imageFilename = currentDateTime.replaceAll("\\D+", "");
// Save the image to gallery with the unique filename
        saveImageToGallery(bitmap, imageFilename, MLkitActivity.this);
// open List View activity
        Intent intent = new Intent(MLkitActivity.this, ListViewActivity.class);
        intent.putExtra("reader", "Barcode Reader");
        intent.putExtra("result", textViewOutput.getText().toString());
        intent.putExtra("filename", imageFilename);
        startActivity(intent);
    }


    private Bitmap getBitmapFromUri(Uri uri) {
        try {
            ImageDecoder.Source source =
                    ImageDecoder.createSource(getContentResolver(), uri);
            Bitmap bitmap = ImageDecoder.decodeBitmap(source);
            return bitmap;
        } catch (IOException e) {
            Log.e("URI_TO_BITMAP", "Failed to load image", e);
            return null;
        }
    }
    private void saveImageToGallery(Bitmap bitmap, String fileName, Context
            context) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES);
        Uri imageUri =
                context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try {
            OutputStream outputStream =
                    context.getContentResolver().openOutputStream(imageUri);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
            Log.d("SAVE_GALLERY", "Image saved to gallery: " +
                    imageUri.toString());
        } catch (IOException e) {
            Log.e("SAVE_GALLERY", "Error saving image", e);
        }
    }



    ActivityResultLauncher<Intent> activityResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == RESULT_OK) {
                                if (result.getData() != null &&
                                        result.getData().getData() != null)
                                    imageFileUri = result.getData().getData();
                                imageView.setImageURI(imageFileUri);
// Add code for ML Kit below this line
                                textViewOutput.setText("");
                                InputImage image = null;
                                try {
                                    image = InputImage.fromFilePath(getBaseContext(), imageFileUri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (image != null) {
                                    processImageFromBarcodeReader(image);
                                }
                            }
                        }
                    });

    public void processImageFromBarcodeReader (InputImage image) {
        BarcodeScannerOptions options =
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build();
        BarcodeScanner scanner = BarcodeScanning.getClient(options);
        Task<List<Barcode>> result = scanner.process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Barcode>>() {
                    @Override
                    public void onSuccess(List<Barcode> barcodes) {
                        textViewOutput.append(Html.fromHtml("<font color ='navy'><b>Detected_barcode:</b></font><br>", Html.FROM_HTML_MODE_LEGACY));
                        String result = "";
                        for (Barcode barcode : barcodes) {
                            result = barcode.getRawValue();
                            textViewOutput.append(result + "\n");
                        }
                        if (result.length() < 2) {
                            textViewOutput.append(" Barcode not found.\n");
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        textViewOutput.setText("Failed");
                    }
                });
    }





}