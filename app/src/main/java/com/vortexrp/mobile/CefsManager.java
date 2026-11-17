package com.vortexrp.mobile;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Verified CEF resource package downloader/installer.
 *
 * Archive format:
 *   vortex-cef-manifest.json
 *   {
 *     "schemaVersion": 1,
 *     "files": [{"path":"cef/index.html","sha256":"<64 hex chars>"}]
 *   }
 *
 * The package is installed only in the storage folder explicitly selected by the user.
 * The manifest's per-file hashes and the archive's configured SHA-256 are both checked.
 */
public final class CefsManager {
    private static final String MANIFEST = "vortex-cef-manifest.json";
    private static final String INDEX_NAME = "cefs-backup-index.json";
    private static final long MAX_ARCHIVE_BYTES = 300L * 1024L * 1024L;
    private static final long MAX_FILE_BYTES = 96L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 450L * 1024L * 1024L;
    private static final int MAX_FILES = 2500;

    private CefsManager() {}

    public interface Progress {
        void onProgress(long current, long total, String label);
    }

    public static File downloadVerified(Context context, String urlText, String expectedSha256,
                                        Progress progress) throws Exception {
        if (urlText == null || !urlText.trim().toLowerCase().startsWith("https://")) {
            throw new IllegalArgumentException("Use an HTTPS download URL.");
        }
        String expected = normalizeHash(expectedSha256);
        if (expected.length() != 64) {
            throw new IllegalArgumentException("Enter the package's 64-character SHA-256 hash first.");
        }

        URL url = new URL(urlText.trim());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "VORTEX-RP-Launcher/3.0");
        File temp = new File(context.getFilesDir(), "vortex-cefs-package.part");
        File verified = new File(context.getFilesDir(), "vortex-cefs-package.zip");

        try {
            connection.connect();
            if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
                throw new IllegalArgumentException("The download redirected away from HTTPS.");
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Package server returned HTTP " + status + ".");
            }
            long total = connection.getContentLengthLong();
            if (total > MAX_ARCHIVE_BYTES) throw new IllegalStateException("Package is larger than the 300 MiB limit.");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long copied = 0;
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    copied += read;
                    if (copied > MAX_ARCHIVE_BYTES) throw new IllegalStateException("Package exceeded the 300 MiB limit.");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    if (progress != null) progress.onProgress(copied, total > 0 ? total : -1, "Downloading package");
                }
            }

            String actual = toHex(digest.digest());
            if (!actual.equals(expected)) {
                temp.delete();
                throw new SecurityException("SHA-256 verification failed. The downloaded package was rejected.");
            }
            if (!temp.renameTo(verified)) {
                copyFile(temp, verified);
                if (!temp.delete()) temp.deleteOnExit();
            }
            return verified;
        } finally {
            connection.disconnect();
        }
    }

    public static int install(Context context, Uri treeUri, File archive, Progress progress) throws Exception {
        if (treeUri == null) throw new IllegalArgumentException("Choose the game/client folder first.");
        if (archive == null || !archive.isFile()) throw new IllegalArgumentException("Download the verified package first.");
        File indexFile = new File(context.getFilesDir(), INDEX_NAME);
        if (indexFile.exists()) {
            throw new IllegalStateException("A backup is already active. Restore it before installing another package.");
        }

        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.isDirectory() || !root.canWrite()) {
            throw new IllegalStateException("The selected folder is not writable. Choose a writable game/client folder.");
        }

        File backupDir = new File(context.getFilesDir(), "cefs-backups-" + System.currentTimeMillis());
        if (!backupDir.mkdirs() && !backupDir.isDirectory()) {
            throw new IllegalStateException("Could not create a private backup folder.");
        }

        JSONObject index = new JSONObject();
        JSONArray backups = new JSONArray();
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                throw new IllegalStateException("Package is missing " + MANIFEST + ".");
            }
            JSONObject manifest;
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                manifest = new JSONObject(new String(readBounded(in, 512 * 1024), java.nio.charset.StandardCharsets.UTF_8));
            }
            if (manifest.optInt("schemaVersion", 0) != 1) {
                throw new IllegalStateException("Unsupported CEF package manifest schema.");
            }
            JSONArray files = manifest.optJSONArray("files");
            if (files == null || files.length() == 0 || files.length() > MAX_FILES) {
                throw new IllegalStateException("Manifest must list between 1 and " + MAX_FILES + " files.");
            }

            index.put("treeUri", treeUri.toString());
            index.put("backupDir", backupDir.getName());
            index.put("entries", backups);
            saveIndex(indexFile, index);

            Set<String> seen = new HashSet<>();
            long totalBytes = 0;
            for (int i = 0; i < files.length(); i++) {
                JSONObject item = files.getJSONObject(i);
                String path = validateRelativePath(item.optString("path", ""));
                if (MANIFEST.equals(path)) throw new IllegalStateException("The manifest cannot be installed as a game file.");
                if (!seen.add(path)) throw new IllegalStateException("Duplicate file path in package: " + path);

                String expected = normalizeHash(item.optString("sha256", ""));
                if (expected.length() != 64) throw new IllegalStateException("Missing SHA-256 for " + path + ".");
                ZipEntry entry = zip.getEntry(path);
                if (entry == null || entry.isDirectory()) throw new IllegalStateException("Archive is missing " + path + ".");
                if (entry.getSize() > MAX_FILE_BYTES) throw new IllegalStateException(path + " exceeds the per-file limit.");
                totalBytes += Math.max(0, entry.getSize());
                if (totalBytes > MAX_TOTAL_BYTES) throw new IllegalStateException("Package exceeds the total extracted-size limit.");

                String actual;
                try (InputStream in = zip.getInputStream(entry)) {
                    actual = sha256(in, MAX_FILE_BYTES);
                }
                if (!actual.equals(expected)) throw new SecurityException("File integrity check failed: " + path);

                DocumentFile parent = ensureParent(root, path);
                String fileName = lastSegment(path);
                DocumentFile existing = parent.findFile(fileName);
                if (existing != null && existing.isDirectory()) {
                    throw new IllegalStateException("Cannot replace a directory with a file: " + path);
                }

                JSONObject backup = new JSONObject();
                backup.put("path", path);
                backup.put("existed", existing != null && existing.isFile());
                String backupName = "file-" + i + ".bin";
                if (existing != null && existing.isFile()) {
                    File destination = new File(backupDir, backupName);
                    try (InputStream in = context.getContentResolver().openInputStream(existing.getUri());
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                        if (in == null) throw new IllegalStateException("Could not read original file: " + path);
                        copy(in, out, Long.MAX_VALUE, null, -1);
                    }
                    backup.put("backup", backupName);
                }
                backups.put(backup);
                saveIndex(indexFile, index);

                if (existing != null && !existing.delete()) {
                    throw new IllegalStateException("Could not safely replace existing file: " + path);
                }
                DocumentFile created = parent.createFile("application/octet-stream", fileName);
                if (created == null) throw new IllegalStateException("Could not create destination file: " + path);
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = context.getContentResolver().openOutputStream(created.getUri(), "wt")) {
                    if (out == null) throw new IllegalStateException("Could not write destination file: " + path);
                    copy(in, out, MAX_FILE_BYTES, progress, i + 1);
                }
                if (progress != null) progress.onProgress(i + 1, files.length(), "Installing " + path);
            }
            return files.length();
        } catch (Exception failure) {
            try {
                restoreInternal(context, treeUri, indexFile, null);
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public static boolean hasBackup(Context context) {
        return new File(context.getFilesDir(), INDEX_NAME).isFile();
    }

    public static int restore(Context context, Uri treeUri, Progress progress) throws Exception {
        File index = new File(context.getFilesDir(), INDEX_NAME);
        if (!index.isFile()) throw new IllegalStateException("No VORTEX CEF backup is available.");
        return restoreInternal(context, treeUri, index, progress);
    }

    private static int restoreInternal(Context context, Uri treeUri, File indexFile, Progress progress) throws Exception {
        JSONObject index;
        try (FileInputStream in = new FileInputStream(indexFile)) {
            index = new JSONObject(new String(readBounded(in, 2 * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8));
        }
        if (!treeUri.toString().equals(index.optString("treeUri", ""))) {
            throw new IllegalStateException("Select the same game/client folder used for the backup.");
        }
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null || !root.canWrite()) throw new IllegalStateException("Selected destination is not writable.");

        JSONArray entries = index.getJSONArray("entries");
        File backupDir = new File(context.getFilesDir(), index.getString("backupDir"));
        int restored = 0;
        for (int i = entries.length() - 1; i >= 0; i--) {
            JSONObject entry = entries.getJSONObject(i);
            String path = validateRelativePath(entry.getString("path"));
            DocumentFile parent = ensureParent(root, path);
            String name = lastSegment(path);
            DocumentFile current = parent.findFile(name);
            if (current != null && !current.delete()) throw new IllegalStateException("Could not remove installed file: " + path);

            if (entry.optBoolean("existed", false)) {
                File source = new File(backupDir, entry.getString("backup"));
                if (!source.isFile()) throw new IllegalStateException("Backup file is missing for " + path);
                DocumentFile restoredFile = parent.createFile("application/octet-stream", name);
                if (restoredFile == null) throw new IllegalStateException("Could not restore " + path);
                try (InputStream in = new BufferedInputStream(new FileInputStream(source));
                     OutputStream out = context.getContentResolver().openOutputStream(restoredFile.getUri(), "wt")) {
                    if (out == null) throw new IllegalStateException("Could not write restored file: " + path);
                    copy(in, out, Long.MAX_VALUE, null, -1);
                }
            }
            restored++;
            if (progress != null) progress.onProgress(restored, entries.length(), "Restoring " + path);
        }
        deleteRecursively(backupDir);
        if (!indexFile.delete()) throw new IllegalStateException("Restore completed, but the backup index could not be removed.");
        return restored;
    }

    private static DocumentFile ensureParent(DocumentFile root, String path) throws Exception {
        String[] parts = path.split("/");
        DocumentFile current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            DocumentFile next = current.findFile(parts[i]);
            if (next == null) next = current.createDirectory(parts[i]);
            if (next == null || !next.isDirectory()) throw new IllegalStateException("Could not create folder for " + path);
            current = next;
        }
        return current;
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static String validateRelativePath(String path) {
        if (path == null || path.trim().isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains(":")) {
            throw new IllegalArgumentException("Unsafe package path.");
        }
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) throw new IllegalArgumentException("Unsafe package path: " + path);
            if (part.length() > 180) throw new IllegalArgumentException("Package path segment is too long.");
        }
        return path;
    }

    private static String normalizeHash(String hash) {
        return hash == null ? "" : hash.trim().toLowerCase().replace(" ", "");
    }

    private static String sha256(InputStream in, long limit) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32 * 1024];
        long count = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            count += read;
            if (count > limit) throw new IllegalStateException("A package file exceeded its size limit.");
            digest.update(buffer, 0, read);
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static byte[] readBounded(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalStateException("Metadata file is too large.");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out, long limit, Progress progress, long step) throws Exception {
        byte[] buffer = new byte[32 * 1024];
        long copied = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            copied += read;
            if (copied > limit) throw new IllegalStateException("File exceeded its size limit.");
            out.write(buffer, 0, read);
            if (progress != null) progress.onProgress(copied, limit, "Writing package file");
        }
        out.flush();
    }

    private static void copyFile(File source, File target) throws Exception {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            copy(in, out, Long.MAX_VALUE, null, -1);
        }
    }

    private static void saveIndex(File file, JSONObject index) throws Exception {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(index.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (file.exists() && !file.delete()) throw new IllegalStateException("Could not update backup index.");
        if (!temp.renameTo(file)) throw new IllegalStateException("Could not save backup index.");
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
