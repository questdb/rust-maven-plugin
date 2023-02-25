package io.questdb.jar.jni;

public enum OsInfo {
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

    public String getPlatform() {
        return platform;
    }

    public String getLibPrefix() {
        return libPrefix;
    }

    public String getLibSuffix() {
        return libSuffix;
    }

    public String getExeSuffix() {
        return exeSuffix;
    }
}