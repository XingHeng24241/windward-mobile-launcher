package com.tungsten.fcl.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tungsten.fcl.setting.ConfigHolder;
import com.tungsten.fcl.setting.Profile;
import com.tungsten.fcl.setting.Profiles;
import com.tungsten.fclcore.mod.Modpack;
import com.tungsten.fclcore.mod.modrinth.ModrinthManifest;
import com.tungsten.fclcore.mod.modrinth.ModrinthModpackProvider;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.DigestUtils;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.CompressingUtils;
import com.tungsten.fclcore.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Installs the fixed Windward Modrinth pack during the first-run bootstrap flow.
 *
 * <p>The class deliberately reuses FCL's normal modpack task graph. It only owns the immutable
 * bundled input, the completion marker and recovery of an interrupted bootstrap-owned version.</p>
 */
public final class WindwardModpackBootstrap {
    public static final String ASSET_PATH = "windward/modpack.mrpack";
    public static final String ASSET_SHA256 =
            "D84FAA9172EB2561C323EB14E9AFC2850303DD3EDBCA42D0BACEE0BCC327379F";
    public static final String VERSION_NAME = "Windward 客户端";
    public static final String PACK_NAME = "Windward 客户端";
    public static final String PACK_VERSION = "1.0.4";
    public static final String MINECRAFT_VERSION = "1.21.11";
    public static final String FABRIC_VERSION = "0.19.5";

    private static final String PREFS_NAME = "windward_modpack_bootstrap";
    private static final String PREF_ASSET_HASH = "asset_hash";
    private static final String PREF_GAME_DIRECTORY = "game_directory";
    private static final String MARKER_FILE = ".windward-bootstrap.json";
    private static final String STATE_INSTALLING = "installing";
    private static final String STATE_COMPLETE = "complete";

    private WindwardModpackBootstrap() {
    }

    /**
     * Checks the recorded installation first, then scans configured profiles for an exact match.
     * A plain same-name version never counts as the bundled Windward pack.
     */
    public static boolean isInstalled(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String recordedHash = preferences.getString(PREF_ASSET_HASH, null);
        String recordedDirectory = preferences.getString(PREF_GAME_DIRECTORY, null);
        if (ASSET_SHA256.equalsIgnoreCase(recordedHash) && recordedDirectory != null
                && isInstalledAtGameDirectory(new File(recordedDirectory))) {
            return true;
        }

        if (ConfigHolder.isInit()) {
            for (Profile profile : Profiles.getProfiles()) {
                if (isInstalledAtGameDirectory(profile.getGameDir())) {
                    rememberInstallation(context, profile.getGameDir());
                    return true;
                }
            }
        }

        preferences.edit()
                .remove(PREF_ASSET_HASH)
                .remove(PREF_GAME_DIRECTORY)
                .apply();
        return false;
    }

    static boolean isInstalledAtGameDirectory(File gameDirectory) {
        File versionRoot = getVersionRoot(gameDirectory);
        return markerMatches(versionRoot, STATE_COMPLETE) && hasExpectedInstalledFiles(versionRoot);
    }

    /**
     * Prepares the embedded MRPACK and returns FCL's standard Modrinth installation task.
     */
    public static Task<?> createInstallTask(Context context, Profile profile) throws Exception {
        File versionRoot = getVersionRoot(profile.getGameDir());
        if (isInstalledAtGameDirectory(profile.getGameDir())) {
            throw new AlreadyInstalledException();
        }

        if (versionRoot.exists()) {
            if (markerMatches(versionRoot, STATE_INSTALLING)) {
                // Only remove a directory carrying our exact in-progress marker. A user-created
                // same-name version is never overwritten.
                FileUtils.deleteDirectory(versionRoot);
                profile.getRepository().refreshVersions();
            } else {
                throw new VersionConflictException();
            }
        }

        File modpackFile = copyBundledModpack(context);
        Charset encoding = CompressingUtils.findSuitableEncoding(modpackFile.toPath());
        Modpack modpack = ModpackHelper.readModpackManifest(modpackFile.toPath(), encoding);
        validateBundledModpack(modpack);

        Files.createDirectories(versionRoot.toPath());
        writeMarker(versionRoot, STATE_INSTALLING);
        return ModpackHelper.getInstallTask(profile, modpackFile, VERSION_NAME, modpack);
    }

    /**
     * Writes the success marker only after FCL has produced a complete matching installation.
     */
    public static void completeInstallation(Context context, Profile profile) throws IOException {
        File versionRoot = getVersionRoot(profile.getGameDir());
        if (!markerMatches(versionRoot, STATE_INSTALLING) || !hasExpectedInstalledFiles(versionRoot)) {
            throw new IOException("Windward modpack installation did not produce the expected files");
        }
        writeMarker(versionRoot, STATE_COMPLETE);
        rememberInstallation(context, profile.getGameDir());
    }

    private static void validateBundledModpack(Modpack modpack) throws InvalidBundledModpackException {
        if (!(modpack.getManifest() instanceof ModrinthManifest)) {
            throw new InvalidBundledModpackException();
        }
        ModrinthManifest manifest = (ModrinthManifest) modpack.getManifest();
        Map<String, String> dependencies = manifest.getDependencies();
        if (!"minecraft".equals(manifest.getGame())
                || manifest.getFormatVersion() != 1
                || !PACK_NAME.equals(manifest.getName())
                || !PACK_VERSION.equals(manifest.getVersionId())
                || dependencies == null
                || !MINECRAFT_VERSION.equals(dependencies.get("minecraft"))
                || !FABRIC_VERSION.equals(dependencies.get("fabric-loader"))) {
            throw new InvalidBundledModpackException();
        }
    }

    private static File copyBundledModpack(Context context) throws IOException {
        File cacheDirectory = new File(context.getCacheDir(), "windward-bootstrap");
        Files.createDirectories(cacheDirectory.toPath());
        File target = new File(cacheDirectory, "modpack.mrpack");
        if (target.isFile() && hashMatches(target)) {
            return target;
        }

        File temporary = new File(cacheDirectory, "modpack.mrpack.part");
        Files.deleteIfExists(temporary.toPath());
        try (InputStream input = context.getAssets().open(ASSET_PATH)) {
            Files.copy(input, temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (!hashMatches(temporary)) {
            Files.deleteIfExists(temporary.toPath());
            throw new IOException("Bundled Windward modpack checksum mismatch");
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static boolean hashMatches(File file) throws IOException {
        return ASSET_SHA256.equalsIgnoreCase(DigestUtils.digestToString("SHA-256", file.toPath()));
    }

    private static boolean hasExpectedInstalledFiles(File versionRoot) {
        File versionJson = new File(versionRoot, VERSION_NAME + ".json");
        File indexFile = new File(versionRoot, "modrinth.index.json");
        File configurationFile = new File(versionRoot, "modpack.json");
        if (!versionJson.isFile() || !indexFile.isFile() || !configurationFile.isFile()) {
            return false;
        }

        try {
            ModrinthManifest manifest = JsonUtils.GSON.fromJson(
                    FileUtils.readText(indexFile), ModrinthManifest.class);
            if (manifest == null || manifest.getDependencies() == null
                    || !"minecraft".equals(manifest.getGame())
                    || manifest.getFormatVersion() != 1
                    || !PACK_NAME.equals(manifest.getName())
                    || !PACK_VERSION.equals(manifest.getVersionId())
                    || !MINECRAFT_VERSION.equals(manifest.getDependencies().get("minecraft"))
                    || !FABRIC_VERSION.equals(manifest.getDependencies().get("fabric-loader"))) {
                return false;
            }

            JsonObject configuration = JsonParser.parseString(
                    FileUtils.readText(configurationFile)).getAsJsonObject();
            return configuration.has("type")
                    && ModrinthModpackProvider.INSTANCE.getName().equals(
                    configuration.get("type").getAsString())
                    && configuration.has("name")
                    && PACK_NAME.equals(configuration.get("name").getAsString())
                    && configuration.has("version")
                    && PACK_VERSION.equals(configuration.get("version").getAsString());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static File getVersionRoot(File gameDirectory) {
        return new File(new File(gameDirectory, "versions"), VERSION_NAME);
    }

    private static File getMarkerFile(File versionRoot) {
        return new File(versionRoot, MARKER_FILE);
    }

    private static boolean markerMatches(File versionRoot, String state) {
        File marker = getMarkerFile(versionRoot);
        if (!marker.isFile()) {
            return false;
        }
        try {
            JsonObject json = JsonParser.parseString(FileUtils.readText(marker)).getAsJsonObject();
            return json.has("state") && state.equals(json.get("state").getAsString())
                    && json.has("assetSha256")
                    && ASSET_SHA256.equalsIgnoreCase(json.get("assetSha256").getAsString())
                    && json.has("name") && PACK_NAME.equals(json.get("name").getAsString())
                    && json.has("version")
                    && PACK_VERSION.equals(json.get("version").getAsString());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void writeMarker(File versionRoot, String state) throws IOException {
        Files.createDirectories(versionRoot.toPath());
        JsonObject marker = new JsonObject();
        marker.addProperty("state", state);
        marker.addProperty("assetSha256", ASSET_SHA256);
        marker.addProperty("name", PACK_NAME);
        marker.addProperty("version", PACK_VERSION);
        marker.addProperty("minecraft", MINECRAFT_VERSION);
        marker.addProperty("fabric", FABRIC_VERSION);
        FileUtils.writeText(getMarkerFile(versionRoot), JsonUtils.GSON.toJson(marker));
    }

    private static void rememberInstallation(Context context, File gameDirectory) {
        String canonicalPath;
        try {
            canonicalPath = gameDirectory.getCanonicalPath();
        } catch (IOException ignored) {
            canonicalPath = gameDirectory.getAbsolutePath();
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_ASSET_HASH, ASSET_SHA256)
                .putString(PREF_GAME_DIRECTORY, canonicalPath)
                .apply();
    }

    public static final class VersionConflictException extends IOException {
        public VersionConflictException() {
            super("A different version named " + VERSION_NAME + " already exists");
        }
    }

    public static final class AlreadyInstalledException extends IOException {
        public AlreadyInstalledException() {
            super("The Windward modpack is already installed");
        }
    }

    public static final class InvalidBundledModpackException extends IOException {
        public InvalidBundledModpackException() {
            super("The bundled Windward modpack does not match the fixed release metadata");
        }
    }
}
