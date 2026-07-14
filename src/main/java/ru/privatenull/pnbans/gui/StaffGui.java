package ru.privatenull.pnbans.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pnbans.util.MessageUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StaffGui implements Listener {
    private static final int[] STAFF_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private final HistoryGui historyGui;

    public StaffGui(HistoryGui historyGui) { this.historyGui = historyGui; }

    public void open(Player viewer, int page) {
        List<Player> staff = new ArrayList<>(Bukkit.getOnlinePlayers());
        staff.removeIf(player -> !isModerator(player));
        staff.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pageSize = STAFF_SLOTS.length;
        int from = page * pageSize;
        boolean hasNext = staff.size() > from + pageSize;
        List<Player> visible = from >= staff.size() ? List.of() : staff.subList(from, Math.min(from + pageSize, staff.size()));
        Holder holder = new Holder(page, hasNext, visible.stream().map(Player::getName).toList());
        Inventory inventory = Bukkit.createInventory(holder, 54, MessageUtil.color("&7Журнал персонала &8| &fМодераторы"));
        holder.inventory = inventory;
        decorate(inventory);
        inventory.setItem(4, item(Material.COMPASS, "&#A0EFA1Персонал онлайн", "",
                "&#A0EFA1 «Основное»",
                " &7- &fМодераторов онлайн: &#D8DF9D" + staff.size(),
                " &7- &fТекущая страница: &#D8DF9D" + (page + 1),
                "",
                "&#FFC67A «Действие»",
                " &7- &fВыберите сотрудника ниже",
                ""));
        for (int index = 0; index < visible.size(); index++) inventory.setItem(STAFF_SLOTS[index], staffItem(visible.get(index)));
        if (visible.isEmpty()) inventory.setItem(22, item(Material.BARRIER, "&cМодераторов нет", "",
                "&#A0EFA1 «Персонал»",
                " &7- &fСейчас нет сотрудников",
                " &7- &fс правами модерации",
                ""));
        inventory.setItem(48, GuiNavigation.previous(page));
        inventory.setItem(50, GuiNavigation.next(page, hasNext));
        viewer.openInventory(inventory);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;
        if (event.getRawSlot() == 48 && holder.page > 0) { open(player, holder.page - 1); return; }
        if (event.getRawSlot() == 50 && holder.hasNext) { open(player, holder.page + 1); return; }
        int index = slotIndex(event.getRawSlot());
        if (index >= 0 && index < holder.names.size()) historyGui.openForActor(player, holder.names.get(index), 0);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        for (int slot : event.getRawSlots()) if (slot < 54) { event.setCancelled(true); return; }
    }
    private boolean isModerator(Player player) { return player.hasPermission("pnbans.admin") || player.hasPermission("pnbans.ban") || player.hasPermission("pnbans.ban.ip") || player.hasPermission("pnbans.mute") || player.hasPermission("pnbans.warn"); }
    private ItemStack staffItem(Player player) {
        List<String> roles = new ArrayList<>();
        if (player.hasPermission("pnbans.ban") || player.hasPermission("pnbans.ban.ip")) roles.add("&cбаны");
        if (player.hasPermission("pnbans.mute")) roles.add("&#FFC67Aмуты");
        if (player.hasPermission("pnbans.warn")) roles.add("&#C096ABпредупреждения");
        if (player.hasPermission("pnbans.admin")) roles.add("&#A0EFA1администратор");
        return GuiItemFactory.playerHead(player, "&#D8DF9D" + player.getName(),
                "",
                "&#A0EFA1 «Сотрудник»",
                " &7- &fСтатус: &aонлайн",
                " &7- &fДоступ: &f" + String.join("&8, &f", roles),
                "",
                "&#FFC67A «Действие»",
                "&7ЛКМ &8— &fоткрыть журнал",
                "");
    }
    private int slotIndex(int slot) { for (int i = 0; i < STAFF_SLOTS.length; i++) if (STAFF_SLOTS[i] == slot) return i; return -1; }
    private void decorate(Inventory inventory) { ItemStack orange = item(Material.ORANGE_STAINED_GLASS_PANE, " "); ItemStack black = item(Material.BLACK_STAINED_GLASS_PANE, " "); for (int i = 0; i <= 8; i++) inventory.setItem(i, orange); for (int i : new int[]{9,17,26,35}) inventory.setItem(i, orange); for (int i : new int[]{18,27,36,44}) inventory.setItem(i, black); for (int i=45;i<=53;i++) inventory.setItem(i,black); }
    private ItemStack item(Material material, String name, String... lore) { return GuiItemFactory.item(material, name, lore); }
    private static final class Holder implements InventoryHolder { private final int page; private final boolean hasNext; private final List<String> names; private Inventory inventory; private Holder(int page, boolean hasNext, List<String> names) { this.page=page; this.hasNext=hasNext; this.names=names; } @Override public Inventory getInventory() { return inventory; } }
}
