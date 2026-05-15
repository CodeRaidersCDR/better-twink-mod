package com.minemods.bettertwink.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.minemods.bettertwink.client.storage.ConfigurationPersistence;

import java.util.*;

/**
 * Менеджер для сохранения и загрузки конфигураций по серверам
 */
public class ConfigurationManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ConfigurationManager INSTANCE;
    
    private Map<String, ServerConfiguration> serverConfigs;
    private String currentServer;

    private ConfigurationManager() {
        this.serverConfigs = new HashMap<>();
        this.currentServer = "default";
    }

    public static ConfigurationManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ConfigurationManager();
        }
        return INSTANCE;
    }

    public void setCurrentServer(String serverAddress, String serverName, String playerName) {
        // Unique key = serverAddress@playerName so each account has its own config on that server
        String key = serverAddress + "@" + playerName;
        this.currentServer = key;

        if (!serverConfigs.containsKey(key)) {
            ServerConfiguration cfg = new ServerConfiguration(serverName, serverAddress, playerName);
            serverConfigs.put(key, cfg);
            LOGGER.info("Created new server configuration for: {} (account: {})", serverAddress, playerName);
        }
    }

    /** Legacy overload — uses empty player name (keeps old saved data accessible). */
    public void setCurrentServer(String serverAddress, String serverName) {
        setCurrentServer(serverAddress, serverName, "");
    }

    public ServerConfiguration getCurrentServerConfig() {
        // computeIfAbsent stores and returns the new config — prevents throwaway objects
        return serverConfigs.computeIfAbsent(currentServer,
                k -> new ServerConfiguration(k, k));
    }

    public String getCurrentServer() {
        return currentServer;
    }

    public ServerConfiguration getServerConfig(String serverAddress) {
        return serverConfigs.get(serverAddress);
    }

    public Map<String, ServerConfiguration> getAllServerConfigs() {
        return new HashMap<>(serverConfigs);
    }

    public void saveCurrentServer() {
        getCurrentServerConfig(); // ensure entry exists in map
        ConfigurationPersistence.saveConfigurations();
        LOGGER.info("Saved configuration for server: {}", currentServer);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CurrentServer", currentServer);

        ListTag serversTag = new ListTag();
        for (ServerConfiguration config : serverConfigs.values()) {
            serversTag.add(config.serializeNBT());
        }
        tag.put("Servers", serversTag);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        this.currentServer = tag.getString("CurrentServer");
        this.serverConfigs.clear();

        ListTag serversTag = tag.getList("Servers", Tag.TAG_COMPOUND);
        for (int i = 0; i < serversTag.size(); i++) {
            ServerConfiguration config = ServerConfiguration.deserializeNBT(serversTag.getCompound(i));
            // Reconstruct the composite key (address@player) used as map key
            String key = config.getServerAddress() + "@" + config.getPlayerName();
            serverConfigs.put(key, config);
        }

        LOGGER.info("Loaded {} server configurations", serverConfigs.size());
    }
}
