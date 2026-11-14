package dev.che1s.betadrop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class RuleEngine {

    private final Map<Key, List<ItemStack>> rules = new HashMap<Key, List<ItemStack>>();
    private final Map<Key, Integer> ruleLines = new HashMap<Key, Integer>();
    private final List<String> diagnostics;

    public RuleEngine(List<String> lines) {
        this(lines, null);
    }

    public RuleEngine(List<String> lines, List<String> diagnostics) {
        this.diagnostics = diagnostics;
        for (int index = 0; index < lines.size(); index++) {
            parseLine(lines.get(index), index + 1);
        }
    }

    private void parseLine(String line, int lineNumber) {
        if (lineNumber == 1 && line.startsWith("\uFEFF")) {
            line = line.substring(1);
        }

        int commentIndex = line.indexOf('#');
        if (commentIndex >= 0) {
            line = line.substring(0, commentIndex);
        }
        line = line.trim();
        if (line.isEmpty()) {
            return;
        }

        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) {
            warning(lineNumber, "expected 3 fields separated by '|' but found " + parts.length);
            return;
        }

        Parsed hand = parseSpec(parts[0]);
        Parsed block = parseSpec(parts[1]);
        if (!validateSpec("held item", hand, lineNumber, 65535)) {
            return;
        }
        if (!validateSpec("block", block, lineNumber, 255)) {
            return;
        }

        List<ItemStack> drops = new ArrayList<ItemStack>();
        String[] items = parts[2].split(",", -1);
        for (String itemSpec : items) {
            String trimmedItemSpec = itemSpec.trim();
            if (trimmedItemSpec.isEmpty()) {
                warning(lineNumber, "empty drop entry");
                continue;
            }

            String[] itemAndAmount = trimmedItemSpec.split("\\*", -1);
            if (itemAndAmount.length > 2) {
                warning(lineNumber, "invalid drop entry '" + trimmedItemSpec + "'");
                continue;
            }

            int amount = 1;
            if (itemAndAmount.length == 2) {
                try {
                    amount = Integer.parseInt(itemAndAmount[1].trim());
                } catch (NumberFormatException exception) {
                    warning(lineNumber, "invalid drop amount in '" + trimmedItemSpec + "'");
                    continue;
                }
            }
            if (amount < 1 || amount > 64) {
                warning(lineNumber, "drop amount must be between 1 and 64 in '" + trimmedItemSpec + "'");
                continue;
            }

            Parsed item = parseSpec(itemAndAmount[0]);
            if (!validateSpec("drop item", item, lineNumber, 65535)) {
                continue;
            }
            int maxStackSize = item.material.getMaxStackSize();
            if (maxStackSize > 0 && amount > maxStackSize) {
                warning(lineNumber, "drop amount " + amount + " exceeds max stack size "
                        + maxStackSize + " for " + item.material.name());
                continue;
            }

            if (item.data != null) {
                drops.add(new ItemStack(item.material, amount, item.data.shortValue()));
            } else {
                drops.add(new ItemStack(item.material, amount));
            }
        }

        if (!drops.isEmpty()) {
            Key key = new Key(hand.material, hand.data, block.material, block.data);
            Integer previousLine = ruleLines.get(key);
            if (previousLine != null) {
                warning(lineNumber, "duplicate rule from line " + previousLine + "; the later rule replaces it");
            }
            rules.put(key, Collections.unmodifiableList(drops));
            ruleLines.put(key, lineNumber);
        }
    }

    int ruleCount() {
        return rules.size();
    }

    public List<ItemStack> findDrops(Material hand, int handData, Material block, int blockData) {
        List<ItemStack> drops = lookup(hand, handData, block, blockData);
        return drops != null ? drops : Collections.<ItemStack>emptyList();
    }

    public boolean hasRule(Material hand, int handData, Material block, int blockData) {
        return lookup(hand, handData, block, blockData) != null;
    }

    private List<ItemStack> lookup(Material hand, int handData, Material block, int blockData) {
        List<ItemStack> drops = rules.get(new Key(hand, handData, block, blockData));
        if (drops != null) {
            return drops;
        }

        drops = rules.get(new Key(hand, handData, block, null));
        if (drops != null) {
            return drops;
        }

        drops = rules.get(new Key(hand, null, block, blockData));
        if (drops != null) {
            return drops;
        }

        return rules.get(new Key(hand, null, block, null));
    }

    private Parsed parseSpec(String value) {
        Parsed parsed = new Parsed();
        if (value == null) {
            parsed.error = "missing material";
            return parsed;
        }

        String spec = value.trim();
        if (spec.isEmpty()) {
            parsed.error = "missing material";
            return parsed;
        }

        String materialName = spec;
        Integer data = null;
        int separator = spec.indexOf(':');
        if (separator >= 0) {
            materialName = spec.substring(0, separator);
            try {
                data = Integer.parseInt(spec.substring(separator + 1).trim());
            } catch (Exception ignored) {
                parsed.error = "invalid data value in '" + spec + "'";
                return parsed;
            }
        }

        parsed.material = parseMaterial(materialName);
        if (parsed.material == null) {
            parsed.error = "unknown material '" + materialName.trim() + "'";
            return parsed;
        }
        parsed.data = data;
        return parsed;
    }

    private boolean validateSpec(String field, Parsed parsed, int lineNumber, int maxData) {
        if (parsed.error != null) {
            warning(lineNumber, field + ": " + parsed.error);
            return false;
        }
        if (parsed.data != null && (parsed.data < 0 || parsed.data > maxData)) {
            warning(lineNumber, field + " data value must be between 0 and " + maxData);
            return false;
        }
        return true;
    }

    private Material parseMaterial(String value) {
        if (value == null) {
            return null;
        }

        String name = value.trim().toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(name);
        if (material != null) {
            return material;
        }

        try {
            int id = Integer.parseInt(name);
            for (Material candidate : Material.values()) {
                if (candidate.getId() == id) {
                    return candidate;
                }
            }
        } catch (NumberFormatException ignored) {
            // Not a legacy numeric material ID.
        }
        return null;
    }

    private void warning(int lineNumber, String message) {
        if (diagnostics != null) {
            diagnostics.add("rules.txt line " + lineNumber + ": " + message);
        }
    }

    private static final class Parsed {
        private Material material;
        private Integer data;
        private String error;
    }

    private static final class Key {
        private final Material hand;
        private final Integer handData;
        private final Material block;
        private final Integer blockData;

        private Key(Material hand, Integer handData, Material block, Integer blockData) {
            this.hand = hand;
            this.handData = handData;
            this.block = block;
            this.blockData = blockData;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Key)) {
                return false;
            }
            Key other = (Key) object;
            return hand == other.hand
                    && Objects.equals(handData, other.handData)
                    && block == other.block
                    && Objects.equals(blockData, other.blockData);
        }

        @Override
        public int hashCode() {
            int result = hand != null ? hand.ordinal() : 0;
            result = 31 * result + (handData != null ? handData.hashCode() : 0);
            result = 31 * result + (block != null ? block.ordinal() : 0);
            result = 31 * result + (blockData != null ? blockData.hashCode() : 0);
            return result;
        }
    }
}
