package io.questdb.maven.rust;

import org.apache.maven.plugin.logging.Log;

public class TestLog implements Log {
    public static final TestLog INSTANCE = new TestLog();

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(CharSequence charSequence) {
        System.out.println("[DEBUG] " + charSequence);
    }

    @Override
    public void debug(CharSequence charSequence, Throwable throwable) {
        System.out.println("[DEBUG] msg={" + charSequence + "}, throwable={" + throwable + "}");
    }

    @Override
    public void debug(Throwable throwable) {
        System.out.println("[DEBUG] throwable={" + throwable + "}");
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(CharSequence charSequence) {
        System.out.println("[INFO] " + charSequence);
    }

    @Override
    public void info(CharSequence charSequence, Throwable throwable) {
        System.out.println("[INFO] msg={" + charSequence + "}, throwable={" + throwable + "}");
    }

    @Override
    public void info(Throwable throwable) {
        System.out.println("[INFO] throwable={" + throwable + "}");
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(CharSequence charSequence) {
        System.out.println("[WARN] " + charSequence);
    }

    @Override
    public void warn(CharSequence charSequence, Throwable throwable) {
        System.out.println("[WARN] msg={" + charSequence + "}, throwable={" + throwable + "}");
    }

    @Override
    public void warn(Throwable throwable) {
        System.out.println("[WARN] throwable={" + throwable + "}");
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(CharSequence charSequence) {
        System.out.println("[ERROR] " + charSequence);
    }

    @Override
    public void error(CharSequence charSequence, Throwable throwable) {
        System.out.println("[ERROR] msg={" + charSequence + "}, throwable={" + throwable + "}");
    }

    @Override
    public void error(Throwable throwable) {
        System.out.println("[ERROR] throwable={" + throwable + "}");
    }
}
