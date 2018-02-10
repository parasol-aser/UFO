package aser.ufo;

import java.util.Arrays;

public class VectorClock {
	
	public static short CLOCK_LENGTH;
	
	public final int[] timestamps;
	private short tid;
	
	public VectorClock(short idx)
	{
		this.tid = idx;
		
		timestamps = new int[CLOCK_LENGTH];
		for(int i=0;i<CLOCK_LENGTH;i++)
		{
			timestamps[i] = 0;
		}
	}
	
	public VectorClock(VectorClock vc)
	{
		this.tid = vc.tid;
		this.timestamps = Arrays.copyOf(vc.timestamps, vc.timestamps.length);
	}
	public void tick()
	{
		timestamps[tid]++;
	}
	public void join(VectorClock vc)
	{
		for(int i=0;i<timestamps.length;i++)
			if(this.timestamps[i]<vc.timestamps[i])
				this.timestamps[i] = vc.timestamps[i];
	}
	
	public boolean happensBefore(VectorClock vc2) {
		
		for(int i=0;i<timestamps.length;i++)
			if(timestamps[i]>vc2.timestamps[i])
				return false;
		
		return true;
	}

}
