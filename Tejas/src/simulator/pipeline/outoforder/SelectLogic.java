package pipeline.outoforder;

import generic.Core;
import generic.Event;
import generic.EventQueue;
import generic.PortType;
import generic.SimulationElement;
import generic.OperationType;
public class SelectLogic extends SimulationElement {
	
	Core core;
	OutOrderExecutionEngine execEngine;
	InstructionWindow IW;	
	int issueWidth;
	
	public SelectLogic(Core core, OutOrderExecutionEngine execEngine)
	{
		super(PortType.Unlimited, -1, -1, -1, -1);
		this.core = core;
		this.execEngine = execEngine;
		IW = execEngine.getInstructionWindow();
		issueWidth = core.getIssueWidth();
	}
	
	/*
	 * ready instructions' issue are attempted (maximum of 'issueWidth' number of issues)
	 * important - all issues must be attempted first; only then must awakening be done
	 * 		this is because an awakened instruction is a
	 * 		candidate for issue ONLY in the next cycle 
	 */
	public void performSelect()
	{
		ReorderBuffer ROB = execEngine.getReorderBuffer();		
		if(execEngine.isToStall5() == true /*pipeline stalled due to branch mis-prediction*/
				|| ROB.head == -1 /*ROB empty*/)
		{
			return;
		}
		
		execEngine.getExecutionCore().clearPortUsage();
		
		int noIssued = 0;
		int i;
		ReorderBufferEntry ROBEntry;
		boolean mfence_seen = false;
		
		i = ROB.head;
		do
		{
			ROBEntry = ROB.ROB[i];

			//if(ROBEntry.getIssued() == false && ROBEntry.getAssociatedIWEntry() == null) {
			//				System.out.println(ROBEntry);
			//	misc.Error.showErrorAndExit("ROB has entry but IW does not");
			//}

			OperationType op_type = ROBEntry.getInstruction().getOperationType();
			if(op_type == OperationType.mfence) {
				mfence_seen = true;
			}
			
			if(ROBEntry.getIssued() == false &&
					ROBEntry.getAssociatedIWEntry() != null)
			{
				//possible if entry has not been pushed into IW

				if(mfence_seen == false || (op_type != OperationType.load && op_type!= OperationType.store)){
				
					if(ROBEntry.getAssociatedIWEntry().issueInstruction())
					{
						//if issued
						noIssued++;						
					}
				}
			}
			
			if(noIssued >= issueWidth)
			{
				break;
			}
			
			i = (i+1)%ROB.MaxROBSize;
			
		}while(i != (ROB.tail+1)%ROB.MaxROBSize);
	}

	@Override
	public void handleEvent(EventQueue eventQ, Event event) {
				
	}

}
