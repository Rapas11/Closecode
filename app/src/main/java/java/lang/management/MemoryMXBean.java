package java.lang.management;

public interface MemoryMXBean {
    MemoryUsage getHeapMemoryUsage();
    MemoryUsage getNonHeapMemoryUsage();
    int getObjectPendingFinalizationCount();
    void gc();
}
