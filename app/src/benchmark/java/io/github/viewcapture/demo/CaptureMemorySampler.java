package io.github.viewcapture.demo;

import android.os.Debug;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONException;
import org.json.JSONObject;

/** Samples process RSS separately from the timing benchmark. */
@android.annotation.TargetApi(29)
final class CaptureMemorySampler implements AutoCloseable {
    private final AtomicLong peakKb = new AtomicLong();
    private volatile boolean running = true;
    private final Thread thread =
            new Thread(
                    () -> {
                        while (running) {
                            peakKb.accumulateAndGet(rssKb(), Math::max);
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException interrupted) {
                                return;
                            }
                        }
                    },
                    "CaptureRssSampler");

    void start() {
        peakKb.set(rssKb());
        thread.start();
    }

    long peakKb() {
        return peakKb.get();
    }

    static long rssKb() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    return Long.parseLong(line.trim().split("\\s+")[1]);
                }
            }
            throw new IllegalStateException("VmRSS unavailable");
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    static JSONObject snapshot() throws JSONException {
        Debug.MemoryInfo memory = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memory);
        Runtime runtime = Runtime.getRuntime();
        JSONObject result = new JSONObject();
        result.put("rssKb", rssKb());
        result.put("pssKb", memory.getTotalPss());
        result.put("graphicsPssKb", memory.getMemoryStat("summary.graphics"));
        result.put("javaUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        result.put("nativeAllocatedBytes", Debug.getNativeHeapAllocatedSize());
        return result;
    }

    @Override
    public void close() {
        peakKb.accumulateAndGet(rssKb(), Math::max);
        running = false;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
