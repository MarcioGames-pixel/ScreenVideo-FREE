package com.screenvideo.free;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
    private File ffmpegBin;

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

            if (textViews.size() >= 4) {
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

            // Inicializa e extrai o binário do FFmpeg contido na pasta assets/
            initFFmpeg();

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initFFmpeg() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    ffmpegBin = new File(getFilesDir(), "ffmpeg");
                    
                    // Extrai o arquivo apenas se ele não existir para economizar processamento
                    if (!ffmpegBin.exists()) {
                        setStatus("Extracting FFmpeg from assets...");
                        InputStream is = getAssets().open("ffmpeg");
                        FileOutputStream os = new FileOutputStream(ffmpegBin);
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                        os.flush();
                        os.close();
                        is.close();
                    }

                    // Força permissão de execução via terminal root (obrigatório para Gingerbread)
                    Process p = Runtime.getRuntime().exec("su");
                    DataOutputStream dos = new DataOutputStream(p.getOutputStream());
                    dos.writeBytes("chmod 755 " + ffmpegBin.getAbsolutePath() + "\n");
                    dos.writeBytes("exit\n");
                    dos.flush();
                    p.waitFor();

                    setStatus("Ready to record (Root & FFmpeg OK)");

                } catch (Exception e) {
                    setStatus("FFmpeg initialization error: " + e.getMessage());
                }
            }
        }).start();
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
                    os.writeBytes("cat /dev/graphics/fb0 > " + testFile.getAbsolutePath() + "\n");
                    os.writeBytes("exit\n");
                    os.flush();
                    p.waitFor();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (testFile.exists() && testFile.length() > 0) {
                                if (status != null) status.setText("TEST OK - " + testFile.length() + " bytes");
                                if (preview != null) preview.setText("Saved to:\n" + testFile.getName());
                            } else {
                                if (status != null) status.setText("ERROR: /dev/graphics/fb0 returned empty");
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
                Process suProcess = null;
                DataOutputStream os = null;
                int frameCount = 0;

                try {
                    File out = outputDir();
                    currentVideoFolder = new File(out, "tmp_" + System.currentTimeMillis());
                    currentVideoFolder.mkdirs();

                    long timeBetweenFrames = 1000L / FPS;
                    long nextFrameTime = System.currentTimeMillis();

                    suProcess = Runtime.getRuntime().exec("su");
                    os = new DataOutputStream(suProcess.getOutputStream());

                    while (recording) {
                        File frameFile = new File(currentVideoFolder, "frame_" + String.format("%04d", frameCount) + ".raw");
                        
                        os.writeBytes("cat /dev/graphics/fb0 > " + frameFile.getAbsolutePath() + "\n");
                        os.flush();

                        frameCount++;
                        nextFrameTime += timeBetweenFrames;

                        long sleep = nextFrameTime - System.currentTimeMillis();
                        if (sleep > 0) {
                            Thread.sleep(sleep);
                        }
                    }

                    os.writeBytes("exit\n");
                    os.flush();
                    suProcess.waitFor();

                } catch (final Exception e) {
                    setStatus("ERROR: " + e.getMessage());
                } finally {
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { if (suProcess != null) suProcess.destroy(); } catch (Exception ignored) {}
                }

                if (frameCount > 0) {
                    convertToMp4(frameCount);
                } else {
                    recording = false;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (status != null) status.setText("ERROR: 0 frames recorded");
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
                    
                    // Usa o binário extraído do assets executando com o path absoluto interno do app
                    String ffmpegPath = (ffmpegBin != null) ? ffmpegBin.getAbsolutePath() : "ffmpeg";
                    
                                        String ffmpegCmd = ffmpegPath + " -f rawvideo -pixel_format rgb565 -video_size 480x800 -framerate " + FPS + 
                                       " -i " + currentVideoFolder.getAbsolutePath() + "/frame_%04d.raw" +
                                       " -c:v libx264 -pix_fmt yuv420p -y " + mp4File.getAbsolutePath() + "\n";
                    
                    os.writeBytes(ffmpegCmd);
                    os.writeBytes("exit\n");
                    os.flush();
                    int exitVal = p.waitFor();

                    // Limpeza dos arquivos de frames temporários para liberar espaço
                    deleteFolderContents(currentVideoFolder);
                    currentVideoFolder.delete();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            recording = false;
                            if (exitVal == 0 && mp4File.exists() && mp4File.length() > 0) {
                                if (status != null) status.setText("VIDEO SAVED!");
                                if (preview != null) preview.setText("MP4 successfully compiled:\n" + mp4File.getName() + "\nTotal Frames: " + totalFrames);
                            } else {
                                if (status != null) status.setText("Muxing error (Exit code: " + exitVal + ")");
                            }
                            if (record != null) record.setText("RECORD");
                        }
                    });

                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            recording = false;
                            setStatus("Muxing error: " + errorMsg);
                            if (record != null) record.setText("RECORD");
                        }
                    });
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
            .setMessage("Target: " + FPS + " FPS\nOutput format: H.264 MP4 Container\nEngine: Persistent FB0 Pipe & Assets FFmpeg")
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
