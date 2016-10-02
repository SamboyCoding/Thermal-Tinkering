package me.samboycoding.thermaltinkering;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import tconstruct.TConstruct;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.FluidType;
import tconstruct.library.crafting.LiquidCasting;
import tconstruct.library.crafting.Smeltery;
import tconstruct.library.util.IPattern;
import tconstruct.smeltery.TinkerSmeltery;
import tconstruct.tools.TinkerTools;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import cofh.api.modhelpers.ThermalExpansionHelper;
import cofh.thermalexpansion.ThermalExpansion;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ThermalTinkering.MODID, name = ThermalTinkering.NAME, version = ThermalTinkering.VERSION, dependencies = ThermalTinkering.DEPENDENCY_STRING)
public class ThermalTinkering
{
	Logger log;
    public static final String MODID = "thermaltinkering";
    public static final String VERSION = "1.0";
    public static final String NAME = "Thermal Tinkering";
    public static final String DEPENDENCY_STRING = "" + //This is just here for formatting 
    		"required-after:Forge@[10.13.3.1384,11.14);" +
            "required-after:Mantle@[1.7.10-0.3.2,);" +
            "required-after:ThermalExpansion@[1.7.10R4.0.0RC2,);" +
            "required-after:ThermalFoundation@[1.7.10R1.0.0RC3,);" +
            "required-after:CoFHAPI|energy;" +
            "required-after:CoFHCore;";
    
    @EventHandler
    public void preinit(FMLPreInitializationEvent e)
    {
    	log = e.getModLog();
    	log.info("Preinit Complete");
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
		//TinkerSmeltery.fluids; //List of fluids to register
		//TinkerSmeltery.fluidBlocks;
    	
    	LiquidCasting tableCasting = TConstructRegistry.instance.getTableCasting();	
    	
    	//tableCasting.cast
    	
    	List<FluidType> exceptions = Arrays.asList(new FluidType[] { FluidType.getFluidType("Water"), FluidType.getFluidType("Stone"), FluidType.getFluidType("Emerald"), FluidType.getFluidType("Ender"), FluidType.getFluidType("Glass"), FluidType.getFluidType("Slime"), FluidType.getFluidType("Obsidian") });
        Iterator iter = FluidType.fluidTypes.entrySet().iterator();
        
        ItemStack ingotPattern2 = new ItemStack(TinkerSmeltery.metalPattern, 1, 0);
        
        while (iter.hasNext())
        {
            Map.Entry pairs = (Map.Entry) iter.next();
            FluidType ft = (FluidType) pairs.getValue();
            if (exceptions.contains(ft))
                continue;
            String fluidTypeName = (String) pairs.getKey();
            
            for (ItemStack ore : OreDictionary.getOres("ingot" + fluidTypeName))
            {
            	if(ore == null)
            	{
            		log.error("NO ORE FOUND FOR TYPE ingot" + fluidTypeName + "!");
            		continue;
            	}
            	if(ft.fluid == null)
            	{
            		log.error("NO FLUID FOUND FOR FLUIDTYPE " + ft + "!");
            		continue;
            	}
            	if(ore.getDisplayName() == null)
            	{
            		log.error("NO DISPLAYNAME FOUND FOR ORE RESULT " + ore + "!");
            		continue;
            	}
            	if(ft.fluid.getName() == null)
            	{
            		log.error("NO NAME FOUND FOR FLUID " + ft.fluid + "!");
            		continue;
            	}
            	log.info("Adding melting recipe for ingots: 1 " + ore.getDisplayName() + " makes 144 mb of " + ft.fluid.getLocalizedName(new FluidStack(ft.fluid, 1)));
            	ThermalExpansionHelper.addCrucibleRecipe(5000, new ItemStack(ore.getItem(), 1, ore.getItemDamage()), new FluidStack(ft.fluid, 144));
            	
            	log.info("Adding filling recipe for ingots: 144mb of " + ft.fluid.getLocalizedName(new FluidStack(ft.fluid, 1)) + " makes 1 " + ore.getDisplayName());
            	ThermalExpansionHelper.addTransposerFill(800, ingotPattern2, ore, new FluidStack(ft.fluid, 144), false);
            }
            
            
            TinkerSmeltery.liquids = new FluidStack[] { new FluidStack(TinkerSmeltery.moltenIronFluid, 1), new FluidStack(TinkerSmeltery.moltenCopperFluid, 1), new FluidStack(TinkerSmeltery.moltenCobaltFluid, 1), new FluidStack(TinkerSmeltery.moltenArditeFluid, 1), new FluidStack(TinkerSmeltery.moltenManyullynFluid, 1), new FluidStack(TinkerSmeltery.moltenBronzeFluid, 1), new FluidStack(TinkerSmeltery.moltenAlumiteFluid, 1), new FluidStack(TinkerSmeltery.moltenObsidianFluid, 1), new FluidStack(TinkerSmeltery.moltenSteelFluid, 1), new FluidStack(TinkerSmeltery.pigIronFluid, 1) };
            int[] liquidDamage = new int[] { 2, 13, 10, 11, 12, 14, 15, 6, 16, 18 }; // ItemStack
                                                                                     // damage
                                                                                     // value
            int fluidAmount = 0;
            Fluid fs = null;

            for (int thisPart = 0; thisPart < TinkerTools.patternOutputs.length; thisPart++) //Loop through all defined parts
            {
                if (TinkerTools.patternOutputs[thisPart] != null) //Check not null
                {
                    ItemStack cast = new ItemStack(TinkerSmeltery.metalPattern, 1, thisPart + 1); //Cast for this part

                    //Pattern making
                    log.info("Adding filling recipe for patterns: 144mb of Molten Gold or Aluminum Brass makes 1 " + cast.getDisplayName());
                    ThermalExpansionHelper.addTransposerFill(800, new ItemStack(TinkerTools.patternOutputs[thisPart], 1, Short.MAX_VALUE), cast, new FluidStack(TinkerSmeltery.moltenAlubrassFluid, TConstruct.ingotLiquidValue), false);
                    ThermalExpansionHelper.addTransposerFill(800, new ItemStack(TinkerTools.patternOutputs[thisPart], 1, Short.MAX_VALUE), cast, new FluidStack(TinkerSmeltery.moltenGoldFluid, TConstruct.ingotLiquidValue), false);

                    for (int thisMaterial = 0; thisMaterial < TinkerSmeltery.liquids.length; thisMaterial++)
                    {
                    	//For each fluid register in the smeltery
                        fs = TinkerSmeltery.liquids[thisMaterial].getFluid();
                        fluidAmount = ((IPattern) TinkerSmeltery.metalPattern).getPatternCost(cast) * TConstruct.ingotLiquidValue / 2; //The amount of fluid required
                        ItemStack part = new ItemStack(TinkerTools.patternOutputs[thisPart], 1, liquidDamage[thisMaterial]);
                        
                        log.info("Adding filling recipe for part: " + fluidAmount + "mb of " + fs.getLocalizedName(new FluidStack(fs, 1)) + " makes 1 " + part.getDisplayName());
                        ThermalExpansionHelper.addTransposerFill(800, cast, part, new FluidStack(fs, fluidAmount), false);
                        
                        log.info("Adding melting recipe for part: 1 " + part.getDisplayName() + " makes " + fluidAmount + "mb of " + fs.getLocalizedName(new FluidStack(fs, 1)));
                        ThermalExpansionHelper.addCrucibleRecipe(5000, part, new FluidStack(fs, fluidAmount));
                    }
                }
            }
        }
    }
}
