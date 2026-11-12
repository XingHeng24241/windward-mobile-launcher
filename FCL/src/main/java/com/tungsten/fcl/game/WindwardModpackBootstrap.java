package com.tungsten.fcl.game;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.CompressingUtils;
import com.tungsten.fclcore.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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
    private static final String CONFIGURATION_FILE = "modpack.cfg";
    private static final String STATE_INSTALLING = "installing";
    private static final String STATE_COMPLETE = "complete";
    private static final int MAX_REPORTED_PROBLEMS = 20;

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
        if (ASSET_SHA256.equalsIgnoreCase(recordedHash) && recordedDirectory != null) {
            File gameDirectory = new File(recordedDirectory);
            if (isInstalledAtGameDirectory(gameDirectory)
                    || tryRecoverInstallation(context, gameDirectory)) {
                return true;
            }
        }

        if (ConfigHolder.isInit()) {
            for (Profile profile : Profiles.getProfiles()) {
                if (isInstalledAtGameDirectory(profile.getGameDir())
                        || tryRecoverInstallation(context, profile.getGameDir())) {
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

    static boolean recoverCompletedInstallationAtGameDirectory(File gameDirectory)
            throws IOException {
        File versionRoot = getVersionRoot(gameDirectory);
        if (!markerMatches(versionRoot, STATE_INSTALLING)) {
            return false;
        }

        InstallationInspection inspection = inspectInstallation(versionRoot, true);
        if (!inspection.isValid()) {
            logInspection("Windward bootstrap recovery was not safe", versionRoot, inspection);
            return false;
        }

        writeMarker(versionRoot, STATE_COMPLETE);
        return true;
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
                if (recoverCompletedInstallationAtGameDirectory(profile.getGameDir())) {
                    rememberInstallation(context, profile.getGameDir());
                    throw new AlreadyInstalledException();
                }
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
        List<String> problems = new ArrayList<>();
        if (!markerMatches(versionRoot, STATE_INSTALLING)) {
            addProblem(problems, "安装标记缺失或状态不正确: " + getMarkerFile(versionRoot));
        }
        problems.addAll(inspectInstallation(versionRoot, false).getProblems());
        if (!problems.isEmpty()) {
            throw new IncompleteInstallationException(problems);
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
        return inspectInstallation(versionRoot, false).isValid();
    }

    private static InstallationInspection inspectInstallation(
            File versionRoot, boolean verifyPayload) {
        List<String> problems = new ArrayList<>();
        File versionJson = new File(versionRoot, VERSION_NAME + ".json");
        File indexFile = new File(versionRoot, "modrinth.index.json");
        File configurationFile = new File(versionRoot, CONFIGURATION_FILE);
        if (!versionJson.isFile()) {
            addProblem(problems, "缺少游戏版本文件: " + versionJson);
        }
        if (!indexFile.isFile()) {
            addProblem(problems, "缺少 Modrinth 清单: " + indexFile);
        }
        if (!configurationFile.isFile()) {
            addProblem(problems, "缺少 FCL 整合包配置: " + configurationFile);
        }

        ModrinthManifest manifest = null;
        JsonObject configuration = null;
        try {
            if (indexFile.isFile()) {
                manifest = JsonUtils.GSON.fromJson(
                        FileUtils.readText(indexFile), ModrinthManifest.class);
                if (!isExpectedManifest(manifest)) {
                    addProblem(problems, "Modrinth 清单身份或依赖版本不匹配: " + indexFile);
                }
            }
        } catch (Exception exception) {
            addProblem(problems, "无法读取 Modrinth 清单: " + exception.getMessage());
        }

        try {
            if (configurationFile.isFile()) {
                configuration = JsonParser.parseString(
                        FileUtils.readText(configurationFile)).getAsJsonObject();
                if (!isExpectedConfiguration(configuration)) {
                    addProblem(problems, "FCL 整合包配置身份不匹配: " + configurationFile);
                }
            }
        } catch (Exception exception) {
            addProblem(problems, "无法读取 FCL 整合包配置: " + exception.getMessage());
        }

        if (verifyPayload && isExpectedManifest(manifest)
                && isExpectedConfiguration(configuration)) {
            try {
                verifyManifestFiles(versionRoot, manifest, problems);
                verifyOverrideFiles(versionRoot, configuration, problems);
            } catch (RuntimeException exception) {
                addProblem(problems, "整合包文件记录无效: " + exception.getMessage());
            }
        }
        return new InstallationInspection(problems);
    }

    private static boolean isExpectedManifest(ModrinthManifest manifest) {
        return manifest != null
                && manifest.getDependencies() != null
                && "minecraft".equals(manifest.getGame())
                && manifest.getFormatVersion() == 1
                && PACK_NAME.equals(manifest.getName())
                && PACK_VERSION.equals(manifest.getVersionId())
                && MINECRAFT_VERSION.equals(manifest.getDependencies().get("minecraft"))
                && FABRIC_VERSION.equals(manifest.getDependencies().get("fabric-loader"));
    }

    private static boolean isExpectedConfiguration(JsonObject configuration) {
        return configuration != null
                && hasStringValue(configuration, "type",
                ModrinthModpackProvider.INSTANCE.getName())
                && hasStringValue(configuration, "name", PACK_NAME)
                && hasStringValue(configuration, "version", PACK_VERSION);
    }

    private static boolean hasStringValue(JsonObject json, String key, String expected) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return false;
        }
        try {
            return expected.equals(json.get(key).getAsString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void verifyManifestFiles(
            File versionRoot, ModrinthManifest manifest, List<String> problems) {
        if (manifest.getFiles() == null) {
            addProblem(problems, "Modrinth 清单没有 files 列表");
            return;
        }

        for (ModrinthManifest.File manifestFile : manifest.getFiles()) {
            if (problems.size() >= MAX_REPORTED_PROBLEMS) {
                return;
            }
            if (manifestFile.getEnv() != null
                    && "unsupported".equals(
                    manifestFile.getEnv().getOrDefault("client", "required"))) {
                continue;
            }
            if (manifestFile.getDownloads() == null || manifestFile.getDownloads().isEmpty()) {
                continue;
            }

            File target = resolvePayloadFile(versionRoot, manifestFile.getPath(), problems);
            if (target == null || !target.isFile()) {
                if (target != null) {
                    addProblem(problems, "缺少整合包下载文件: " + manifestFile.getPath());
                }
                continue;
            }
            if (manifestFile.getFileSize() >= 0
                    && target.length() != manifestFile.getFileSize()) {
                addProblem(problems, "整合包文件大小不匹配: " + manifestFile.getPath());
                continue;
            }

            Map<String, String> hashes = manifestFile.getHashes();
            if (hashes == null || hashes.isEmpty()) {
                addProblem(problems, "整合包文件没有校验值: " + manifestFile.getPath());
                continue;
            }
            if (hashes.containsKey("sha512")) {
                verifyFileHash(target, manifestFile.getPath(), "SHA-512",
                        hashes.get("sha512"), problems);
            } else if (hashes.containsKey("sha1")) {
                verifyFileHash(target, manifestFile.getPath(), "SHA-1",
                        hashes.get("sha1"), problems);
            } else {
                addProblem(problems, "整合包文件没有受支持的校验值: " + manifestFile.getPath());
            }
        }
    }

    private static void verifyOverrideFiles(
            File versionRoot, JsonObject configuration, List<String> problems) {
        JsonElement overridesElement = configuration.get("overrides");
        if (overridesElement == null || !overridesElement.isJsonArray()) {
            addProblem(problems, "FCL 整合包配置没有 overrides 列表");
            return;
        }

        JsonArray overrides = overridesElement.getAsJsonArray();
        for (JsonElement element : overrides) {
            if (problems.size() >= MAX_REPORTED_PROBLEMS) {
                return;
            }
            if (!element.isJsonObject()) {
                addProblem(problems, "FCL 整合包配置包含无效的覆盖文件记录");
                continue;
            }
            JsonObject override = element.getAsJsonObject();
            if (!override.has("path") || !override.has("hash")) {
                addProblem(problems, "FCL 整合包配置中的覆盖文件记录不完整");
                continue;
            }
            String relativePath = override.get("path").getAsString();
            String expectedHash = override.get("hash").getAsString();
            File target = resolvePayloadFile(versionRoot, relativePath, problems);
            if (target == null || !target.isFile()) {
                if (target != null) {
                    addProblem(problems, "缺少整合包覆盖文件: " + relativePath);
                }
                continue;
            }
            verifyFileHash(target, relativePath, "SHA-1", expectedHash, problems);
        }
    }

    private static File resolvePayloadFile(
            File versionRoot, String relativePath, List<String> problems) {
        if (relativePath == null || relativePath.isEmpty()) {
            addProblem(problems, "整合包包含空文件路径");
            return null;
        }
        Path root = versionRoot.toPath().toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            addProblem(problems, "整合包包含不安全的文件路径: " + relativePath);
            return null;
        }
        return target.toFile();
    }

    private static void verifyFileHash(
            File file,
            String relativePath,
            String algorithm,
            String expectedHash,
            List<String> problems) {
        try {
            String actualHash = DigestUtils.digestToString(algorithm, file.toPath());
            if (!actualHash.equalsIgnoreCase(expectedHash)) {
                addProblem(problems, "整合包文件校验失败: " + relativePath);
            }
        } catch (IOException exception) {
            addProblem(problems, "无法校验整合包文件 " + relativePath + ": "
                    + exception.getMessage());
        }
    }

    private static void addProblem(List<String> problems, String problem) {
        if (problems.size() < MAX_REPORTED_PROBLEMS) {
            problems.add(problem);
        }
    }

    private static boolean tryRecoverInstallation(Context context, File gameDirectory) {
        try {
            if (recoverCompletedInstallationAtGameDirectory(gameDirectory)) {
                rememberInstallation(context, gameDirectory);
                return true;
            }
        } catch (IOException exception) {
            Logging.LOG.log(Level.WARNING,
                    "Failed to recover a completed Windward bootstrap installation", exception);
        }
        return false;
    }

    private static void logInspection(
            String message, File versionRoot, InstallationInspection inspection) {
        Logging.LOG.warning(message + " at " + versionRoot + ": " + inspection.describe());
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

    private static final class InstallationInspection {
        private final List<String> problems;

        private InstallationInspection(List<String> problems) {
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }

        private boolean isValid() {
            return problems.isEmpty();
        }

        private List<String> getProblems() {
            return problems;
        }

        private String describe() {
            return problems.isEmpty() ? "no problems" : String.join("; ", problems);
        }
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

    public static final class IncompleteInstallationException extends IOException {
        private final List<String> problems;

        public IncompleteInstallationException(List<String> problems) {
            super("Windward 整合包安装验收失败：" + String.join("；", problems));
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        }

        public List<String> getProblems() {
            return problems;
        }
    }
}
