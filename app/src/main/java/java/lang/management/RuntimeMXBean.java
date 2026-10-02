package java.lang.management;

import java.util.Map;

public interface RuntimeMXBean {
    long getStartTime();
    long getUptime();
    String getName();
    String getVmName();
    String getVmVersion();
    String getSpecName();
    String getManagementSpecVersion();
    ClassLoader getClassLoader();
    String getClassPath();
    String getBootClassPath();
    boolean isBootClassPathSupported();
    long getClassLoadingTime();
    long getTotalCompilationTime();
    Map<String, String> getInputArguments();
    Map<String, String> getSystemProperties();
}
