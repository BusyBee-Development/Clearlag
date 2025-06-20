package me.minebuilders.clearlag.config;

import me.minebuilders.clearlag.Clearlag;
import me.minebuilders.clearlag.Util;
import me.minebuilders.clearlag.annotations.ConfigModule;
import me.minebuilders.clearlag.annotations.ConfigPath;
import me.minebuilders.clearlag.annotations.ConfigValue;
import me.minebuilders.clearlag.config.configupdater.ConfigUpdater;
import me.minebuilders.clearlag.config.configvalues.ConfigData;
import me.minebuilders.clearlag.config.configvalues.PrimitiveCV;
import me.minebuilders.clearlag.modules.Module;
import me.minebuilders.clearlag.reflection.ReflectionUtil;
import me.minebuilders.clearlag.tasks.WarnTask;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

public class ConfigHandler {

    private Configuration config;

    private final int version = 20;

    public Configuration getConfig() {
        return config;
    }

    public ConfigHandler() {

        final Filter currentFilter = Clearlag.getInstance().getLogger().getFilter();

        final AtomicBoolean configLoadFailed = new AtomicBoolean(false);

        try {

            //Ridicious..
            Clearlag.getInstance().getServer().getLogger().setFilter(new Filter() {

                @Override
                public boolean isLoggable(LogRecord record) {

                    if (record.getMessage().contains("ClearLag"))
                        configLoadFailed.set(true);

                    return true;
                }
            });

            config = Clearlag.getInstance().getConfig();

            if (configLoadFailed.get()) {

                Util.warning("Clearlag failed to load to your config. Clearlag will now attempt to repair your config by force-updating it...");

                try {

                    updateConfig();

                    Clearlag.getInstance().reloadConfig();

                    config = Clearlag.getInstance().getConfig();

                } catch (Exception e) {
                    Util.warning("Clearlag failed to force-update your config. Using the default config - please repair your configuration using https://yaml-online-parser.appspot.com/");
                    throw e;
                }
            }

            Clearlag.getInstance().getServer().getLogger().setFilter(currentFilter);

        } catch (Exception e) {
            config = Clearlag.getInstance().getConfig();
        }

        if (!new File(Clearlag.getInstance().getDataFolder(), "config.yml").exists()) {
            Util.log("Config not found. Generating default config...");
            Clearlag.getInstance().saveDefaultConfig();

        } else if (!isConfigUpdated()) {

            boolean resetConfig = false;

            if (config.getBoolean("config-updater.force-update") && !isConfigUpdated()) {

                resetConfig("Old-Config.yml");

                resetConfig = true;

            } else if (!isConfigUpdated()) {

                try {
                    updateConfig();
                } catch (Exception e) {
                    Util.warning("Clearlag was unable to update your configuration and was forced to rename your current config and create a new one!");
                    Util.warning("Please run your config through a parser to check for errors: http://yaml-online-parser.appspot.com/");

                    resetConfig("Invalid-Config.yml");

                    resetConfig = true;

                    e.printStackTrace();
                }
            }

            WarnTask warnTask = new WarnTask(resetConfig);

            warnTask.setEnabled();
        }

        reloadConfig();
    }

    public boolean isConfigUpdated() {
        return (config.isSet("config-version") && Util.isInteger(config.getString("config-version")) && (config.getInt("config-version") >= version));
    }

    public String fieldToConfigValue(Field field) {
        return javaToConfigValue(field.getName());
    }

    public String javaToConfigValue(String str) {

        StringBuilder sb = new StringBuilder();

        for (char c : str.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append("-");
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public void setModuleConfigValues() throws Exception {

        for (Module module : Clearlag.getModules()) {

            if (module.isEnabled()) {
                setObjectConfigValues(module);
            }
        }
    }

    public void setObjectConfigValues(Object object) throws Exception {

        String path = null;

        final ConfigPath configPath = object.getClass().getAnnotation(ConfigPath.class);

        if (configPath != null) {
            path = configPath.path();
        }

        setObjectConfigValues(object, path);
    }

    public void setObjectConfigValues(Object object, String path) throws Exception {//Todo: Apply config setting to objects too

        Class<?> clazz = object.getClass();

        while (clazz != null && clazz != Object.class && clazz != Module.class) {

            for (Field field : clazz.getDeclaredFields()) {

                final ConfigValue configValue = field.getAnnotation(ConfigValue.class);
                final ConfigModule configModule = field.getAnnotation(ConfigModule.class);

                if (configValue != null) {

                    field.setAccessible(true);

                    ConfigData cd = configValue.valueType().getConfigData();

                    String configPath;

                    // First check if a ConfigKey is specified
                    if (configValue.key() != ConfigKey.NONE) {
                        configPath = configValue.key().getPath();
                    } 
                    // Then check if a legacy path is specified
                    else if (!configValue.legacyPath().isEmpty()) {
                        configPath = configValue.legacyPath();
                    }
                    // Finally fall back to the original path or generate one from the field name
                    else {
                        configPath = configValue.path().length() <= 1 ? path + "." + fieldToConfigValue(field) : configValue.path();
                    }

                    Object ob = cd.getValue(configPath);

                    if (ob == null) {
                        Object tp = field.get(object);

                        if (tp != null)
                            ob = tp.getClass().getDeclaredConstructor().newInstance();
                    }

                    field.set(object, (cd instanceof PrimitiveCV ? ReflectionUtil.castPrimitedValues(field.getType(), ob) : ob));

                }

                if (configModule != null) {
                    field.setAccessible(true);

                    setObjectConfigValues(field.get(object));
                }
            }

            clazz = clazz.getSuperclass();
        }
    }

    public static boolean containsReloadableFields(Object ob) {
        for (Field field : ob.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(ConfigValue.class) || field.isAnnotationPresent(ConfigModule.class)) {
                return true;
            }
        }
        return false;
    }

    private void resetConfig(String renamedName) {
        renameCurrentConfig(renamedName);

        Clearlag.getInstance().saveDefaultConfig();

        Clearlag.getInstance().reloadConfig();
    }

    public void reloadConfig() {
        Clearlag.getInstance().reloadConfig();
        config = Clearlag.getInstance().getConfig();
    }

    /**
     * Get a configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @param <T> The expected return type
     * @return The configuration value
     */
    @SuppressWarnings("unchecked")
    public <T> T get(ConfigKey path) {
        return (T) config.get(path.getPath());
    }

    /**
     * Get a boolean configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The boolean value
     */
    public boolean getBoolean(ConfigKey path) {
        return config.getBoolean(path.getPath());
    }

    /**
     * Get an integer configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The integer value
     */
    public int getInt(ConfigKey path) {
        return config.getInt(path.getPath());
    }

    /**
     * Get a string configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The string value
     */
    public String getString(ConfigKey path) {
        return config.getString(path.getPath());
    }

    /**
     * Get a double configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The double value
     */
    public double getDouble(ConfigKey path) {
        return config.getDouble(path.getPath());
    }

    /**
     * Get a long configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The long value
     */
    public long getLong(ConfigKey path) {
        return config.getLong(path.getPath());
    }

    /**
     * Get a list configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The list value
     */
    public List<?> getList(ConfigKey path) {
        return config.getList(path.getPath());
    }

    /**
     * Get a string list configuration value using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The string list value
     */
    public List<String> getStringList(ConfigKey path) {
        return config.getStringList(path.getPath());
    }

    /**
     * Get a configuration section using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return The configuration section
     */
    public ConfigurationSection getConfigurationSection(ConfigKey path) {
        return config.getConfigurationSection(path.getPath());
    }

    /**
     * Check if a configuration path exists using a ConfigKey enum
     * @param path The ConfigKey enum value
     * @return True if the path exists
     */
    public boolean isSet(ConfigKey path) {
        return config.isSet(path.getPath());
    }

    private void renameCurrentConfig(String newName) {

        final File newf = new File(Clearlag.getInstance().getDataFolder().getAbsolutePath(), "config.yml");
        final File oldf = new File(Clearlag.getInstance().getDataFolder().getAbsolutePath(), newName);

        if (oldf.isFile())
            oldf.delete();

        if (newf.isFile())
            newf.renameTo(new File(Clearlag.getInstance().getDataFolder().getAbsolutePath(), newName));
    }

    private void updateConfig() throws Exception {
        Util.log("Updating config to v" + Clearlag.getInstance().getDescription().getVersion() + "...");

        final File file = new File(Clearlag.getInstance().getDataFolder() + "/config.yml");

        final ConfigUpdater configUpdater = new ConfigUpdater();

        configUpdater.addCarriedOverPath("custom-trigger-removal");
        configUpdater.addCarriedOverPath("item-spawn-age-setter");

        configUpdater.addNonMergableKey("config-version");

        configUpdater.setUpdatingToConfig(Clearlag.getInstance().getResource("config.yml"));
        configUpdater.setUpdatingFromConfig(new FileInputStream(Clearlag.getInstance().getDataFolder() + "/config.yml"));

        renameCurrentConfig("before-update-config.yml");

        configUpdater.updateConfig(file);

        Util.log("Successfully updated config to v" + Clearlag.getInstance().getDescription().getVersion() + "!");
    }
}
