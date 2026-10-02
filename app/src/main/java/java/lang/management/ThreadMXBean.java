package java.lang.management;

public interface ThreadMXBean {
    int getThreadCount();
    int getPeakThreadCount();
    int getTotalStartedThreadCount();
    int getDaemonThreadCount();
    long[] getAllThreadIds();
    ThreadInfo getThreadInfo(long id);
    ThreadInfo[] getThreadInfo(long[] ids);
    ThreadInfo[] getThreadInfo(long[] ids, int maxDepth);
    long getThreadCpuTime(long id);
    long getThreadCpuTime();
    long getThreadUserTime(long id);
    long getThreadUserTime();
    long getCurrentThreadCpuTime();
    long getCurrentThreadUserTime();
    boolean isCurrentThreadCpuTimeSupported();
    boolean isThreadCpuTimeSupported();
    boolean isThreadCpuTimeEnabled();
    boolean isThreadUserTimeSupported();
    boolean isThreadUserTimeEnabled();
    void setThreadCpuTimeEnabled(boolean enable);
    void setThreadUserTimeEnabled(boolean enable);
    ThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers);
    ThreadInfo[] dumpThreads(ThreadInfo[] threads, boolean lockedMonitors, boolean lockedSynchronizers);
    long[] findDeadlockedThreads();
    long[] findMonitorDeadlockedThreads();
    long getThreadAllocatedBytes(long id);
    long[] getThreadAllocatedBytes(long[] ids);
    boolean isThreadAllocatedMemorySupported();
    boolean isThreadAllocatedMemoryEnabled();
    void setThreadAllocatedMemoryEnabled(boolean enable);
}
