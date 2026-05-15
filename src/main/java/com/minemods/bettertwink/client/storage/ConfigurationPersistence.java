package com.minemods.bettertwink.client.storage;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.logging.LogUtils;
import com.minemods.bettertwink.data.ConfigurationManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * Управление сохранением и загрузкой конфигураций на диск
 */
@Mod.EventBusSubscriber(modid = "bettertwink", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ConfigurationPersistence {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONFIG_FOLDER = "config/bettertwink";
    private static final String CONFIG_FILE = "configurations.nbt";

    /**
     * Загружает конфигурации с диска
     */
    public static void loadConfigurations() {
        try {
            File configFile = getConfigFile();
            
            if (configFile.exists()) {
                CompoundTag tag = NbtIo.read(configFile);
                if (tag != null) {
                    ConfigurationManager.getInstance().deserializeNBT(tag);
                    LOGGER.info("Loaded configurations from disk");
                } else {
                    LOGGER.warn("Config file is empty or corrupted");
                }
            } else {
                LOGGER.info("No existing configurations found, creating new");
                ensureConfigFolder();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load configurations", e);
        }
    }

    /**
     * Сохраняет конфигурации на диск
     */
    public static void saveConfigurations() {
        try {
            ensureConfigFolder();
            File configFile = getConfigFile();
            
            CompoundTag tag = ConfigurationManager.getInstance().serializeNBT();
            NbtIo.write(tag, configFile);
            LOGGER.info("Saved configurations to disk");
        } catch (IOException e) {
            LOGGER.error("Failed to save configurations", e);
        }
    }

    /**
     * Получает файл конфигурации
     */
    private static File getConfigFile() {
        return new File(getConfigFolder(), CONFIG_FILE);
    }

    /**
     * Получает папку конфигурации
     */
    private static File getConfigFolder() {
        return new File(Minecraft.getInstance().gameDirectory, CONFIG_FOLDER);
    }

    /**
     * Убеждаемся, что папка конфигурации существует
     */
    private static void ensureConfigFolder() {
        File folder = getConfigFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    /**
     * Экспортирует конфигурацию в файл
     */
    public static void exportConfiguration(String serverAddress, File targetFile) {
        try {
            ConfigurationManager mgr = ConfigurationManager.getInstance();
            var serverConfig = mgr.getServerConfig(serverAddress);
            
            if (serverConfig != null) {
                CompoundTag tag = serverConfig.serializeNBT();
                NbtIo.write(tag, targetFile);
                LOGGER.info("Exported configuration for server: {}", serverAddress);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to export configuration", e);
        }
    }

    /**
     * Импортирует конфигурацию из файла
     */
    public static void importConfiguration(File sourceFile) {
        try {
            CompoundTag tag = NbtIo.read(sourceFile);
            if (tag != null) {
                ConfigurationManager.getInstance().saveCurrentServer();
                LOGGER.info("Imported configuration");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to import configuration", e);
        }
    }
}
