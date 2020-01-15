package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		this.swapList = new LinkedList<Integer>();
		this.totalSPNCount = 0;
		this.swapPage = 0;
		
		this.allPinned = 0;
//		this.allPinned = 0;
		
		this.swapFile = ThreadedKernel.fileSystem.open("jsr.txt", true);
		
		clockLock = new Lock();
		allPagesPinnedCV = new Condition(clockLock);
		
		ipt = new InvertedPageTable[Machine.processor().getNumPhysPages()];
		for (int i=0; i<Machine.processor().getNumPhysPages(); i++) {
			ipt[i] = new InvertedPageTable();
		}
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		// CLOSE AND DELETE SWAPFILE
		this.swapFile.close();
		ThreadedKernel.fileSystem.remove("jsr.txt");
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static LinkedList<Integer> swapList;
	
	public static OpenFile swapFile;
	
	public static int totalSPNCount;
	
	public static Lock clockLock;
	
	public static Condition allPagesPinnedCV;
	
	public static InvertedPageTable[] ipt;
	
	public static int swapPage;
	
	public static int allPinned;
}
