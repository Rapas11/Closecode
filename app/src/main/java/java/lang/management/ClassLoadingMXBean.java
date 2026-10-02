package java.lang.management;

public interface ClassLoadingMXBean {
    long getTotalLoadedClassCount();
    long getLoadedClassCount();
    long getUnloadedClassCount();
    boolean isClassLoadingMonitorSupported();
    void setClassLoadingMonitorSupported(boolean enabled);
    boolean isLoadedClassCountSupported();
    boolean isUnloadedClassCountSupported();
}
