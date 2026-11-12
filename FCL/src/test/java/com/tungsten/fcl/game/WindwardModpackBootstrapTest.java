package com.tungsten.fcl.game;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.FileUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

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

    private static void createInstallation(File gameDirectory, String state, String fabricVersion)
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
        FileUtils.writeText(new File(root, "modpack.json"),
                JsonUtils.GSON.toJson(configuration));

        JsonObject marker = new JsonObject();
        marker.addProperty("state", state);
        marker.addProperty("assetSha256", WindwardModpackBootstrap.ASSET_SHA256);
        marker.addProperty("name", WindwardModpackBootstrap.PACK_NAME);
        marker.addProperty("version", WindwardModpackBootstrap.PACK_VERSION);
        FileUtils.writeText(new File(root, ".windward-bootstrap.json"),
                JsonUtils.GSON.toJson(marker));
    }
}
