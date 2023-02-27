package io.questdb.jar.jni;

public enum OsInfo {

    /** Use static methods instead of this singleton. */
    INSTANCE();

    private final String platform;
    private final String libPrefix;
    private final String libSuffix;
    private final String exeSuffix;

    OsInfo() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows")) {
            osName = "windows";  // Too many flavours, binaries are compatible.
        }
        final String osArch = System.getProperty("os.arch").toLowerCase();
        this.platform = (osName + "-" + osArch).replace(' ', '_');
        this.libPrefix = osName.startsWith("windows") ? "" : "lib";
        this.libSuffix = osName.startsWith("windows")
                ? ".dll" : osName.contains("mac")
                ? ".dylib" : ".so";
        this.exeSuffix = osName.startsWith("windows")
                ? ".exe" : "";
    }

    /**
     * Returns the platform name, e.g. "linux-amd64".
     */
    public static String platform() {
        return INSTANCE.getPlatform();
    }

    public static String libPrefix() {
        return INSTANCE.getLibPrefix();
    }

    public static String libSuffix() {
        return INSTANCE.getLibSuffix();
    }

    public static String exeSuffix() {
        return INSTANCE.getExeSuffix();
    }

    private String getPlatform() {
        return platform;
    }

    private String getLibPrefix() {
        return libPrefix;
    }

    private String getLibSuffix() {
        return libSuffix;
    }

    private String getExeSuffix() {
        return exeSuffix;
    }
}