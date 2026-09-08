package com.screenvideo.free;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int FPS = 5; 

    private TextView status;
    private TextView preview;
    private Button record;

    private volatile boolean recording;
    private Thread recordThread;
    private File currentVideoFolder;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            setContentView(R.layout.activity_main);

            ViewGroup root = (ViewGroup) findViewById(android.R.id.content);
            List<View> allViews = new ArrayList<View>();
            findAllViews(root, allViews);

            List<TextView> textViews = new ArrayList<TextView>();
            List<Button> buttons = new ArrayList<Button>();

            for (View v : allViews) {
                if (v instanceof Button) {
                    buttons.add((Button) v);
                } else if (v instanceof TextView) {
                    textViews.add((TextView) v);
                }
            }

            if (textViews.size() >= 3) {
                status = textViews.get(2);
                preview = textViews.get(3);
            }

            if (buttons.size() >= 3) {
                Button test = buttons.get(0);
                Button settings = buttons.get(1);
                record = buttons.get(2);

                test.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        testFramebuffer();
                    }
                });

                settings.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showSettings();
                    }
                });

                record.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (recording) {
                            stopRecording();
                        } else {
                            startRecording();
                        }
                    }
                });
            }

            setStatus("Ready to record (Root OK)");

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void findAllViews(ViewGroup parent, List<View> views) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            views.add(child);
            if (child instanceof ViewGroup) {
                findAllViews((ViewGroup) child, views);
            }
        }
    }

    private void testFramebuffer() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    File out = outputDir();
                    final File testFile = new File(out, "frame_test.raw");

                    Process p = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    
                    // CORREÇÃO: Chama explicitamente o sh -c para evitar o erro de Broken Pipe
                    os.writeBytes("sh -c '/system/bin/screencap > " + testFile.getAbsolutePath() + "'\n");
                    os.writeBytes("exit\n");
                    os.flush();
                    p.waitFor();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (testFile.exists() && testFile.length() > 0) {
                                if (status != null) status.setText("TEST OK - " + testFile.length() + " bytes");
                                if (preview != null) preview.setText("Saved to:\n" + testFile.getName());
                            } else {
                                if (status != null) status.setText("ERROR: Empty file");
                            }
                        }
                    });

                } catch (final Exception e) {
                    setStatus("TEST ERROR: " + e.getMessage());
                }
            }
        }).start();
    }

    private void startRecording() {
        if (recording) return;

        recording = true;
        if (record != null) record.setText("STOP");
        if (status != null) status.setText("RECORDING...");

        recordThread = new Thread(new Runnable() {
            public void run() {
                try {
                    File out = outputDir();
                    currentVideoFolder = new File(out, "tmp_" + System.currentTimeMillis());
                    currentVideoFolder.mkdirs();

                    long timeBetweenFrames = 1000L / FPS;
                    long nextFrameTime = System.currentTimeMillis();
                    int frameCount = 0;

                    while (recording) {
                        File frameFile = new File(currentVideoFolder, "frame_" + String.format("%04d", frameCount) + ".raw");
                        
                        Process p = Runtime.getRuntime().exec("su");
                        DataOutputStream os = new DataOutputStream(p.getOutputStream());
                        
                        // CORREÇÃO: Aplicado sh -c também dentro da gravação contínua
                        os.writeBytes("sh -c '/system/bin/screencap > " + frameFile.getAbsolutePath() + "'\n");
                        os.writeBytes("exit\n");
                        os.flush();
                        p.waitFor();

                        frameCount++;
                        nextFrameTime += timeBetweenFrames;

                        long sleep = nextFrameTime - System.currentTimeMillis();
                        if (sleep > 0) {
                            Thread.sleep(sleep);
                        }
                    }

                    convertToMp4(frameCount);

                } catch (final Exception e) {
                    recording = false;
                    setStatus("ERROR: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (record != null) record.setText("RECORD");
                        }
                    });
                }
            }
        }, "ScreenVideoRecorder");

        recordThread.start();
    }

    private void stopRecording() {
        recording = false;
        if (status != null) status.setText("PROCESSING...");
    }

    private void convertToMp4(final int totalFrames) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    File out = outputDir();
                    final File mp4File = new File(out, "video_" + System.currentTimeMillis() + ".mp4");

                    Process p = Runtime.getRuntime().exec("su");
                    DataOutputStream os = new DataOutputStream(p.getOutputStream());
                    
                    String ffmpegCmd = "ffmpeg -f rawvideo -pixel_format rgba -video_size 480x800 -framerate " + FPS + 
                                       " -i " + currentVideoFolder.getAbsolutePath() + "/frame_%04d.raw" +
                                       " -c:v libx264 -pix_fmt yuv420p -y " + mp4File.getAbsolutePath() + "\n";
                    
                    os.writeBytes(ffmpegCmd);
                    os.writeBytes("exit\n");
                    os.flush();
                    p.waitFor();

                    deleteFolderContents(currentVideoFolder);
                    currentVideoFolder.delete();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (status != null) status.setText("VIDEO SAVED!");
                            if (preview != null) preview.setText("MP4 successfully compiled:\n" + mp4File.getName() + "\nTotal Frames: " + totalFrames);
                            if (record != null) record.setText("RECORD");
                        }
                    });

                } catch (Exception e) {
                    setStatus("Muxing error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void deleteFolderContents(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (status != null) status.setText(s);
            }
        });
    }

    private void showSettings() {
        new AlertDialog.Builder(this)
            .setTitle("ScreenVideo FREE")
            .setMessage("Target: " + FPS + " FPS\nOutput format: H.264 MP4 Container\nEngine: Native Shell Muxing")
            .setPositiveButton("OK", null)
            .show();
    }

    private File outputDir() {
        File base = Environment.getExternalStorageDirectory();
        File dir = new File(base, "ScreenVideo");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @Override
    protected void onDestroy() {
        recording = false;
        if (recordThread != null) {
            recordThread.interrupt();
        }
        super.onDestroy();
    }
    }
