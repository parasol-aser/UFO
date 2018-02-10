

package aser.ufo.trace;

import aser.ufo.NewReachEngine;
import aser.ufo.SimpleReachEngine;
import aser.ufo.UFO;
import aser.ufo.VectorClock;
import aser.ufo.misc.Pair;
import aser.ufo.misc.RawUaf;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.shorts.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trace.*;

import java.util.*;



  /**
   * tid -> integer
   * address -> long
   * nLock taskId -> long
   * name starts with '_' -> temp
   */
public class Indexer {

    private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

    protected static final boolean CHECK_MEM_ERROR = true;

    public static final int PRE_LOAD = 0;
    public static final int IDX_BUILT = 1;
    public static final int PRE_SEARCH = 2;
    public static final int PRE_NORM = 3;
    protected volatile int state_ = PRE_LOAD;

    public int getState() {
      return state_;
    }
    // addr -> tid mem_acc
//  protected Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<ArrayList<MemAccNode>>> addr2Tid2seqAcc =
//      new Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<ArrayList<MemAccNode>>>(UFO.INITSZ_L * 2);

    // track malloc & free,  temp tree, deleted later
    // addr to node
//  protected TreeMap<Long, AbstractNode> _allocationTree = new TreeMap<Long, AbstractNode>();

    // index not needed
//  protected HashMap<Long, ArrayList<MemAccNode>> dealloc2seqAcc = new HashMap<Long, ArrayList<MemAccNode>>(INITSZ_L);
    protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> _rawTid2seq = new Short2ObjectOpenHashMap<ArrayList<AbstractNode>>(UFO.INITSZ_S / 2);

    protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> tid2CallSeq = new Short2ObjectOpenHashMap<ArrayList<AbstractNode>>(UFO.INITSZ_S / 2);
//  protected Short2ObjectOpenHashMap<LongArrayList> tid2CallSeq = new Short2ObjectOpenHashMap<LongArrayList>(UFO.INITSZ_S / 2);

    public TraceMetaInfo metaInfo = new TraceMetaInfo();

    // for thread sync constraints
    protected Short2ObjectOpenHashMap<AbstractNode> tidFirstNode = new Short2ObjectOpenHashMap<AbstractNode>(UFO.INITSZ_S / 2);
    protected Short2ObjectOpenHashMap<AbstractNode> tidLastNode = new Short2ObjectOpenHashMap<AbstractNode>(UFO.INITSZ_S / 2);

    private static class SharedAccIndexes {

      // z3 declare var, all shared acc and other nodes
      protected ArrayList<AbstractNode> allNodeSeq = new ArrayList<AbstractNode>(UFO.INITSZ_L);

      protected HashMap<MemAccNode, DeallocNode> acc2Dealloc = new HashMap<MemAccNode, DeallocNode>(UFO.INITSZ_L);

      protected Short2ObjectOpenHashMap<ArrayList<ReadNode>> tid2seqReads = new Short2ObjectOpenHashMap<ArrayList<ReadNode>>(UFO.INITSZ_L);

      //protected ArrayList<ReadNode> seqRead = new ArrayList<ReadNode>(UFO.INITSZ_L);

      //  protected ArrayList<WriteNode> seqWrite = new ArrayList<WriteNode>(UFO.INITSZ_L);
      // addr to acc node
      protected Long2LongOpenHashMap initWrites = new Long2LongOpenHashMap(UFO.INITSZ_L);

      protected Long2ObjectOpenHashMap<ArrayList<WriteNode>> addr2SeqWrite = new Long2ObjectOpenHashMap<ArrayList<WriteNode>>(UFO.INITSZ_L);
      //  addr -> mem_acc
//  protected Long2ObjectOpenHashMap<ArrayList<MemAccNode>> addr2sqeAcc = new Long2ObjectOpenHashMap<ArrayList<MemAccNode>>(UFO.INITSZ_L * 2);

      // shared acc and all other (nLock dealloc ...)
      protected Short2ObjectOpenHashMap<ArrayList<AbstractNode>> tid2sqeNodes = new Short2ObjectOpenHashMap<ArrayList<AbstractNode>>(UFO.INITSZ_L);

      //
//    void trim() {
//      allNodeSeq.trimToSize();
//      seqRead.trimToSize();
////    seqWrite.trimToSize();
//      initWrites.trim();
//      addr2SeqWrite.trim();
////    addr2sqeAcc.trim();
//      tid2sqeNodes.trim();
//    }
      void destroy() {
    	  tid2seqReads = null;
//    seqWrite.destroy();
        initWrites = null;
        addr2SeqWrite = null;
//    addr2sqeAcc.destroy();
        tid2sqeNodes = null;
        acc2Dealloc = null;
      }

      void trim() {
        allNodeSeq.trimToSize();
        tid2seqReads.trim();
//    seqWrite.trimToSize();
        initWrites.trim();
        addr2SeqWrite.trim();
//    addr2sqeAcc.trim();
        tid2sqeNodes.trim();
      }

	public void addReadNode(ReadNode node) {
		
	        ArrayList<ReadNode> tidNodes = tid2seqReads.get(node.tid);


	          if (tidNodes == null) {
	            tidNodes = new ArrayList<ReadNode>(UFO.INITSZ_L);
	            tid2seqReads.put(node.tid, tidNodes);
	          }
	          tidNodes.add(node);
		
	}
    }

    protected SharedAccIndexes shared = new SharedAccIndexes();


    public Indexer() {
      shared.initWrites.defaultReturnValue(-1);
    }

    public void addTidSeq(short tid, ArrayList<AbstractNode> seq) {
      if (state_ > PRE_LOAD)
        throw new IllegalStateException("events already loaded and indexed");

      _rawTid2seq.put(tid, seq);
      metaInfo.tidRawNodesCounts.put(tid, seq.size());
      metaInfo.rawNodeCount += seq.size();
      if (LOG.isTraceEnabled()) {
        for (AbstractNode n : seq)
          LOG.trace(n.toString());
      }
    }


    private NewReachEngine reachEngine = new NewReachEngine();

    public void postProcess() {
      if (state_ >= IDX_BUILT)
        throw new IllegalStateException("indexes already built");
      // 1. first pass handles:
      // sync,
      // alloc & dealloc,
      // call seq
      // tid first node, last node
      pass1st();

      // check reachability engine
      
      allocator.matchInterThrDealloc(reachEngine);
      
      //System.out.println("postProcess");

      // 2. second pass:
      LongOpenHashSet sharedAddrSet = findSharedAcc();

      // 3. third pass, handle shared mem acc (index: addr tid dealloc allnode)
      for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> e : _rawTid2seq.short2ObjectEntrySet()) {
        short tid = e.getShortKey();
        ArrayList<AbstractNode> tidNodes = shared.tid2sqeNodes.get(tid);

        for (AbstractNode node : e.getValue()) {

          if (tidNodes == null) {
            tidNodes = new ArrayList<AbstractNode>(UFO.INITSZ_L);
            shared.tid2sqeNodes.put(tid, tidNodes);
          }

          if (node instanceof MemAccNode) {
            MemAccNode memNode = (MemAccNode) node;
            if (sharedAddrSet.contains(memNode.getAddr())) {
              shared.allNodeSeq.add(node);
              tidNodes.add(memNode);
              handleTSMemAcc(tid, memNode);
            }

          } else if (!(node instanceof FuncEntryNode)
              && !(node instanceof FuncExitNode)) {
            // other types, except func in/out
            shared.allNodeSeq.add(node);
            tidNodes.add(node);
          }
        } // for each node in thread
      } // for each thread


      metaInfo.sharedAddrs = sharedAddrSet;
      metaInfo.countAllNodes = getAllNodeSeq().size();

      if (!LOG.isTraceEnabled()) {
//      _allocationTree = null;
//      _rawTid2seq = null;
//      trim();
      } else {
        LOG.debug(metaInfo.toString());
      }

      state_ = IDX_BUILT;
      state_ = PRE_SEARCH; //
    }


    private Allocator allocator = new Allocator();

    /**
     * sync,
     * alloc & dealloc,
     * call seq
     * tid first node, last node
     */
    protected void pass1st() {

      for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> e : _rawTid2seq.short2ObjectEntrySet()) {
        final short curTid = e.getShortKey();
        ArrayList<AbstractNode> nodes = e.getValue();

        for (AbstractNode node : nodes) {
          if (node instanceof AllocNode) {
            metaInfo.countAlloc++;
            AllocNode an = (AllocNode) node;
//          long addrLeft = an.addr;
//          long addrRight = an.addr + an.length;
//          AbstractNode oldAn = _allocationTree.put(addrLeft, an);
//          _allocationTree.put(addrRight, an);
//
//          if (CHECK_MEM_ERROR) {
//            if (oldAn != null) {
//              LOG.error("!!!! memory allocation at same address:{}  old:{}  new length: {}", addrLeft, oldAn, an.length);
//            }
//            Long addrGE = _allocationTree.ceilingKey(addrLeft);
//            if (addrGE != null && addrGE <= addrRight) {
//              LOG.error(
//                  "!!! memory allocation overlap, left:{}  previous right bound:{} current right bound {}",
//                  addrLeft, addrGE, addrRight);
//            }
//            Long addrLE = _allocationTree.floorKey(addrRight);
//            if (addrLE != null && addrLE != addrLeft) {
//              LOG.error(
//                  "!!! memory allocation overlap, right:{}  previous left bound: {}   current left bound {}",
//                  addrLeft, addrLE, addrLeft);
//            }
//          }

            allocator.push(an);

          } else if (node instanceof DeallocNode) {
            // matching delloc with alloc, replacing alloc with dealloc
            metaInfo.countDealloc++;
            DeallocNode dnode = (DeallocNode) node;
//          long addrL = dnode.addr;
//          AbstractNode oldNode = _allocationTree.get(addrL);
//          if (oldNode == null) {
//            LOG.warn("!!! could not find matching alloc at address {}", addrL);
//            continue;
//          } else if (oldNode instanceof AllocNode) {
//            dnode.length = ((AllocNode) oldNode).length;
//            _allocationTree.put(addrL, dnode); // replace left
//
////              long addrR = addrL + len;
////              AbstractNode oldrn = _allocationTree.put(addrR, dnode); // left and right
////              assert (oldrn == oldNode);
//
//          } else if (oldNode instanceof DeallocNode)
//            LOG.warn("!!! memory deallocate at same address " + addrL + "  ignoring current one");

            allocator.insert(dnode);
          } else if (node instanceof FuncExitNode || node instanceof FuncEntryNode) {
            ArrayList<AbstractNode> callseq = tid2CallSeq.get(curTid);
            if (callseq == null) {
              callseq = new ArrayList<AbstractNode>(UFO.INITSZ_L);
              tid2CallSeq.put(curTid, callseq);
            }
            callseq.add(node);
            metaInfo.countFuncCall++;
          } else if (node instanceof ISyncNode) {
//              handleSync(tid, (ISyncNode) node);
            handleSync2(curTid, (ISyncNode) node);
          }

        } // for one tid
      } // for all tid
//    checkSyncStack();
      finishSync();
    }


    /**
     * find shared acc {
     * shared heap access,
     * addr written by more than 2 threads
     * addr write / read diff threads
     * @return
     */
    protected LongOpenHashSet findSharedAcc() {

      Long2ObjectOpenHashMap<ShortOpenHashSet> addr2TidReads = new Long2ObjectOpenHashMap<ShortOpenHashSet>(UFO.INITSZ_S);
      Long2ObjectOpenHashMap<ShortOpenHashSet> addr2TidWrites = new Long2ObjectOpenHashMap<ShortOpenHashSet>(UFO.INITSZ_S);
      LongOpenHashSet sharedAddrSet = new LongOpenHashSet(UFO.INITSZ_L * 2);
//    sharedAddrSet.addAll(_allocationTree.keySet());

      for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> e : _rawTid2seq.short2ObjectEntrySet()) {
        final short tid = e.getShortKey();
        for (AbstractNode node : e.getValue()) {
          if (!(node instanceof MemAccNode))
            continue;
          // save shared memory access
          MemAccNode memNode = (MemAccNode) node;
          final long addr = memNode.getAddr();

          if (allocator.checkAcc(memNode, reachEngine))
            sharedAddrSet.add(addr);
          //==============================================================================================================
          if (node instanceof ReadNode || node instanceof RangeReadNode) {
            ShortOpenHashSet tidSetR = addr2TidReads.get(addr);
            if (tidSetR == null) {
              tidSetR = new ShortOpenHashSet(UFO.INITSZ_S / 10);
              addr2TidReads.put(addr, tidSetR);
            }
            tidSetR.add(tid);
          } else if (node instanceof RangeWriteNode || node instanceof WriteNode) {
            ShortOpenHashSet tidSetW = addr2TidWrites.get(addr);
            if (tidSetW == null) {
              tidSetW = new ShortOpenHashSet(UFO.INITSZ_S / 10);
              addr2TidWrites.put(addr, tidSetW);
            }
            tidSetW.add(tid);
          }

        } // for addr
      } // for tid

      LongOpenHashSet addrs = new LongOpenHashSet(UFO.INITSZ_L * 2);
      addrs.addAll(addr2TidReads.keySet());
      addrs.addAll(addr2TidWrites.keySet());
      for (long addr : addrs) {
        ShortOpenHashSet wtids = addr2TidWrites.get(addr);
        if (wtids != null && wtids.size() > 0) {
          if (wtids.size() > 1) { // write thread > 1
            sharedAddrSet.add(addr);
          } else if (wtids.size() == 1) { // only one write
            short wtid = wtids.iterator().nextShort();
            ShortOpenHashSet rtids = addr2TidReads.get(addr);
            if (rtids != null) {
              rtids.remove(wtid); // remove self
              if (rtids.size() > 0)// another read
                sharedAddrSet.add(addr);
            }
          }
        }

      } //for addr

      return sharedAddrSet;
    }
    

    // called in the second pass
    // build tid addr dealloc index
    protected void handleTSMemAcc(int tid, MemAccNode node) {
      // index: addr -> acc
      long addr = node.getAddr();

//    Map.Entry<Long, AbstractNode> e = _allocationTree.floorEntry(addr);
//    if (e != null && e.getValue() instanceof DeallocNode) {
//      DeallocNode dn = (DeallocNode) e.getValue();
//      if (dn.tid != tid) {
//        shared.acc2Dealloc.put(node, dn);
////        ArrayList<MemAccNode> seqAcc = dealloc2seqAcc.get(addr);
////        if (seqAcc == null) {
////          seqAcc = new ArrayList<MemAccNode>(INITSZ_S);
////          dealloc2seqAcc.put(addr, seqAcc);
////        }
////        seqAcc.add(node);
//      }
//    } else if (CHECK_ALLOC_OVERLAP) {
//      LOG.error("!!! could not match memory access at " + addr + "  with free()");
//    }
      // index: seq read, seq write
      if (node instanceof RangeReadNode) {

      } else if (node instanceof ReadNode) {
        metaInfo.countRead++;
        
        
        shared.addReadNode((ReadNode) node);

      } else if (node instanceof RangeWriteNode) {

      } else if (node instanceof WriteNode) {
        metaInfo.countWrite++;
//        seqWrite.add((WriteNode) node);
        ArrayList<WriteNode> seqW = shared.addr2SeqWrite.get(addr);
        if (seqW == null) {
          seqW = new ArrayList<WriteNode>(UFO.INITSZ_L);
          shared.addr2SeqWrite.put(addr, seqW);
        }
        seqW.add((WriteNode) node);

        if (!shared.initWrites.containsKey(addr)) {
          shared.initWrites.put(addr, ((WriteNode) node).value);
        }
      }
    }

//  protected static void addAddr2TidWrite(Long2ObjectOpenHashMap<IntOpenHashSet> _addr2TidWrites,
//                                int tid, long addr) {
//    IntOpenHashSet tidSetW = _addr2TidWrites.get(addr);
//    if (tidSetW == null) {
//      tidSetW = new IntOpenHashSet(UFO.INITSZ_S);
//      _addr2TidWrites.put(addr, tidSetW);
//    }
//    tidSetW.add(tid);
//  }

    Long2ObjectOpenHashMap<ArrayList<LockPair>> addr2LockPairLs = new Long2ObjectOpenHashMap<ArrayList<LockPair>>(UFO.INITSZ_S);

    protected void handleSync2(short tid, ISyncNode node) {

      long addr = node.getAddr();
      ArrayList<ISyncNode> syncNodes = _syncNodesMap.get(addr);
      if (syncNodes == null) {
        syncNodes = new ArrayList<ISyncNode>(UFO.INITSZ_S);
        _syncNodesMap.put(addr, syncNodes);
      }
      syncNodes.add(node);

//      if (node instanceof TStartNode) {
//        thrStartNodeList.add((TStartNode) node);
//        metaInfo.countTStart++;
//
//      } else if (node instanceof TJoinNode) {
//        joinNodeList.add((TJoinNode) node);
//        metaInfo.countTJoin++;
//
//      } else
      if (node instanceof LockNode) {
        Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
        if (stack == null) {
          stack = new Stack<ISyncNode>();
          _tid2SyncStack.put(tid, stack);
        }
        stack.push(node);
        metaInfo.countLock++;

      } else if (node instanceof UnlockNode) {
        metaInfo.countUnlock++;
        Long2ObjectOpenHashMap<ArrayList<LockPair>> indexedLockpairs = _tid2LockPairs.get(tid);
        if (indexedLockpairs == null) {
          indexedLockpairs = new Long2ObjectOpenHashMap<ArrayList<LockPair>>(UFO.INITSZ_S);
          _tid2LockPairs.put(tid, indexedLockpairs);
        }
        long lockId = ((UnlockNode) node).lockID;
        ArrayList<LockPair> lockpairLs = indexedLockpairs.get(lockId);
        if (lockpairLs == null) {
          lockpairLs = new ArrayList<LockPair>();
          indexedLockpairs.put(lockId, lockpairLs);
        }

        Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
        if (stack == null) {
          stack = new Stack<ISyncNode>();
          _tid2SyncStack.put(tid, stack);
        }
        //assert(stack.fsize()>0); //this is possible when segmented
        if (stack.size() == 0)
          lockpairLs.add(new LockPair(null, node));
        else if (stack.size() == 1)
          lockpairLs.add(new LockPair(stack.pop(), node));
        else
          stack.pop();//handle reentrant nLock
      } // nUnlock
      
//      else if (node instanceof WaitNode) {
//    	  	saveToWaitNotifyList((IWaitNotifyNode) node);
//      }
//      else if (node instanceof NotifyNode) {
//    	  	saveToWaitNotifyList((IWaitNotifyNode) node);
//      }
//      else if (node instanceof NotifyAllNode) {
//    	  	saveToWaitNotifyList((IWaitNotifyNode) node);
//      }
    }


    
    public void finishSync() {
      checkSyncStack();

      for (Long2ObjectOpenHashMap<ArrayList<LockPair>> tidAddr2LpLs : _tid2LockPairs.values()) {
        for (Long2ObjectMap.Entry<ArrayList<LockPair>> e : tidAddr2LpLs.long2ObjectEntrySet()) {
          long lockID = e.getLongKey();
          ArrayList<LockPair> addrLpLs = addr2LockPairLs.get(lockID);
          if (addrLpLs == null) {
            addrLpLs = new ArrayList<LockPair>(UFO.INITSZ_S * 5);
            addr2LockPairLs.put(lockID, addrLpLs);
          }
          addrLpLs.addAll(e.getValue());
        }
      }
    }
    protected void checkSyncStack() {

      //check threadSyncStack - only to handle when segmented

      for (Short2ObjectMap.Entry<Stack<ISyncNode>> entry : _tid2SyncStack.short2ObjectEntrySet()) {
        final short tid = entry.getShortKey();
        Stack<ISyncNode> stack = entry.getValue();

        if (!stack.isEmpty()) {
          Long2ObjectOpenHashMap<ArrayList<LockPair>> indexedLockpairs = _tid2LockPairs.get(tid);
          if (indexedLockpairs == null) {
            indexedLockpairs = new Long2ObjectOpenHashMap<ArrayList<LockPair>>(UFO.INITSZ_S);
            _tid2LockPairs.put(tid, indexedLockpairs);
          }

          while (!stack.isEmpty()) {
            ISyncNode syncnode = stack.pop();//nLock or wait
            long addr = syncnode.getAddr();
            ArrayList<LockPair> lockpairs = indexedLockpairs.get(addr);
            if (lockpairs == null) {
              lockpairs = new ArrayList<LockPair>(UFO.INITSZ_S);
              indexedLockpairs.put(addr, lockpairs);
            }
            lockpairs.add(new LockPair(syncnode, null));
          }
        } // stack not empty
      } // for
    }


    public Long2ObjectOpenHashMap<ArrayList<LockPair>> getAddr2LockPairLs() {
      return addr2LockPairLs;
    }


    Long2ObjectOpenHashMap<ArrayList<ISyncNode>> _syncNodesMap =
        new Long2ObjectOpenHashMap<ArrayList<ISyncNode>>(UFO.INITSZ_S);

    Short2ObjectOpenHashMap<Long2ObjectOpenHashMap<ArrayList<LockPair>>> _tid2LockPairs
        = new Short2ObjectOpenHashMap<Long2ObjectOpenHashMap<ArrayList<LockPair>>>(UFO.INITSZ_S / 2);

    Short2ObjectOpenHashMap<Stack<ISyncNode>> _tid2SyncStack = new Short2ObjectOpenHashMap<Stack<ISyncNode>>(UFO.INITSZ_S / 2);

    protected void handleSync(short tid, ISyncNode node) {
//    info.incrementSyncNumber();

      long addr = node.getAddr();
      ArrayList<ISyncNode> syncNodes = _syncNodesMap.get(addr);
      if (syncNodes == null) {
        syncNodes = new ArrayList<ISyncNode>(UFO.INITSZ_S);
        _syncNodesMap.put(addr, syncNodes);
      }
      syncNodes.add(node);

      if (node instanceof LockNode) {
        Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
        if (stack == null) {
          stack = new Stack<ISyncNode>();
          _tid2SyncStack.put(tid, stack);
        }
        stack.push(node);

      } else if (node instanceof UnlockNode) {
        Long2ObjectOpenHashMap<ArrayList<LockPair>> indexedLockpairs = _tid2LockPairs.get(tid);
        if (indexedLockpairs == null) {
          indexedLockpairs = new Long2ObjectOpenHashMap<ArrayList<LockPair>>(UFO.INITSZ_S);
          _tid2LockPairs.put(tid, indexedLockpairs);
        }
        long lockId = ((UnlockNode) node).lockID;
        ArrayList<LockPair> lockpairLs = indexedLockpairs.get(lockId);
        if (lockpairLs == null) {
          lockpairLs = new ArrayList<LockPair>();
          indexedLockpairs.put(lockId, lockpairLs);
        }

        Stack<ISyncNode> stack = _tid2SyncStack.get(tid);
        if (stack == null) {
          stack = new Stack<ISyncNode>();
          _tid2SyncStack.put(tid, stack);
        }
        //assert(stack.fsize()>0); //this is possible when segmented
        if (stack.size() == 0)
          lockpairLs.add(new LockPair(null, node));
        else if (stack.size() == 1)
          lockpairLs.add(new LockPair(stack.pop(), node));
        else
          stack.pop();//handle reentrant nLock
      } // nUnlock
    }


    protected Long2ObjectOpenHashMap<AbstractNode> gid2node;

    public Long2ObjectOpenHashMap<AbstractNode> getGid2node() {
      return gid2node;
    }

    public void prepareNorm() {
      if (state_ <= IDX_BUILT)
        throw new IllegalStateException();
      // destroy all

      tidFirstNode = null;
      tidLastNode = null;
      metaInfo = null;
      _syncNodesMap = null;

      _tid2LockPairs = null;
      _tid2SyncStack = null;
      shared.destroy();
      System.gc();
      gid2node = new Long2ObjectOpenHashMap<AbstractNode>(UFO.INITSZ_L * 2);
      for (AbstractNode n : shared.allNodeSeq)
        gid2node.put(n.gid, n);

      gid2node.trim();
      // destroy
      shared.allNodeSeq = null;
      state_ = PRE_NORM;
      System.gc();
    }

    // second phase normalize is in
    public ArrayList<AbstractNode> normalize1st(RawUaf uaf) {
      if (state_ < PRE_NORM)
        throw new IllegalStateException();
      LongArrayList schedule = uaf.schedule;
      ArrayList<AbstractNode> uafNodes = new ArrayList<AbstractNode>(schedule.size());
      for (long gid : schedule) {
        AbstractNode an = gid2node.get(gid);
        if (an == null)
          throw new NoSuchElementException("could not found trace with gid " + gid);
//      if (an instanceof ISyncNode
//          || an instanceof DeallocNode
//          || an instanceof AllocNode
//          || an == uaf.accNode) {
        uafNodes.add(an);
//      }
      }
      return uafNodes;
    }

    /**
     * most recent call in the back
     *
     * @param node
     * @return
     */
    public LongArrayList buildCallStack(AbstractNode node) {
      final short tid = node.tid;
      final long gid = node.gid;
      ArrayList<AbstractNode> callseq = tid2CallSeq.get(tid);
      if (callseq == null || callseq.size() < 1)
        return null;
      LongArrayList callStack = new LongArrayList(100);
      for (AbstractNode n : callseq) {
        if (n.gid > gid)
          break;
        if (n instanceof FuncEntryNode) {
          long pc = ((FuncEntryNode) n).pc;
          callStack.push(pc);

        } else if (n instanceof FuncExitNode) {
          if (!callStack.isEmpty())
            callStack.popLong();

        } else throw new IllegalStateException("Unknown event in call seq " + n);
      }
      return callStack;
    }

    public void getTSDependentSeqRead(ArrayList<ReadNode> allReadNodes, AbstractNode node) {
       
    	ArrayList<ReadNode> tidNodes = shared.tid2seqReads.get(node.tid);
    	if(tidNodes==null||tidNodes.isEmpty()) return;
    	
    	int min = 0;
    	int max = tidNodes.size()-1;
    	
    	//find the latest read before this node
    	int id=(min+max)/2;
    	
    	while(true)
    	{
	    	ReadNode tmp = tidNodes.get(id);
	    	if(tmp.gid<node.gid)
	    	{
	    		if(id+1>max||tidNodes.get(++id).gid>node.gid) break;
	    		min=id;
	    		id=(min+max)/2;
	    	}
	    	else if (tmp.gid>node.gid)
	    	{
	    		if(id-1<min || tidNodes.get(--id).gid<node.gid) break;
	    		max=id;
	    		id=(min+max)/2;
	    	}
	    	else
	    	{
	    		//exclude itself
	    		break;
	    	}
    	}
    	
    	if(tidNodes.get(id).gid<node.gid&&id<max)
    		id++;//special case
    	
    	
    	for(int i=0;i<id;i++)
    		allReadNodes.add(tidNodes.get(i));
    		
    }

//  public ArrayList<WriteNode> getSeqWrite() {
//    return seqWrite;
//  }

    public HashMap<MemAccNode, HashSet<AllocaPair>> getMachtedAcc() {
      return allocator.machtedAcc;
    }

    public HashMap<MemAccNode, DeallocNode> getTSAcc2Dealloc() {
//    throw new RuntimeException("Not implemented");
      return shared.acc2Dealloc;
    }

    public Long2ObjectOpenHashMap<ArrayList<WriteNode>> getTSAddr2SeqWrite() {
      return shared.addr2SeqWrite;
    }

    public ArrayList<AbstractNode> getAllNodeSeq() {
      if(state_ >= PRE_NORM)
        throw new IllegalStateException("After pre-norm, no seq");
      return shared.allNodeSeq;
    }

    public Long2ObjectOpenHashMap<ArrayList<ISyncNode>> get_syncNodesMap() {
      return _syncNodesMap;
    }

    public Short2ObjectOpenHashMap<ArrayList<AbstractNode>> getTSTid2sqeNodes() {
      return shared.tid2sqeNodes;
    }

    public Short2ObjectOpenHashMap<AbstractNode> getTidLastNode() {
      return tidLastNode;
    }

    public Short2ObjectOpenHashMap<AbstractNode> getTidFirstNode() {
      return tidFirstNode;
    }

    public Long2LongOpenHashMap getTSInitWrites() {
      return shared.initWrites;
    }

    public NewReachEngine getReachEngine() {
      return reachEngine;
    }

//  public List<Pair<DeallocNode, MemAccNode>> getUafList() {
//    Set<Map.Entry<MemAccNode, DeallocNode>> acc2DellocSets = shared.acc2Dealloc.entrySet();
//    final ArrayList<Pair<DeallocNode, MemAccNode>> uafLs = new ArrayList<Pair<DeallocNode, MemAccNode>>(acc2DellocSets.size());
//    for (Map.Entry<MemAccNode, DeallocNode> e : acc2DellocSets) {
//      final MemAccNode accNode = e.getKey();
//      final DeallocNode deNode = e.getValue();
//      uafLs.add(new Pair<DeallocNode, MemAccNode>(deNode, accNode));
//    }
//    return uafLs;
//  }


    public void trim() {
      shared.trim();

      tidFirstNode.trim();
      tidLastNode.trim();
      this._syncNodesMap.trim();
      this._tid2LockPairs.trim();
      this._tid2SyncStack.trim();
    }


  }
