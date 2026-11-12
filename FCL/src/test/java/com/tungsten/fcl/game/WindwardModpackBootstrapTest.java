package com.tungsten.fcl.game;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tungsten.fclcore.util.DigestUtils;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.FileUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class WindwardModpackBootstrapTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exactCompletedInstallationIsAccepted() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("game");
        createInstallation(gameDirectory, "complete", WindwardModpackBootstrap.FABRIC_VERSION);

        assertTrue(WindwardModpackBootstrap.isInstalledAtGameDirectory(gameDirectory));
    }

    @Test
    public void inProgressInstallationIsRejected() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("game");
        createInstallation(gameDirectory, "installing", WindwardModpackBootstrap.FABRIC_VERSION);

        assertFalse(WindwardModpackBootstrap.isInstalledAtGameDirectory(gameDirectory));
    }

    @Test
    public void mismatchedFabricVersionIsRejected() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("game");
        createInstallation(gameDirectory, "complete", "0.0.0");

        assertFalse(WindwardModpackBootstrap.isInstalledAtGameDirectory(gameDirectory));
    }

    @Test
    public void obsoleteModpackJsonNameIsRejected() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("game");
        File root = createInstallation(
                gameDirectory, "complete", WindwardModpackBootstrap.FABRIC_VERSION);
        Files.move(new File(root, "modpack.cfg").toPath(),
                new File(root, "modpack.json").toPath(), StandardCopyOption.REPLACE_EXISTING);

        assertFalse(WindwardModpackBootstrap.isInstalledAtGameDirectory(gameDirectory));
    }

    @Test
    public void completedPayloadWithInstallingMarkerIsRecovered() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("game");
        File root = createInstallation(
                gameDirectory, "installing", WindwardModpackBootstrap.FABRIC_VERSION);
        addPayload(root, true);

        assertTrue(WindwardModpackBootstrap.recoverCompletedInstallationAtGameDirectory(
                gameDirectory));
        assertTrue(WindwardModpackBootstrap.isInstalledAtGameDirectory(gameDirectory));
    }

    @Test
    public void interruptedPayloadIsNotRecovered() throws Exception {
        File gameDirectory = temporaryFolder.newFolder("game");
        File root = createInstallation(
                gameDirectory, "installing", WindwardModpackBootstrap.FABRIC_VERSION);
        addPayload(root, false);

        assertFalse(WindwardModpackBootstrap.recoverCompletedInstallationAtGameDirectory(
                gameDirectory));
        assertFalse(WindwardModpackBootstrap.isInstalledAtGameDirectory(gameDirectory));
    }

    private static File createInstallation(File gameDirectory, String state, String fabricVersion)
            throws Exception {
        File root = new File(new File(gameDirectory, "versions"),
                WindwardModpackBootstrap.VERSION_NAME);
        assertTrue(root.mkdirs());
        assertTrue(new File(root, WindwardModpackBootstrap.VERSION_NAME + ".json").createNewFile());

        JsonObject index = new JsonObject();
        index.addProperty("game", "minecraft");
        index.addProperty("formatVersion", 1);
        index.addProperty("versionId", WindwardModpackBootstrap.PACK_VERSION);
        index.addProperty("name", WindwardModpackBootstrap.PACK_NAME);
        index.add("files", new com.google.gson.JsonArray());
        JsonObject dependencies = new JsonObject();
        dependencies.addProperty("minecraft", WindwardModpackBootstrap.MINECRAFT_VERSION);
        dependencies.addProperty("fabric-loader", fabricVersion);
        index.add("dependencies", dependencies);
        FileUtils.writeText(new File(root, "modrinth.index.json"), JsonUtils.GSON.toJson(index));

        JsonObject configuration = new JsonObject();
        configuration.addProperty("type", "Modrinth");
        configuration.addProperty("name", WindwardModpackBootstrap.PACK_NAME);
        configuration.addProperty("version", WindwardModpackBootstrap.PACK_VERSION);
        configuration.add("overrides", new JsonArray());
        FileUtils.writeText(new File(root, "modpack.cfg"),
                JsonUtils.GSON.toJson(configuration));

        JsonObject marker = new JsonObject();
        marker.addProperty("state", state);
        marker.addProperty("assetSha256", WindwardModpackBootstrap.ASSET_SHA256);
        marker.addProperty("name", WindwardModpackBootstrap.PACK_NAME);
        marker.addProperty("version", WindwardModpackBootstrap.PACK_VERSION);
        FileUtils.writeText(new File(root, ".windward-bootstrap.json"),
                JsonUtils.GSON.toJson(marker));
        return root;
    }

    private static void addPayload(File root, boolean createDownloadedFile) throws Exception {
        byte[] downloadedContent = "downloaded-mod".getBytes(StandardCharsets.UTF_8);
        File downloadedFile = new File(root, "mods/example.jar");
        if (createDownloadedFile) {
            Files.createDirectories(downloadedFile.getParentFile().toPath());
            Files.write(downloadedFile.toPath(), downloadedContent);
        }

        JsonObject index = JsonUtils.GSON.fromJson(
                FileUtils.readText(new File(root, "modrinth.index.json")), JsonObject.class);
        JsonObject manifestFile = new JsonObject();
        manifestFile.addProperty("path", "mods/example.jar");
        manifestFile.addProperty("fileSize", downloadedContent.length);
        JsonObject hashes = new JsonObject();
        hashes.addProperty("sha1", DigestUtils.digestToString("SHA-1", downloadedContent));
        manifestFile.add("hashes", hashes);
        JsonArray downloads = new JsonArray();
        downloads.add("https://example.invalid/example.jar");
        manifestFile.add("downloads", downloads);
        index.getAsJsonArray("files").add(manifestFile);
        FileUtils.writeText(new File(root, "modrinth.index.json"), JsonUtils.GSON.toJson(index));

        byte[] overrideContent = "override".getBytes(StandardCharsets.UTF_8);
        File overrideFile = new File(root, "config/example.txt");
        Files.createDirectories(overrideFile.getParentFile().toPath());
        Files.write(overrideFile.toPath(), overrideContent);
        JsonObject configuration = JsonUtils.GSON.fromJson(
                FileUtils.readText(new File(root, "modpack.cfg")), JsonObject.class);
        JsonObject override = new JsonObject();
        override.addProperty("path", "config/example.txt");
        override.addProperty("hash", DigestUtils.digestToString("SHA-1", overrideFile.toPath()));
        configuration.getAsJsonArray("overrides").add(override);
        FileUtils.writeText(new File(root, "modpack.cfg"),
                JsonUtils.GSON.toJson(configuration));
    }
}
