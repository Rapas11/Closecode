package java.lang.management;

public class MemoryUsage {
    private final long used;
    private final long committed;

    public MemoryUsage() {
        this(0, 0, 0);
    }

    public MemoryUsage(long init, long used, long committed) {
        this.used = used;
        this.committed = committed;
    }

    public long getInit() { return used; }
    public long getUsed() { return used; }
    public long getCommitted() { return committed; }
    public long getMax() { return committed; }
    public long getUsedSize() { return used; }
    public long getCommittedSize() { return committed; }
    public long getMaxSize() { return committed; }
    public long getInitSize() { return used; }
    @Override public String toString() { return used + "/" + committed; }
}
