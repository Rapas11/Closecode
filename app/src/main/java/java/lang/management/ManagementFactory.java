package java.lang.management;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TEMPORARY shims so the Kotlin compiler can initialise on Android.
 *
 * The K2 compiler's PerformanceManager touches java.lang.management, which does
 * not exist in Android's core library. These no-op implementations let startup
 * proceed. Nothing here reports real data.
 */
public final class ManagementFactory {

    private static final ThreadMXBean THREAD_MX = new ThreadMXBean() {
        public int getThreadCount() { return 1; }
        public int getPeakThreadCount() { return 1; }
        public int getTotalStartedThreadCount() { return 1; }
        public int getDaemonThreadCount() { return 0; }
        public long[] getAllThreadIds() { return new long[0]; }
        public ThreadInfo getThreadInfo(long id) { return null; }
        public ThreadInfo[] getThreadInfo(long[] ids) { return new ThreadInfo[0]; }
        public ThreadInfo[] getThreadInfo(long[] ids, int maxDepth) { return new ThreadInfo[0]; }
        public long getThreadCpuTime(long id) { return -1L; }
        public long getThreadCpuTime() { return -1L; }
        public long getThreadUserTime(long id) { return -1L; }
        public long getThreadUserTime() { return -1L; }
        public long getCurrentThreadCpuTime() { return -1L; }
        public long getCurrentThreadUserTime() { return -1L; }
        public boolean isCurrentThreadCpuTimeSupported() { return false; }
        public boolean isThreadCpuTimeSupported() { return false; }
        public boolean isThreadCpuTimeEnabled() { return false; }
        public boolean isThreadUserTimeSupported() { return false; }
        public boolean isThreadUserTimeEnabled() { return false; }
        public void setThreadCpuTimeEnabled(boolean enable) { }
        public void setThreadUserTimeEnabled(boolean enable) { }
        public ThreadInfo[] dumpAllThreads(boolean a, boolean b) { return new ThreadInfo[0]; }
        public ThreadInfo[] dumpThreads(ThreadInfo[] t, boolean a, boolean b) { return new ThreadInfo[0]; }
        public long[] findDeadlockedThreads() { return null; }
        public long[] findMonitorDeadlockedThreads() { return null; }
        public long getThreadAllocatedBytes(long id) { return -1L; }
        public long[] getThreadAllocatedBytes(long[] ids) { return new long[0]; }
        public boolean isThreadAllocatedMemorySupported() { return false; }
        public boolean isThreadAllocatedMemoryEnabled() { return false; }
        public void setThreadAllocatedMemoryEnabled(boolean enable) { }
    };

    private static final RuntimeMXBean RUNTIME_MX = new RuntimeMXBean() {
        public long getStartTime() { return 0L; }
        public long getUptime() { return 0L; }
        public String getName() { return "Android"; }
        public String getVmName() { return "Dalvik"; }
        public String getVmVersion() { return "0"; }
        public String getSpecName() { return "Android"; }
        public String getManagementSpecVersion() { return "unavailable"; }
        public ClassLoader getClassLoader() { return getClass().getClassLoader(); }
        public String getClassPath() { return ""; }
        public String getBootClassPath() { return ""; }
        public boolean isBootClassPathSupported() { return false; }
        public long getClassLoadingTime() { return 0L; }
        public long getTotalCompilationTime() { return 0L; }
        public Map<String, String> getInputArguments() { return Collections.emptyMap(); }
        public Map<String, String> getSystemProperties() { return Collections.emptyMap(); }
    };

    private static final MemoryMXBean MEMORY_MX = new MemoryMXBean() {
        public MemoryUsage getHeapMemoryUsage() { return new MemoryUsage(0, 0, 0); }
        public MemoryUsage getNonHeapMemoryUsage() { return new MemoryUsage(0, 0, 0); }
        public int getObjectPendingFinalizationCount() { return 0; }
        public void gc() { }
    };

    private ManagementFactory() {
    }

    public static ThreadMXBean getThreadMXBean() { return THREAD_MX; }

    public static RuntimeMXBean getRuntimeMXBean() { return RUNTIME_MX; }

    public static MemoryMXBean getMemoryMXBean() { return MEMORY_MX; }

    public static CompilationMXBean getCompilationMXBean() { return null; }

    public static ClassLoadingMXBean getClassLoadingMXBean() { return null; }

    public static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() { return Collections.emptyList(); }

    public static List<MemoryPoolMXBean> getMemoryPoolMXBeans() { return Collections.emptyList(); }

    public static <T> T getPlatformMXBean(Class<T> type) { return null; }

    public static List<?> getPlatformMXBeans(Class<?> type) { return Collections.emptyList(); }
}
