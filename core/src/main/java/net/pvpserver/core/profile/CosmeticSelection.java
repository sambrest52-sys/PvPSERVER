package net.pvpserver.core.profile;

/**
 * Selected cosmetic ids ("none" when disabled).
 */
public final class CosmeticSelection {

    private String killEffect = "none";
    private String deathAnimation = "none";
    private String joinMessage = "none";

    /** @return kill effect id */
    public String killEffect() {
        return killEffect;
    }

    /** @param id kill effect id */
    public void killEffect(String id) {
        this.killEffect = id == null ? "none" : id;
    }

    /** @return death animation id */
    public String deathAnimation() {
        return deathAnimation;
    }

    /** @param id death animation id */
    public void deathAnimation(String id) {
        this.deathAnimation = id == null ? "none" : id;
    }

    /** @return join message id */
    public String joinMessage() {
        return joinMessage;
    }

    /** @param id join message id */
    public void joinMessage(String id) {
        this.joinMessage = id == null ? "none" : id;
    }
}
