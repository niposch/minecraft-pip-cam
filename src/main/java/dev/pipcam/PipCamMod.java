package dev.pipcam;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

/** Client-only picture-in-picture camera. */
@Mod(value = PipCamMod.MODID, dist = Dist.CLIENT)
public class PipCamMod {
    public static final String MODID = "pipcam";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PipCamMod(IEventBus modBus, ModContainer container) {
        PipConfig.load();
        modBus.addListener(PipKeys::register);
        // "Config" button in the Mods list opens the same menu
        container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new PipScreen(parent));
        LOGGER.info("PiP Camera loaded");
    }
}
