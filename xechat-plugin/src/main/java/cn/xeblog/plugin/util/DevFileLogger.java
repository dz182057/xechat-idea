package cn.xeblog.plugin.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DevFileLogger {

    private static final String LOG_FILE_PROPERTY = "xechat.plugin.dev.log.file";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private DevFileLogger() {
    }

    public static void installIfEnabled() {
        String logFile = System.getProperty(LOG_FILE_PROPERTY);
        if (logFile == null || logFile.trim().isEmpty()) {
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try {
            Path logPath = Paths.get(logFile.trim()).toAbsolutePath();
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            PrintStream fileStream = new PrintStream(
                    new BufferedOutputStream(Files.newOutputStream(
                            logPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND,
                            StandardOpenOption.WRITE
                    )),
                    true,
                    StandardCharsets.UTF_8
            );
            System.setOut(new PrintStream(new TeeOutputStream(originalOut, fileStream), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new TeeOutputStream(originalErr, fileStream), true, StandardCharsets.UTF_8));
            System.out.println("XEChat runIde 日志文件: " + logPath);
        } catch (IOException e) {
            originalErr.println("XEChat runIde 日志文件初始化失败: " + e.getMessage());
            INSTALLED.set(false);
        }
    }

    private static final class TeeOutputStream extends OutputStream {

        private final OutputStream console;

        private final OutputStream file;

        private TeeOutputStream(OutputStream console, OutputStream file) {
            this.console = console;
            this.file = file;
        }

        @Override
        public void write(int b) throws IOException {
            console.write(b);
            file.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            console.write(b, off, len);
            file.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            console.flush();
            file.flush();
        }
    }
}
