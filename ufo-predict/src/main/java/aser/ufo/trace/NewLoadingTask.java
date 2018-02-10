package aser.ufo.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aser.ufo.NewReachEngine;
import trace.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class NewLoadingTask implements Callable<TLEventSeq> {

  private static final Logger LOG = LoggerFactory.getLogger(NewLoadingTask.class);

  public final FileInfo fileInfo;

  public NewLoadingTask(FileInfo fi) {
    this.fileInfo = fi;
  }

  public TLEventSeq call() throws Exception {
    return load();
  }

  //private long lastIdx;
  public TLEventSeq load() {
    final short tid = fileInfo.tid;
    TLEventSeq seq = new TLEventSeq(tid);
    NewReachEngine.cur_order_index = 0;//reset order list
    TLHeader header;
    try {
      ByteReader br = new BufferedByteReader();
      br.init(fileInfo);
      int bnext;
      if (fileInfo.fileOffset == 0) {
        bnext = br.read();
        if (bnext != -1) {
          header = getHeader(bnext, br);
          seq.header = header;
//          if (header.data == 1) {
//            br.read();// move 1B forward, because br.finish() will set 1B backward
//            br.finish(fileInfo);
//            br = new UnCompressorReader();
//            br.init(fileInfo);
//          }
        }
      }
      bnext = br.read();
      
      try {
      while (bnext != -1) {
    	  
        AbstractNode node = getNode(tid, bnext, br, seq.stat);

        seq.stat.c_total++;
        seq.numOfEvents++;
        
        if (node != null) {          

//       	 if(seq.stat.c_isync%1000==0)
//       		 LOG.info("Num of ISYNC nodes {}", seq.stat.c_isync);
       	 
        	//assign global id to node: tid - local_event_number (consistently!)
       	 //unique 
        	node.gid = Bytes.longs.add(tid, seq.numOfEvents);
        	

        //LOG.debug(node.toString());//JEFF

        	if(node instanceof TBeginNode)
        	{
        		NewReachEngine.saveToThreadFirstNode(tid, (TBeginNode) node);
        	}
        	else if (node instanceof TEndNode)
        	{
        		NewReachEngine.saveToThreadLastNode(tid, (TEndNode) node);
        	}
        	else  if (node instanceof TStartNode) {
        		
              NewReachEngine.saveToStartNodeList((TStartNode) node);
              
            } else if (node instanceof TJoinNode) {

                NewReachEngine.saveToJoinNodeList((TJoinNode) node);
            }
          
            else if (node instanceof WaitNode) {
              	 seq.stat.c_isync++;
        	  	NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
          }
          else if (node instanceof NotifyNode) {
            	 seq.stat.c_isync++;

        	  NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
          }
          else if (node instanceof NotifyAllNode) {
            	 seq.stat.c_isync++;
        	  NewReachEngine.saveToWaitNotifyList((IWaitNotifyNode) node);
          }
        }
        bnext = br.read();
    	  }
      }catch(IOException e)
    	  {
    	  		e.printStackTrace();
          //TODO: handle last thread node once error happens
    	  		TEndNode node =  new TEndNode(tid,tid,0);//failed
    	  		seq.numOfEvents++;
            	node.gid = Bytes.longs.add(tid, seq.numOfEvents);

        		NewReachEngine.saveToThreadLastNode(tid, node);

    		        	  }
      
      
      
      br.finish(fileInfo);
//      LOG.debug(
//          "File:{} finished loading, offset reset to {}",
//          fileInfo.tid, fileInfo.fileOffset);
    } catch (Exception e) {
//      throw new RuntimeException(e);
      LOG.error("error parsing trace " + tid, e);
      seq.events = null;
      return seq;
    }
    return seq;
  }

  private static TLHeader getHeader(final int typeIdx,
                                    ByteReader breader) throws IOException {
    if (typeIdx != 13)
      throw new RuntimeException("Could not read header");
//        const u64 version = UFO_VERSION;
//        const TidType tid;
//        const u64 timestamp;
//        const u32 length;
    long version = getLong64b(breader);
    short tidParent = getShort(breader);
    long time = getLong64b(breader);
    int len = getInt(breader);
    //LOG.debug(">>> UFO header version:{}  tid:{}  time:{}  len:{}", version , tidParent, new Date(time), len);
    return new TLHeader(tidParent, version, time, len);
  }

  private AbstractNode getNode(final short curTid,
                                      final int typeIdx,
                                      ByteReader breader, TLStat stat) throws IOException {
    short tidParent;
    short tidKid;
    long addr;
    long pc;
    int size;
    long time;
    int eTime;
    long len;

    int type_idx__ = typeIdx & 0xffffff3f;
    
    switch (typeIdx) {
    		
      case 0: // cBegin
        tidParent = getShort(breader);
        pc = getLong48b(breader);
        eTime = getInt(breader);
//                System.out.println("Begin " + tidParent + "  from " + _tidParent);
//                node = new TStartNode(gidGen++, _tidParent, pc_id, "" + tidParent, AbstractNode.TYPE.START);
        long tmp = getLong48b(breader);
        tmp = getInt(breader);
        tmp = getLong48b(breader);
        tmp = getInt(breader);
        return new TBeginNode(curTid, tidParent, eTime);

      case 1: // cEnd
        tidParent = getShort(breader);
        eTime = getInt(breader);
//                return new TJoinNode(_tidParent, pc_id, "" + tidParent, AbstractNode.TYPE.JOIN);
//                System.out.println("End " + tidParent + "  to " + _tidParent);
        return new TEndNode(curTid, tidParent, eTime);
      case 2: // thread start
          long index = getLong48b(breader);
        tidKid = getShort(breader);
        eTime = getInt(breader);
        pc = getLong48b(breader);
//                System.out.println("Start  " + _tidParent + "  ->  " + tidParent);
//                println(s"#$_tidParent ---> #$tidParent")
        stat.c_tstart++;
        return new TStartNode(index,curTid, tidKid, eTime, pc);
      case 3: // join
           index = getLong48b(breader);

        tidKid = getShort(breader);
        eTime = getInt(breader);
        pc = getLong48b(breader);
//                System.out.println("Join  " + tidParent + "  <-  " + _tidParent);
        stat.c_join++;
        return new TJoinNode(index,curTid, tidKid, eTime, pc);
//      * ThreadAcqLock,
//  * ThreadRelLock = 5,
//  * MemAlloc,
//  * MemDealloc,
//  * MemRead = 8,
//  * MemWrite,
//  * MemRangeRead = 10,
//  * MemRangeWrite
      case 4: // lock  8 + (13 + 48 -> 64) -> 72
        //long index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
//                System.out.println("#" + _tid + " lock  " + addr);
        stat.c_lock++;
        //lastIdx = index;
//	public LockNode(short tid, long lockID, long pc, long idx) {
        return null;//JEFF
      case 5: // nUnlock
        addr = getLong48b(breader);
        pc = getLong48b(breader);
//                System.out.println("#" + _tid + " nUnlock  " + addr);
        stat.c_unlock++;
        return null;//JEFF
      case 6: // alloc
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
//        System.out.println("allocate #" + _tid + " " + fsize + "  from " + addr);
        stat.c_alloc++;
       // lastIdx = index;
        return null;//JEFF
      case 7: // dealloc
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
//        System.out.println("deallocate #" + _tid + "  from " + addr);
        stat.c_dealloc++;
        //lastIdx = index;
        return null;//JEFF
      case 10: // range r
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
        stat.c_range_r++;
        //lastIdx = index;
        return null;//JEFF
//                System.out.println("#" + _tid + " read range " + fsize + "  from " + addr);
      case 11: // range w
//        index = getLong48b(breader);
        addr = getLong48b(breader);
        pc = getLong48b(breader);
        size = getInt(breader);
        //lastIdx = index;
//                System.out.println("#" + _tid + " read write " + fsize + "  from " + addr);
        stat.c_range_w++;
        return null;//JEFF
      case 12: // PtrAssignment
        long src = getLong48b(breader);
        long dest = getLong48b(breader);
//        System.out.println(">>> prop " + Long.toHexString(dest) + "   <= " + Long.toHexString(src));
        //long idx = lastIdx;
        //lastIdx = 0;
//  public PtrPropNode(short tid, long src, long dest, long idx) {
        return null;//JEFF
      case 14: // InfoPacket
//        const u64 timestamp;
//        const u64 length;
        time = getLong64b(breader);
        len = getLong64b(breader);
//        LOG.debug(">>> UFO packet:{}  time: {} len: {} ", new Date(time), len);
        return null;
      case 15: //Func Entry
        pc = getLong48b(breader);
        //FuncEntryNode funcEntryNode = new FuncEntryNode(curTid, pc);
        //JEFF
        //LOG.debug(funcEntryNode.toString());
        return null;//JEFF
      case 16: //Func Exit
          return null;//JEFF
      case 17: // ThrCondWait
         index = getLong48b(breader);
    	  	long   cond = getLong48b(breader);
    	  	long  mutex = getLong48b(breader);
        pc = getLong48b(breader);
        stat.c_wait++;
    	    return new WaitNode(index,curTid,cond,mutex,pc); 
      case 18: // ThrCondSignal
    	  	index = getLong48b(breader);
    	  	cond = getLong48b(breader);
             pc = getLong48b(breader);
             stat.c_notify++;
       	    return new NotifyNode(index,curTid,cond,pc); 
      case 19: // ThrCondBroadCast
    	  index = getLong48b(breader);
    	  cond = getLong48b(breader);
         pc = getLong48b(breader);
         stat.c_notifyAll++;
   	    return new NotifyAllNode(index,curTid,cond,pc); 
      case 20: // de-ref
        long ptrAddr = getLong48b(breader);
//        System.out.println(">>> deref " + Long.toHexString(ptrAddr));
        return null;
      default: // 8 + (13 + 48 -> 64) -> 72 + header (1, 2, 4, 8)
        int type_idx = typeIdx & 0xffffff3f;
        if (type_idx <= 13) {
          size = 1 << (typeIdx >> 6);
          MemAccNode accN = null;
          if (type_idx == 8)
          {

            accN = getRWNode(size, false, curTid, breader, stat);
          }
          else if (type_idx == 9)
          {
            accN = getRWNode(size, true, curTid, breader, stat);
          }
          //lastIdx = accN.idx;
          return null;//JEFF
        }
        //JEFF may be it is corrupted
//        System.err.println("Unrecognized trace, type index " + typeIdx + " m " + type_idx);
        return null;
//        throw new IOException("Unrecognized trace, tid #"+curTid+" type index " + typeIdx + " m " + type_idx);
    }
  }

  private static MemAccNode getRWNode(int size,
                                      boolean isW,
                                      short curTid,
                                      ByteReader breader, TLStat stat) throws IOException {

    ByteBuffer valueBuf = ByteBuffer.wrap(new byte[8]).order(ByteOrder.LITTLE_ENDIAN);
//    long index = getLong48b(breader);
    long addr = getLong48b(breader);
    long pc = getLong48b(breader);

    int sz = 0;
    while (sz != size) {
      int v = breader.read();
      valueBuf.put((byte) v);
      sz++;
    }
    long[] st = stat.c_read;
    if (isW)
      st = stat.c_write;
    Number obj = null;
    switch (size) {
      case 1:
        st[0]++;
        obj = valueBuf.get(0);
        break;
      case 2:
        st[1]++;
        obj = valueBuf.getShort(0);
        break;
      case 4:
        st[2]++;
        obj = valueBuf.getInt(0);
        break;
      case 8:
        st[3]++;
        obj = valueBuf.getLong(0);
        break;
    }
    valueBuf.clear();
    return null;//JEFF
  }

  public static short getShort(ByteReader breader) throws IOException {
    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    return Bytes.shorts.add(b2, b1);
  }

  public static int getInt(ByteReader breader) throws IOException {

    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    byte b3 = (byte) breader.read();
    byte b4 = (byte) breader.read();
    return Bytes.ints._Ladd(b4, b3, b2, b1);
//    ints.add(getShort(breader), getShort(breader))
  }


  public static long getLong48b(ByteReader breader) throws IOException {
    byte b0 = (byte) breader.read();
    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    byte b3 = (byte) breader.read();
    byte b4 = (byte) breader.read();
    byte b5 = (byte) breader.read();
    return Bytes.longs._Ladd((byte) 0x00, (byte) 0x00, b5, b4, b3, b2, b1, b0);
  }

  public static long getLong64b(ByteReader breader) throws IOException {
    byte b0 = (byte) breader.read();
    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    byte b3 = (byte) breader.read();
    byte b4 = (byte) breader.read();
    byte b5 = (byte) breader.read();
    byte b6 = (byte) breader.read();
    byte b7 = (byte) breader.read();
    return Bytes.longs._Ladd(b7, b6, b5, b4, b3, b2, b1, b0);
  }
}
