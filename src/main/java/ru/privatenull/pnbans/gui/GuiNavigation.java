package ru.privatenull.pnbans.gui;

import org.bukkit.inventory.ItemStack;

final class GuiNavigation {
    private static final String NEXT_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjllYTFkODYyNDdmNGFmMzUxZWQxODY2YmNhNmEzMDQwYTA2YzY4MTc3Yzc4ZTQyMzE2YTEwOThlNjBmYjdkMyJ9fX0=";
    private static final String PREVIOUS_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODI3MWE0NzEwNDQ5NWUzNTdjM2U4ZTgwZjUxMWE5ZjEwMmIwNzAwY2E5Yjg4ZTg4Yjc5NWQzM2ZmMjAxMDVlYiJ9fX0=";
    private static final String UNAVAILABLE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjc1NDgzNjJhMjRjMGZhODQ1M2U0ZDkzZTY4YzU5NjlkZGJkZTU3YmY2NjY2YzAzMTljMWVkMWU4NGQ4OTA2NSJ9fX0=";

    private GuiNavigation() {
    }

    static ItemStack previous(int page) {
        if (page <= 0) {
            return GuiItemFactory.customHead(UNAVAILABLE_TEXTURE, "&8Предыдущая страница",
                    "",
                    "&#A0EFA1 «Навигация»",
                    " &7- &fТекущая страница: &#D8DF9D1",
                    "",
                    "&#C096AB «Статус»",
                    " &7- &fЭто первая страница",
                    "");
        }
        return GuiItemFactory.customHead(PREVIOUS_TEXTURE, "&#FFC67A← &fПредыдущая страница",
                "",
                "&#A0EFA1 «Навигация»",
                " &7- &fТекущая страница: &#D8DF9D" + (page + 1),
                " &7- &fОткрыть страницу: &#D8DF9D" + page,
                "",
                "&#FFC67A «Действие»",
                "&7ЛКМ &8— &fоткрыть страницу",
                "");
    }

    static ItemStack next(int page, boolean hasNextPage) {
        if (!hasNextPage) {
            return GuiItemFactory.customHead(UNAVAILABLE_TEXTURE, "&8Следующей страницы нет",
                    "",
                    "&#A0EFA1 «Навигация»",
                    " &7- &fТекущая страница: &#D8DF9D" + (page + 1),
                    "",
                    "&#C096AB «Статус»",
                    " &7- &fДальше страниц нет",
                    "");
        }
        return GuiItemFactory.customHead(NEXT_TEXTURE, "&#A0EFA1Следующая страница &f→",
                "",
                "&#A0EFA1 «Навигация»",
                " &7- &fТекущая страница: &#D8DF9D" + (page + 1),
                " &7- &fОткрыть страницу: &#D8DF9D" + (page + 2),
                "",
                "&#FFC67A «Действие»",
                "&7ЛКМ &8— &fоткрыть страницу",
                "");
    }
}
