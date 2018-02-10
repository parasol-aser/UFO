package aser.ufo.trace;

import aser.ufo.NewReachEngine;
import aser.ufo.SimpleReachEngine;
import aser.ufo.UFO;
import aser.ufo.misc.Pair;
import aser.ufo.misc.RawUaf;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trace.*;

import java.util.*;

public class Indexer2 extends Indexer {

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

    protected ArrayList<ReadNode> seqRead = new ArrayList<ReadNode>(UFO.INITSZ_L);

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
      seqRead = null;
//    seqWrite.destroy();
      initWrites = null;
      addr2SeqWrite = null;
//    addr2sqeAcc.destroy();
      tid2sqeNodes = null;
      acc2Dealloc = null;
    }

    void trim() {
      allNodeSeq.trimToSize();
      seqRead.trimToSize();
//    seqWrite.trimToSize();
      initWrites.trim();
      addr2SeqWrite.trim();
//    addr2sqeAcc.trim();
      tid2sqeNodes.trim();
    }
  }

  protected SharedAccIndexes shared = new SharedAccIndexes();


  public Indexer2() {
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


  //private SimpleReachEngine reachEngine = new SimpleReachEngine();
  private NewReachEngine reachEngine = new NewReachEngine();


  private List<Pair<HeapMonitor.AllocPair, MemAccNode>> accNapList = new ArrayList<Pair<HeapMonitor.AllocPair, MemAccNode>>(UFO.INITSZ_L);
  public List<Pair<HeapMonitor.AllocPair, MemAccNode>> getPossibleUafList() {
    return accNapList;
  }

//  5485 252117
  public void postProcess() {
    if (state_ >= IDX_BUILT)
      throw new IllegalStateException("indexes already built");
    // 1. first pass handles:
    // sync,
    // alloc & dealloc,
    // call seq
    // tid first node, last node
    pass1st();

    // build reach engine
    for (TStartNode node : NewReachEngine.thrStartNodeList) {
      short tid = node.tidKid;
      AbstractNode fnode = tidFirstNode.get(tid);
      if (fnode != null)
        reachEngine.addEdge(node, fnode);
    }

    for (TJoinNode node : NewReachEngine.joinNodeList) {
      short tid = node.tid_join;
      AbstractNode lnode = tidLastNode.get(tid);
      if (lnode != null) {
        reachEngine.addEdge(lnode, node);
      }
    }

//    allocator.matchInterThrDealloc(reachEngine);
    heapMonitor.pairDealloc();

    // 2. second pass: find shared addr, process ptr prop
    LongOpenHashSet sharedAddrSet = findSharedAcc();

    // 3. third pass, handle shared mem acc (index: addr tid dealloc allnode)
    for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> e : _rawTid2seq.short2ObjectEntrySet()) {
      final short tid = e.getShortKey();
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

          HeapMonitor.AllocPair ap = heapMonitor.searchAllocPair(memNode);

          if (ap != null) {
            if (ap.deallocEvent.tid != memNode.tid
                && !reachEngine.canReach(memNode, ap.deallocEvent)) // acc cannot reach to dealloc
              accNapList.add(new Pair<HeapMonitor.AllocPair, MemAccNode>(ap, memNode));
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


  //  private Allocator allocator = new Allocator();
  private HeapMonitor heapMonitor = new HeapMonitor();

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

      if (nodes.size() > 0) {
        if (!tidFirstNode.containsKey(curTid)) {
          tidFirstNode.put(curTid, nodes.get(0));
        }
        tidLastNode.put(curTid, nodes.get(nodes.size() - 1));
      }

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

//          allocator.push(an);
          heapMonitor.allocated(an);

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

//          allocator.insert(dnode);
          heapMonitor.deallocated(dnode);

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
   *
   *
   * process ptr prop
   *
   */
  protected LongOpenHashSet findSharedAcc() {

    Long2ObjectOpenHashMap<ShortOpenHashSet> addr2TidReads = new Long2ObjectOpenHashMap<ShortOpenHashSet>(UFO.INITSZ_S);
    Long2ObjectOpenHashMap<ShortOpenHashSet> addr2TidWrites = new Long2ObjectOpenHashMap<ShortOpenHashSet>(UFO.INITSZ_S);
    LongOpenHashSet sharedAddrSet = new LongOpenHashSet(UFO.INITSZ_L * 2);
//    sharedAddrSet.addAll(_allocationTree.keySet());

    for (Short2ObjectOpenHashMap.Entry<ArrayList<AbstractNode>> e : _rawTid2seq.short2ObjectEntrySet()) {
      final short tid = e.getShortKey();
      for (AbstractNode node : e.getValue()) {

        if (node instanceof PtrPropNode) {
          heapMonitor.ptrprop((PtrPropNode)node);
        }

        if (!(node instanceof MemAccNode))
          continue;
        // save shared memory access
        MemAccNode memNode = (MemAccNode) node;
        final long addr = memNode.getAddr();

//        if (allocator.checkAcc(memNode, reachEngine))
//          sharedAddrSet.add(addr);
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


}
