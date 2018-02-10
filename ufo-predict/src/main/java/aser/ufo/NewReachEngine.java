package aser.ufo;

import aser.ufo.trace.Bytes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import trace.AbstractNode;
import trace.ISyncNode;
import trace.IWaitNotifyNode;
import trace.NotifyAllNode;
import trace.NotifyNode;
import trace.TBeginNode;
import trace.TEndNode;
import trace.TJoinNode;
import trace.TStartNode;
import trace.WaitNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class NewReachEngine {
  
	
	public static Short2ObjectOpenHashMap<AbstractNode> tidFirstNode = new Short2ObjectOpenHashMap<AbstractNode>(UFO.INITSZ_S / 2);
	public static Short2ObjectOpenHashMap<AbstractNode> tidLastNode = new Short2ObjectOpenHashMap<AbstractNode>(UFO.INITSZ_S / 2);
    
	public static ArrayList<ISyncNode> orderedSyncNodeList = new ArrayList<ISyncNode>(UFO.INITSZ_SYNC);

	
	public static  ArrayList<TStartNode> thrStartNodeList = new ArrayList<TStartNode>(UFO.INITSZ_S * 5);
	public static ArrayList<TJoinNode> joinNodeList = new ArrayList<TJoinNode>(UFO.INITSZ_S * 5);
	
	//make this global across phases
public static  Long2ObjectOpenHashMap<ArrayList<IWaitNotifyNode>> cond2WaitNotifyLs = new Long2ObjectOpenHashMap<ArrayList<IWaitNotifyNode>>(UFO.INITSZ_SYNC);

public static void saveToWaitNotifyList(IWaitNotifyNode node) {
	
	addToOrderedSyncList(node);

//	long cond = node.getAddr();
//	  ArrayList<IWaitNotifyNode> waitNotifyLs = cond2WaitNotifyLs.get(cond);
//	  if(waitNotifyLs == null)
//	  {
//		  waitNotifyLs = new ArrayList<IWaitNotifyNode>(UFO.INITSZ_SYNC * 5);
//		  cond2WaitNotifyLs.put(cond, waitNotifyLs);
//		  waitNotifyLs.add(node);
//	  }
//	  else
//	  {
//		  //make sure it is ordered by the idx
//		  if(waitNotifyLs.get(0).getIndex()>node.getIndex())
//			  waitNotifyLs.add(0, node);
//		  else
//		  for(int i=waitNotifyLs.size()-1;i>=0;i--)
//		  {
//			  IWaitNotifyNode node2 = waitNotifyLs.get(i);
//			  if(node2.getIndex()<node.getIndex())
//			  {
//				  waitNotifyLs.add(node);//ordered by idx
//				  break;
//			  }
//		  }
//	  
//	  }
}
public static void saveToStartNodeList(TStartNode node)
{
	addToOrderedSyncList(node);
	
    //thrStartNodeList.add(node);
}
public static void saveToJoinNodeList( TJoinNode node)
{
	addToOrderedSyncList(node);

    //joinNodeList.add(node);
}
public static void saveToThreadFirstNode(short tid, TBeginNode node)
{
    tidFirstNode.put(tid,node);
}
public static void saveToThreadLastNode(short tid, TEndNode node)
{
    tidLastNode.put(tid, node);
}
public static void postprocessing()
{  
	//TODO: need to consider the order of these nodes
     
	for(int i=0;i<orderedSyncNodeList.size();i++)
	{
		ISyncNode node = orderedSyncNodeList.get(i);
		if(node instanceof TStartNode)
		{
			short tid = ((TStartNode) node).tidKid;
	        AbstractNode fnode = tidFirstNode.get(tid);
	        if (fnode != null)
	         addEdge(node, fnode);
		}
		else if (node instanceof TJoinNode)
		{
			short tid = ((TJoinNode) node).tid_join;
	        AbstractNode lnode = tidLastNode.get(tid);
	        if (lnode != null) {
	          addEdge(lnode, node);
	        }
		}
		else if(node instanceof WaitNode)
  		{
			long cond = node.getAddr();
			  ArrayList<IWaitNotifyNode> waitNotifyLs = cond2WaitNotifyLs.get(cond);
			  if(waitNotifyLs == null)
			  {
				  waitNotifyLs = new ArrayList<IWaitNotifyNode>();
				  cond2WaitNotifyLs.put(cond, waitNotifyLs);
			  }
			  waitNotifyLs.add((IWaitNotifyNode) node);

  		}
  		else if (node instanceof NotifyNode)
  		{
  			long cond = node.getAddr();
  			ArrayList<IWaitNotifyNode> waitNotifyLs = cond2WaitNotifyLs.get(cond);
  			if(waitNotifyLs!=null&&!waitNotifyLs.isEmpty())
  			{
  				ISyncNode wait = waitNotifyLs.remove(0);
  					addEdge(node, wait);//signal to wait
  			}
  		}
  		else if (node instanceof NotifyAllNode)
  		{
  			long cond = node.getAddr();
  			ArrayList<IWaitNotifyNode> waitNotifyLs = cond2WaitNotifyLs.get(cond);
  			{
  	  			if(waitNotifyLs!=null)
  				while(!waitNotifyLs.isEmpty())
  				{
  	  				ISyncNode wait = waitNotifyLs.remove(0);
  					addEdge(node, wait);//signal to all wait
  				}
  			}
  				
  		}
	
	}
//    for (TStartNode node : thrStartNodeList) {
//        short tid = node.tidKid;
//        AbstractNode fnode = tidFirstNode.get(tid);
//        if (fnode != null)
//         addEdge(node, fnode);
//      }
//
//      for (TJoinNode node : joinNodeList) {
//        short tid = node.tid_join;
//        AbstractNode lnode = tidLastNode.get(tid);
//        if (lnode != null) {
//          addEdge(lnode, node);
//        }
//      }

      //add wait-notify to reach Engine
//      for (ArrayList<IWaitNotifyNode> list: cond2WaitNotifyLs.values())
//      {	
//    	  if(list.size()>1)
//    	  {
//    	  	int curIndex = 0;
//    	  	for(int k =0; k<list.size(); k++)
//    	  	{
//    	  		ISyncNode node = list.get(k);
//    	  		if(node instanceof WaitNode)
//    	  		{
//    	  			
//    	  		}
//    	  		else if (node instanceof NotifyNode)
//    	  		{
//    	  			ISyncNode wait_node_at_curIndex = list.get(curIndex++);
//    	  			while (! (wait_node_at_curIndex instanceof WaitNode) && curIndex<k)
//    	  				wait_node_at_curIndex = list.get(curIndex++);
//    	  			
//    	  			if(wait_node_at_curIndex instanceof WaitNode)
//    	  				addEdge(node, wait_node_at_curIndex);//signal to wait
//    	  		}
//    	  		else if (node instanceof NotifyAllNode)
//    	  		{
//    	  			for(;curIndex<k;curIndex++)
//    	  			{
//    	  				ISyncNode wait_node_at_curIndex = list.get(curIndex);
//    	  				if(wait_node_at_curIndex instanceof WaitNode)
//    	  					addEdge(node, wait_node_at_curIndex);//signal to all wait
//    	  			}
//    	  				
//    	  		}
//    	  	}
//    	  }
//      }
}

  private static HashMap<Long,VectorClock> long2VCs = new HashMap<Long, VectorClock>();
  private static HashMap<Short,VectorClock> tidToVCs = new HashMap<Short, VectorClock>();

  private static Short2ObjectOpenHashMap<ArrayList<Long>> tid2GidsMap =
	        new Short2ObjectOpenHashMap<ArrayList<Long>>(UFO.INITSZ_S);
  
  private static HashMap<Short,Short> tid2IndexMap = new HashMap<Short,Short>();

  public static void setThreadIdsVectorClock(short[] shortArray) {

	    VectorClock.CLOCK_LENGTH = (short) shortArray.length;
	    for(short i =0;i<shortArray.length;i++)
	    		tid2IndexMap.put(shortArray[i],i);
	}
  
  private static VectorClock getThreadVectorClock(short tid)
  {
	    VectorClock tidVC = tidToVCs.get(tid);
	    if(tidVC==null)
	    {
	    		Short idx = tid2IndexMap.get(tid);
	    		if(idx==null)
	    		{
	    			//TODO: need a better fix 
	    			idx = 0;
//	    		 idx = (short) tid2IndexMap.size();
//	    		 tid2IndexMap.put(tid, idx);
	    		}
	    		tidVC = new VectorClock(idx);
	    		tidToVCs.put(tid, tidVC);
	    }
	    
	    return tidVC;
  }
  
  private static void addSyncGidToTidList(short tid, long gid)
  {
	  ArrayList<Long> gids = tid2GidsMap.get(tid);
	  if(gids == null)
	  {
		  gids = new ArrayList<Long>();
		  tid2GidsMap.put(tid, gids);
		  
		  gids.add(gid);
	  }
	  else
	  {
	  
		  if(gid<gids.get(0))
			  gids.add(0,gid);
		  else
			for(int i=gids.size()-1;i>=0;i--)
			{
				Long gid2 = gids.get(i);
				if(gid>gid2)
				{
					gids.add(i+1,gid);//make sure this is correct
					break;
				}
			}
	  }
	  

  }
  
  public static void addEdge(AbstractNode from, AbstractNode to) {
    if (from.tid == to.tid)
      return;
    
    addSyncGidToTidList(from.tid, from.gid);
    addSyncGidToTidList(to.tid, to.gid);

    //get and tick the VC of the current thread
    VectorClock tid1VC = getThreadVectorClock(from.tid);
    VectorClock tid2VC = getThreadVectorClock(to.tid);

    
    tid1VC.tick();
    tid2VC.tick();
    //
    
    //create new VC for the sync points
    
    VectorClock vc1 = new VectorClock(tid1VC);
    VectorClock vc2 = new VectorClock(tid2VC);
    vc2.join(vc1);
    

//    long fromID = Bytes.longs.add(from.tid, from.gid);
//    long toID = Bytes.longs.add(to.tid, to.gid);
    
    //save vector clock
    long2VCs.put(from.gid, vc1);
    long2VCs.put(to.gid, vc2);
  }

  public static boolean canReach(AbstractNode n1, AbstractNode n2) {
    final short tid1 = n1.tid;
    final short tid2 = n2.tid;
    final long gid1 = n1.gid;
    final long gid2 = n2.gid;
    if (tid1 == tid2) {
      // gid grows within one thread
      return gid1 <= gid2;
    } else { // diff thread

    	//find the nearest sync point for each node 
    	long gid1_down = findSyncGidDown(tid1, gid1);
    	if(gid1_down==0) //no sync point after gid1 from tid1
    		return false; // can never reach
    	
    	long gid2_up = findSyncGidUp(tid2, gid2);
    	if (gid2_up ==0)// no sync point before gid2 from tid2
    		return false;
    	
    	//NO CACHE
    	
    	//Instead, maintain a vector clock for each sync point 
    	
//    	long key1 = Bytes.longs.add(tid1, gid1_down);
//    	long key2 = Bytes.longs.add(tid2, gid2_up);

     	VectorClock vc1 = 	long2VCs.get(gid1_down);
     	VectorClock vc2 = 	long2VCs.get(gid2_up);

    	if(vc1.happensBefore(vc2))
    		return true;
    	else return false;
    	
    } //diff threads
  }

private static long findSyncGidUp(short tid2, long gid2) {
	ArrayList<Long> gids = tid2GidsMap.get(tid2);
	if(gids==null || gids.isEmpty())
		return 0;//
	for(int i=gids.size()-1;i>=0;i--)
	{
		Long gid = gids.get(i);
		if(gid2>gid)
			return gid;
	}
	return 0;

	
}

private static long findSyncGidDown(short tid1, long gid1) {
	

	ArrayList<Long> gids = tid2GidsMap.get(tid1);
	if(gids==null || gids.isEmpty())
		return 0;//
	for(int i=0;i<gids.size();i++)
	{
		Long gid = gids.get(i);
		if(gid1<gid)
			return gid;
	}
	
	return 0;
	
}

public static int cur_order_index;
private static void addToOrderedSyncList(ISyncNode node) {
	
	long gindex = node.getIndex();
	
	for(;cur_order_index<orderedSyncNodeList.size();cur_order_index++)
	{
		if(gindex<=orderedSyncNodeList.get(cur_order_index).getIndex())
			break;
		
	}
	orderedSyncNodeList.add(cur_order_index,node);
}


}




