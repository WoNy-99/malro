// com/example/malro/FirebaseAudioUrlProvider.java
package com.example.malro;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;

public class FirebaseAudioUrlProvider implements AudioUrlProvider {

    private final StorageReference rootRef;

    public FirebaseAudioUrlProvider(@NonNull StorageReference rootRef) {
        this.rootRef = rootRef;
    }

    @Override
    public void upload(@NonNull File file, @NonNull Callback cb) {
        try {
            StorageReference ref = rootRef.child("pronunciation/" + file.getName());
            UploadTask task = ref.putFile(Uri.fromFile(file));
            task.addOnFailureListener(cb::onError)
                    .addOnSuccessListener(snapshot ->
                            ref.getDownloadUrl().addOnSuccessListener(uri ->
                                            cb.onSuccess(uri.toString()))
                                    .addOnFailureListener(cb::onError));
        } catch (Exception e) {
            cb.onError(e);
        }
    }
}
