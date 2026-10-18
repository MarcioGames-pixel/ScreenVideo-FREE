package com.screenvideo.free;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/* Legacy video-only recorder. Root is required for system-screen capture. */
public class MainActivity extends Activity {
    private TextView status;
    private TextView details;
    private Button recordButton;
    private volatile boolean recording = false;
    private Thread captureThread;
    private File outputDir, sessionDir, outputVideo, ffmpegBin, pidFile;
    private int width = 480, height = 800, sdk = 3, frameCount = 0;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        try { sdk = Integer.parseInt(Build.VERSION.SDK); } catch (Exception ignored) {}
        setContentView(R.layout.activity_main);
        status = (TextView)findViewById(R.id.status);
        details = (TextView)findViewById(R.id.preview);
        recordButton = (Button)findViewById(R.id.record);
        try {
            width = getWindowManager().getDefaultDisplay().getWidth();
            height = getWindowManager().getDefaultDisplay().getHeight();
        } catch (Exception ignored) {}
        findViewById(R.id.test).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { testRoot(); }
        });
        findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSettings(); }
        });
        recordButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (recording) stopRecording(); else startRecording(); }
        });
        initPaths();
        extractFFmpeg();
        status.setText("READY · ANDROID " + sdk);
        details.setText(width + " × " + height + "\nVideo only · root access required");
    }

    private void initPaths() {
        outputDir = new File(Environment.getExternalStorageDirectory(), "ScreenVideo");
        if (!outputDir.exists()) outputDir.mkdirs();
        pidFile = new File(outputDir, ".screenvideo.pid");
    }

    private void extractFFmpeg() {
        try {
            ffmpegBin = new File(getFilesDir(), "ffmpeg");
            if (!ffmpegBin.exists() || ffmpegBin.length() < 100000) {
                InputStream in = getAssets().open("ffmpeg");
                FileOutputStream out = new FileOutputStream(ffmpegBin);
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                out.close(); in.close();
            }
            root("chmod 755 " + ffmpegBin.getAbsolutePath());
        } catch (Exception e) { setStatus("FFmpeg setup failed: " + e.getMessage()); }
    }

    private void testRoot() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    final String result = root("id; ls -l /dev/graphics/fb0; command -v screenrecord");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            status.setText("ROOT CHECK FINISHED");
                            details.setText(result.length() == 0 ? "Command finished." : result);
                        }
                    });
                } catch (final Exception e) { setStatus("ROOT ERROR: " + e.getMessage()); }
            }
        }).start();
    }

    private void startRecording() {
        if (recording) return;
        initPaths();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            status.setText("ERROR: cannot create /sdcard/ScreenVideo"); return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        if (sdk >= 19) {
            outputVideo = new File(outputDir, "ScreenVideo_" + stamp + ".mp4");
            startKitKatRecording();
        } else {
            sessionDir = new File(outputDir, "frames_" + stamp);
            if (!sessionDir.mkdirs()) { status.setText("ERROR: cannot create frame folder"); return; }
            frameCount = 0; recording = true;
            recordButton.setText("STOP & SAVE");
            status.setText("RECORDING · ROOT FRAMEBUFFER");
            details.setText("Capturing " + width + " × " + height + "\nKeep the VM visible");
            captureThread = new Thread(new Runnable() { public void run() { captureFrames(); } }, "ScreenVideoFrames");
            captureThread.start();
        }
    }

    private void startKitKatRecording() {
        try {
            if (outputVideo.exists()) outputVideo.delete();
            String cmd = "screenrecord --bit-rate 4000000 " + outputVideo.getAbsolutePath()
                    + " >/dev/null 2>&1 & echo $! > " + pidFile.getAbsolutePath();
            root(cmd);
            Thread.sleep(500);
            if (!pidFile.exists() || pidFile.length() == 0) {
                status.setText("ERROR: screenrecord did not start"); return;
            }
            recording = true; recordButton.setText("STOP & SAVE");
            status.setText("RECORDING · ANDROID 4.4");
            details.setText("Video only · MP4/H.264\n/sdcard/ScreenVideo");
        } catch (Exception e) { recording = false; status.setText("START ERROR: " + e.getMessage()); }
    }

    private void captureFrames() {
        try {
            long next = System.currentTimeMillis();
            while (recording) {
                File frame = new File(sessionDir, String.format(Locale.US, "frame_%06d.raw", frameCount));
                long bytes = (long)width * (long)height * 2L;
                root("dd if=/dev/graphics/fb0 of=" + frame.getAbsolutePath()
                        + " bs=" + bytes + " count=1 2>/dev/null");
                if (frame.exists() && frame.length() >= bytes) frameCount++;
                else {
                    frame.delete();
                    if (frameCount == 0) throw new Exception("Framebuffer read failed; check root and device support.");
                }
                next += 200;
                long wait = next - System.currentTimeMillis();
                if (wait > 0) Thread.sleep(wait);
            }
        } catch (final Exception e) {
            recording = false;
            runOnUiThread(new Runnable() { public void run() { status.setText("CAPTURE ERROR: " + e.getMessage()); } });
        }
        if (frameCount > 0) convertFrames(); else finishFailure("No frames captured; no video was saved.");
    }

    private void stopRecording() {
        if (!recording) return;
        if (sdk >= 19) {
            recording = false; status.setText("FINALIZING VIDEO…");
            new Thread(new Runnable() {
                public void run() {
                    try {
                        String pid = readSmallFile(pidFile);
                        if (pid.length() > 0) root("kill -2 " + pid.trim() + " 2>/dev/null");
                        Thread.sleep(1200); validateVideo(outputVideo, 0);
                    } catch (Exception e) { finishFailure("Could not stop screenrecord: " + e.getMessage()); }
                }
            }).start();
        } else {
            recording = false; status.setText("PROCESSING FRAMES…"); recordButton.setEnabled(false);
        }
    }

    private void convertFrames() {
        try {
            outputVideo = new File(outputDir, "ScreenVideo_" + System.currentTimeMillis() + ".mp4");
            String cmd = ffmpegBin.getAbsolutePath() + " -y -f rawvideo -pixel_format rgb565le"
                    + " -video_size " + width + "x" + height
                    + " -framerate 5 -i " + sessionDir.getAbsolutePath() + "/frame_%06d.raw"
                    + " -an -c:v libx264 -pix_fmt yuv420p " + outputVideo.getAbsolutePath();
            int code = root(cmd);
            deleteContents(sessionDir); sessionDir.delete();
            validateVideo(outputVideo, code);
        } catch (Exception e) { finishFailure("MP4 conversion failed: " + e.getMessage()); }
    }

    private void validateVideo(final File file, final int exitCode) {
        final boolean valid = exitCode == 0 && file != null && file.exists() && file.length() > 1024;
        if (!valid && file != null && file.exists()) file.delete();
        runOnUiThread(new Runnable() {
            public void run() {
                recording = false; recordButton.setEnabled(true); recordButton.setText("START RECORDING");
                if (valid) {
                    status.setText("VIDEO SAVED · " + file.length() + " bytes");
                    details.setText(file.getAbsolutePath() + "\nVideo only · " + frameCount + " frames");
                    Toast.makeText(MainActivity.this, "Video saved", Toast.LENGTH_LONG).show();
                } else {
                    status.setText("ERROR · NO VALID VIDEO");
                    details.setText("Invalid or empty output was deleted. Check root access and try again.");
                }
            }
        });
    }

    private void finishFailure(final String message) {
        runOnUiThread(new Runnable() {
            public void run() {
                recording = false;
                if (recordButton != null) { recordButton.setEnabled(true); recordButton.setText("START RECORDING"); }
                if (status != null) status.setText("RECORDING FAILED");
                if (details != null) details.setText(message);
            }
        });
    }

    private String root(String command) throws Exception {
        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes(command + "\nexit\n"); os.flush(); os.close();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = p.getInputStream(); byte[] b = new byte[1024]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        p.waitFor();
        return new String(out.toByteArray());
    }

    private String readSmallFile(File file) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            byte[] b = new byte[(int)Math.min(file.length(), 128)];
            int n = in.read(b); in.close(); return n > 0 ? new String(b, 0, n) : "";
        } catch (Exception e) { return ""; }
    }

    private void deleteContents(File dir) {
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files != null) for (int i = 0; i < files.length; i++) files[i].delete();
    }

    private void showSettings() {
        new AlertDialog.Builder(this).setTitle("ScreenVideo FREE · Legacy")
                .setMessage("Android 4.4: root screenrecord command.\n"
                        + "Android 1.5–4.3: root framebuffer + bundled FFmpeg.\n"
                        + "Video only; no system audio or microphone.\n"
                        + "Framebuffer formats vary by device.\nOutput: /sdcard/ScreenVideo/")
                .setPositiveButton("OK", null).show();
    }

    private void setStatus(final String value) {
        runOnUiThread(new Runnable() { public void run() { if (status != null) status.setText(value); } });
    }

    protected void onDestroy() { recording = false; super.onDestroy(); }
}