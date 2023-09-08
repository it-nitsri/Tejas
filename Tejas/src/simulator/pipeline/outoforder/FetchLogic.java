package pipeline.outoforder;

import config.SimulationConfig;
import main.ArchitecturalComponent;
import main.CustomObjectPool;
import memorysystem.AddressCarryingEvent;
import generic.Barrier;
import generic.BarrierTable;
import generic.Core;
import generic.Event;
import generic.EventQueue;
import generic.GenericCircularQueue;
import generic.Instruction;
import generic.OperationType;
import generic.PortType;
import generic.RequestType;
import generic.SimulationElement;

import emulatorinterface.GlobalTable;

import java.util.Hashtable;

public class FetchLogic extends SimulationElement {
	
	Core core;
	OutOrderExecutionEngine execEngine;
	private final boolean debug = false; //debugging causality
	GenericCircularQueue<Instruction>[] inputToPipeline;
	int inputPipeToReadNext;
	ICacheBuffer iCacheBuffer;
	GenericCircularQueue<Instruction> fetchBuffer;	
	int fetchWidth;
	OperationType[] instructionsToBeDropped;
	boolean sleep;

	//NEED A PER ADDRESS SIGNAL HERE TO MAKE SENSE OF ANYTHING
	private Object resume_signal_mutex = new Object();
	private long resume_signals;

	


	private Hashtable<Long,Integer> signals;
	private long sync_head_addr;
	
	public FetchLogic(Core core, OutOrderExecutionEngine execEngine)
	{
		super(PortType.Unlimited, -1, -1, -1, -1);
		this.core = core;
		this.execEngine = execEngine;
		fetchBuffer = execEngine.getFetchBuffer();
		fetchWidth = core.getDecodeWidth();
		inputPipeToReadNext = 0;
		sleep = false;

		instructionsToBeDropped = new OperationType[] {
															OperationType.interrupt,
															OperationType.sync
													};

		
		signals = new Hashtable<Long,Integer>();

		long sync_head_addr = -1;
	}
	
	public void performFetch()
	{
		//to detach pipeline
		boolean checkTranslatorSpeed = false;
		
		if(checkTranslatorSpeed)
		{
			Instruction inst;
			while((inst = inputToPipeline[0].dequeue()) != null)
			{
				if(inst.getOperationType() == OperationType.inValid)
				{
					execEngine.setExecutionComplete(true);
				}
				CustomObjectPool.getInstructionPool().returnObject(inst);
			}
			
			return;
		}
		
		if(sleep == true)
		{
			synchronized(resume_signal_mutex) {
				//if(resume_signals >0) {
					
				//	resume_signals --;
				//	setSleep(false);
				//	}

				if(signals.containsKey(sync_head_addr)) {
					resume_signals --;
					Integer ret = signals.get(sync_head_addr);
					if(ret.intValue() == 1) {
						signals.remove(sync_head_addr);
					}
					else {
						signals.put(sync_head_addr,ret.intValue() -1);
					}
					setSleep(false);

					
					
					
				}
			}
			return;
		}
		
		Instruction newInstruction;
		
		if(!execEngine.isToStall1() &&
				!execEngine.isToStall2() &&
				!execEngine.isToStall3() &&
				!execEngine.isToStall4() &&
				!execEngine.isToStall5())
		{
			//add instructions, for whom "fetch" from iCache has completed, to fetch buffer
			//decode stage reads from this buffer
			for(int i = 0; i < fetchWidth; i++)
			{
				if(fetchBuffer.isFull() == true)
				{
					break;
				}
				
				newInstruction = iCacheBuffer.getNextInstruction();
				if(newInstruction != null)
				{
					fetchBuffer.enqueue(newInstruction);
				}
				else
				{
					this.core.getExecEngine().incrementInstructionMemStall(1); 
					break;
				}
			}
		}
		
		//this loop reads from inputToPipeline and places the instruction in iCacheBuffer
		//fetch of the instruction is also issued to the iCache
		for(int i = 0; i < iCacheBuffer.size; i++)
		{
			if(inputToPipeline[inputPipeToReadNext].size() <= 0)
			{
				break;
			}
			
			newInstruction = inputToPipeline[inputPipeToReadNext].peek(0);
			
			//process sync operation(Barrier)
			if(newInstruction.getOperationType() == OperationType.sync){


				//Add into the pipeline a fence instruction
				//if failed redo this first and then process sync
				Instruction mfence = Instruction.getMFenceInstruction();
				mfence.setCISCProgramCounter(newInstruction.getCISCProgramCounter());
				
				if(!iCacheBuffer.isFull() && execEngine.getCoreMemorySystem().getiCache().isBusy(mfence.getCISCProgramCounter())==false)
				{
					iCacheBuffer.addToBuffer(mfence);
					if(SimulationConfig.detachMemSysInsn == false)
					{
						
						execEngine.getCoreMemorySystem().issueRequestToInstrCache(mfence.getCISCProgramCounter());
						
					}
				}
				else
				{
					break;
				}
				

				
				long barrierAddress = newInstruction.getCISCProgramCounter();
				if(barrierAddress == 1 || barrierAddress == 0) {
					misc.Error.showErrorAndExit("Unknown sync value at core "+this.core.getCore_number());
				}
				Barrier bar = BarrierTable.barrierList.get(barrierAddress);
				if(bar !=null) {
					if(debug) System.out.println("[DEBUG] Encounterd barrier instruction at "+barrierAddress);
					
					bar.incrementThreads();
					if(this.core.TreeBarrier == true){
						sync_head_addr = barrierAddress;
						setSleep(true);
						int coreId = this.core.getCore_number();
						ArchitecturalComponent.coreBroadcastBus.getPort().put(new AddressCarryingEvent(
																									   0,
																									   this.core.eventQueue,
																									   1,
																									   ArchitecturalComponent.coreBroadcastBus, 
																									   ArchitecturalComponent.coreBroadcastBus, 
																									   RequestType.TREE_BARRIER, 
																									   barrierAddress,
																									   coreId));
					}
					else{
						if(bar.timeToCross())
						{
							System.out.println("    Time to cross " + bar.getBarrierAddress());
							setSleep(true);
							for(int j=0; j<bar.getNumThreads(); j++ ){
								ArchitecturalComponent.coreBroadcastBus.addToResumeCore(bar.getBlockedThreads().elementAt(j));
							}
							ArchitecturalComponent.coreBroadcastBus.getPort().put(new AddressCarryingEvent(
																										   this.core.eventQueue,
																										   1,
																										   ArchitecturalComponent.coreBroadcastBus, 
																										   ArchitecturalComponent.coreBroadcastBus, 
																										   RequestType.PIPELINE_RESUME, 
																										   0));
	
						}
						else
						{
							System.out.println("Total on bar " + bar.getBarrierAddress() + " is " + bar.getNumThreadsArrived());
							sync_head_addr = barrierAddress;
							setSleep(true);

							
						}
					}
				}

				//if this was not a barrier
				//then we wait for an external resume

				else {
					if(debug) System.out.println("[DEBUG] Encounterd lock/wait at "+barrierAddress);
					sync_head_addr = barrierAddress;
					setSleep(true);

					
				}


				
				
			}
			
			//drop instructions on the drop list
			if(shouldInstructionBeDropped(newInstruction) == true)
			{
				inputToPipeline[inputPipeToReadNext].pollFirst();
				CustomObjectPool.getInstructionPool().returnObject(newInstruction);
				i--;
				continue;
			}

			
			//drop memory operations if specified in configuration file
			if(newInstruction.getOperationType() == OperationType.load ||
					newInstruction.getOperationType() == OperationType.store)
			{
				if(SimulationConfig.detachMemSysData == true)
				{
					inputToPipeline[inputPipeToReadNext].pollFirst();
					CustomObjectPool.getInstructionPool().returnObject(newInstruction);
					i--;
					continue;
				}
			}
			
			//add to iCache buffer, and issue request to iCache
			if(!iCacheBuffer.isFull() && execEngine.getCoreMemorySystem().getiCache().isBusy(newInstruction.getCISCProgramCounter())==false)
			{
				iCacheBuffer.addToBuffer(inputToPipeline[inputPipeToReadNext].pollFirst());
				if(SimulationConfig.detachMemSysInsn == false && newInstruction.getOperationType() != OperationType.inValid)
				{
						// The first micro-operation of an instruction has a valid CISC IP. All the subsequent 
					  	// micro-ops will have IP = -1(meaning invalid). We must not forward this requests to iCache.
						if(newInstruction.getCISCProgramCounter()!=-1)
						{
							execEngine.getCoreMemorySystem().issueRequestToInstrCache(newInstruction.getCISCProgramCounter());
						}
				}
			}
			else
			{
				break;
			}
		}
		
		//SMT support
		//round-robin among the various input-to-pipelines, fetching from a different
		//non-empty input every cycle
		/*
		int noOfIterations = 0;
		do
		{
			inputPipeToReadNext = (inputPipeToReadNext + 1)%core.getNo_of_input_pipes();
			noOfIterations++;
		}while(inputToPipeline[inputPipeToReadNext].isEmpty() == true
				&& noOfIterations < fetchWidth);*/
	}

	@Override
	public void handleEvent(EventQueue eventQ, Event event) {
			
	}
	
	boolean shouldInstructionBeDropped(Instruction instruction)
	{
		for(int i = 0; i < instructionsToBeDropped.length; i++)
		{
			if(instructionsToBeDropped[i] == instruction.getOperationType())
			{
				return true;
			}
		}
		return false;
	}
	
	public void processCompletionOfMemRequest(long address)
	{
		iCacheBuffer.updateFetchComplete(address);
	}
	
	public GenericCircularQueue<Instruction>[] getInputToPipeline() {
		return inputToPipeline;
	}

	public void setInputToPipeline(GenericCircularQueue<Instruction>[] inputToPipeline) {
		this.inputToPipeline = inputToPipeline;
	}

	public void setICacheBuffer(ICacheBuffer iCacheBuffer)
	{
		this.iCacheBuffer = iCacheBuffer;
	}

	public boolean isSleep() {
		return sleep;
	}

	public void activate() {

		this.sleep = false;
	}
	
	private void setSleep(boolean sleep) {
		if(sleep == true) {
			//	Thread.dumpStack();
			if(debug)System.out.println("sleeping pipeline " + this.core.getCore_number());
		}
		else {
			if(resume_signals <0) misc.Error.showErrorAndExit("Trying to awake a sleeping pipeline");
			//Thread.dumpStack();
			if(debug) System.out.println("resuming pipeline " + this.core.getCore_number() + ". External signals left "+resume_signals);
		}
		this.sleep = sleep;
	}

	public void received_external_resume_signal(int source,long address) {
		
		synchronized(resume_signal_mutex) {
			resume_signals ++;

			//resume signal recieved for this address
			
			Integer ret = signals.put(address,1);
			if(ret!=null){
				signals.put(address,ret.intValue()+1);
			}
			
			//if(resume_signals > 20)
			//	misc.Error.showErrorAndExit("Signals increased more than what should be normal");
			
		}
	}
}
