package nachos.threads;

import java.util.Comparator;

public class PriorityQueueDS {
	public PriorityQueueDS(long waitTime, KThread thread) {
		super();
		this.waitTime = waitTime;
		this.thread = thread;
	}
	public long getWaitTime() {
		return waitTime;
	}
	public void setWaitTime(long waitTime) {
		this.waitTime = waitTime;
	}
	public KThread getThread() {
		return thread;
	}
	public void setThread(KThread thread) {
		this.thread = thread;
	}
	long waitTime;
	KThread thread;
}