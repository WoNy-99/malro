package com.example.malro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.content.ContextCompat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioRecorderHelper {
    private static final String TAG = "AudioRecorderHelper";

    // App policy
    private static final int SAMPLE_RATE    = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT;

    private final Context appCtx;
    private AudioRecord recorder;
    private Thread writeThread;
    private volatile boolean isRecording = false;

    private String outputFilePath;

    public AudioRecorderHelper(Context context) {
        this.appCtx = context.getApplicationContext();
    }

    /** 외부에서 권한 보유 여부 손쉽게 확인 */
    public boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 녹음 시작 (권한 보유 상태에서만 호출) */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @SuppressLint("MissingPermission") // 직접 체크 + 호출 측에서 권장
    public String startRecording(String fileNameWithoutExt) {
        if (!hasRecordAudioPermission()) {
            throw new SecurityException("RECORD_AUDIO permission not granted");
        }

        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            throw new IllegalStateException("Invalid min buffer size");
        }
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE); // 1초 이상 여유

        // 저장 경로: /Android/data/<pkg>/files/Music/MalroRecordings
        File dir = new File(appCtx.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "MalroRecordings");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory");
        }
        File out = new File(dir, fileNameWithoutExt + ".wav");
        outputFilePath = out.getAbsolutePath();

        // Builder API (API 23+)
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build();

        recorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build();

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioRecord initialization failed");
        }

        recorder.startRecording();
        isRecording = true;

        writeThread = new Thread(() -> writePcmLoop(bufferSize), "MalroAudioWriter");
        writeThread.start();

        return outputFilePath;
    }

    /** 녹음 중지 및 WAV 헤더 갱신 */
    public void stopRecording() {
        isRecording = false;

        if (recorder != null) {
            try { recorder.stop(); } catch (IllegalStateException ignored) {}
            recorder.release();
            recorder = null;
        }

        if (writeThread != null) {
            try { writeThread.join(); } catch (InterruptedException ignored) {}
            writeThread = null;
        }

        if (outputFilePath != null) {
            try { updateWavHeader(outputFilePath); }
            catch (IOException e) { Log.e(TAG, "WAV header update failed", e); }
        }
    }

    public boolean isRecording() { return isRecording; }

    /** 마지막 저장 파일 경로 조회 */
    public String getOutputFilePath() { return outputFilePath; }

    /* ================== 내부 구현 ================== */

    private void writePcmLoop(int bufferSize) {
        byte[] buf = new byte[bufferSize];

        try (FileOutputStream fos = new FileOutputStream(outputFilePath);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {

            // placeholder header (sizes=0) — stop 시 갱신
            writeWavHeader(bos, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

            while (isRecording && recorder != null) {
                int read = recorder.read(buf, 0, buf.length);
                if (read > 0) bos.write(buf, 0, read);
            }
            bos.flush();

        } catch (IOException e) {
            Log.e(TAG, "Error writing audio", e);
        }
    }

    /** WAV RIFF header (little-endian) */
    private void writeWavHeader(OutputStream out, int sampleRate, int channelConfig, int audioFormat) throws IOException {
        int channels = (channelConfig == AudioFormat.CHANNEL_IN_MONO) ? 1 : 2;
        int bitsPerSample = (audioFormat == AudioFormat.ENCODING_PCM_16BIT) ? 16 : 8;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        out.write(new byte[]{'R','I','F','F'}); writeLEInt(out, 0);                 // ChunkSize placeholder
        out.write(new byte[]{'W','A','V','E'});
        out.write(new byte[]{'f','m','t',' '}); writeLEInt(out, 16);                // Subchunk1Size
        writeLEShort(out, (short)1);                                                // PCM
        writeLEShort(out, (short)channels);
        writeLEInt(out, sampleRate);
        writeLEInt(out, byteRate);
        writeLEShort(out, (short)blockAlign);
        writeLEShort(out, (short)bitsPerSample);
        out.write(new byte[]{'d','a','t','a'}); writeLEInt(out, 0);                 // Subchunk2Size placeholder
    }

    private void updateWavHeader(String path) throws IOException {
        File f = new File(path);
        if (!f.exists() || f.length() < 44) return;

        long len = f.length();
        int chunkSize = (int)(len - 8);
        int dataSize  = (int)(len - 44);

        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(4);  writeLEInt(raf, chunkSize);
            raf.seek(40); writeLEInt(raf, dataSize);
        }
    }

    private void writeLEInt(OutputStream out, int v) throws IOException {
        out.write(new byte[]{ (byte)v, (byte)(v>>8), (byte)(v>>16), (byte)(v>>24) });
    }
    private void writeLEShort(OutputStream out, short v) throws IOException {
        out.write(new byte[]{ (byte)v, (byte)(v>>8) });
    }
    private void writeLEInt(RandomAccessFile raf, int v) throws IOException {
        byte[] le = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
        raf.write(le);
    }
}
