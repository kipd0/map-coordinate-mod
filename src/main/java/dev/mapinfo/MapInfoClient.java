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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MapInfoClient implements ClientModInitializer {

    private static final Pattern LOCATION = Pattern.compile(
        "(-?\\d+(?:\\.\\d+)?)\\s+" +
        "(-?\\d+(?:\\.\\d+)?)\\s+" +
        "(-?\\d+(?:\\.\\d+)?)\\s+" +
        "([A-Za-z0-9_:\\-]+)"
    );

    private static KeyMapping inspectKey;

    @Override
    public void onInitializeClient() {

        inspectKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.mapinfo.inspect",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_J,
                KeyMapping.Category.register(
                    ResourceLocation.fromNamespaceAndPath(
                        "mapinfo",
                        "main"
                    )
                )
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (inspectKey.consumeClick()) {
                inspectHeldMap(client);
            }
        });
    }

    private static void inspectHeldMap(Minecraft client) {

        if (client.player == null) {
            return;
        }

        ItemStack stack = client.player.getMainHandItem();

        if (!stack.is(Items.FILLED_MAP)) {

            ItemStack offhand = client.player.getOffhandItem();

            if (offhand.is(Items.FILLED_MAP)) {
                stack = offhand;
            }
        }

        if (!stack.is(Items.FILLED_MAP)) {

            message(
                client,
                "Hold a filled map first.",
                ChatFormatting.RED
            );

            return;
        }

        String creator = null;
        String location = null;

        CustomData customData =
            stack.get(DataComponents.CUSTOM_DATA);

        if (customData != null) {

            CompoundTag root =
                customData.copyTag();

            Optional<CompoundTag> valuesOptional =
                root.getCompound("PublicBukkitValues");

            if (valuesOptional.isPresent()) {

                CompoundTag values =
                    valuesOptional.get();

                creator =
                    values
                        .getString("minecraft:mapcreator")
                        .orElse(null);

                byte[] locationBytes =
                    values
                        .getByteArray("minecraft:mapdlocats")
                        .orElse(null);

                if (locationBytes != null) {
                    location =
                        decodeLocation(locationBytes);
                }
            }
        }

        message(
            client,
            "----- Map Info -----",
            ChatFormatting.GOLD
        );

        /*
         * We intentionally don't read MAP_ID here yet.
         *
         * Minecraft 1.21.11 changed the map ID component
         * representation compared with older versions.
         * Creator/location are the important DonutSMP fields.
         */

        if (creator != null && !creator.isBlank()) {

            message(
                client,
                "Creator: " + creator,
                ChatFormatting.AQUA
            );

        } else {

            message(
                client,
                "Creator: not stored",
                ChatFormatting.DARK_GRAY
            );
        }

        if (location != null) {

            message(
                client,
                location,
                ChatFormatting.GREEN
            );

        } else {

            message(
                client,
                "Location: not stored",
                ChatFormatting.DARK_GRAY
            );
        }
    }

    private static String decodeLocation(byte[] data) {

        /*
         * DonutSMP's mapdlocats contains a binary prefix,
         * followed by readable coordinate data such as:
         *
         * 186285.93362665616 64.0
         * 165430.15204459568 world
         */

        String raw =
            new String(
                data,
                StandardCharsets.ISO_8859_1
            );

        Matcher matcher =
            LOCATION.matcher(raw);

        if (!matcher.find()) {
            return null;
        }

        try {

            double exactX =
                Double.parseDouble(matcher.group(1));

            double exactY =
                Double.parseDouble(matcher.group(2));

            double exactZ =
                Double.parseDouble(matcher.group(3));

            String world =
                matcher.group(4);

            long x =
                Math.round(exactX);

            long y =
                Math.round(exactY);

            long z =
                Math.round(exactZ);

            String prettyWorld =
                switch (world) {

                    case "world" ->
                        "Overworld";

                    case "world_nether" ->
                        "Nether";

                    case "world_the_end" ->
                        "The End";

                    default ->
                        world;
                };

            return
                "Location: X " +
                x +
                ", Y " +
                y +
                ", Z " +
                z +
                " (" +
                prettyWorld +
                ")";

        } catch (NumberFormatException e) {

            return null;
        }
    }

    private static void message(
        Minecraft client,
        String text,
        ChatFormatting color
    ) {

        if (client.player != null) {

            client.player.displayClientMessage(
                Component
                    .literal(text)
                    .withStyle(color),
                false
            );
        }
    }
}
