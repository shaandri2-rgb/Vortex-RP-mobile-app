package com.vortexrp.mobile;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs user-supplied GTA: San Andreas data archives into Test launcher's
 * private app-specific storage. It never downloads or bundles Rockstar game data.
 *
 * A remote archive must have a SHA-256 supplied by the user/server operator.
 * Local archives are copied from Android's document picker and checked while extracting.
 */
public final class GameResourcesManager {
    private static final long MAX_ARCHIVE_BYTES = 3L * 1024L * 1024L * 1024L;
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 6L * 1024L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 30000;

    private GameResourcesManager() {}

    public interface Progress {
        void onProgress(long current, long total, String label);
    }

    public static File getInstallDirectory(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        return new File(base, "game-data");
    }

    private static File getBackupDirectory(Context context) {
        File target = getInstallDirectory(context);
        return new File(target.getParentFile(), "game-data-backup");
    }

    public static boolean hasInstalledData(Context context) {
        File dir = getInstallDirectory(context);
        File[] items = dir.listFiles();
        return dir.isDirectory() && items != null && items.length > 0;
    }

    public static boolean hasBackup(Context context) {
        File backup = getBackupDirectory(context);
        File[] items = backup.listFiles();
        return backup.isDirectory() && items != null && items.length > 0;
    }

    public static long getInstalledSize(Context context) {
        return directorySize(getInstallDirectory(context));
    }

    public static int getInstalledFileCount(Context context) {
        return fileCount(getInstallDirectory(context));
    }

    public static File copyLocalZip(Context context, Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("Choose a ZIP archive first.");
        File destination = new File(context.getCacheDir(), "game-resources-local.zip");
        long copied = 0;
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = new BufferedInputStream(resolver.openInputStream(uri));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
            if (in == null) throw new IllegalStateException("Android could not open the selected file.");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                copied += n;
                if (copied > MAX_ARCHIVE_BYTES) {
                    destination.delete();
                    throw new IllegalStateException("The ZIP is larger than the 3 GB safety limit.");
                }
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            destination.delete();
            throw e;
        }
        if (copied < 22) {
            destination.delete();
            throw new IllegalArgumentException("The selected file is empty or is not a valid ZIP archive.");
        }
        return destination;
    }

    public static File downloadVerified(Context context, String urlText, String expectedSha256,
                                         Progress progress) throws Exception {
        if (urlText == null || !urlText.trim().toLowerCase(Locale.US).startsWith("https://")) {
            throw new IllegalArgumentException("Use a direct HTTPS ZIP download link.");
        }
        String expected = normalizeHash(expectedSha256);
        if (expected.length() != 64) {
            throw new IllegalArgumentException("Enter the 64-character SHA-256 checksum for this ZIP.");
        }

        HttpURLConnection connection = null;
        File temp = new File(context.getCacheDir(), "game-resources-download.part");
        File verified = new File(context.getCacheDir(), "game-resources-download.zip");
        try {
            URL url = new URL(urlText.trim());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(45000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "TestLauncher-Android/5.0");
            int status = connection.getResponseCode();
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IllegalArgumentException("The download redirected away from HTTPS.");
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Game-data server returned HTTP " + status + ".");
            }
            long total = connection.getContentLengthLong();
            if (total > MAX_ARCHIVE_BYTES) {
                throw new IllegalStateException("The ZIP is larger than the 3 GB safety limit.");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied = 0;
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    copied += n;
                    if (copied > MAX_ARCHIVE_BYTES) {
                        throw new IllegalStateException("The ZIP exceeded the 3 GB safety limit.");
                    }
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    if (progress != null) progress.onProgress(copied, total, "Downloading game resources");
                }
            }
            String actual = toHex(digest.digest());
            if (!actual.equals(expected)) {
                throw new SecurityException("SHA-256 verification failed. The game-data archive was rejected.");
            }
            if (verified.exists() && !verified.delete()) {
                throw new IllegalStateException("Could not replace the previous temporary download.");
            }
            if (!temp.renameTo(verified)) {
                copyFile(temp, verified);
                if (!temp.delete()) temp.deleteOnExit();
            }
            return verified;
        } finally {
            if (connection != null) connection.disconnect();
            if (temp.exists()) temp.delete();
        }
    }

    public static int install(Context context, File archive, String edition, Progress progress) throws Exception {
        if (archive == null || !archive.isFile() || archive.length() < 22) {
            throw new IllegalArgumentException("Select or download a valid game-data ZIP first.");
        }
        if (archive.length() > MAX_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("The ZIP is larger than the 3 GB safety limit.");
        }

        File target = getInstallDirectory(context);
        File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create the launcher's game-data folder.");
        }
        File staging = new File(parent, "game-data.installing");
        File backup = getBackupDirectory(context);
        deleteRecursively(staging);
        if (!staging.mkdirs()) throw new IllegalStateException("Could not create a temporary installation folder.");

        int installed = 0;
        long expanded = 0;
        Set<String> seen = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive)) {
            if (zip.size() == 0 || zip.size() > MAX_ENTRIES) {
                throw new IllegalStateException("The ZIP must contain between 1 and " + MAX_ENTRIES + " entries.");
            }

            long declared = 0;
            for (java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                String path = safePath(entry.getName());
                String key = path.toLowerCase(Locale.US);
                if (!seen.add(key)) throw new IllegalStateException("Duplicate ZIP path: " + path);
                if (isExecutablePayload(path)) {
                    throw new SecurityException("Executable code is not allowed in a game-data package: " + path);
                }
                if (!entry.isDirectory()) {
                    if (entry.getSize() > MAX_FILE_BYTES) {
                        throw new IllegalStateException("A file exceeds the 2 GB per-file limit: " + path);
                    }
                    if (entry.getSize() > 0) declared += entry.getSize();
                    if (declared > MAX_TOTAL_BYTES) {
                        throw new IllegalStateException("The ZIP expands beyond the 6 GB safety limit.");
                    }
                }
            }

            long available = parent.getUsableSpace();
            if (declared > 0 && available > 0 && declared + (128L * 1024L * 1024L) > available) {
                throw new IllegalStateException("Not enough free storage for this game-data package.");
            }

            int totalEntries = zip.size();
            int visited = 0;
            for (java.util.Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                String path = safePath(entry.getName());
                File output = new File(staging, path);
                String rootPath = staging.getCanonicalPath() + File.separator;
                if (!output.getCanonicalPath().startsWith(rootPath)) {
                    throw new SecurityException("Unsafe ZIP path rejected: " + path);
                }
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) {
                        throw new IllegalStateException("Could not create folder: " + path);
                    }
                } else {
                    File folder = output.getParentFile();
                    if (!folder.isDirectory() && !folder.mkdirs()) {
                        throw new IllegalStateException("Could not create folder for: " + path);
                    }
                    long fileBytes = 0;
                    try (InputStream in = new BufferedInputStream(zip.getInputStream(entry));
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                        byte[] buffer = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            fileBytes += n;
                            expanded += n;
                            if (fileBytes > MAX_FILE_BYTES || expanded > MAX_TOTAL_BYTES) {
                                throw new IllegalStateException("The extracted package exceeds the safety limits.");
                            }
                            out.write(buffer, 0, n);
                        }
                    }
                    if (fileBytes == 0) {
                        output.delete();
                        throw new IllegalStateException("Empty resource file rejected: " + path);
                    }
                    installed++;
                }
                visited++;
                if (progress != null) progress.onProgress(visited, totalEntries, "Installing " + edition + " • " + path);
            }
        } catch (Exception e) {
            deleteRecursively(staging);
            throw e;
        }

        if (installed == 0) {
            deleteRecursively(staging);
            throw new IllegalStateException("The ZIP did not contain any game resource files.");
        }

        // Keep one previous installation so the player can roll back.
        deleteRecursively(backup);
        boolean movedOld = false;
        if (target.exists()) {
            if (!target.renameTo(backup)) {
                deleteRecursively(staging);
                throw new IllegalStateException("Could not move the previous game-data folder to backup.");
            }
            movedOld = true;
        }
        if (!staging.renameTo(target)) {
            if (movedOld) backup.renameTo(target);
            deleteRecursively(staging);
            throw new IllegalStateException("Could not activate the new game-data folder. Previous data was preserved.");
        }

        File info = new File(parent, "game-data-install.txt");
        try (FileOutputStream out = new FileOutputStream(info)) {
            String details = "edition=" + edition + "\nfiles=" + installed + "\ninstalledAt=" + System.currentTimeMillis() + "\n";
            out.write(details.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
        return installed;
    }

    public static void restoreBackup(Context context) throws Exception {
        File target = getInstallDirectory(context);
        File backup = getBackupDirectory(context);
        if (!backup.isDirectory()) throw new IllegalStateException("No previous game-data backup exists.");
        File parent = target.getParentFile();
        File failed = new File(parent, "game-data-replaced");
        deleteRecursively(failed);
        if (target.exists() && !target.renameTo(failed)) {
            throw new IllegalStateException("Could not move current game data out of the way.");
        }
        if (!backup.renameTo(target)) {
            if (failed.exists()) failed.renameTo(target);
            throw new IllegalStateException("Could not restore previous game data.");
        }
        deleteRecursively(failed);
        File info = new File(parent, "game-data-install.txt");
        if (info.exists()) info.delete();
    }

    private static String safePath(String raw) {
        if (raw == null || raw.length() == 0 || raw.startsWith("/") || raw.startsWith("\\") || raw.contains("\\") || raw.contains(":") || raw.indexOf('\u0000') >= 0) {
            throw new SecurityException("Unsafe ZIP path rejected.");
        }
        String path = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (path.isEmpty()) throw new SecurityException("Empty ZIP path rejected.");
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new SecurityException("Unsafe ZIP path rejected: " + raw);
            }
        }
        return path;
    }

    private static boolean isExecutablePayload(String path) {
        String lower = path.toLowerCase(Locale.US);
        return lower.endsWith(".apk") || lower.endsWith(".dex") || lower.endsWith(".jar") ||
                lower.endsWith(".so") || lower.endsWith(".exe") || lower.endsWith(".dll") ||
                lower.endsWith(".sh") || lower.endsWith(".bat");
    }

    private static String normalizeHash(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.US);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value & 0xff));
        return result.toString();
    }

    private static void copyFile(File source, File target) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private static long directorySize(File file) {
        if (!file.exists()) return 0;
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    private static int fileCount(File file) {
        if (!file.exists()) return 0;
        if (file.isFile()) return 1;
        int total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += fileCount(child);
        return total;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
