package dev.che1s.betadrop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

public class RuleEngineTest {

    @Test
    public void matchesNamedMaterialsAndAmounts() {
        RuleEngine engine = new RuleEngine(Arrays.asList(
                "WOOD_PICKAXE|STONE|COBBLESTONE*2,FLINT*1"));

        assertTrue(engine.hasRule(Material.WOOD_PICKAXE, 0, Material.STONE, 0));
        List<ItemStack> drops = engine.findDrops(Material.WOOD_PICKAXE, 0, Material.STONE, 0);

        assertEquals(2, drops.size());
        assertEquals(Material.COBBLESTONE, drops.get(0).getType());
        assertEquals(2, drops.get(0).getAmount());
        assertEquals(Material.FLINT, drops.get(1).getType());
        assertEquals(1, drops.get(1).getAmount());
    }

    @Test
    public void blockDataRuleOverridesWildcardRule() {
        RuleEngine engine = new RuleEngine(Arrays.asList(
                "WOOD_PICKAXE|35|35*1",
                "WOOD_PICKAXE|35:14|35:15*1"));

        assertTrue(engine.hasRule(Material.WOOD_PICKAXE, 0, byId(35), 0));
        assertTrue(engine.hasRule(Material.WOOD_PICKAXE, 0, byId(35), 14));

        List<ItemStack> generic = engine.findDrops(Material.WOOD_PICKAXE, 0, byId(35), 0);
        List<ItemStack> specific = engine.findDrops(Material.WOOD_PICKAXE, 0, byId(35), 14);

        assertEquals(1, generic.size());
        assertEquals(0, generic.get(0).getDurability());
        assertEquals(1, specific.size());
        assertEquals(15, specific.get(0).getDurability());
    }

    @Test
    public void glowingRedstoneRuleDropsNormalRedstoneOre() {
        RuleEngine engine = new RuleEngine(Arrays.asList("WOOD_PICKAXE|74|73*1"));

        assertFalse(engine.hasRule(
                Material.WOOD_PICKAXE, 0, Material.REDSTONE_ORE, 0));
        assertTrue(engine.hasRule(
                Material.WOOD_PICKAXE, 0, Material.GLOWING_REDSTONE_ORE, 0));
        assertEquals(Material.REDSTONE_ORE, engine.findDrops(
                Material.WOOD_PICKAXE, 0, Material.GLOWING_REDSTONE_ORE, 0)
                .get(0).getType());
    }

    @Test
    public void returnsEmptyListForUnknownRule() {
        RuleEngine engine = new RuleEngine(Arrays.asList("STONE|DIRT|DIAMOND*1"));

        assertFalse(engine.hasRule(Material.WOOD_PICKAXE, 0, Material.STONE, 0));
        assertTrue(engine.findDrops(Material.WOOD_PICKAXE, 0, Material.STONE, 0).isEmpty());
    }

    @Test
    public void ignoresMalformedRuleLines() {
        RuleEngine engine = new RuleEngine(Arrays.asList(
                "INVALID LINE",
                "UNKNOWN_MATERIAL|STONE|DIRT*1"));

        assertFalse(engine.hasRule(Material.AIR, 0, Material.STONE, 0));
    }

    @Test
    public void ignoresBilingualTrailingComments() {
        RuleEngine engine = new RuleEngine(Arrays.asList(
                "WOOD_PICKAXE|STONE|COBBLESTONE*2 # 木镐采石头 / Wood pickaxe on stone",
                "# 整行注释 / Full-line comment",
                ""));

        assertTrue(engine.hasRule(Material.WOOD_PICKAXE, 0, Material.STONE, 0));
        assertEquals(2, engine.findDrops(
                Material.WOOD_PICKAXE, 0, Material.STONE, 0).get(0).getAmount());
        assertEquals(1, engine.ruleCount());
    }

    @Test
    public void defaultRulesCanBeLoaded() throws Exception {
        List<String> lines = java.nio.file.Files.readAllLines(
                java.nio.file.Paths.get("src/main/resources/rules.txt"),
                java.nio.charset.StandardCharsets.UTF_8);
        List<String> diagnostics = new ArrayList<String>();

        assertTrue(new RuleEngine(lines, diagnostics).ruleCount() > 0);
        assertTrue(diagnostics.toString(), diagnostics.isEmpty());
    }

    @Test
    public void reportsInvalidAmountsWithLineNumbers() {
        List<String> diagnostics = new ArrayList<String>();
        RuleEngine engine = new RuleEngine(Arrays.asList(
                "WOOD_PICKAXE|STONE|COBBLESTONE*0",
                "WOOD_PICKAXE|COBBLESTONE|FLINT*65",
                "WOOD_PICKAXE|DIRT|DIRT*2"), diagnostics);

        assertEquals(1, engine.ruleCount());
        assertEquals(2, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("line 1"));
        assertTrue(diagnostics.get(0).contains("between 1 and 64"));
        assertTrue(diagnostics.get(1).contains("line 2"));
    }

    @Test
    public void reportsDuplicateRulesAndOverridesEarlierRule() {
        List<String> diagnostics = new ArrayList<String>();
        RuleEngine engine = new RuleEngine(Arrays.asList(
                "WOOD_PICKAXE|STONE|COBBLESTONE*1",
                "WOOD_PICKAXE|STONE|FLINT*2"), diagnostics);

        assertEquals(1, engine.ruleCount());
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("line 2"));
        assertTrue(diagnostics.get(0).contains("line 1"));
        assertEquals(Material.FLINT, engine.findDrops(
                Material.WOOD_PICKAXE, 0, Material.STONE, 0).get(0).getType());
    }

    private static Material byId(int id) {
        for (Material material : Material.values()) {
            if (material.getId() == id) {
                return material;
            }
        }
        throw new AssertionError("Unknown material ID " + id);
    }
}
