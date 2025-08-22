package com.lycoris.modid;

import com.lycoris.modid.blocks.ModBlocks;
import com.lycoris.modid.component.ModDataComponentTypes;
import com.lycoris.modid.item.ModItemGroups;
import com.lycoris.modid.item.ModItems;
import com.lycoris.modid.sound.ModSounds;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LycorisAwakening implements ModInitializer {
	public static final String MOD_ID = "lycoris-awakening";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.registerSounds();
		ModItemGroups.registerItemGroups();
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();

		ModDataComponentTypes.registerDataComponentTypes();
	}
}