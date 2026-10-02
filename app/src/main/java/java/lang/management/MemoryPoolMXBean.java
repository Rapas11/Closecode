package java.lang.management;

public interface MemoryPoolMXBean {
    String getName();
    MemoryType getType();
    MemoryUsage getUsage();
    MemoryUsage getPeakUsage();
    void resetPeakUsage();
    long getUsageThreshold();
    long getUsageThresholdCount();
    void setUsageThreshold(long threshold);
}
