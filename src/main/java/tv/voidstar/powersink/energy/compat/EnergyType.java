package tv.voidstar.powersink.energy.compat;

public enum EnergyType {
    FORGE("Forge 能源"),
    MEKANISM("Mekanism 能源"),
    IMMERSIVE_ENGINEERING("沉浸工程能源"),
    NONE("无");

    private final String chineseName;

    EnergyType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public static EnergyType fromString(String s) {
        if (s == null) return NONE;
        try {
            return valueOf(s.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    @Override
    public String toString() {
        return name();
    }
}
