package ru.privatenull.pnbans.model;

public enum PunishmentType {
    BAN("Бан"),
    IP_BAN("IP-бан"),
    MUTE("Мут"),
    WARN("Предупреждение");

    private final String displayName;

    PunishmentType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
