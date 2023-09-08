package emulatorinterface;

public class ThreadBlockState {
	public enum blockState{LIVE, BLOCK, INACTIVE};
	public boolean blocked_fetch = false;
	blockState BlockState;
	int encode;
	public ThreadBlockState() {
		// TODO Auto-generated constructor stub
		this.BlockState=blockState.INACTIVE;
		encode=-1;
	}
	blockState getState()
	{
		return BlockState;
	}
	/**
	 * 
	 * @param encode
	 * LOCK	14,15
	 * JOIN	18,19
	 * CONDWAIT	20,21
	 * BARRIERWAIT	22,23
	 */
	public void gotBlockingPacket(int encode)
	{
		switch(BlockState)
		{
			case LIVE: this.encode=encode; BlockState=blockState.BLOCK;break;
			case BLOCK: this.encode=encode;break;
			case INACTIVE: this.encode=encode; BlockState=blockState.BLOCK;break;
		}

		
		
	}
	/**
	 * Thread started receiving packets after blockage
	 */
	public void gotUnBlockingPacket()
	{
		switch(BlockState)
		{
			case LIVE: break;
			case BLOCK: this.encode=-1;BlockState=blockState.LIVE;break;
			case INACTIVE: this.encode=-1;BlockState=blockState.LIVE;break;
		}
	}
	
	public void gotLive()
	{
		if(BlockState==blockState.INACTIVE)
		{
			BlockState=blockState.LIVE;
		}
	}

	public boolean fetch_packets() {
		return !(blocked_fetch);
	}
	
	public void block_fetch() {
		//System.out.println("Blocking fetch at emu thread");
		blocked_fetch = true;
	}

	public void resume_fetch() {
		//System.out.println("Resuming fetch at emu thread");
		blocked_fetch = false;
	}
}
