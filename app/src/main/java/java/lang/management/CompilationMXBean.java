package java.lang.management;

public interface CompilationMXBean {
    String getName();
    long getTotalCompilationTime();
    boolean isCompilationTimeMonitoringSupported();
    void setCompilationTimeMonitoringEnabled(boolean enabled);
}
