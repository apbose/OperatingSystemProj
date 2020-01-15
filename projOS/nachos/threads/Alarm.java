package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityQueueDS;

import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}
	
	/**
	 * Priority Queue to hold the thread and it's associated wakeTime to ensure
	 * we wake the appropriate threads in the right order.
	 */
	public PriorityQueue<PriorityQueueDS> pq = new PriorityQueue<PriorityQueueDS>(new PriorityQueueDSComparator());

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		//KThread.currentThread().yield();
		//System.out.println("Ping ..");
		boolean intStatus = Machine.interrupt().disable();
		while (!pq.isEmpty() && Machine.timer().getTime() >= pq.peek().getWaitTime()) {
			//System.out.println("Pong ..");
			PriorityQueueDS threadTop = pq.poll();
			threadTop.thread.ready();
			Machine.interrupt().restore(intStatus);
		}
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		
		if (x <= 0) {
			return;
		}
		
		long wakeTime = Machine.timer().getTime() + x;
		boolean intStatus = Machine.interrupt().disable();
		PriorityQueueDS sleepThread = new PriorityQueueDS(wakeTime, KThread.currentThread());
		pq.add(sleepThread);
		
		sleepThread.thread.sleep();
		Machine.interrupt().restore(intStatus);
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
        public boolean cancel(KThread thread) {
		return false;
	}
        
        // Add Alarm testing code to the Alarm class
        
        public static void alarmTest1() {
    	int durations[] = {1000, 10*1000, 100*1000};
    	long t0, t1;

    	for (int d : durations) {
    	    t0 = Machine.timer().getTime();
    	    ThreadedKernel.alarm.waitUntil (d);
    	    t1 = Machine.timer().getTime();
    	    System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
    	}
        }

        // Implement more test methods here ...

        public static void alarmTest2() {
        	int durations[] = {10000, 100, 0, -100, 480, 490};
        	long t0, t1;

        	for (int d : durations) {
        	    t0 = Machine.timer().getTime();
        	    ThreadedKernel.alarm.waitUntil (d);
        	    t1 = Machine.timer().getTime();
        	    System.out.println ("alarmTest2: waited for " + (t1 - t0) + " ticks");
        	}
        }
        // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
        public static void selfTest() {
    	alarmTest1();
    	alarmTest2();
    	// Invoke your other test methods here ...
        }            
}