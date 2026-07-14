package ru.privatenull.pnbans.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import ru.privatenull.pnbans.util.MessageUtil;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GuiItemFactory {
    private static final Pattern TEXTURE_URL = Pattern.compile(
            "https?://(?:textures|texttures)\\.minecraft\\.net/texture/[A-Za-z0-9]+"
    );

    private GuiItemFactory() {
    }

    static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        apply(item, name, lore);
        return item;
    }

    static ItemStack customHead(String base64, String name, String... lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            URL texture = textureUrl(base64);
            if (texture != null) {
                UUID uuid = UUID.randomUUID();
                PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "pn" + uuid.toString().replace("-", "").substring(0, 14));
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(texture);
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                head.setItemMeta(meta);
            }
        }
        apply(head, name, lore);
        return head;
    }

    static ItemStack playerHead(Player player, String name, String... lore) {
        ItemStack head = item(Material.PLAYER_HEAD, name, lore);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static void apply(ItemStack item, String name, String... lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(MessageUtil.color(name));
        List<String> colored = new ArrayList<>();
        for (String line : lore) colored.add(MessageUtil.color(line));
        meta.setLore(colored);
        item.setItemMeta(meta);
    }

    private static URL textureUrl(String base64) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL.matcher(decoded);
            if (!matcher.find()) return null;
            return new URL(matcher.group().replace("texttures.minecraft.net", "textures.minecraft.net"));
        } catch (Exception ignored) {
            return null;
        }
    }
}
