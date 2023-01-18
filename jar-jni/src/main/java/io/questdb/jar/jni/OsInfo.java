package io.questdb.jar.jni;

public enum OsInfo {
    INSTANCE();

    private final String platform;
    private final String libPrefix;
    private final String libSuffix;

    OsInfo() {
        final String osName = System.getProperty("os.name").toLowerCase();
        final String osArch = System.getProperty("os.arch").toLowerCase();
        this.platform = osName + "_" + osArch;
        if (osName.contains("win")) {
            this.libPrefix = "";
            this.libSuffix = ".dll";
        } else if (osName.contains("mac")) {
            this.libPrefix = "lib";
            this.libSuffix = ".dylib";
        } else {
            this.libPrefix = "lib";
            this.libSuffix = ".so";
        }
    }

    public String getPlatform() { return platform; }

    public String getLibPrefix() {
        return libPrefix;
    }

    public String getLibSuffix() {
        return libSuffix;
    }
}