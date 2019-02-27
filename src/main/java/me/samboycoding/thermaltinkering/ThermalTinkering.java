package me.samboycoding.thermaltinkering;

import cofh.api.util.ThermalExpansionHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
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
import java.util.stream.Collectors;

@Mod(modid = ThermalTinkering.MODID, name = ThermalTinkering.NAME, version = ThermalTinkering.VERSION, dependencies = ThermalTinkering.DEPENDENCY_STRING)
public class ThermalTinkering {
    static final String MODID = "thermaltinkering";
    static final String VERSION = "1.0";
    static final String NAME = "Thermal Tinkering";
    static final String DEPENDENCY_STRING = "" // This is just here for formatting
            + "required-after:mantle;"
            + "required-after:tconstruct;"
            + "required-before:thermalexpansion;"
            + "required-after:thermalfoundation;"
            + "required-after:cofhcore@[4.6.2,);";

    private Logger log;

    private List<String> exceptionFluidIDs;
    private File fluidDumpFile;
    private File moltenDumpFile;

    private Configuration cfg;

    private int partMeltingMultiplier;
    private int partCastingMultiplier;
    private int castCreationCost;
    private int ingotMeltingMultiplier;
    private int ingotCastingMultiplier;

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

        fluidDumpFile = new File(configDirectory, "fluids.txt");
        if (fluidDumpFile.exists()) {
            fluidDumpFile.delete(); // Remove old version to be repopulated
        }
        fluidDumpFile.createNewFile();

        moltenDumpFile = new File(configDirectory, "moltenFluids.txt");
        if (moltenDumpFile.exists()) {
            moltenDumpFile.delete();
        }
        moltenDumpFile.createNewFile();

        cfg = new Configuration(configFile);
        cfg.load();

        exceptionFluidIDs = new ArrayList<>(Arrays.asList(cfg.getStringList("blacklist", "main", new String[] {"obsidian"}, "List of fluids to NOT add recipes for.")));

        castCreationCost = cfg.getInt("castCreationCost", "main", 8000, 1000, 50000, "The amount of energy, in RF, required to make a cast");
        partMeltingMultiplier = cfg.getInt("partMeltingMultiplier", "main", 50, 1, 500, "To calculate the amount of RF needed to melt a tool part, we take its smeltery melting temperature and multiply it by this value.");
        partCastingMultiplier = cfg.getInt("partCastingMultiplier", "main", 15, 1, 500, "To calculate the amount of RF needed to cast a tool part, we take its smeltery melting temperature and multiply it by this value.");
        ingotMeltingMultiplier = cfg.getInt("ingotMeltingMultiplier", "main", 8, 1, 500, "Energy required, in RF, to melt an ingot is equal to this multiplied by the ingot's melting temperature in the smeltery.");
        ingotCastingMultiplier = cfg.getInt("ingotCastingMultiplier", "main", 2, 1, 500, "Energy required, in RF, to cast an ingot is equal to this multiplied by the ingot's melting temperature in the smeltery.");
        cfg.save();

        log.info("Pre Init Complete");
    }

    @Mod.EventHandler
    public void Init(FMLInitializationEvent event) {
        try {
            // Map the blacklist fluid IDs to actual fluids
            List<Fluid> blacklist = exceptionFluidIDs.stream().filter(id -> FluidRegistry.getFluid(id) != null)
                    .map(FluidRegistry::getFluid)
                    .collect(Collectors.toList());

            // Rebuild the blacklist from the fluids that are actually registered.
            exceptionFluidIDs = blacklist
                    .stream()
                    .map(Fluid::getName)
                    .collect(Collectors.toList());

            if(exceptionFluidIDs.size() > 0)
                log.info("Blacklisted " + blacklist.size() + " fluids from config.");

            // Save the blacklist to config.
            cfg.get("main", "blacklist", new String[0]).set(exceptionFluidIDs.toArray(new String[0]));
            cfg.save();

            log.debug("All loaded liquids: " + FluidRegistry.getRegisteredFluids().entrySet());

            //#region Fluid Dumps
            List<Fluid> allMoltenFluids = TinkerRegistry.getAllMaterials()
                    .stream()
                    .filter(mat -> mat.isCastable() && mat.getFluid() != null)
                    .map(Material::getFluid)
                    .collect(Collectors.toList());

            StringBuilder allLiquidsDump = new StringBuilder("#This is a simple dump of all loaded liquids, in raw form, with their block names (in brackets) and their localized names, in the format of:\n\n#FLUIDID \t\t\t\t\t\t\t\t\t\t  (FLUIDBLOCK)\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t   = FLUIDLOCALIZEDNAME\n\n");
            allLiquidsDump.append("#ANY AND ALL CHANGES TO THIS FILE WILL BE REPLACED WHEN THIS FILE IS REGENERATED NEXT TIME THE GAME IS RUN. THIS IS FOR INFORMATIONAL PURPOSES ONLY AND IS NOT A CONFIG FILE!\n\n");
            StringBuilder allMoltenDump = new StringBuilder("#This is a simple dump of all molten liquids, in raw form, with their block names (in brackets) and their localized names, in the format of:\n\n#FLUIDID \t\t\t\t\t\t\t\t\t\t  (FLUIDBLOCK)\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t   = FLUIDLOCALIZEDNAME\n\n");
            allMoltenDump.append("#ANY AND ALL CHANGES TO THIS FILE WILL BE REPLACED WHEN THIS FILE IS REGENERATED NEXT TIME THE GAME IS RUN. THIS IS FOR INFORMATIONAL PURPOSES ONLY AND IS NOT A CONFIG FILE!\n\n");

            for (Fluid fluid: FluidRegistry.getRegisteredFluids().values()) {
                String id = fluid.getName();

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

            ItemStack ingotCast = TinkerSmeltery.castIngot;

            // Ingots
            for (Material mat : TinkerRegistry.getAllMaterials()) {
                if (!mat.isCastable() || mat.getFluid() == null) continue;

                Fluid fluid = mat.getFluid();
                if (blacklist.contains(fluid))
                    continue;

                String fluidId = fluid.getName();

                String oreDictName = StringUtils.capitalize(fluidId.replace(".molten", ""));

                NonNullList<ItemStack> ingots = OreDictionary.getOres("ingot" + oreDictName);
                if (ingots.isEmpty()) {
                    log.error("Fluid " + fluidId + " should have at least one ingot \"ingot" + oreDictName + "\" but doesn't.");
                }

                for (ItemStack ingotStack : ingots) {
                    if (ingotStack.getDisplayName().isEmpty()) {
                        log.error(ingotStack + " does not have a display name?");
                        continue;
                    }
                    if (fluid.getName() == null || fluid.getName().isEmpty()) {
                        log.error("Fluid \"" + fluid + "\" doesn't have a name?");
                        continue;
                    }

                    log.debug("Mapping " + ingotStack.getDisplayName() + " <=> " + fluid.getLocalizedName(new FluidStack(fluid, 1)));

                    // Making Ingot casts
                    ThermalExpansionHelper.addTransposerFill(castCreationCost, ingotStack, ingotCast, new FluidStack(TinkerFluids.alubrass, Material.VALUE_Ingot), false);
                    ThermalExpansionHelper.addTransposerFill(castCreationCost, ingotStack, ingotCast, new FluidStack(TinkerFluids.gold, Material.VALUE_Ingot * 2), false);

                    int smelteryMeltingTemp = calcTemperature(fluid.getTemperature(), Material.VALUE_Ingot);
                    // Melting ingots
                    ThermalExpansionHelper.addCrucibleRecipe(smelteryMeltingTemp * ingotMeltingMultiplier, new ItemStack(ingotStack.getItem(), 1, ingotStack.getItemDamage()), new FluidStack(fluid, Material.VALUE_Ingot));

                    // Casting ingots
                    ThermalExpansionHelper.addTransposerFill(smelteryMeltingTemp * ingotCastingMultiplier, ingotCast, ingotStack, new FluidStack(fluid, Material.VALUE_Ingot), false);
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
                    Fluid fluid = mat.getFluid();

                    if(blacklist.contains(fluid)) {
                        log.info("\t-Skipping material " + mat.getIdentifier() + " as its fluid, " + fluid.getName() + ", is blacklisted.");
                        continue;
                    }

                    //Making the casts
                    ItemStack part = toolPart.getItemstackWithMaterial(mat);

                    //log.info("Adding recipes for part: " + part + " (" + part.getDisplayName() + "/" + part.getItem().getRegistryName() + ")");

                    ItemStack cast = new ItemStack(TinkerSmeltery.cast);
                    Cast.setTagForPart(cast, part.getItem());

                    //Making part casts

//                    log.info("Adding transposer fill recipe: Gold/Alubrass + " + part.getDisplayName() + " => " + cast.getDisplayName(), 1, 0);
                    // Aluminum brass
                    ThermalExpansionHelper.addTransposerFill(castCreationCost, part, cast, oneIngotOfMoltenAluBrass, false);
                    // Gold
                    ThermalExpansionHelper.addTransposerFill(castCreationCost, part, cast, twoIngotsOfMoltenGold, false);

                    // Now using the casts

                    //The temperature this part would melt at in the smeltery, given its fluid temperature, and the amount of fluid the part is worth
                    int smelteryMeltingTemp = calcTemperature(fluid.getTemperature(), toolPart.getCost());

//                    log.info("Adding transposer fill recipe: " + fluid.getLocalizedName(new FluidStack(fluid, 1)) + " + " + cast.getDisplayName() + " => " + part.getDisplayName() + " with " + (smelteryMeltingTemp * 1.5f) + " RF", 1, 0);
                    // Make {part} from {toolPart.getCost()} millibuckets of {fluid} using {cast} and {melting temperature * 1.5} RF
                    ThermalExpansionHelper.addTransposerFill(smelteryMeltingTemp * partCastingMultiplier, cast, part, new FluidStack(fluid, toolPart.getCost()), false);

                    //log.info("Adding magma crucible recipe: " + part.getDisplayName() + " => " + toolPart.getCost() + "mB of " + fluid.getLocalizedName(new FluidStack(fluid, 1)) + " with " + (smelteryMeltingTemp * 1.5f) + " RF", 1, 0);
                    // Melt {part} into {toolPart.getCost()} millibuckets of {fluid}
                    ThermalExpansionHelper.addCrucibleRecipe(smelteryMeltingTemp * partMeltingMultiplier, part, new FluidStack(fluid, toolPart.getCost()));

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
