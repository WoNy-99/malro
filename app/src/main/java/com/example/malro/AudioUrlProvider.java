// com/example/malro/AudioUrlProvider.java
package com.example.malro;

import androidx.annotation.NonNull;
import java.io.File;

public interface AudioUrlProvider {
    interface Callback {
        void onSuccess(@NonNull String publicUrl);
        void onError(@NonNull Exception e);
    }
    void upload(@NonNull File file, @NonNull Callback cb);
}
