package app.pixelplanner;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** A short, pleasant two-note "tick" played when a widget item is checked off. */
final class TickSound {
    static void play() {
        new Thread(new Runnable() {
            @Override public void run() {
                AudioTrack t = null;
                try {
                    int sr = 22050;
                    double[][] notes = {{880, 0.07}, {1318, 0.12}};
                    int total = 0;
                    for (double[] n : notes) total += (int) (sr * n[1]);
                    short[] buf = new short[total];
                    int p = 0;
                    for (double[] n : notes) {
                        int len = (int) (sr * n[1]);
                        for (int i = 0; i < len; i++) {
                            double env = Math.min(1.0, i / 120.0) * Math.min(1.0, (len - i) / 400.0);
                            buf[p++] = (short) (Math.sin(2 * Math.PI * n[0] * i / sr) * env * 9000);
                        }
                    }
                    t = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(buf.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
                    t.write(buf, 0, buf.length);
                    t.play();
                    Thread.sleep(450);
                } catch (Throwable ignored) {
                } finally {
                    try { if (t != null) t.release(); } catch (Throwable ignored) { }
                }
            }
        }).start();
    }
}
