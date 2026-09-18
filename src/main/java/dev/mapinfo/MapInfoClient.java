package dev.mapinfo;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.MapId;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MapInfoClient implements ClientModInitializer {
    private static final Pattern LOCATION = Pattern.compile(
        "(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+([A-Za-z0-9_:\\-]+)"
    );
    private static KeyMapping key;

    @Override
    public void onInitializeClient() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.mapinfo.inspect", InputConstants.Type.KEYSYM, InputConstants.KEY_J, "category.mapinfo"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) inspect(client);
        });
    }

    private static void inspect(Minecraft client) {
        if (client.player == null) return;

        ItemStack stack = client.player.getMainHandItem();
        if (!stack.is(Items.FILLED_MAP) && client.player.getOffhandItem().is(Items.FILLED_MAP))
            stack = client.player.getOffhandItem();

        if (!stack.is(Items.FILLED_MAP)) {
            msg(client, "Hold a filled map first.", ChatFormatting.RED);
            return;
        }

        String creator = null, location = null;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);

        if (custom != null) {
            CompoundTag root = custom.copyTag();
            CompoundTag values = compound(root, "PublicBukkitValues");
            if (values != null) {
                creator = string(values, "minecraft:mapcreator");
                byte[] bytes = bytes(values, "minecraft:mapdlocats");
                if (bytes != null) location = decode(bytes);
            }
        }

        msg(client, "----- Map Info -----", ChatFormatting.GOLD);
        MapId id = stack.get(DataComponents.MAP_ID);
        if (id != null) msg(client, "Map ID: " + id.id(), ChatFormatting.GRAY);
        msg(client, "Creator: " + (creator == null ? "not stored" : creator),
            creator == null ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA);
        msg(client, location == null ? "Location: not stored" : location,
            location == null ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN);
    }

    private static CompoundTag compound(CompoundTag t, String k) {
        return t.contains(k, Tag.TAG_COMPOUND) ? t.getCompound(k).orElse(null) : null;
    }
    private static String string(CompoundTag t, String k) {
        return t.contains(k, Tag.TAG_STRING) ? t.getString(k).orElse(null) : null;
    }
    private static byte[] bytes(CompoundTag t, String k) {
        return t.contains(k, Tag.TAG_BYTE_ARRAY) ? t.getByteArray(k).orElse(null) : null;
    }

    private static String decode(byte[] data) {
        String raw = new String(data, StandardCharsets.ISO_8859_1);
        Matcher m = LOCATION.matcher(raw);
        if (!m.find()) return null;
        try {
            long x = Math.round(Double.parseDouble(m.group(1)));
            long y = Math.round(Double.parseDouble(m.group(2)));
            long z = Math.round(Double.parseDouble(m.group(3)));
            String w = switch (m.group(4)) {
                case "world" -> "Overworld";
                case "world_nether" -> "Nether";
                case "world_the_end" -> "The End";
                default -> m.group(4);
            };
            return "Location: X " + x + ", Y " + y + ", Z " + z + " (" + w + ")";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void msg(Minecraft c, String s, ChatFormatting color) {
        if (c.player != null) c.player.displayClientMessage(Component.literal(s).withStyle(color), false);
    }
}
