package com.lycoris.awakening;

import com.lycoris.awakening.blocks.ModBlocks;
import com.lycoris.awakening.component.ModDataComponentTypes;
import com.lycoris.awakening.effect.ModEffects;
import com.lycoris.awakening.item.ModItemGroups;
import com.lycoris.awakening.item.ModItems;
import com.lycoris.awakening.item.custom.dualblades.DualBladeHandler;
import com.lycoris.awakening.network.ModNetworking;
import com.lycoris.awakening.sound.ModSounds;
import com.lycoris.awakening.weather.*;
import com.lycoris.awakening.world.ModDimensions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LycorisAwakening implements ModInitializer {
	public static final String MOD_ID = "lycoris-awakening";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
        //Content registers
		ModDimensions.registerDimensions();
		ModSounds.registerSounds();
		ModEffects.registerEffects();
		ModItemGroups.registerItemGroups();
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModDataComponentTypes.registerDataComponentTypes();

        //Handler registers
        DualBladeHandler.init();

		//Payload registers
		ModNetworking.registerPayloads();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("testweather")
					.executes(ctx -> {
						ServerWorld world = ctx.getSource().getWorld();
						WeatherNetworking.sendWeatherStart(world, CustomWeatherType.SANGUINE_MOON, 600);
						return 1;
					}));

			dispatcher.register(CommandManager.literal("stopweather")
					.executes(ctx -> {
						ServerWorld world = ctx.getSource().getWorld();
						WeatherNetworking.sendWeatherStop(world);
						return 1;
					}));
		});
	}
}
