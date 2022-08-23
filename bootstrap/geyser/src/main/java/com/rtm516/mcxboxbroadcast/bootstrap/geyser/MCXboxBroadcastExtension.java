package com.rtm516.mcxboxbroadcast.bootstrap.geyser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.rtm516.mcxboxbroadcast.core.Logger;
import com.rtm516.mcxboxbroadcast.core.SessionInfo;
import com.rtm516.mcxboxbroadcast.core.SessionManager;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionCreationException;
import com.rtm516.mcxboxbroadcast.core.exceptions.SessionUpdateException;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MCXboxBroadcastExtension extends JavaPlugin implements Listener {
    Logger logger;
    SessionManager sessionManager;
    SessionInfo sessionInfo;
    ExtensionConfig config;

    static String CONFIG_FILE = "config.yml";

    public MCXboxBroadcastExtension() {
    }

    @Override
    public void onEnable() {
        // Pre-check for Geyser-Spigot installation
        if (!Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
            this.getSLF4JLogger().warn("Geyser-Spigot installation is required to work plguin");
            return;
        }

        logger = new ExtensionLoggerImpl(this.getSLF4JLogger());
        sessionManager = new SessionManager(this.getDataFolder().toString(), logger);

        File configFile = new File(this.getDataFolder(), CONFIG_FILE);

        // Create the config file if it doesn't exist
        if (!configFile.exists()) {
            try (FileWriter writer = new FileWriter(configFile)) {
                try (InputStream input = this.getResource(CONFIG_FILE)) {
                    byte[] bytes = new byte[input.available()];

                    input.read(bytes);
                    writer.write(new String(bytes).toCharArray());
                    writer.flush();
                }
            } catch (IOException e) {
                logger.error("Failed to create config", e);
                return;
            }
        }

        try {
            config = new ObjectMapper(new YAMLFactory()).readValue(configFile, ExtensionConfig.class);
        } catch (IOException e) {
            logger.error("Failed to load config", e);
            return;
        }


        // Pull onto another thread so we don't hang the main thread
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            logger.info("Setting up Xbox session...");

            // Get the ip to broadcast
            String ip = config.remoteAddress;
            if (ip.equals("auto")) {
                // Taken from core Geyser code
                logger.error("Currently, auto IP address detection is not implemented");
                return;
            }

            // Get the port to broadcast
            if (config.remotePort.equals("auto")) {
                logger.error("Currently, auto port number detection is not implemented");
                return;
            }
            int port = Integer.parseInt(config.remotePort);

            // Create the session information based on the Geyser config
            sessionInfo = new SessionInfo();
            sessionInfo.setHostName(config.hostName);
            sessionInfo.setWorldName(config.worldName);
            sessionInfo.setPlayers(0);
            sessionInfo.setMaxPlayers(Bukkit.getServer().getMaxPlayers());

            if (!setBedrockDefaultCodec(sessionInfo)) {
                return;
            }

            sessionInfo.setIp(ip);
            sessionInfo.setPort(port);

            // Create the Xbox session
            try {
                sessionManager.createSession(sessionInfo);
                logger.info("Created Xbox session!");
            } catch (SessionCreationException | SessionUpdateException e) {
                logger.error("Failed to create xbox session!", e);
                return;
            }

            // Start the update timer
            Bukkit.getPluginManager().registerEvents(this, this);
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::updateSession, 0, 20 * config.updateInterval);
        });
    }

    private boolean setBedrockDefaultCodec(SessionInfo info) {
        try {
            Class<?> protocol = Class.forName("org.geysermc.geyser.network.MinecraftProtocol");
            Field codecField = protocol.getField("DEFAULT_BEDROCK_CODEC");
            Object codec = codecField.get(null);

            Class<?> codecClass = Class.forName("com.nukkitx.protocol.bedrock.BedrockPacketCodec");
            Method getProtocolVersion = codecClass.getMethod("getProtocolVersion");
            Method getMinecraftVersion = codecClass.getMethod("getMinecraftVersion");

            info.setProtocol((Integer) getProtocolVersion.invoke(codec));
            info.setVersion((String) getMinecraftVersion.invoke(codec));
        } catch (Exception e) {
            logger.error("Version update", e);
            return false;
        }
        return true;
    }

    private void updateSession() {
        // Make sure the connection is still active
        sessionManager.checkConnection();

        // Update the player count for the session
        try {
            sessionInfo.setPlayers(this.getServer().getOnlinePlayers().size());
            sessionManager.updateSession(sessionInfo);
        } catch (SessionUpdateException e) {
            logger.error("Failed to update session information!", e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(this, this::updateSession);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(this, this::updateSession);
    }
}
