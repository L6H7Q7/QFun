package me.yxp.qfun.custom;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.media.ExifInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Default QQ penguin art plus a persistent user-selected image/text overlay. */
public final class CustomSplashFeature {
    private static final String PREFS = "QFun_CustomSplash";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "QFun-splash-image");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Context hostContext;
    private static String modulePath;
    private static ClassLoader moduleLoader;
    private static ClassLoader hostLoader;
    private static byte[] defaultLight;
    private static byte[] defaultDark;

    private CustomSplashFeature() {}

    public static void install(ClassLoader host, ClassLoader module, String path) {
        hostLoader = host;
        moduleLoader = module;
        modulePath = path;
        try {
            XposedBridge.hookMethod(Application.class.getDeclaredMethod("attach", Context.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    remember((Context) hook.args[0]);
                }
            });
            XposedBridge.hookMethod(AssetManager.class.getDeclaredMethod("open", String.class, int.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    String name = (String) hook.args[0];
                    if (!SplashRules.mainAsset(name)) return;
                    try {
                        boolean dark = name.contains("_night") || night(context());
                        byte[] data = imageBytes(dark);
                        if (data != null) hook.setResult(new ByteArrayInputStream(data));
                    } catch (Throwable failure) {
                        log("Asset override failed: " + failure);
                    }
                }
            });
            log("Startup asset hook installed");
        } catch (Throwable failure) {
            log("Startup asset hook unavailable: " + failure);
        }
        try {
            Class<?> widget = XposedHelpers.findClass("com.tencent.mobileqq.splashad.SplashWidget", host);
            XposedBridge.hookMethod(widget.getDeclaredMethod("setSplashDrawable", Drawable.class, boolean.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    try {
                        Context current = ((android.view.View) hook.thisObject).getContext();
                        remember(current);
                        byte[] data = imageBytes(night(current));
                        if (data == null) return;
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (bitmap == null) return;
                        hook.args[0] = new BitmapDrawable(current.getResources(), bitmap);
                        // Custom images use a transparent full-width square; stock art keeps QQ's layout mode.
                        if (prefs(current).getBoolean("custom_enabled", false)) hook.args[1] = false;
                    } catch (Throwable failure) {
                        log("Splash drawable override failed: " + failure);
                    }
                }
            });
        } catch (Throwable failure) {
            log("Splash drawable hook unavailable: " + failure);
        }
        try {
            Class<?> settings = XposedHelpers.findClass("me.yxp.qfun.activity.SettingActivity", module);
            XposedBridge.hookMethod(settings.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if ((Integer) hook.args[0] != SplashRules.PICK_IMAGE) return;
                    Activity activity = (Activity) hook.thisObject;
                    remember(activity);
                    String text = prefs(activity).getString("pending_caption", prefs(activity).getString("caption", ""));
                    Intent result = (Intent) hook.args[2];
                    hook.setResult(null);
                    if ((Integer) hook.args[1] != Activity.RESULT_OK || result == null || result.getData() == null) {
                        show(activity, text);
                        return;
                    }
                    Uri uri = result.getData();
                    Context app = activity.getApplicationContext();
                    WORK.execute(() -> {
                        Throwable problem = null;
                        try { importImage(app, uri, text); }
                        catch (Throwable failure) { problem = failure; log("Image import failed: " + failure); }
                        Throwable error = problem;
                        main(() -> {
                            if (!active(activity)) return;
                            Toast.makeText(activity, error == null ? "图片已保存并启用，下次冷启动生效" : "图片导入失败：" + message(error), Toast.LENGTH_LONG).show();
                            show(activity, error == null ? null : text);
                        });
                    });
                }
            });
            log("Image picker result hook installed");
        } catch (Throwable failure) {
            log("Image picker hook unavailable: " + failure);
        }
    }

    public static void show(Activity activity) { show(activity, null); }

    private static void show(Activity activity, String draft) {
        if (!active(activity)) return;
        remember(activity);
        boolean dark = night(activity);
        Context theme = new ContextThemeWrapper(activity, dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density + .5f);
        SharedPreferences pref = prefs(activity);
        boolean hasImage = source(activity).isFile();
        LinearLayout body = new LinearLayout(theme);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad / 2, pad, pad / 2);
        TextView status = new TextView(theme);
        status.setText(pref.getBoolean("custom_enabled", false) ? "当前：自定义启动画面" : "当前：QQ 默认企鹅图");
        status.setTextSize(16);
        body.addView(status);
        TextView help = new TextView(theme);
        help.setText("替换启动页中间的图文，保留底部 QQ 标识。修改后需结束 QQ 进程并重新打开才会显示。");
        help.setPadding(0, pad / 2, 0, pad / 2);
        body.addView(help);
        Button choose = new Button(theme);
        choose.setText("选择图片并启用");
        body.addView(choose);
        CheckBox useImage = new CheckBox(theme);
        useImage.setText(hasImage ? "使用已选图片" : "使用图片（请先选择）");
        useImage.setChecked(hasImage && pref.getBoolean("use_image", true));
        body.addView(useImage);
        TextView textLabel = new TextView(theme);
        textLabel.setText("自定义文字（可留空，最多 160 字）");
        body.addView(textLabel);
        EditText text = new EditText(theme);
        text.setHint("填写想显示的文字；只用文字时取消勾选图片");
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        text.setMinLines(2);
        text.setMaxLines(5);
        text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SplashRules.MAX_CAPTION_LENGTH)});
        text.setText(draft != null ? draft : pref.getString("caption", ""));
        body.addView(text, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(theme);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(theme).setTitle("启动画面设置").setView(scroll)
            .setPositiveButton("保存并启用自定义", null).setNegativeButton("取消", null)
            .setNeutralButton("恢复 QQ 默认", null).create();
        choose.setOnClickListener(view -> {
            try {
                String caption = SplashRules.caption(text.getText().toString());
                pref.edit().putString("pending_caption", caption).apply();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivityForResult(intent, SplashRules.PICK_IMAGE);
                dialog.dismiss();
            } catch (Exception failure) {
                Toast.makeText(activity, "无法打开图片选择器：" + message(failure), Toast.LENGTH_LONG).show();
            }
        });
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                if (!pref.edit().putBoolean("custom_enabled", false).commit()) {
                    Toast.makeText(activity, "保存失败，请重试", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(activity, "已恢复 QQ 默认企鹅图，下次冷启动生效", Toast.LENGTH_LONG).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String caption;
                try {
                    caption = SplashRules.caption(text.getText().toString());
                    SplashRules.validateCustom(useImage.isChecked(), source(activity).isFile(), caption);
                } catch (IllegalArgumentException invalid) {
                    text.setError(invalid.getMessage());
                    return;
                }
                boolean includeImage = useImage.isChecked();
                for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) dialog.getButton(which).setEnabled(false);
                choose.setEnabled(false);
                Context app = activity.getApplicationContext();
                WORK.execute(() -> {
                    Throwable problem = null;
                    try { renderAndSave(app, includeImage ? source(app) : null, includeImage, caption); }
                    catch (Throwable failure) { problem = failure; log("Custom splash save failed: " + failure); }
                    Throwable error = problem;
                    main(() -> {
                        if (!active(activity)) return;
                        if (error == null) {
                            dialog.dismiss();
                            Toast.makeText(activity, "已保存自定义启动图，下次冷启动生效", Toast.LENGTH_LONG).show();
                        } else {
                            text.setError("保存失败：" + message(error));
                            for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) dialog.getButton(which).setEnabled(true);
                            choose.setEnabled(true);
                        }
                    });
                });
            });
        });
        dialog.show();
    }

    private static void importImage(Context context, Uri uri, String caption) throws IOException {
        File raw = new File(directory(context), "import-" + System.nanoTime() + ".tmp");
        File image = new File(directory(context), "source-" + System.nanoTime() + ".png");
        Bitmap decoded = null;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(raw)) {
                if (input == null) throw new IOException("无法读取图片");
                byte[] buffer = new byte[8192];
                int total = 0, count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > SplashRules.MAX_IMAGE_BYTES) throw new IOException("图片超过 32 MB");
                    output.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(raw.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("无法解码这张图片");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1536) options.inSampleSize *= 2;
            decoded = BitmapFactory.decodeFile(raw.getPath(), options);
            if (decoded == null) throw new IOException("无法解码这张图片");
            decoded = orient(decoded, raw);
            writePng(decoded, image);
            renderAndSave(context, image, true, SplashRules.caption(caption));
        } finally {
            if (decoded != null) decoded.recycle();
            raw.delete();
            // Published source files remain referenced by preferences; failed imports are discarded.
            if (!image.getName().equals(prefs(context).getString("selected_source", ""))) image.delete();
        }
    }

    private static void renderAndSave(Context context, File image, boolean useImage, String caption) throws IOException {
        SplashRules.validateCustom(useImage, image != null && image.isFile(), caption);
        String token = Long.toHexString(System.nanoTime());
        File light = new File(directory(context), "render-light-" + token + ".png");
        File dark = new File(directory(context), "render-dark-" + token + ".png");
        Bitmap picture = useImage ? BitmapFactory.decodeFile(image.getPath()) : null;
        if (useImage && picture == null) throw new IOException("已选图片无法读取，请重新选择");
        boolean published = false;
        try {
            render(picture, caption, false, light);
            render(picture, caption, true, dark);
            String previousLight = prefs(context).getString("light_file", "");
            String previousDark = prefs(context).getString("dark_file", "");
            String previousSource = prefs(context).getString("selected_source", "");
            SharedPreferences.Editor edit = prefs(context).edit().putBoolean("custom_enabled", true)
                .putBoolean("use_image", useImage).putString("caption", caption)
                .putString("light_file", light.getName()).putString("dark_file", dark.getName());
            if (image != null && useImage) edit.putString("selected_source", image.getName());
            if (!edit.commit()) throw new IOException("无法保存本地配置");
            published = true;
            discardGenerated(context, previousLight);
            discardGenerated(context, previousDark);
            if (image != null && useImage && !image.getName().equals(previousSource)) discardGenerated(context, previousSource);
        } finally {
            if (picture != null) picture.recycle();
            if (!published) { light.delete(); dark.delete(); }
        }
    }

    private static void discardGenerated(Context context, String name) {
        if (name != null && name.matches("(render-(light|dark)-[a-f0-9]+|source-[0-9]+)\\.png")) {
            new File(new File(context.getFilesDir(), "qfun_custom_splash"), name).delete();
        }
    }

    private static void render(Bitmap picture, String caption, boolean dark, File destination) throws IOException {
        Bitmap output = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(output);
            boolean hasText = !caption.isEmpty();
            if (picture != null) {
                float availableHeight = hasText ? 704 : 768;
                float scale = Math.min(768f / picture.getWidth(), availableHeight / picture.getHeight());
                float width = picture.getWidth() * scale, height = picture.getHeight() * scale;
                float top = hasText ? 80 + (availableHeight - height) / 2 : (1024 - height) / 2;
                canvas.drawBitmap(picture, null, new RectF((1024 - width) / 2, top, (1024 + width) / 2, top + height), new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            }
            if (hasText) {
                TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(dark ? Color.WHITE : Color.BLACK);
                paint.setTextSize(48);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                StaticLayout layout = StaticLayout.Builder.obtain(caption, 0, caption.length(), paint, 920)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(8, 1f).setMaxLines(4)
                    .setEllipsize(TextUtils.TruncateAt.END).build();
                canvas.save();
                canvas.translate(52, picture != null ? 800 : (1024 - layout.getHeight()) / 2f);
                layout.draw(canvas);
                canvas.restore();
            }
            writePng(output, destination);
        } finally { output.recycle(); }
    }

    private static void writePng(Bitmap bitmap, File file) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("无法生成启动图片");
            output.getFD().sync();
        }
    }

    private static Bitmap orient(Bitmap bitmap, File file) {
        try {
            int orientation = new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(270); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
                default: return bitmap;
            }
            Bitmap corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (corrected != bitmap) bitmap.recycle();
            return corrected;
        } catch (IOException unsupportedMetadata) { return bitmap; }
    }

    private static byte[] imageBytes(boolean dark) throws IOException {
        Context current = context();
        if (current != null && prefs(current).getBoolean("custom_enabled", false)) {
            String name = prefs(current).getString(dark ? "dark_file" : "light_file", "");
            if (name != null && name.matches("render-(light|dark)-[a-f0-9]+\\.png")) {
                File file = new File(directory(current), name);
                if (file.isFile()) try (InputStream input = new FileInputStream(file)) { return read(input, 16 * 1024 * 1024); }
            }
        }
        return defaults(dark);
    }

    private static synchronized byte[] defaults(boolean dark) throws IOException {
        byte[] cached = dark ? defaultDark : defaultLight;
        if (cached != null) return cached;
        String entry = "assets/qfun-splash/" + (dark ? "default_main_night.png" : "default_main.png");
        if (modulePath == null || modulePath.isEmpty()) {
            try {
                Class<?> loader = XposedHelpers.findClass("me.yxp.qfun.common.ModuleLoader", moduleLoader);
                modulePath = (String) XposedHelpers.callStaticMethod(loader, "getMODULE_PATH");
            } catch (Throwable ignored) {}
        }
        if (modulePath == null || modulePath.isEmpty()) throw new IOException("Module path unavailable");
        try (ZipFile zip = new ZipFile(modulePath)) {
            ZipEntry item = zip.getEntry(entry);
            if (item == null) throw new IOException("Bundled QQ splash missing");
            try (InputStream input = zip.getInputStream(item)) { cached = read(input, 1024 * 1024); }
        }
        if (dark) defaultDark = cached; else defaultLight = cached;
        return cached;
    }

    private static byte[] read(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("Image data too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static Context context() {
        if (hostContext == null) {
            Application application = AndroidAppHelper.currentApplication();
            if (application != null) remember(application);
        }
        return hostContext;
    }

    private static void remember(Context context) {
        Context app = context.getApplicationContext();
        hostContext = app == null ? context : app;
    }

    private static SharedPreferences prefs(Context context) {
        Context app = context.getApplicationContext();
        return (app == null ? context : app).getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static File directory(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "qfun_custom_splash");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建图片保存目录");
        return directory;
    }
    private static File source(Context context) {
        String name = prefs(context).getString("selected_source", "");
        if (name == null || !name.matches("source-[0-9]+\\.png")) return new File(context.getFilesDir(), "qfun-splash-no-image");
        return new File(new File(context.getFilesDir(), "qfun_custom_splash"), name);
    }
    private static boolean night(Context context) {
        try { return (Boolean) XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.tencent.mobileqq.utils.QQTheme", hostLoader), "isNowThemeIsNight"); }
        catch (Throwable ignored) { return context != null && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    }
    private static boolean active(Activity activity) { return !activity.isFinishing() && !activity.isDestroyed(); }
    private static void main(Runnable runnable) { new Handler(Looper.getMainLooper()).post(runnable); }
    private static String message(Throwable failure) { return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(); }
    private static void log(String message) { XposedBridge.log("[QFunSplash] " + message); }
}
