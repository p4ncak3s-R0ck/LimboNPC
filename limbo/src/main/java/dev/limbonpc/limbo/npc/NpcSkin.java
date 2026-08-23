package dev.limbonpc.limbo.npc;

public record NpcSkin(Type type, String username, String value, String signature) {
    public enum Type { NONE, USERNAME, TEXTURE }

    public static NpcSkin none() { return new NpcSkin(Type.NONE, null, null, null); }
    public static NpcSkin username(String username, String value, String signature) {
        return new NpcSkin(Type.USERNAME, username, value, signature);
    }
    public static NpcSkin texture(String value, String signature) {
        return new NpcSkin(Type.TEXTURE, null, value, signature);
    }
    public boolean hasTexture() { return value != null && !value.isBlank() && signature != null && !signature.isBlank(); }
}
