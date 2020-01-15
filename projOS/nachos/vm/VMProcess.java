package nachos.vm;

import java.util.Arrays;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		//return super.loadSections();
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, i, false, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}
	
	/**
	 * Clock Algorithm
	 * @return
	 */
	protected int clockAlgorithm() {
		//VMKernel.clockLock.acquire();
		//int swapPage = 0; // keep this as global and shared variable 
		//int allPinned = 1;
		int temp = 0; 
		while(true){
			if(VMKernel.ipt[VMKernel.swapPage].pinned == false){
//				VMKernel.allPinned = 0;
				//VMKernel.clockLock.release();
				
				temp = VMKernel.swapPage;
				VMKernel.swapPage = (VMKernel.swapPage + 1 ) % (Machine.processor().getNumPhysPages());
				
				if(VMKernel.ipt[temp].te.used == false) {
					return temp;
				}
				else {
					VMKernel.ipt[temp].te.used = false;
				}				
			}
		 
			if(VMKernel.ipt[VMKernel.swapPage].pinned == true){
				VMKernel.swapPage = (VMKernel.swapPage + 1) % (Machine.processor().getNumPhysPages());
//				VMKernel.allPinned = 1;
//				VMKernel.swapPage++;
			}
		 
//			if(VMKernel.swapPage == Machine.processor().getNumPhysPages() && VMKernel.allPinned == 1){
//				VMKernel.allPagesPinnedCV.sleep(); // use condition variable here
//			}
			if(VMKernel.allPinned == Machine.processor().getNumPhysPages()) {
				System.out.println("$$$$$$$$$$$$$");
				VMKernel.allPagesPinnedCV.sleep(); 
			}
		}
	}
	
	
	protected void handlePaging(int vaddr) {
		UserKernel.lockFreePhyPages.acquire();
		Processor processor = Machine.processor();
		int bvpage = processor.pageFromAddress(vaddr);
		
//		System.out.println("inside paging ");
//		System.out.println(Machine.processor().getNumPhysPages());
//		System.out.println(vaddr);
		
		int pagesLoaded = 0, vpn = 0;
		TranslationEntry te;
		
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				vpn = section.getFirstVPN() + i;
				
				if(vpn == bvpage) {
					te = pageTable[vpn];
					
					int phyIndex = -1;
					if(UserKernel.phyPages.isEmpty()) {
						phyIndex = clockAlgorithm(); // B's ppn, this -> current A
						int spn = -1;
						int evictedVPN = VMKernel.ipt[phyIndex].vpn;   // B's vpn
						VMProcess evictedProcess = VMKernel.ipt[phyIndex].process;   // B is evicted
//						evictedProcess.pageTable[evictedVPN].ppn = phyIndex;  // TAKE NOTE: VERY IMP
						
						evictedProcess.pageTable[evictedVPN].valid = false;
						
						te.ppn = phyIndex;
						te.valid = true;
						te.used = true;
						
						VMKernel.ipt[phyIndex].process = this;
						VMKernel.ipt[phyIndex].ppn = phyIndex;
						VMKernel.ipt[phyIndex].vpn = vpn;
						VMKernel.ipt[phyIndex].te = te;
												
						if (evictedProcess.pageTable[evictedVPN].dirty) {
							if (!VMKernel.swapList.isEmpty()) {
								spn = VMKernel.swapList.removeFirst();
							}
							
							else {
								spn = VMKernel.totalSPNCount++;
							}
//							VMKernel.swapFile.write(spn * pageSize, processor.getMemory(), phyIndex * pageSize, pageSize);
							VMKernel.swapFile.write(Processor.makeAddress(spn, 0), processor.getMemory(), Processor.makeAddress(phyIndex, 0) , pageSize);
							evictedProcess.pageTable[evictedVPN].vpn = spn;
						}
					}

					else {
						phyIndex = UserKernel.phyPages.removeFirst();
						te.ppn = phyIndex;
						te.valid = true;
						te.used = true;
						
						VMKernel.ipt[phyIndex].process = this;
						VMKernel.ipt[phyIndex].ppn = phyIndex;
						VMKernel.ipt[phyIndex].vpn = vpn;
						VMKernel.ipt[phyIndex].te = te;
					}
					
					
					// loading started (black penguins ) -- lazy loading
					if (!te.dirty) {
						if(section.isReadOnly()) {
							te.readOnly = true;
						}
						section.loadPage(i, phyIndex);	
						pagesLoaded++;
					}
					
					else {
//						int pageNumber = te.ppn;
						te.dirty = true;
//						VMKernel.swapFile.read(te.vpn * pageSize, processor.getMemory(), pageNumber * pageSize , pageSize);
						VMKernel.swapFile.read(Processor.makeAddress(te.vpn, 0), processor.getMemory(), Processor.makeAddress(te.ppn, 0) , pageSize);
						VMKernel.swapList.add(te.vpn);
						
						// ADDED LATE NIGHT 
//						te.vpn = vpn;
					}
				}
			}
		}
		
		// Stack
		for (int i = 0; i < (numPages - pagesLoaded); i++) {
			vpn = vpn + 1;
			if(vpn == bvpage) {
				te = pageTable[vpn];
				
				int phyIndex = -1;
				if(UserKernel.phyPages.isEmpty()) {
					phyIndex = clockAlgorithm(); // B's ppn, this -> current A
					int spn = -1;
					int evictedVPN = VMKernel.ipt[phyIndex].vpn;   // B's vpn
					VMProcess evictedProcess = VMKernel.ipt[phyIndex].process;   // B is evicted
//					evictedProcess.pageTable[evictedVPN].ppn = phyIndex;  // TAKE NOTE: VERY IMP
					
					evictedProcess.pageTable[evictedVPN].valid = false;
					
					te.ppn = phyIndex;
					te.valid = true;
					te.used = true;
					
					VMKernel.ipt[phyIndex].process = this;
					VMKernel.ipt[phyIndex].ppn = phyIndex;
					VMKernel.ipt[phyIndex].vpn = vpn;
					VMKernel.ipt[phyIndex].te = te;
					
					if (evictedProcess.pageTable[evictedVPN].dirty) {
						if (!VMKernel.swapList.isEmpty()) {
							spn = VMKernel.swapList.removeFirst();
						}
						
						else {
							spn = VMKernel.totalSPNCount++;
						}
//						VMKernel.swapFile.write(spn * pageSize, processor.getMemory(), phyIndex * pageSize, pageSize);
						VMKernel.swapFile.write(Processor.makeAddress(spn, 0), processor.getMemory(), Processor.makeAddress(phyIndex, 0) , pageSize);
						evictedProcess.pageTable[evictedVPN].vpn = spn;
					}
				}
				
				else {
					phyIndex = UserKernel.phyPages.removeFirst();
					te.ppn = phyIndex;
					te.valid = true;
					te.used = true;
					
					VMKernel.ipt[phyIndex].process = this;
					VMKernel.ipt[phyIndex].ppn = phyIndex;
					VMKernel.ipt[phyIndex].vpn = vpn;
					VMKernel.ipt[phyIndex].te = te;
				}
				
				// lazy loading
				if (!te.dirty) {
					byte [] zeros = new byte[pageSize];
					for(int j =0; i < pageSize; i++) {
						zeros[j] = 0;
					}	
					System.arraycopy(zeros, 0 , processor.getMemory(), te.ppn * pageSize, pageSize);		
				}
				
				else {
//					int pageNumber = te.ppn;
					te.dirty = true;
//					VMKernel.swapFile.read(te.vpn * pageSize, processor.getMemory(), pageNumber * pageSize , pageSize);
					VMKernel.swapFile.read(Processor.makeAddress(te.vpn, 0), processor.getMemory(), Processor.makeAddress(te.ppn, 0) , pageSize);
					VMKernel.swapList.add(te.vpn);
				}
				
			}
		}
		UserKernel.lockFreePhyPages.release();
		
//		System.out.println(Arrays.toString(pageTable));
//		for (int i = 0; i < pageTable.length; i++) {
//			System.out.printf("vpn: %d, ppn: %d,", pageTable[i].vpn, pageTable[i].ppn);
//			System.out.println("VPN: "+ pageTable[i].vpn);
//			System.out.println("PPN: " +  pageTable[i].ppn);
//			System.out.println(pageTable[i].vpn + page);
//		}
	}
	
	
	
	
	
	/*
	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		VMKernel.clockLock.acquire();
		byte[] memory = Machine.processor().getMemory();

//		|| vaddr >= memory.length
		
		if (vaddr < 0) {
			VMKernel.clockLock.release();
			return 0;
		}
		
		int paddr = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int initialOffset = Processor.offsetFromAddress(vaddr);
		
		if(vpn >= pageTable.length) {
			VMKernel.clockLock.release();
			return 0;
		}
		
		TranslationEntry te = pageTable[vpn];
		
		if(te.valid == false) {
			handlePaging(vaddr);
			if(pageTable[vpn].valid) {
				te = pageTable[vpn];
				
				if(te.readOnly == true) {
					VMKernel.clockLock.release();
					return 0;
				}
				te.used = true;
				VMKernel.ipt[te.ppn].pinned = true;
			}
			else {
				VMKernel.clockLock.release();
				return 0;
			}
		}
		
		if (te.readOnly == true) {
			VMKernel.clockLock.release();
			return 0;
		}
		
		paddr = te.ppn * pageSize + initialOffset;
		
		int totalWritten=0;
		
		for(int i=0; i < length/pageSize; i++) {
			
			int amount = pageSize - initialOffset;
			System.arraycopy(data, offset, memory, paddr, amount);
			
			if(amount > 0) {
				te.dirty = true;
			}
			
			offset += amount;
			totalWritten += amount;
			
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPagesPinnedCV.wake();
			
			//te.used = false;
			vpn = vpn + 1;
			
			if(vpn >= pageTable.length) {
				VMKernel.clockLock.release();
				return totalWritten;
			}
			
			te = pageTable[vpn];
			
			if(te.valid == false) {
				
				handlePaging(Processor.makeAddress(vpn, 0));
				if(pageTable[vpn].valid) {
					te = pageTable[vpn];
					if(te.readOnly == true) {
						VMKernel.clockLock.release();
						return totalWritten;
					}
					te.used = true;
					VMKernel.ipt[te.ppn].pinned = true;
				}
				else {
					VMKernel.clockLock.release();
					return totalWritten;
				}
			}
			
			if (te.readOnly == true) {
				VMKernel.clockLock.release();
				return totalWritten;
			}
			
			initialOffset = 0;
			paddr = te.ppn * pageSize;
			VMKernel.ipt[te.ppn].pinned = true;
			
		}
			
		if(te.valid == true && te.readOnly == false) {
			
			int amount = length - totalWritten;		
			System.arraycopy(data, offset, memory, paddr, amount);
			if(amount > 0) {
				te.dirty = true;
			}
			
			totalWritten += amount;
			//te.used = false;
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPagesPinnedCV.wake();
		}
		VMKernel.clockLock.release();
		return totalWritten;
	}
	*/
	
	
	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
 
		VMKernel.clockLock.acquire();
		byte[] memory = Machine.processor().getMemory();
 
//		|| vaddr >= memory.length
 
		if (vaddr < 0) {
			VMKernel.clockLock.release();
			return 0;
		}
 
		int paddr = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int initialOffset = Processor.offsetFromAddress(vaddr);
 
		if(vpn >= pageTable.length) {
			VMKernel.clockLock.release();
			return 0;
		}
 
		TranslationEntry te = pageTable[vpn];
 
		if(te.valid == false) {
			handlePaging(vaddr);
			if(pageTable[vpn].valid) {
				te = pageTable[vpn];
				if(te.readOnly == true) {
					VMKernel.clockLock.release();
					return 0;
				}
//				te.used = true;
//				VMKernel.ipt[te.ppn].pinned = true;
			}
			else {
				VMKernel.clockLock.release();
				return 0;
			}
		}
		
		
		paddr = te.ppn * pageSize + initialOffset;
		
		te.used = true;
		VMKernel.ipt[te.ppn].pinned = true;
		VMKernel.allPinned++;
		
		int totalWritten=0;
 
		for(int i=0; i < length/pageSize; i++) {
 
			int amount = pageSize - initialOffset;
			System.arraycopy(data, offset, memory, paddr, amount);
 
			if(amount > 0) {
				te.dirty = true;
			}
 
			offset += amount;
			totalWritten += amount;
 
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPinned--;
			VMKernel.allPagesPinnedCV.wake();
 
			vpn = vpn + 1;
 
			if(vpn >= pageTable.length) {
				VMKernel.clockLock.release();
				return totalWritten;
			}
 
			te = pageTable[vpn];
 
			if(te.valid == false) {
				handlePaging(Processor.makeAddress(vpn, 0));
				if(pageTable[vpn].valid) {
					te = pageTable[vpn];
					if(te.readOnly == true) {
						VMKernel.clockLock.release();
						return totalWritten;
					}
//					te.used = true;					
//					VMKernel.ipt[te.ppn].pinned = true;
				}
				else {
					VMKernel.clockLock.release();
					return totalWritten;
				}
			}
 
			initialOffset = 0;
			paddr = te.ppn * pageSize;
//			te.used = true;
//			VMKernel.ipt[te.ppn].pinned = true;
			te.used = true;
			VMKernel.ipt[te.ppn].pinned = true;
			VMKernel.allPinned++;
		}	
		if(te.valid == true && te.readOnly == false) {
			//only possible when initialOffset is non zero
			if(length - totalWritten > pageSize - initialOffset ) {
				int amount = pageSize - initialOffset;
				System.arraycopy(data, offset, memory, paddr, amount);				
				if(amount > 0) {
					te.dirty = true;
				}
 
				offset += amount;
				totalWritten += amount;
 
				VMKernel.ipt[te.ppn].pinned = false;
				VMKernel.allPinned--;
				VMKernel.allPagesPinnedCV.wake();
 
				vpn = vpn + 1;
 
				if(vpn >= pageTable.length) {
					VMKernel.clockLock.release();
					return totalWritten;
				}
 
				te = pageTable[vpn];
 
				if(te.valid == false) {
 
					handlePaging(Processor.makeAddress(vpn, 0));
					if(pageTable[vpn].valid) {
						te = pageTable[vpn];
						if(te.readOnly == true) {
							VMKernel.clockLock.release();
							return totalWritten;
						}
//						te.used = true;
//						VMKernel.ipt[te.ppn].pinned = true;
					}
					else {
						VMKernel.clockLock.release();
						return totalWritten;
					}
				}
 
				initialOffset = 0;
				paddr = te.ppn * pageSize;
//				te.used = true;
//				VMKernel.ipt[te.ppn].pinned = true;
				te.used = true;
				VMKernel.ipt[te.ppn].pinned = true;
				VMKernel.allPinned++;
 
				amount = length - totalWritten;		
				System.arraycopy(data, offset, memory, paddr, amount);
				if(amount > 0) {
					te.dirty = true;
				}
 
				totalWritten += amount;
				VMKernel.ipt[te.ppn].pinned = false;
				VMKernel.allPinned--;
				VMKernel.allPagesPinnedCV.wake();
			}
			else {
				int amount = length - totalWritten;		
				System.arraycopy(data, offset, memory, paddr, amount);
				if(amount > 0) {
					te.dirty = true;
				}
 
				totalWritten += amount;
				VMKernel.ipt[te.ppn].pinned = false;
				VMKernel.allPinned--;
				VMKernel.allPagesPinnedCV.wake();
			}
		}
		VMKernel.clockLock.release();
		return totalWritten;			
	}
	
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
 
		VMKernel.clockLock.acquire();
		byte[] memory = Machine.processor().getMemory();
 
		if (vaddr < 0)
		{
			VMKernel.clockLock.release();
			return 0;
		}
		int paddr = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int initialOffset = Processor.offsetFromAddress(vaddr);
 
		if(vpn >= pageTable.length) {
			VMKernel.clockLock.release();
			return 0;
		}
 
		TranslationEntry te = pageTable[vpn];
 
		if(te.valid == false) {
			handlePaging(vaddr);
			if(pageTable[vpn].valid) {
				te = pageTable[vpn];
//				VMKernel.ipt[te.ppn].pinned = true;
			}
			else {
				VMKernel.clockLock.release();
				return 0;
			}
		}
		
		te.used = true;
		VMKernel.ipt[te.ppn].pinned = true;
		VMKernel.allPinned++;
		
		paddr = te.ppn * pageSize + initialOffset;
		int totalRead = 0;
 
		for(int i = 0; i < length/pageSize; i++) { 
 
			int amount = pageSize - initialOffset;
			System.arraycopy(memory, paddr, data, offset, amount);
			offset += amount;
			totalRead += amount;
 
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPinned--;
			VMKernel.allPagesPinnedCV.wake();
 
			vpn = vpn + 1;
 
			if(vpn >= pageTable.length) {
				VMKernel.clockLock.release();
				return totalRead;
			}
 
			te = pageTable[vpn];
 
			if(te.valid == false) {
				handlePaging(Processor.makeAddress(vpn, 0));  // te.vpn originally is wrong as it is spn now
				if(pageTable[vpn].valid) {
					te = pageTable[vpn];
//					VMKernel.ipt[te.ppn].pinned = true;
				}
				else {
					VMKernel.clockLock.release();
					return totalRead;
				}
			}
 
			te.used = true;
			initialOffset = 0;
			paddr = te.ppn * pageSize;
			VMKernel.ipt[te.ppn].pinned = true;
			VMKernel.allPinned++;
			
		}
 
		if(te.valid == true) {
			//only possible when initialOffset is non zero
			if(length - totalRead > pageSize - initialOffset ) {
				int amount = pageSize - initialOffset;
				System.arraycopy(memory, paddr, data, offset, amount);
				offset +=amount;
				totalRead += amount;
				VMKernel.ipt[te.ppn].pinned = false;
				VMKernel.allPinned--;
				VMKernel.allPagesPinnedCV.wake();
 
				vpn = vpn+1;
 
				if(vpn >= pageTable.length) {
					VMKernel.clockLock.release();
					return totalRead;
				}
 
				te = pageTable[vpn];
 
				if(te.valid == false) {
					handlePaging(Processor.makeAddress(vpn, 0));  // te.vpn originally is wrong as it is spn now
					if(pageTable[vpn].valid) {
						te = pageTable[vpn];
//						VMKernel.ipt[te.ppn].pinned = true;
					}
					else {
						VMKernel.clockLock.release();
						return totalRead;
					}
				}
 
				te.used = true;
				initialOffset = 0;
				paddr = te.ppn * pageSize;
				VMKernel.ipt[te.ppn].pinned = true;
				VMKernel.allPinned++;
				
				amount = length - totalRead;
				System.arraycopy(memory, paddr, data, offset, amount);
				totalRead += amount;
 
				VMKernel.ipt[te.ppn].pinned = false;
				VMKernel.allPinned--;
				VMKernel.allPagesPinnedCV.wake();
 
			}
			else {
 
				int amount = length - totalRead;
				System.arraycopy(memory, paddr, data, offset, amount);
				totalRead += amount; 
				VMKernel.ipt[te.ppn].pinned = false;
				VMKernel.allPinned--;
				VMKernel.allPagesPinnedCV.wake();
			}
		}
		VMKernel.clockLock.release();
		return totalRead;
		}
	
	
	/*
	@Override 
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		VMKernel.clockLock.acquire();
		byte[] memory = Machine.processor().getMemory();
	
		if (vaddr < 0)
		{
			VMKernel.clockLock.release();
			return 0;
		}
		
		int paddr = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int initialOffset = Processor.offsetFromAddress(vaddr);
		
		if(vpn > pageTable.length) {
			VMKernel.clockLock.release();
			return 0;
		}
		
		TranslationEntry te = pageTable[vpn];
		
		if(te.valid == false) {
			handlePaging(vaddr);
			if(pageTable[vpn].valid) {
				te = pageTable[vpn];
				te.used = true;
				VMKernel.ipt[te.ppn].pinned = true;
			}
			else {
				VMKernel.clockLock.release();
				return 0;
			}
		}
		
		paddr = te.ppn * pageSize + initialOffset;
		
		//te.used = true;
		int totalRead = 0;
		
		for(int i = 0; i < length/pageSize; i++) { 
			
			int amount = pageSize - initialOffset;
			System.arraycopy(memory, paddr, data, offset, amount);
			offset += amount;
			totalRead += amount;
			
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPagesPinnedCV.wake();
			
			//te.used = false;
			vpn = vpn + 1;
			
			if(vpn > pageTable.length) {
				VMKernel.clockLock.release();
				return totalRead;
			}
			
			te = pageTable[vpn];
			
			if(te.valid == false) {
				handlePaging(Processor.makeAddress(te.vpn, 0));
				if(pageTable[vpn].valid) {
					te = pageTable[vpn];
					te.used = true;
					VMKernel.ipt[te.ppn].pinned = true;
				}
				else {
					VMKernel.clockLock.release();
					return totalRead;
				}
			}
			
			initialOffset = 0;
			paddr = te.ppn * pageSize;
			//te.used = true;
			VMKernel.ipt[te.ppn].pinned = true;
			
		}
		
		if(te.valid == true) {
			int amount = length - totalRead;
			System.arraycopy(memory, paddr, data, offset, amount);
			totalRead += amount;
			
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPagesPinnedCV.wake();
		}
		VMKernel.clockLock.release();
		return totalRead;
	}
	*/
	
	
	/*
	@Override 
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		VMKernel.clockLock.acquire();
		byte[] memory = Machine.processor().getMemory();
		
//		|| vaddr >= memory.length
		if (vaddr < 0)
		{
			VMKernel.clockLock.release();
			return 0;
		}
		int paddr = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int initialOffset = Processor.offsetFromAddress(vaddr);
		
		if(vpn >= pageTable.length) {
			VMKernel.clockLock.release();
			return 0;
		}
		
		TranslationEntry te = pageTable[vpn];
		
		if(te.valid == false) {
			handlePaging(vaddr);
			if(pageTable[vpn].valid) {
				te = pageTable[vpn];
				VMKernel.ipt[te.ppn].pinned = true;
			}
			else {
				VMKernel.clockLock.release();
				return 0;
			}
		}
		te.used = true;
		paddr = te.ppn * pageSize + initialOffset;
		
		//te.used = true;
		int totalRead = 0;
		
		for(int i = 0; i < length/pageSize; i++) { 
			
			int amount = pageSize - initialOffset;
			System.arraycopy(memory, paddr, data, offset, amount);
			offset += amount;
			totalRead += amount;
			
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPagesPinnedCV.wake();
			
			vpn = vpn + 1;
			
			if(vpn >= pageTable.length) {
				VMKernel.clockLock.release();
				return totalRead;
			}
			
			te = pageTable[vpn];
			
			if(te.valid == false) {
				handlePaging(Processor.makeAddress(vpn, 0));  // te.vpn originally is wrong as it is spn now
				if(pageTable[vpn].valid) {
					te = pageTable[vpn];
					VMKernel.ipt[te.ppn].pinned = true;
				}
				else {
					VMKernel.clockLock.release();
					return totalRead;
				}
			}
			
			te.used = true;
			initialOffset = 0;
			paddr = te.ppn * pageSize;
			//te.used = true;
			VMKernel.ipt[te.ppn].pinned = true;
			
		}
		
		if(te.valid == true) {
			int amount = length - totalRead;
			System.arraycopy(memory, paddr, data, offset, amount);
			totalRead += amount;
//			te.used = false;
			
			VMKernel.ipt[te.ppn].pinned = false;
			VMKernel.allPagesPinnedCV.wake();
		}
		VMKernel.clockLock.release();
		return totalRead;
	}
	*/

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionPageFault:
			int badaddr = processor.readRegister(Processor.regBadVAddr);
			handlePaging(badaddr);
			break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
