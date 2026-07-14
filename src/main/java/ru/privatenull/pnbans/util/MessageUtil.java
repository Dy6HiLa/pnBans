package ru.privatenull.pnbans.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnbans.PnBansPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Central message renderer. messages.yml supports MiniMessage, &#RRGGBB, & codes and §x hex. */
public final class MessageUtil {
    private MessageUtil() { }

    public static void send(PnBansPlugin plugin, CommandSender sender, String path, Map<String, String> values) {
        sender.sendMessage(component(plugin, path, List.of("{prefix}&cНе найдено сообщение: " + path), values));
    }

    public static void sendDupeIpAlert(PnBansPlugin plugin, CommandSender sender, Map<String, String> values) {
        List<String> lines = configuredLines(plugin, "dupeip-alert", List.of(
                "", "&#429F91&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                "&#429F91&lDUPEIP &8| &fОбнаружена подозрительная связь", "",
                "&#A0EFA1▸ &fИгрок: &#D8DF9D{player}",
                "&#A0EFA1▸ &fСвязанный аккаунт: &#D8DF9D{related_player}",
                "&#A0EFA1▸ &fРиск: {risk_color}{risk_level} &8— &f{risk}/100",
                "&#A0EFA1▸ &fПричины: &7{evidence}",
                "&#A0EFA1▸ &fСтрана: &7{country}", "", "{open}",
                "&#429F91&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", ""));
        Map<String, String> all = placeholders(plugin, values);
        Component action = ColorUtil.component("&#429F91▸ &fНажмите, чтобы открыть проверку")
                .clickEvent(ClickEvent.runCommand(values.getOrDefault("command", "/dupeip " + values.getOrDefault("player", ""))))
                .hoverEvent(HoverEvent.showText(ColorUtil.component(
                        "&#A0EFA1«Проверка связей»\n&7Игрок: &f" + values.getOrDefault("player", "—")
                                + "\n&7ЛКМ &8— &fоткрыть DupeIP")));
        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result = result.append(Component.newline());
            String line = replace(lines.get(i), all);
            int at = line.indexOf("{open}");
            result = at < 0 ? result.append(ColorUtil.component(line)) : result.append(ColorUtil.component(line.substring(0, at))).append(action).append(ColorUtil.component(line.substring(at + 6)));
        }
        sender.sendMessage(result);
    }

    public static String color(String value) { return ColorUtil.colorize(value); }

    public static String lines(PnBansPlugin plugin, String path, List<String> defaults, Map<String, String> values) {
        return ColorUtil.colorize(join(configuredLines(plugin, path, defaults), placeholders(plugin, values)));
    }

    public static Component component(PnBansPlugin plugin, String path, List<String> defaults, Map<String, String> values) {
        return ColorUtil.component(join(configuredLines(plugin, path, defaults), placeholders(plugin, values)));
    }

    private static String join(List<String> lines, Map<String, String> values) {
        return lines.stream().map(line -> replace(line, values)).collect(java.util.stream.Collectors.joining("\n"));
    }

    private static Map<String, String> placeholders(PnBansPlugin plugin, Map<String, String> values) {
        Map<String, String> all = new HashMap<>(values);
        all.putIfAbsent("prefix", prefix(plugin));
        return all;
    }

    private static String prefix(PnBansPlugin plugin) { return plugin.getMessages().getString("prefix", "&8[&apnBans&8] "); }
    private static List<String> configuredLines(PnBansPlugin plugin, String path, List<String> defaults) {
        List<String> lines;
        if (plugin.getMessages().isList(path)) {
            List<String> configured = plugin.getMessages().getStringList(path);
            lines = configured.isEmpty() ? defaults : configured;
        } else {
            String configured = plugin.getMessages().getString(path);
            lines = configured == null ? defaults : List.of(configured);
        }
        return path.equals("dupeip-report") || path.equals("dupeip-alert")
                ? DupeIpMessageFilter.withoutCoordinates(lines) : lines;
    }

    private static String replace(String text, Map<String, String> values) { for (Map.Entry<String,String> entry:values.entrySet()) text=text.replace("{"+entry.getKey()+"}",entry.getValue()); return text; }
}
