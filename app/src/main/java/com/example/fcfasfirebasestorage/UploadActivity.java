package com.example.fcfasfirebasestorage;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.fcfasfirebasestorage.util.Helper;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnPausedListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class UploadActivity extends AppCompatActivity implements View.OnClickListener{

    private static final String TAG = "UploadActivity";
    private static final int RC_UPLOAD_STREAM = 101;
    private static final int RC_UPLOAD_FILE = 102;
    private ImageView mImageView;
    private StorageReference folderRef, imageRef;
    private TextView mTextView;
    private UploadTask mUploadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        folderRef = storageRef.child("photo");
        imageRef = folderRef.child("firebase.png");

        Log.d(TAG, imageRef.getPath());
        Log.d(TAG, imageRef.getParent().getPath());

        bindWidget();
    }

    private void bindWidget() {
        mImageView = findViewById(R.id.imageview);
        mTextView = findViewById(R.id.textview);
        findViewById(R.id.button_upload_from_memory).setOnClickListener(this);
        findViewById(R.id.button_upload_from_stream).setOnClickListener(this);
        findViewById(R.id.button_upload_from_file).setOnClickListener(this);
        findViewById(R.id.button_upload_resume).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        switch (v.getId()) {
            case R.id.button_upload_from_memory:
                uploadFromDataInMemory();
                break;
            case R.id.button_upload_from_stream:
                startActivityForResult(intent, RC_UPLOAD_STREAM);
                break;
            case R.id.button_upload_from_file:
                startActivityForResult(intent, RC_UPLOAD_FILE);
                break;
            case R.id.button_upload_resume:
                Helper.mProgressDialog.show();
                mUploadTask.resume();
                break;
        }
    }

    private void uploadFromDataInMemory() {
        Helper.showDialog(this);
        // mendapatkkan data dari ImageView sebagai bytes
        mImageView.setDrawingCacheEnabled(true);
        mImageView.buildDrawingCache();
        Bitmap bitmap = mImageView.getDrawingCache();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] data = baos.toByteArray();

        mUploadTask = imageRef.putBytes(data);
        mUploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Helper.dismissDialog();
                mTextView.setText(String.format("Failure: %s", exception.getMessage()));
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                Helper.dismissDialog();
                mTextView.setText(taskSnapshot.getStorage().getDownloadUrl().toString());
            }
        });
    }

    private void uploadFromStream(String path) {
        Helper.showDialog(this);
        InputStream stream = null;
        try {
            stream = new FileInputStream(new File(path));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mUploadTask = imageRef.putStream(stream);
        mUploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Helper.dismissDialog();
                mTextView.setText(String.format("Failure: %s", exception.getMessage()));
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Helper.dismissDialog();
                mTextView.setText(taskSnapshot.getStorage().getDownloadUrl().toString());
            }
        });
    }

    private void uploadFromFile(String path) {
        Uri file = Uri.fromFile(new File(path));
        StorageReference imageRef = folderRef.child(file.getLastPathSegment());
        //StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpg").build();
        //UploadTask uploadTask = imageRef.putFile(file, metadata);
        mUploadTask = imageRef.putFile(file);

        Helper.initProgressDialog(this);
        Helper.mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mUploadTask.cancel();
            }
        });
        Helper.mProgressDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Pause", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mUploadTask.pause();
            }
        });
        Helper.mProgressDialog.show();

        mUploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Helper.dismissProgressDialog();
                mTextView.setText(String.format("Failure: %s", exception.getMessage()));
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Helper.dismissProgressDialog();
                findViewById(R.id.button_upload_resume).setVisibility(View.GONE);
                mTextView.setText(taskSnapshot.getStorage().getDownloadUrl().toString());
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                int progress = (int) ((100 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount());
                Helper.setProgress(progress);
            }
        }).addOnPausedListener(new OnPausedListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onPaused(UploadTask.TaskSnapshot taskSnapshot) {
                findViewById(R.id.button_upload_resume).setVisibility(View.VISIBLE);
                mTextView.setText(R.string.upload_paused);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Helper.dismissProgressDialog();
        Helper.dismissDialog();
    }
}
