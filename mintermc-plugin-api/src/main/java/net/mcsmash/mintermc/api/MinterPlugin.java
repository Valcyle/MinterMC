package net.mcsmash.mintermc.api;

/**
 * The base interface for all MinterMC plugins.
 * <br>
 * Plugins are loaded dynamically at runtime by the MinterMC kernel.
 * They can listen to bot events, register custom SDK endpoints, 
 * or provide advanced control logic like pathfinding.
 */
public interface MinterPlugin {
    
    /**
     * Called when the plugin is enabled during server startup.
     */
    void onEnable();
    
    /**
     * Called when the plugin is disabled during server shutdown.
     */
    void onDisable();
}
