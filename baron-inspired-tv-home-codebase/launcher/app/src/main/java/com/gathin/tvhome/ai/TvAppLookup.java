package com.gathin.tvhome.ai;

/**
 * Interface contract for resolving spoken app labels and aliases to installed Android packages.
 */
public interface TvAppLookup {
    /**
     * Finds installed package name matching query label, alias, or package ID.
     * @param query Spoken/typed application name or identifier
     * @return Android package name if found and installed, or null if not found
     */
    String findPackageForLabel(String query);

    /**
     * Returns human-readable display label for an installed package.
     * @param pkg Android package name
     * @return Display label, or null if not installed
     */
    String getLabelForPackage(String pkg);

    /**
     * Default helper to check if package is installed.
     */
    default boolean isPackageInstalled(String pkg) {
        return pkg != null && getLabelForPackage(pkg) != null;
    }
}
