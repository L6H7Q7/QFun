package me.yxp.qfun.custom;

import android.content.Context;
import android.util.Log;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Logs only stage names, local sequence numbers, numeric status codes and exception types. */
public final class RedPacketTrialLog {
    private static final Pattern CODE = Pattern.compile("(?i)(?:retcode|ret_code|re_code|code)[=: ]+(-?\\d+)");
    public static File file(Context context) {
        File root = context.getExternalFilesDir(null);
        if (root == null) root = context.getFilesDir();
        return new File(root, "QFun/log/redpacket-trial.log");
    }
    public static synchronized void write(String trace, String event, String safeDetail) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date())
                + " [QFunRedPacket] " + trace + " " + event + " " + safeDetail;
        Log.i("QFunRedPacket", line);
        try { XposedBridge.log(line); } catch (Throwable ignored) {}
        Context context = UiCleanupSettings.hostContext();
        if (context == null) return;
        try {
            File output = file(context);
            File folder = output.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) return;
            if (output.length() > 512 * 1024) {
                File old = new File(folder, "redpacket-trial.previous.log");
                if (old.exists() && !old.delete()) return;
                if (!output.renameTo(old)) return;
            }
            try (FileOutputStream stream = new FileOutputStream(output, true)) {
                stream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {}
    }
    public static String error(Throwable failure) {
        Throwable error = failure;
        for (int i = 0; i < 5 && error.getCause() != null; i++) error = error.getCause();
        String result = "exception=" + error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message != null) {
            Matcher match = CODE.matcher(message);
            if (match.find()) result += " code=" + match.group(1);
            if (error instanceof ClassNotFoundException) {
                Matcher name = Pattern.compile("(?:me\\.yxp\\.|com\\.tencent\\.|kotlin\\.)[\\w.$]+").matcher(message);
                if (name.find()) result += " missing_class=" + name.group();
            }
        }
        return result;
    }
    private RedPacketTrialLog() {}
}
