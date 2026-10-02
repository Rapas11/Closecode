package java.lang.management;

public class ThreadInfo {
    private final long id;
    private final String name;

    public ThreadInfo(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public long getThreadId() { return id; }
    public String getThreadName() { return name; }
    public String getLockName() { return null; }
    public long getLockOwnerId() { return -1L; }
    public String getLockOwnerName() { return null; }
    public long getBlockedTime() { return -1L; }
    public long getBlockedCount() { return -1L; }
    public long getWaitedTime() { return -1L; }
    public long getWaitedCount() { return -1L; }
    public boolean isSuspended() { return false; }
    public boolean isInNative() { return false; }
    @Override public String toString() { return name; }
}
