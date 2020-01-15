package nachos.threads;

import nachos.machine.*;

/**
 * A <i>SquadMatch</i> groups together player threads of the 
 * three different abilities to play matches with each other.
 * Implement the class <i>SquadMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into such groups.
 */
public class SquadMatch {
    
    /**
     * Allocate a new SquadMatch for matching players of different
     * abilities into a squad to play a match.
     */
    public SquadMatch () {
    	
    }
    
    public boolean printFlags() {
    	boolean flag = true;
    	for (int i=0; i<3; ++i) {
    		//System.out.print(flags[i] + " ");
    		if(flags[i] == false) {
    			flag = false;
    			break;
    		}
    	}
    	//System.out.println("--");
    	return flag;
    }
    
    public void printFlags1() {
    	for (int i=0; i<3; ++i) {
    		System.out.print(flags[i] + " ");
    	}
    	//System.out.println("--");
    }

    /**
     * Wait to form a squad with wizard and thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void warrior () {
    	lock.acquire();
    	
    	if (!flags[0]) {
    		flags[0] = true;
    	}
    	
    	cvThief.wake();
    	cvWizard.wake();
    	cvWarrier.wake();
    	if (!printFlags())
    		cvWarrier.sleep();
    	
    	flags[0] = false;
    	lock.release();
    }

    /**
     * Wait to form a squad with warrior and thief threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void wizard () {
    	lock.acquire();
    	if (!flags[1]) {
    		flags[1] = true;
    	}
    	cvThief.wake();
    	cvWarrier.wake();
    	cvWizard.wake();
    	if(!printFlags())
    		cvWizard.sleep();
    	
    	flags[1] = false;
    	lock.release();
    }

    /**
     * Wait to form a squad with warrior and wizard threads, only
     * returning once all three kinds of player threads have called
     * into this SquadMatch.  A squad always has three threads, and
     * can only be formed by three different kinds of threads.  Many
     * matches may be formed over time, but any one player thread can
     * be assigned to only one match.
     */
    public void thief () {
    	lock.acquire();

    	if (!flags[2]) {
    		flags[2] = true;
    	}
    	
    	cvWizard.wake();
    	cvWarrier.wake();
    	cvThief.wake();
    	if(!printFlags())
    		cvThief.sleep();
    	flags[2] = false;
    	lock.release();
    }
    
    Lock lock = new Lock();
	Condition cvWarrier	 = new Condition(lock);
	boolean flags[] = new boolean[3];
	Condition cvWizard = new Condition(lock);
	Condition cvThief = new Condition(lock);
	
    // Place SquadMatch test code inside of the SquadMatch class.

    public static void squadTest1 () {
    final SquadMatch match = new SquadMatch();

	// Instantiate the threads
	KThread w1 = new KThread( new Runnable () {
		public void run() {
		    match.warrior();
		    System.out.println ("w1 matched");
		}
	    });
	w1.setName("w1");

	KThread z1 = new KThread( new Runnable () {
		public void run() {
		    match.wizard();
		    System.out.println ("z1 matched");
		}
	    });
	z1.setName("z1");

	KThread t1 = new KThread( new Runnable () {
		public void run() {
		    match.thief();
		    System.out.println ("t1 matched");
		}
	    });
	t1.setName("t1");

	// Run the threads.
	w1.fork();
	z1.fork();
	t1.fork();
	
	// if you have join implemented, use the following:
	t1.join();
	z1.join();
	w1.join();
	
	// if you do not have join implemented, use yield to allow
	// time to pass...10 yields should be enough
	/*for (int i = 0; i < 10; i++) {
	    KThread.currentThread().yield();
	}*/
    }
    
    public static void selfTest() {
    	squadTest1();
    }

}
