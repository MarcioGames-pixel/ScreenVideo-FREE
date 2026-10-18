package com.screenvideo.free;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4102;

    private TextView status;
    private TextView preview;
    private Button record;
    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private boolean recording;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.status);
        preview = (TextView) findViewById(R.id.preview);
        record = (Button) findViewById(R.id.record);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        findViewById(R.id.test).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkDisplay(); }
        });
        findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        record.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) stopRecording();
                else requestRecordingPermission();
            }
        });
        checkDisplay();
    }

    private void checkDisplay() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels & ~1;
        screenHeight = metrics.heightPixels & ~1;
        screenDensity = metrics.densityDpi;
        status.setText("READY TO RECORD");
        preview.setText("DISPLAY\n" + screenWidth + " × " + screenHeight
                + "\nAndroid screen capture · VM window included");
    }

    private void requestRecordingPermission() {
        if (projectionManager == null) {
            status.setText("Screen capture is unavailable on this Android version.");
            return;
        }
        try {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE);
        } catch (Exception e) {
            showError("Could not request screen-capture permission", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            status.setText("Permission cancelled — nothing was recorded.");
            return;
        }
        try {
            projection = projectionManager.getMediaProjection(resultCode, data);
            if (projection == null) throw new IllegalStateException("Screen capture permission was not granted");
            startRecording();
        } catch (Exception e) {
            releaseCapture();
            showError("Unable to start recording", e);
        }
    }

    private void startRecording() throws Exception {
        checkDisplay();
        File folder = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if (folder == null) folder = new File(getFilesDir(), "recordings");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create recordings folder");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        outputFile = new File(folder, "ScreenVideo_" + stamp + ".mp4");
        if (outputFile.exists() && !outputFile.delete()) {
            throw new IllegalStateException("Cannot prepare output file");
        }

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(6000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(screenWidth, screenHeight);
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        mediaRecorder.prepare();

        virtualDisplay = projection.createVirtualDisplay(
                "ScreenVideo-FREE",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);
        if (virtualDisplay == null) throw new IllegalStateException("Could not create virtual display");

        mediaRecorder.start();
        recording = true;
        record.setText("STOP & SAVE");
        status.setText("RECORDING · SCREEN + VMs");
        preview.setText("Saving MP4 to app Movies folder\n" + outputFile.getName()
                + "\n30 FPS · H.264");
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        record.setEnabled(false);
        status.setText("FINALIZING VIDEO…");
        boolean stopped = false;
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                stopped = true;
            }
        } catch (RuntimeException e) {
            // MediaRecorder.stop() throws when no valid video frames were encoded.
        } finally {
            releaseCapture();
            record.setEnabled(true);
            record.setText("START RECORDING");
        }

        if (stopped && outputFile != null && outputFile.isFile() && outputFile.length() > 1024) {
            status.setText("VIDEO SAVED · " + readableSize(outputFile.length()));
            preview.setText(outputFile.getAbsolutePath()
                    + "\n\nThe file was checked and is not empty.");
            Toast.makeText(this, "Recording saved", Toast.LENGTH_LONG).show();
        } else {
            if (outputFile != null && outputFile.exists()) outputFile.delete();
            status.setText("RECORDING TOO SHORT — NO EMPTY VIDEO KEPT");
            preview.setText("No valid video was created. Start recording, wait a moment, then stop.");
            Toast.makeText(this, "No valid frames were captured; empty file removed.", Toast.LENGTH_LONG).show();
        }
    }

    private void releaseCapture() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (mediaRecorder != null) {
            try { mediaRecorder.reset(); } catch (Exception ignored) {}
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) {}
            projection = null;
        }
        recording = false;
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void showError(String heading, Exception e) {
        String message = e.getMessage();
        status.setText("ERROR · " + heading);
        preview.setText(message == null ? "Unknown recording error" : message);
        new AlertDialog.Builder(this).setTitle(heading)
                .setMessage(message == null ? e.getClass().getSimpleName() : message)
                .setPositiveButton("OK", null).show();
    }

    private void showSettings() {
        new AlertDialog.Builder(this)
                .setTitle("ScreenVideo FREE")
                .setMessage("Capture: Android MediaProjection (screen mirror)\n"
                        + "Video: H.264 in MP4 · 30 FPS\n"
                        + "Audio: not captured in this build\n"
                        + "Output: app-specific Movies folder\n\n"
                        + "Keep the VM visible on the device screen while recording.")
                .setPositiveButton("OK", null).show();
    }

    @Override
    protected void onDestroy() {
        if (recording) {
            // Stop safely; if no frames exist, stopRecording removes the invalid output.
            stopRecording();
        } else {
            releaseCapture();
        }
        super.onDestroy();
    }
}