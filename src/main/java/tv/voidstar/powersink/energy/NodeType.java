package tv.voidstar.powersink.energy;

public enum NodeType {
    SINK("能源接收节点"),
    SOURCE("能源输出节点"),
    NONE("无");

    private final String chineseName;

    NodeType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
