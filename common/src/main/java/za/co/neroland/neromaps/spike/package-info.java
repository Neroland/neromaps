/**
 * SPIKE (Stage 0, client minimap draw path). Throwaway proof that the minimap can be drawn as one textured quad
 * plus one batched marker pass, updated by sub-rectangle uploads, on every supported Minecraft line. Nothing here
 * is wired into gameplay: the debug HUD layer only draws when the JVM runs with
 * {@code -Dneromaps.spike.minimap=true}. Stage 2 replaces this package with the real client map engine.
 */
package za.co.neroland.neromaps.spike;
