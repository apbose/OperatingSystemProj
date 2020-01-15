
package nachos.vm;

import nachos.machine.*;
import nachos.userprog.UserProcess;

/**
 * A single translation between a virtual page and a physical page.
 */
public final class InvertedPageTable {
	/**
	 * Allocate a new invalid translation entry.
	 */
	public InvertedPageTable() {
		this.ppn = -1;
		this.vpn = -1;
		this.pinned = false;
		this.process = null;
		this.te = null;
	}

	/**
	 * Allocate a new translation entry with the specified initial state.
	 * 
	 * @param vpn the virtual page number.
	 * @param ppn the physical page number.
	 * @param pinned the valid bit.
	 * @param process the process which holds this page.
	 */
	public InvertedPageTable(int vpn, int ppn, boolean pinned, VMProcess process) {
		this.vpn = vpn;
		this.ppn = ppn;
		this.pinned = pinned;
		this.process = process;
	}

	/**
	 * Allocate a new translation entry, copying the contents of an existing
	 * one.
	 * 
	 * @param entry the translation entry to copy.
	 */
	public InvertedPageTable(TranslationEntry entry) {
		this.vpn = entry.vpn;
		this.ppn = entry.ppn;
		this.pinned = false;
		this.process = null;
		this.te = entry;
	}

	/** The virtual page number. */
	public int vpn;

	/** The physical page number. */
	public int ppn;

	/**
	 * Process to which this TE belongs to.
	 */
	public VMProcess process;
	
	
	/**
	 * Pinned flag.
	 */
	boolean pinned;
	
	public TranslationEntry te;
	
	/**
	 * If this flag is <tt>false</tt>, this translation entry is ignored.
	 */
	//public boolean valid;

	/**
	 * If this flag is <tt>true</tt>, the user pprogram is not allowed to modify
	 * the contents of this virtual page.
	 */
	//public boolean readOnly;

	/**
	 * This flag is set to <tt>true</tt> every time the page is read or written
	 * by a user program.
	 */
	//public boolean used;

	/**
	 * This flag is set to <tt>true</tt> every time the page is written by a
	 * user program.
	 */
	//public boolean dirty;
	
}
