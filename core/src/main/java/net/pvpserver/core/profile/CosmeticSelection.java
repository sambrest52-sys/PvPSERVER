package net.pvpserver.core.profile;

/**
 * Selected cosmetic ids ("none" when disabled).
 */
public final class CosmeticSelection {

    private String killEffect = "none";
    private String deathAnimation = "none";
    private String joinMessage = "none";
    private String trail = "none";
    private String joinEffect = "none";

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

    /** @return selected lobby trail id or "none" */
    public String trail() {
        return trail;
    }

    /** @param id lobby trail id or null */
    public void trail(String id) {
        this.trail = id == null ? "none" : id;
    }

    /** @return selected join effect id or "none" */
    public String joinEffect() {
        return joinEffect;
    }

    /** @param id join effect id or null */
    public void joinEffect(String id) {
        this.joinEffect = id == null ? "none" : id;
    }
}
