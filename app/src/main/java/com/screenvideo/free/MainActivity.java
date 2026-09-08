package com.screenvideo.free;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String FB = "/dev/graphics/fb0";
    private static final int WIDTH = 480;
    private static final int HEIGHT = 800;
    private static final int BYTES_PER_PIXEL = 4; 
    private static final int BYTES_PER_FRAME = WIDTH * HEIGHT * BYTES_PER_PIXEL;
    private static final int FPS = 4;

    private TextView status;
    private TextView preview;
    private Button record;

    private volatile boolean recording;
    private Thread recordThread;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        try {
            setContentView(R.layout.activity_main);

            // Varre a tela estruturalmente para encontrar os elementos sem usar IDs
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

            // Mapeia pela ordem exata de aparição no XML estruturado
            if (textViews.size() >= 3) {
                status = textViews.get(2);   // Terceiro TextView (Barra de status)
                preview = textViews.get(3);  // Quarto TextView (Visualizador central)
            }

            if (buttons.size() >= 3) {
                Button test = buttons.get(0);      // Primeiro botão: TESTAR
                Button settings = buttons.get(1);  // Segundo botão: CONFIG
                record = buttons.get(2);          // Terceiro botão: GRAVAR

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

            new Thread(new Runnable() {
                public void run() {
                    tryRootAccess();
                }
            }).start();

        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

    private void tryRootAccess() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("chmod 666 " + FB + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            p.waitFor();
            setStatus("Pronto para gravar (Root OK)");
        } catch (Exception e) {
            setStatus("Aviso: Sem Root (" + e.getMessage() + ")");
        }
    }

    private void testFramebuffer() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    File fb = new File(FB);

                    if (!fb.exists()) {
                        setStatus("ERRO: fb0 não existe");
                        return;
                    }

                    File out = outputDir();
                    File testFile = new File(out, "frame_test.raw");

                    copyOneFrame(fb, testFile);

                    final long size = testFile.length();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (status != null) status.setText("FRAMEBUFFER OK - " + size + " bytes");
                            if (preview != null) {
                                preview.setText(
                                    "TESTE CONCLUÍDO\n" +
                                    WIDTH + " x " + HEIGHT +
                                    "\nFormato: 32-bit RGBA"
                                );
                            }
                            Toast.makeText(MainActivity.this, "Frame salvo", Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (final Exception e) {
                    setStatus("ERRO TESTE: " + e.getMessage());
                }
            }
        }).start();
    }

    private void startRecording() {
        if (recording) return;

        recording = true;
        if (record != null) record.setText("PARAR");
        if (status != null) status.setText("GRAVANDO...");

        recordThread = new Thread(new Runnable() {
            public void run() {
                FileInputStream in = null;
                FileOutputStream fos = null;

                try {
                    File out = outputDir();
                    File raw = new File(out, "screenvideo_" + System.currentTimeMillis() + ".raw");

                    in = new FileInputStream(FB);
                    fos = new FileOutputStream(raw);

                    byte[] frame = new byte[BYTES_PER_FRAME];
                    long next = System.currentTimeMillis();

                    while (recording) {
                        int got = readFully(in, frame);

                        if (got != BYTES_PER_FRAME) {
                            continue;
                        }

                        fos.write(frame);
                        next += 1000L / FPS;

                        long sleep = next - System.currentTimeMillis();
                        if (sleep > 0) {
                            Thread.sleep(sleep);
                        }
                    }

                    final String path = raw.getAbsolutePath();
                    if (in != null) in.close();
                    if (fos != null) fos.close();

                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (status != null) status.setText("GRAVAÇÃO SALVA");
                            if (preview != null) preview.setText("RAW 32BIT\n" + path);
                            if (record != null) record.setText("GRAVAR");
                        }
                    });

                } catch (final Exception e) {
                    try { if (in != null) in.close(); } catch (Exception ignored) {}
                    try { if (fos != null) fos.close(); } catch (Exception ignored) {}

                    recording = false;
                    setStatus("ERRO GRAVAÇÃO: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (record != null) record.setText("GRAVAR");
                        }
                    });
                }
            }
        }, "ScreenVideoRecorder");

        recordThread.start();
    }

    private void stopRecording() {
        recording = false;
        if (status != null) status.setText("PARANDO...");
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
            .setMessage(
                "Entrada: " + FB +
                "\n\nFormato: 32-bit (debug.fb.rgb565=0)" +
                "\nResolução: " + WIDTH + "x" + HEIGHT +
                "\nFPS: " + FPS +
                "\n\nAlvo: HTC Ace / Desire HD (Android 2.3.3)"
            )
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
