package me.samboycoding.thermaltinkering;

import cofh.api.util.ThermalExpansionHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.smeltery.Cast;
import slimeknights.tconstruct.library.tools.IToolPart;
import slimeknights.tconstruct.library.tools.ToolPart;
import slimeknights.tconstruct.shared.TinkerFluids;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mod(modid = ThermalTinkering.MODID, name = ThermalTinkering.NAME, version = ThermalTinkering.VERSION, dependencies = ThermalTinkering.DEPENDENCY_STRING)
public class ThermalTinkering {
    static final String MODID = "thermaltinkering";
    static final String VERSION = "1.0";
    static final String NAME = "Thermal Tinkering";
    static final String DEPENDENCY_STRING = "" // This is just here for formatting
            + "required-after:mantle;"
            + "required-before:thermalexpansion;"
            + "required-after:thermalfoundation;"
            + "required-after:cofhcore;";

    private Logger log;
    private List<String> defaultExceptions = Arrays.asList("glass.molten", "obsidian.molten", "redstone.molten");

    private List<String> exceptionFluidIDs;
    private List<String> whitelistFluidIDs;
    private File fluidDumpFile;
    private File moltenDumpFile;

    private Configuration cfg;

    @Mod.EventHandler
    public void PreInit(FMLPreInitializationEvent e) throws Exception {
        log = e.getModLog();


        File configDirectory = new File(e.getModConfigurationDirectory(), "/ThermalTinkering/");
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }

        File configFile = new File(configDirectory, "config.cfg");
        if (!configFile.exists()) {
            configFile.createNewFile();
        }

        fluidDumpFile = new File(configDirectory, "fluids.cfg");
        if (fluidDumpFile.exists()) {
            fluidDumpFile.delete(); // Remove old version to be repopulated
        }
        fluidDumpFile.createNewFile();

        moltenDumpFile = new File(configDirectory, "moltenFluids.cfg");
        if (moltenDumpFile.exists()) {
            moltenDumpFile.delete();
        }
        moltenDumpFile.createNewFile();

        cfg = new Configuration(configFile);
        cfg.load();

        exceptionFluidIDs = new ArrayList<>(Arrays.asList(cfg.getStringList("blacklist", "main", defaultExceptions.toArray(new String[0]), "List of fluids to completely ignore.")));
        whitelistFluidIDs = new ArrayList<>(Arrays.asList(cfg.getStringList("addFluids", "main", new String[]{}, "List of fluids to explicitly add. Only works if there is already a recipe - just add any we miss.")));

        cfg.save();

        log.info("Pre Init Complete");
    }

    @Mod.EventHandler
    public void Init(FMLInitializationEvent event) throws Exception {
        try {
            // Map the blacklist fluid IDs to actual fluids
            List<Fluid> exceptions = exceptionFluidIDs.stream().filter(id -> FluidRegistry.getFluid(id) != null)
                    .map(FluidRegistry::getFluid)
                    .collect(Collectors.toList());

            // Rebuild the blacklist from the fluids that are actually registered.
            exceptionFluidIDs = exceptions
                    .stream()
                    .map(Fluid::getName)
                    .collect(Collectors.toList());

            log.info("Created exception list of " + exceptions.size() + " fluids.");

            // Save the blacklist to config.
            cfg.get("main", "blacklist", defaultExceptions.toArray(new String[0])).set(exceptionFluidIDs.toArray(new String[0]));
            cfg.save();

            log.info("Loading fluid list...");

            log.debug("All loaded liquids: " + FluidRegistry.getRegisteredFluids().entrySet());
            log.info("Loading molten metals...");

            //#region Fluid Dumps
            List<Fluid> allMoltenFluids = TinkerRegistry.getAllMaterials()
                    .stream()
                    .filter(mat -> mat.isCastable() && mat.getFluid() != null)
                    .map(Material::getFluid)
                    .collect(Collectors.toList());

            StringBuilder allLiquidsDump = new StringBuilder("#This is a simple dump of all loaded liquids, in raw form, with their block names (in brackets) and their localized names, in the format of:\n\n#FLUIDID \t\t\t\t\t\t\t\t\t\t  (FLUIDBLOCK)\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t   = FLUIDLOCALIZEDNAME\n\n");
            StringBuilder allMoltenDump = new StringBuilder("#This is a simple dump of all molten liquids, in raw form, with their block names (in brackets) and their localized names, in the format of:\n\n#FLUIDID \t\t\t\t\t\t\t\t\t\t  (FLUIDBLOCK)\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t   = FLUIDLOCALIZEDNAME\n\n");

            for (Map.Entry<String, Fluid> stringFluidEntry : FluidRegistry.getRegisteredFluids().entrySet()) {
                String id = stringFluidEntry.getKey();
                Fluid fluid = stringFluidEntry.getValue();

                String blockName = fluid.getBlock() != null ? fluid.getBlock().getRegistryName().toString() : "[no block]";

                String spaces = StringUtils.repeat(' ', 130 - blockName.length() - id.length() - 2 - (50 - id.length()));
                String spaces2 = StringUtils.repeat(' ', 50 - id.length());

                allLiquidsDump.append(id).append(spaces2).append("(").append(blockName).append(")").append(spaces).append(" = ").append(fluid.getLocalizedName(new FluidStack(fluid, 1))).append("\n");
            }

            // Dump molten fluids.
            allMoltenFluids.forEach(fl -> {
                String id = fl.getName();
                String blockName = fl.getBlock() != null ? fl.getBlock().getRegistryName().toString() : "[no block]";

                String spaces = StringUtils.repeat(' ', 130 - blockName.length() - id.length() - 2 - (50 - id.length()));
                String spaces2 = StringUtils.repeat(' ', 50 - id.length());
                allMoltenDump.append(id).append(spaces2).append("(").append(blockName).append(")").append(spaces).append(" = ").append(fl.getLocalizedName(new FluidStack(fl, 1))).append("\n");
            });

            PrintWriter writer = new PrintWriter(fluidDumpFile);
            writer.print(allLiquidsDump);
            writer.close();

            log.info("Successfully written loaded fluid dump to " + fluidDumpFile.getAbsolutePath());

            writer = new PrintWriter(moltenDumpFile);
            writer.print(allMoltenDump);
            writer.close();

            log.info("Successfully written molten fluid dump to " + moltenDumpFile.getAbsolutePath());
            //#endregion

            // Map the whitelist fluid IDs to actual fluids
            List<Fluid> whitelist = whitelistFluidIDs.stream().filter(id -> FluidRegistry.getFluid(id) != null)
                    .map(FluidRegistry::getFluid)
                    .collect(Collectors.toList());

            // Rebuild the whitelist from the fluids that are actually registered.
            whitelistFluidIDs = whitelist
                    .stream()
                    .map(Fluid::getName)
                    .collect(Collectors.toList());

            log.info("Will add an additional " + whitelist.size() + " fluids to processing list based on whitelist.");

            cfg.get("main", "addFluids", new String[]{}).set(whitelistFluidIDs.toArray(new String[0]));
            cfg.save();

            log.info("Done loading - adding stuff");

            ItemStack ingotCast = TinkerSmeltery.castIngot;

            // Ingots
            for (Material mat : TinkerRegistry.getAllMaterials()) {
                if (!mat.isCastable() || mat.getFluid() == null) continue;

                Fluid fluid = mat.getFluid();
                if (exceptions.contains(fluid))
                    continue;

                String fluidId = fluid.getName();

                String oreDictName = StringUtils.capitalize(fluidId.replace(".molten", ""));

                for (ItemStack ingotStack : OreDictionary.getOres("ingot" + oreDictName)) {
                    if (ingotStack == null) {
                        log.error("Fluid " + fluidId + " should have an ingot \"ingot" + fluidId + "\" but doesn't.");
                        continue;
                    }
                    if (ingotStack.getDisplayName().isEmpty()) {
                        log.error(ingotStack + " does not have a display name?");
                        continue;
                    }
                    if (fluid.getName() == null || fluid.getName().isEmpty()) {
                        log.error("Fluid \"" + fluid + "\" doesn't have a name?");
                        continue;
                    }

                    log.info("Mapping " + ingotStack.getDisplayName() + " <=> " + fluid.getLocalizedName(new FluidStack(fluid, 1)));

                    // Ingot casts
                    ThermalExpansionHelper.addTransposerFill(800, ingotStack, ingotCast, new FluidStack(TinkerFluids.alubrass, Material.VALUE_Ingot), false);
                    ThermalExpansionHelper.addTransposerFill(800, ingotStack, ingotCast, new FluidStack(TinkerFluids.gold, Material.VALUE_Ingot * 2), false);

                    // Melting ingots
                    ThermalExpansionHelper.addCrucibleRecipe(5000, new ItemStack(ingotStack.getItem(), 1, ingotStack.getItemDamage()), new FluidStack(fluid, Material.VALUE_Ingot));

                    // Casting ingots
                    ThermalExpansionHelper.addTransposerFill(800, ingotCast, ingotStack, new FluidStack(fluid, Material.VALUE_Ingot), false);
                }
            }

            FluidStack oneIngotOfMoltenAluBrass = new FluidStack(TinkerFluids.alubrass, Material.VALUE_Ingot);
            FluidStack twoIngotsOfMoltenGold = new FluidStack(TinkerFluids.gold, Material.VALUE_Ingot * 2);

            for (IToolPart iTp : TinkerRegistry.getToolParts()) {
                if (!iTp.canBeCasted() || !(iTp instanceof ToolPart)) {
                    continue;
                }
                ToolPart toolPart = (ToolPart) iTp;
                log.info("Adding recipes for part " + toolPart.getItemStackDisplayName(new ItemStack(toolPart, 1)) + " (cost " + toolPart.getCost() + "mB)");
                for (Material mat : TinkerRegistry.getAllMaterials()) {
                    if (!mat.isCastable() || !toolPart.canUseMaterial(mat)) continue;

                    //Making the casts
                    ItemStack part = toolPart.getItemstackWithMaterial(mat);

                    ItemStack cast = new ItemStack(TinkerSmeltery.cast);
                    Cast.setTagForPart(cast, part.getItem());

                    log.info("Adding cast recipe: " + part.getDisplayName() + " => " + cast.getDisplayName(), 1, 0);
                    // Aluminum brass
                    ThermalExpansionHelper.addTransposerFill(800, part, cast, oneIngotOfMoltenAluBrass, false);
                    // Gold
                    ThermalExpansionHelper.addTransposerFill(800, part, cast, twoIngotsOfMoltenGold, false);

                    // Now using the casts
                    Fluid fluid = mat.getFluid();
                    //The temperature this part would melt at in the smeltery, given its fluid temperature, and the amount of fluid the part is worth
                    int smelteryMeltingTemp = calcTemperature(fluid.getTemperature(), toolPart.getCost());

                    log.info("Adding transposer recipe: " + fluid.getLocalizedName(new FluidStack(fluid, 1)) + " + " + cast.getDisplayName() + " => " + part.getDisplayName() + " with " + (smelteryMeltingTemp * 1.5f) + " RF", 1, 0);
                    // Make {part} from {toolPart.getCost()} millibuckets of {fluid} using {cast} and {melting temperature * 1.5} RF
                    ThermalExpansionHelper.addTransposerFill((int) (smelteryMeltingTemp * 1.5f), cast, part, new FluidStack(fluid, toolPart.getCost()), false);

                    log.info("Adding magma crucible recipe: " + part.getDisplayName() + " => " + fluid.getLocalizedName(new FluidStack(fluid, 1)) + " with " + (smelteryMeltingTemp * 1.5f) + " RF", 1, 0);
                    // Melt {part} into {toolPart.getCost()} millibuckets of {fluid}
                    ThermalExpansionHelper.addCrucibleRecipe((int) (smelteryMeltingTemp * 1.5f), part, new FluidStack(fluid, toolPart.getCost()));

                }
            }
            log.info("Successfully added recipes for " + TinkerRegistry.getToolParts().size() + " parts.");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // Lifted from the TiCon source code (MeltingRecipe#calcTemperature)
    private static int calcTemperature(int temp, int timeAmount) {
        int base = Material.VALUE_Block;
        int max_tmp = Math.max(0, temp - 300); // we use 0 as baseline, not 300
        double f = (double) timeAmount / (double) base;

        // we calculate 2^log9(f), which effectively gives us 2^(1 for each multiple of 9)
        // we simplify it to f^log9(2) to make calculation simpler
        f = Math.pow(f, 0.31546487678);

        return 300 + (int) (f * (double) max_tmp);
    }
}
