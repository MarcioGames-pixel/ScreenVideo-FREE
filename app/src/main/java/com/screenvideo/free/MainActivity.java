package com.screenvideo.free;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final String FB = "/dev/graphics/fb0";
    private static final int WIDTH = 480;
    private static final int HEIGHT = 800;
    private static final int BYTES_PER_FRAME = WIDTH * HEIGHT * 2;
    private static final int FPS = 4;

    private TextView status;
    private TextView preview;
    private Button record;
    private volatile boolean recording;
    private Thread recordThread;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.status);
        preview = (TextView) findViewById(R.id.preview);
        record = (Button) findViewById(R.id.record);

        ((Button) findViewById(R.id.test)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { testFramebuffer(); }
        });

        ((Button) findViewById(R.id.settings)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });

        record.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) stopRecording();
                else startRecording();
            }
        });
    }

    private void testFramebuffer() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File fb = new File(FB);
                    if (!fb.exists()) {
                        setStatus("ERRO: fb0 não existe");
                        return;
                    }
                    File out = outputDir();
                    File test = new File(out, "frame_test.rgb565");
                    copyOneFrame(fb, test);
                    final long size = test.length();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            status.setText("FRAMEBUFFER OK • " + size + " bytes");
                            preview.setText("TESTE CONCLUÍDO\n" + WIDTH + " × " + HEIGHT + "\nRGB565 LE");
                            Toast.makeText(MainActivity.this, "Frame salvo em " + size + " bytes", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception e) {
                    setStatus("ERRO: " + e.getClass().getSimpleName());
                }
            }
        }).start();
    }

    private void startRecording() {
        if (recording) return;
        recording = true;
        record.setText(R.string.stop);
        status.setText("GRAVANDO...");
        recordThread = new Thread(new Runnable() {
            @Override public void run() {
                File out = outputDir();
                File raw = new File(out, "screenvideo_" + System.currentTimeMillis() + ".rgb565");
                try {
                    FileInputStream in = new FileInputStream(FB);
                    FileOutputStream fos = new FileOutputStream(raw);
                    byte[] frame = new byte[BYTES_PER_FRAME];
                    long next = System.currentTimeMillis();

                    while (recording) {
                        int got = readFully(in, frame);
                        if (got != BYTES_PER_FRAME) break;
                        fos.write(frame);
                        fos.flush();

                        next += 1000L / FPS;
                        long sleep = next - System.currentTimeMillis();
                        if (sleep > 0) Thread.sleep(sleep);
                    }

                    in.close();
                    fos.close();
                    final String path = raw.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            status.setText("GRAVAÇÃO SALVA");
                            preview.setText("RAW RGB565\n" + path);
                            record.setText(R.string.record);
                        }
                    });
                } catch (final Exception e) {
                    recording = false;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            status.setText("ERRO: " + e.getMessage());
                            record.setText(R.string.record);
                        }
                    });
                }
            }
        }, "ScreenVideoRecorder");
        recordThread.start();
    }

    private void stopRecording() {
        recording = false;
        status.setText("PARANDO...");
        if (recordThread != null) recordThread.interrupt();
    }

    private int readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length && recording) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) break;
            off += n;
        }
        return off;
    }

    private void copyOneFrame(File fb, File out) throws IOException {
        FileInputStream in = new FileInputStream(fb);
        FileOutputStream fos = new FileOutputStream(out);
        byte[] b = new byte[BYTES_PER_FRAME];
        int got = 0;
        while (got < b.length) {
            int n = in.read(b, got, b.length - got);
            if (n < 0) break;
            got += n;
        }
        fos.write(b, 0, got);
        fos.close();
        in.close();
    }

    private File outputDir() {
        File base = Environment.getExternalStorageDirectory();
        File dir = new File(base, "ScreenVideo");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void setStatus(final String s) {
        runOnUiThread(new Runnable() {
            @Override public void run() { status.setText(s); }
        });
    }

    private void showSettings() {
        new AlertDialog.Builder(this)
                .setTitle("ScreenVideo FREE")
                .setMessage("Entrada: " + FB + "\n\nFormato: RGB565 little-endian\nResolução: 480×800\nFPS: " + FPS +
                        "\n\nA versão 0.1 grava RAW RGB565. A conversão para MP4 com FFmpeg será adicionada na próxima etapa.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        recording = false;
        super.onDestroy();
    }
}
