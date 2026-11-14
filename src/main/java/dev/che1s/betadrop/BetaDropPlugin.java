package dev.che1s.betadrop;

import dev.che1s.betadrop.metrics.BukkitMetrics;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetaDropPlugin extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 35327;

    private RuleEngine engine;
    private BukkitMetrics metrics;

    @Override
    public void onEnable() {
        ensureDataFolderAndRules();
        List<String> lines = loadRuleLines();
        List<String> diagnostics = new ArrayList<String>();
        engine = new RuleEngine(lines, diagnostics);

        for (String diagnostic : diagnostics) {
            getServer().getLogger().warning("BetaDrop: " + diagnostic);
        }
        if (engine.ruleCount() == 0) {
            getServer().getLogger().warning("BetaDrop: no valid rules loaded; custom drops are disabled.");
        } else {
            getServer().getLogger().info("BetaDrop: loaded " + engine.ruleCount() + " rules"
                    + (diagnostics.isEmpty() ? "." : " with " + diagnostics.size() + " warning(s)."));
        }

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this, engine), this);
        startMetrics();
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        engine = null;
    }

    private void startMetrics() {
        try {
            metrics = new BukkitMetrics(this, BSTATS_PLUGIN_ID);
        } catch (Exception | LinkageError exception) {
            metrics = null;
            getServer().getLogger().warning(
                    "BetaDrop: could not initialize bStats: " + exception.getMessage());
        }
    }

    private List<String> loadRuleLines() {
        List<String> lines = new ArrayList<String>();
        File file = new File(getDataFolder(), "rules.txt");
        InputStream in = null;

        try {
            in = file.exists()
                    ? new FileInputStream(file)
                    : BetaDropPlugin.class.getClassLoader().getResourceAsStream("rules.txt");
        } catch (Exception exception) {
            getServer().getLogger().warning("Could not open rules.txt: " + exception.getMessage());
        }

        if (in == null) {
            return lines;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception exception) {
            getServer().getLogger().warning("Could not read rules.txt: " + exception.getMessage());
        }

        return lines;
    }

    private void ensureDataFolderAndRules() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getServer().getLogger().warning("Could not create plugin data folder: " + dataFolder);
            return;
        }

        File rulesFile = new File(dataFolder, "rules.txt");
        if (rulesFile.exists()) {
            return;
        }

        try (InputStream in = BetaDropPlugin.class.getClassLoader().getResourceAsStream("rules.txt")) {
            if (in != null) {
                Files.copy(in, rulesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            getServer().getLogger().warning("Could not create default rules.txt: " + exception.getMessage());
        }
    }
}
