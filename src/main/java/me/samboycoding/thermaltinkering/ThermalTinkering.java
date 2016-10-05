package me.samboycoding.thermaltinkering;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import cofh.api.modhelpers.ThermalExpansionHelper;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import tconstruct.TConstruct;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.CastingRecipe;
import tconstruct.library.crafting.LiquidCasting;
import tconstruct.library.util.IPattern;
import tconstruct.smeltery.TinkerSmeltery;
import tconstruct.tools.TinkerTools;

@Mod(modid = ThermalTinkering.MODID, name = ThermalTinkering.NAME, version = ThermalTinkering.VERSION, dependencies = ThermalTinkering.DEPENDENCY_STRING)
public class ThermalTinkering
{
	Logger log;
	public static final String MODID = "thermaltinkering";
	public static final String VERSION = "1.0";
	public static final String NAME = "Thermal Tinkering";
	public static final String DEPENDENCY_STRING = "" + // This is just here for formatting
	"required-after:Forge@[10.13.3.1384,11.14);" + "required-after:Mantle@[1.7.10-0.3.2,);" + "required-before:ThermalExpansion@[1.7.10R4.0.0RC2,);" + "required-after:ThermalFoundation@[1.7.10R1.0.0RC3,);" + "required-after:CoFHAPI|energy;" + "required-after:CoFHCore;";
	
	LiquidCasting tableCasting;
	List<Fluid> exceptions;
	List<String> exceptionStrings;
	List<String> toAddStrings;
	File configDirectory;
	File configFile;
	File fluidDump;
	File moltenDump;
	
	Configuration cfg;
	
	@EventHandler
	public void preinit(FMLPreInitializationEvent e) throws Exception
	{
		log = e.getModLog();
		
		configDirectory = new File(e.getModConfigurationDirectory(), "/ThermalTinkering/");
		if (!configDirectory.exists())
		{
			configDirectory.mkdirs();
		}
		
		configFile = new File(configDirectory, "config.cfg");
		if (!configFile.exists())
		{
			configFile.createNewFile();
		}
		
		fluidDump = new File(configDirectory, "fluids.cfg");
		if (fluidDump.exists())
		{
			fluidDump.delete(); // Remove old version to be repopulated
		}
		fluidDump.createNewFile();
		
		moltenDump = new File(configDirectory, "moltenFluids.cfg");
		if (moltenDump.exists())
		{
			moltenDump.delete();
		}
		moltenDump.createNewFile();
		
		cfg = new Configuration(configFile);
		cfg.load();
		
		exceptionStrings = new ArrayList<String>(Arrays.asList(cfg.getStringList("blacklist", "main", new String[] { "glass.molten", "obsidian.molten" }, "List of fluids to completely ignore.")));
		toAddStrings = new ArrayList<String>(Arrays.asList(cfg.getStringList("addFluids", "main", new String[] {}, "List of fluids to explicitly add. Only works if there is already a recipe - just add any we miss.")));
		
		cfg.save();
		
		log.info("Preinit Complete");
	}
	
	public void postInit(FMLPostInitializationEvent e)
	{
		log.info("Beginning PostInit");
		log.info("PostInit Complete");
	}
	
	@EventHandler
	public void loadcomplete(FMLLoadCompleteEvent event) throws Exception
	{
		try
		{
			tableCasting = TConstructRegistry.instance.getTableCasting();
			exceptions = new ArrayList<Fluid>();
			ArrayList<Integer> toRemove = new ArrayList<Integer>();
			
			int removed = 0;
			for (String exception : exceptionStrings)
			{
				Fluid adding = FluidRegistry.getFluid(exception);
				if (adding == null)
				{
					log.warn("Removing invalid liquid " + exception + " from config - it is not registered at this point!");
					toRemove.add(exceptionStrings.indexOf(exception));
					continue;
				}
				removed++;
				exceptions.add(adding);
			}
			log.info("Successfully removed " + removed + " liquids from proccessing list from config.");
			
			if (!toRemove.isEmpty())
			{
				for (int index : toRemove)
				{
					exceptionStrings.remove(index);
				}
				cfg.get("main", "blacklist", new String[] { "glass.molten", "obsidian.molten" }).set((String[]) exceptionStrings.toArray());
				cfg.save();
			}
			
			log.info("Loading fluid list...");
			
			Iterator iter = FluidRegistry.getRegisteredFluids().entrySet().iterator();
			
			log.debug("All loaded liquids: " + FluidRegistry.getRegisteredFluids().entrySet());
			log.info("Loading molten metals...");
			
			Map<String, Fluid> moltenStuff = (Map<String, Fluid>) new HashMap<String, Fluid>();
			String toWrite = "#This is a simple dump of all loaded liquids, in raw form, with their block names (in brackets) and their localized names, in the format of:\n\n#FLUIDID \t\t\t\t\t\t\t\t\t\t  (FLUIDBLOCK)\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t   = FLUIDLOCALIZEDNAME\n\n";
			String toWrite2 = "#This is a simple dump of all molten liquids, in raw form, with their block names (in brackets) and their localized names, in the format of:\n\n#FLUIDID \t\t\t\t\t\t\t\t\t\t  (FLUIDBLOCK)\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t\t   = FLUIDLOCALIZEDNAME\n\n";
			while (iter.hasNext())
			{
				Map.Entry pairs = (Map.Entry) iter.next();
				
				String id = (String) pairs.getKey();
				Fluid fluid = (Fluid) pairs.getValue();
				
				// What follows is a little bit of trickery to get everything lined up. Cause formatting.
				int numSpaces2 = 50 - id.length();
				int numSpaces = 130 - fluid.getBlock().getUnlocalizedName().length() - id.length() - 2 - numSpaces2;
				String spaces = "";
				String spaces2 = "";
				for (int i = 0; i < numSpaces; i++)
				{
					spaces += " ";
				}
				for (int j = 0; j < numSpaces2; j++)
				{
					spaces2 += " ";
				}
				// End madness
				
				if (id.endsWith(".molten"))
				{
					moltenStuff.put(id, fluid);
					toWrite2 += id + spaces2 + "(" + fluid.getBlock().getUnlocalizedName() + ")" + spaces + " = " + fluid.getLocalizedName(new FluidStack(fluid, 1)) + "\n";
				}
				toWrite += id + spaces2 + "(" + fluid.getBlock().getUnlocalizedName() + ")" + spaces + " = " + fluid.getLocalizedName(new FluidStack(fluid, 1)) + "\n";
			}
			
			PrintWriter writer = new PrintWriter(fluidDump);
			writer.print(toWrite);
			writer.close();
			
			log.info("Successfully written loaded fluid dump to " + fluidDump.getAbsolutePath());
			
			writer = new PrintWriter(moltenDump);
			writer.print(toWrite2);
			writer.close();
			
			log.info("Successfuly written molten fluid dump to " + moltenDump.getAbsolutePath());
			
			int added = 0;
			for (String add : toAddStrings)
			{
				Fluid adding = FluidRegistry.getFluid(add);
				if (adding == null)
				{
					log.warn("Removing invalid liquid " + add + " from config - it is not registered at this point!");
					toRemove.add(toAddStrings.indexOf(add));
					continue;
				}
				added++;
				moltenStuff.put(add, adding);
			}
			log.info("Successfully added " + added + " liquids to proccessing list from config.");
			
			if (!toRemove.isEmpty())
			{
				for (int index : toRemove)
				{
					toAddStrings.remove(index);
				}
				cfg.get("main", "addFluids", new String[] {}).set((String[]) toAddStrings.toArray());
				cfg.save();
			}
			
			log.info("Done loading - adding stuff");
			log.debug("Registered molten stuff: " + moltenStuff.entrySet());
			
			Iterator moltenIter = moltenStuff.entrySet().iterator();
			
			ItemStack ingotPattern = new ItemStack(TinkerSmeltery.metalPattern, 1, 0);
			
			// Ingots
			while (moltenIter.hasNext())
			{
				Map.Entry pairs = (Map.Entry) moltenIter.next();
				Fluid ft = (Fluid) pairs.getValue();
				if (exceptions.contains(ft))
					continue;
				String fluidTypeName = (String) pairs.getKey();
				
				String[] split = fluidTypeName.split("\\.");
				int len = split.length;
				int i = -1;
				String oreDictName = "";
				for (String s : split)
				{
					i++;
					if (i == len - 1)
					{
						break; // Do not include the .molten bit.
					}
					oreDictName += s;
				}
				oreDictName = oreDictName.substring(0, 1).toUpperCase() + oreDictName.substring(1);
				for (ItemStack ore : OreDictionary.getOres("ingot" + oreDictName))
				{
					if (ore == null)
					{
						log.error("NO ORE FOUND FOR TYPE ingot" + fluidTypeName + "!");
						continue;
					}
					if (ft == null)
					{
						log.error("NO FLUID FOUND FOR FLUIDTYPE " + ft + "!");
						continue;
					}
					if (ore.getDisplayName() == null)
					{
						log.error("NO DISPLAYNAME FOUND FOR ORE RESULT " + ore + "!");
						continue;
					}
					if (ft.getName() == null)
					{
						log.error("NO NAME FOUND FOR FLUID " + ft + "!");
						continue;
					}
					
					// Ingot casts
					ThermalExpansionHelper.addTransposerFill(800, ore, ingotPattern, new FluidStack(TinkerSmeltery.moltenAlubrassFluid, 144), false);
					ThermalExpansionHelper.addTransposerFill(800, ore, ingotPattern, new FluidStack(TinkerSmeltery.moltenGoldFluid, 288), false);
					
					// Melting ingots
					ThermalExpansionHelper.addCrucibleRecipe(5000, new ItemStack(ore.getItem(), 1, ore.getItemDamage()), new FluidStack(ft, 144));
					
					// Casting ingots
					ThermalExpansionHelper.addTransposerFill(800, ingotPattern, ore, new FluidStack(ft, 144), false);
				}
			}
			
			int fluidAmount = 0;
			Fluid fs = null;
			
			for (int thisPart = 0; thisPart < TinkerTools.patternOutputs.length; thisPart++) // Loop through all defined parts
			{
				if (TinkerTools.patternOutputs[thisPart] != null) // Check not null
				{
					ItemStack cast = new ItemStack(TinkerSmeltery.metalPattern, 1, thisPart + 1); // Cast for this part
					fluidAmount = ((IPattern) TinkerSmeltery.metalPattern).getPatternCost(cast) * TConstruct.ingotLiquidValue / 2; // The amount of fluid required
					log.info("Adding recipes for part " + cast.getDisplayName() + ". Required fluid: " + fluidAmount + "mb");
					
					// Cast recipes
					ThermalExpansionHelper.addTransposerFill(800, new ItemStack(TinkerTools.patternOutputs[thisPart], 1, OreDictionary.WILDCARD_VALUE), cast, new FluidStack(TinkerSmeltery.moltenAlubrassFluid, TConstruct.ingotLiquidValue), false);
					ThermalExpansionHelper.addTransposerFill(800, new ItemStack(TinkerTools.patternOutputs[thisPart], 1, OreDictionary.WILDCARD_VALUE), cast, new FluidStack(TinkerSmeltery.moltenGoldFluid, TConstruct.ingotLiquidValue * 2), false);
					
					// Below is New, improved method for all materials (in
					// theory)
					
					// For each registered fluid
					for (final Map.Entry<String, Fluid> entry : moltenStuff.entrySet())
					{
						// Get the fluid itself
						final Fluid fluid = entry.getValue();
						
						// Create a stack of one ingot
						final FluidStack fluidStackToolRod = new FluidStack(fluid, 72);
						
						if (exceptions.contains(fluid))
							continue;
						
						fs = fluid;
						final FluidStack fluidStack = new FluidStack(fs, fluidAmount);
						
						// Get the output for this metal and cast
						if (cast == null)
						{
							log.fatal("CAST NULL!");
						}
						if (tableCasting == null)
						{
							log.fatal("Casting table null!");
						}
						CastingRecipe thisRecipe = tableCasting.getCastingRecipe(fluidStack, cast);
						
						// If this is an invalid combination, skip it.
						if (thisRecipe == null)
						{
							continue;
						}
						
						// Get the part created
						ItemStack part = thisRecipe.getResult();
						
						// Making part
						ThermalExpansionHelper.addTransposerFill(800 * (fluidAmount / 144), cast, part, new FluidStack(fs, fluidAmount), false);
						
						// Melting part
						ThermalExpansionHelper.addCrucibleRecipe(5000 * (fluidAmount / 144), part, new FluidStack(fs, fluidAmount));
					}
				}
			}
			log.info("Successfully added recipes for " + TinkerTools.patternOutputs.length + " parts.");
		} catch (Throwable e)
		{
			e.printStackTrace();
		}
	}
}
