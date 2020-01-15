package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	
	private OpenFile[] openFiles;
	public int processID = 0;
	public UserProcess() {
		
		UserKernel.lockPID.acquire();
		processID = UserKernel.PID;
		UserKernel.PID++;
		UserKernel.numProcesses++;
		UserKernel.lockPID.release();
		this.map = new HashMap<Integer, UserProcess>();
		this.childStatus = new HashMap<Integer, Integer>();
		this.parentReference = null;
		
		//childStatus = new LinkedList<Integer>();
		
		int numPhysPages = Machine.processor().getNumPhysPages();
		//System.out.println(numPhysPages);
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		
		/* Initialize openFiles LinkedList to hold file handlers. */ 
		openFiles = new OpenFile[16];
		openFiles[0] = UserKernel.console.openForReading();
		openFiles[1] = UserKernel.console.openForWriting();
		
		//lock = new Lock();
		//cv = new Condition(UserKernel.lockChild);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
	
		if (vaddr < 0 || vaddr >= memory.length)
		{
			return 0;
		}
		int paddr = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int initialOffset = Processor.offsetFromAddress(vaddr);
		
		if(vpn > pageTable.length) {
			return 0;
		}
		
		TranslationEntry te = pageTable[vpn];
		
		if(te.valid == false) {
			return 0;
		}
		
		paddr = te.ppn * pageSize + initialOffset;
		
		te.used = true;
		int totalRead = 0;
		
		for(int i = 0; i < length/pageSize; i++) { 
			
			int amount = pageSize - initialOffset;
			System.arraycopy(memory, paddr, data, offset, amount);
			offset += amount;
			totalRead += amount;
			
			te.used = false;
			vpn = vpn + 1;
			
			if(vpn > pageTable.length) {
				return totalRead;
			}
			
			te = pageTable[vpn];
			
			if(te.valid == false) {
				return totalRead;
			}
			
			initialOffset = 0;
			paddr = te.ppn * pageSize;
			te.used = true;
			
		}
		
		if(te.valid == true) {
			int amount = length - totalRead;
			System.arraycopy(memory, paddr, data, offset, amount);
			totalRead += amount;
			te.used = false;
		}
		
		return totalRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= memory.length)
			return 0;
		
		int paddr = 0;
		int vpn = Processor.pageFromAddress(vaddr);
		int initialOffset = Processor.offsetFromAddress(vaddr);
		
		if(vpn > pageTable.length) {
			return 0;
		}
		
		TranslationEntry te = pageTable[vpn];
		
		if(te.valid == false || te.readOnly == true) {
			return 0;
		}
		
		paddr = te.ppn * pageSize + initialOffset;
		
		te.used = true;
		int totalWritten=0;
		
		for(int i=0; i < length/pageSize; i++) {
			
			int amount = pageSize - initialOffset;
			System.arraycopy(data, offset, memory, paddr, amount);
			offset += amount;
			totalWritten += amount;
			
			te.used = false;
			vpn = vpn + 1;
			
			if(vpn > pageTable.length) {
				return totalWritten;
			}
			
			te = pageTable[vpn];
			
			if(te.valid == false || te.readOnly == true) {
				return totalWritten;
			}
			
			initialOffset = 0;
			paddr = te.ppn * pageSize;
			te.used = true;
			
		}
			
		if(te.valid == true && te.readOnly == false) {
			
			int amount = length - totalWritten;
			System.arraycopy(data, offset, memory, paddr, amount);
			totalWritten += amount;
			te.used = false;
		}
		
		return totalWritten;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		//if (numPages > Machine.processor().getNumPhysPages()) {
		UserKernel.lockFreePhyPages.acquire();
		//System.out.println("Free Pages :- " + UserKernel.phyPages.size());
		if (numPages > UserKernel.phyPages.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.lockFreePhyPages.release();
			return false;
		}
		
		int pagesLoaded = 0, vpn = 0;
		TranslationEntry te;
		
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				vpn = section.getFirstVPN() + i;
				
				te = pageTable[vpn];
				
				int phyIndex = UserKernel.phyPages.removeFirst();
				te.ppn = phyIndex;
				te.valid = true;
				
				if(section.isReadOnly()) {
					te.readOnly = true;
				}
				section.loadPage(i, phyIndex);
				
				pagesLoaded++;
				
			}
		}
		
		for (int i = 0; i < (numPages - pagesLoaded); i++) {

			vpn = vpn + 1;
			te = pageTable[vpn];
			
			int phyIndex = UserKernel.phyPages.removeFirst();
			te.ppn = phyIndex;
			te.valid = true;
			
		}
		UserKernel.lockFreePhyPages.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		
		TranslationEntry te;
		UserKernel.lockFreePhyPages.acquire();
		for (int i = 0; i < pageTable.length; i++) {
			te = pageTable[i];
			if (te.valid == true) {
				UserKernel.phyPages.add(te.ppn);
			}
		}
	    UserKernel.lockFreePhyPages.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}
	
	/**
	 * Handle the create() system call. Returns the index of file descriptor if successful.
	 */
	private int handleCreate(int vaddr) {
//		|| vaddr >= Machine.processor().getMemory().length
		if (vaddr < 0) {
			return -1;
		}
		
		String name = readVirtualMemoryString(vaddr, 256);
		
		if (name == null) {
			return -1;
		}
		
		for (int i=0; i<openFiles.length; i++) {
			if (openFiles[i] == null) {
				OpenFile executable = ThreadedKernel.fileSystem.open(name, true);
				if (executable == null) {
					Lib.debug(dbgProcess, "\topen failed");
					return -1;
				}
				else {
					System.out.println("File: " + name + " created successfully");
					this.openFiles[i] = executable;
					return i;
				}
			}
		}
		return -1;
	}
		
		/**
		 * Handle the open() system call.
		 */
		private int handleOpen(int vaddr) {
//			|| vaddr >= Machine.processor().getMemory().length
			if (vaddr < 0) {
				return -1;
			}

			String name = readVirtualMemoryString(vaddr, 256);
			
			if (name == null) {
				return -1;
			}
			
			// Valid
			for (int i=0; i < openFiles.length; i++) {
				if (openFiles[i] == null) {
					OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
					if (executable == null) {
						Lib.debug(dbgProcess, "\topen failed");
						return -1;
					}
					else {
						System.out.println("File: " + name + " opened successfully");
						this.openFiles[i] = executable;
						return i;
					}
				}
			}
		return -1;
		
	}
		
		/**
		 * Handle the close() system call.
		 */
		private int handleClose(int index) {
			if (index < 0 || index > openFiles.length) {
				return -1;
			}
			
			OpenFile file = openFiles[index];
			if (file == null) {
				return -1;
			}
			
			System.out.println("Closing " + openFiles[index].getName());
			
			file.close();
			openFiles[index] = null;
			
			return 0;
	}
		
	
		/**
		 * Handle the unlink() system call.
		 */
		private int handleUnlink(int vaddr) {
//			|| vaddr >= Machine.processor().getMemory().length
			if (vaddr < 0) {
				return -1;
			}
			
			String name = readVirtualMemoryString(vaddr, 256);
			
			if (name == null) {
				return -1;
			}
			
			if (ThreadedKernel.fileSystem.remove(name)) {
				return 0;
			}
			
			return -1;
	}
		/**
		 * Handle write()
		 */
		private int handleWrite(int index, int vaddr, int count) {
            
//			|| count > Machine.processor().getMemory().length 
			
            if (index < 0 || index > 15 || count < 0) { // > instead of >=
                return -1;
            }
            
//            || vaddr >= Machine.processor().getMemory().length
            
            if (vaddr < 0) {
                return -1;
            }
            
            OpenFile file = openFiles[index];            
            if (file == null) {
                return -1;
            }
            
            if (index == 0 && openFiles[index].getName().compareTo("SynchConsole") == 0) {
				return -1;
			}
            
            int total_written = 0;
            int written_data = 0, wException = 0;
            
            int eFlag = 0;
            byte[] writebuff = new byte[pageSize];
            for (int i = 0; i < count/pageSize; i++) {
                
                written_data = readVirtualMemory(vaddr + total_written, writebuff);
                wException = file.write(writebuff, 0, written_data);
                
                Lib.assertTrue(written_data == wException);
                if(wException == -1) {
                    System.out.println("write unsuccessful");
                    return -1;
                }
                total_written += written_data;
//                System.out.println(total_written);
                if(written_data < pageSize) {
                    eFlag = 1;
                    break;
                }
            }
            
            if(eFlag != 1) {
                
                written_data = readVirtualMemory(vaddr + total_written, writebuff, 0, (count - total_written));
                wException = file.write(writebuff, 0, written_data);
                
                Lib.assertTrue(written_data == wException);
                
                if(wException == -1) {
                    System.out.println("write unsuccessful");
                    return -1;
                }
                total_written+= written_data;    
            }
            
            
            if(total_written != count) {   // added new , only for write, read check not required, syscall.h 
                return -1;
            }
            
            return total_written;
            
            /* Page Size = 256 -- Assume 
             * Consider cases -- requested 512 , but EOF @ 128
             *                -- requested 520, but EOF @ 516
                              -- requested 260, but EOF @ 256
                              -- requested 512 , but EOF @ 260                  
                              -- just return -1, if they don't match with count -- all passed after theses changes */
    }   
		
		
        
		/**
		 *  Handle read() 
		 * */
		
		private int handleRead(int index, int vaddr, int count) {
			if (index < 0   || index > 15) {
				return -1;
			}
//			|| count > Machine.processor().getMemory().length    mainMemory = new byte[pageSize * numPhysPages]; thats why removed
			if (count < 0) {
                return -1;
            }
			
//			 || vaddr >= Machine.processor().getMemory().length
			if (vaddr < 0) {
				return -1;
			}
            
			OpenFile file = openFiles[index];
			
			if (file == null) {
				return -1;
			}
			
			if (index == 1 && openFiles[index].getName().compareTo("SynchConsole") == 0) {
				return -1;
			}

			int total_read = 0;
			int readData = 0, rException = 0;

			byte[] readbuff = new byte[pageSize];
            int eFlag = 0;
            for ( int i = 0; i < count/pageSize; i++) {
                
                rException = file.read(readbuff, 0, pageSize);   // amount read , this should be written to VM
                if(rException == -1) {
                    System.out.println("Read exception!!!!");
                    return -1;
                }
                readData = writeVirtualMemory(vaddr + total_read, readbuff, 0, rException); // here also
                Lib.assertTrue(readData == rException);
                System.out.println("read data ...");
                total_read += readData;
                
                if(readData < pageSize) {
                    eFlag = 1;
                    break;
                }
                
            }
                        
            if(eFlag != 1) {
                rException = file.read(readbuff, 0, (count - total_read));
                if(rException == -1) {
                    System.out.println("Read exception!!!!");
                    return -1;
                }
                
                readData = writeVirtualMemory(vaddr + total_read, readbuff, 0, rException);  // rException used, lets say read 520, page size 256 but only 4 is available (EOF)
                Lib.assertTrue(readData == rException);  
                total_read += readData;
            }
            
            return total_read;
		}
		
		/**
		 * Handle the exec() system call.
		 */
		
		private int handleExec(int vaddr, int argc, int argv) {
			if (vaddr < 0 ) { 
				//|| vaddr >= Machine.processor().getMemory().length || argc < 0) {
                return -1;
            }
            
            String file = readVirtualMemoryString(vaddr, 256);
            
            if (file == null || !file.endsWith(".coff")) {
                return -1;
            }

            String[] args = new String[argc];
            
            for (int i=0; i<argc; i++) {
                byte[] buffer = new byte[4];
                readVirtualMemory(argv + 4*i, buffer);
                int addr = Lib.bytesToInt(buffer, 0);
                args[i] = readVirtualMemoryString(addr, 256);
                    if(args[i] == null){
                          return -1;
                    }            
                }
            
            int retValue = -1;
            UserProcess child = newUserProcess();
            child.parentPID = this.processID;
            child.hasParent = 1;
            child.parentReference  = this;
            
            boolean rc = child.execute(file, args);
            
            if (rc == true) {
                this.map.put(child.processID, child);
                retValue = child.processID;
            }
            else{
            	
            	UserKernel.lockPID.acquire();
                UserKernel.numProcesses--;
                UserKernel.lockPID.release();
            }
            return retValue;	
	}
		
		
		/**
		 * Handle the join() system call. This pid is for child not parent so we can use map 
		 */
		private int handleJoin(int pid, int status) {
			
			if (pid < 0) {
				return -1;
			}
			
			if (this.map.containsKey(pid) != true) {
				return -1;
			}
			
			UserProcess child = this.map.get(pid);
			
//			if (child.parentPID != this.processID) {
//				return -1;
//			}

			if (!childStatus.containsKey(pid)) {
				child.thread.join();
			}
			
			Integer stat = childStatus.get(pid);
			if(stat != null) {
				writeVirtualMemory(status, Lib.bytesFromInt(stat.intValue()));
				return 1;
			}
			
			return 0;
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		
		if (processID != 0) {
			return -1;
		}

		Machine.halt();
		

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.
		
		for(int i = 0; i < openFiles.length; i++) {
			if(openFiles[i] != null) {
				openFiles[i].close();
				openFiles[i] = null;
			}
		}
		unloadSections();
		coff.close();
		
		UserKernel.lockPID.acquire();
		
		if(parentReference  != null) {
			if(exceptValue == 1) {
				cExitStatus = null;
			}
			else {
				cExitStatus = status;
			}
			//System.out.println("Exit Status :- " + cExitStatus);
			parentReference.childStatus.put(processID, cExitStatus);
		}
		hasParent = 0;
		
		UserKernel.numProcesses--;
		
//		System.out.println("Exit RC:- " + status);
		
		if(UserKernel.numProcesses == 0) {
			
			Kernel.kernel.terminate();
		}
		
		UserKernel.lockPID.release();
		
		UThread.finish();
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <td><tt>void exit(int status);</tt></td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * <td><tt>int  close(int fd);</tt></td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
			
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			exceptValue = 1;
			handleExit(-1);
			Lib.assertNotReached("Unexpected exception");
		}
	}
	
	/** Create an array to hold open files. */
	//private ArrayList<OpenFile> openFiles;
	
	/** The program being run by this process. */
	protected Coff coff;
	
	/** Flags */
	public static Integer cExitStatus = 0;
	public static int exceptValue = 0;
	public static int hasParent = 0;
	
	/** Store parent's PID to use in join. */
	public static int parentPID = 0;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	
	/***/
	public HashMap<Integer, UserProcess> map;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
    protected UThread thread;
    
	private int initialPC, initialSP;

	private int argc, argv;
	
	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';
	
	private static HashMap<Integer, Integer> childStatus;
	private UserProcess parentReference;
}
