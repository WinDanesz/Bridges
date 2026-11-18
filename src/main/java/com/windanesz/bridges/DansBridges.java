package com.windanesz.bridges;

import com.windanesz.bridges.command.CommandDansBridges;
import com.windanesz.bridges.dansbridges.Tags;
import com.windanesz.bridges.worldgen.CustomBridge;
import com.windanesz.bridges.worldgen.WorldGenBridge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, dependencies = "after:sisyphus;after:futuremc")
public class DansBridges {

    public static final String MODID = Tags.MOD_ID;
    public static final String NAME = Tags.MOD_NAME;

    public static Logger logger;

    @Mod.Instance(Tags.MOD_ID)
    public static DansBridges instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        CustomBridge.init(event.getModConfigurationDirectory());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // After the generators of every other mod, so that their trees and plants are there when a bridge is built
        GameRegistry.registerWorldGenerator(new WorldGenBridge(), Integer.MAX_VALUE - 1);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        CustomBridge.load();
        event.registerServerCommand(new CommandDansBridges());
    }
}
