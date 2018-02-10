package aser.ufo.trace;


import aser.ufo.UFO;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trace.AllocNode;
import trace.DeallocNode;
import trace.MemAccNode;
import trace.PtrPropNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;


public class HeapMonitor {

  private static final Logger LOG = LoggerFactory.getLogger(HeapMonitor.class);

  public static class AllocPair {
    public final AllocNode allocEvent;
    public final DeallocNode deallocEvent;
    public AllocPair(AllocNode allocEvent, DeallocNode deallocEvent) {
      this.allocEvent = allocEvent;
      this.deallocEvent = deallocEvent;
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AllocPair allocPair = (AllocPair) o;
      if (allocEvent != null ? !allocEvent.equals(allocPair.allocEvent) : allocPair.allocEvent != null) return false;
      return deallocEvent != null ? deallocEvent.equals(allocPair.deallocEvent) : allocPair.deallocEvent == null;
    }

    @Override
    public int hashCode() {
      int result = allocEvent != null ? allocEvent.hashCode() : 0;
      result = 31 * result + (deallocEvent != null ? deallocEvent.hashCode() : 0);
      return result;
    }
  }

  public static class AllocPoint {
    Long2ObjectRBTreeMap<AllocNode> allocSeq = new Long2ObjectRBTreeMap<AllocNode>();
    ArrayList<DeallocNode> deallocQ = new ArrayList<DeallocNode>(UFO.INITSZ_S);
  }

  public static class PtrPropPoint {
    public final long src;
    public final long dest;
    public long idx; //approximate
    public AllocPair allocPair;

    public PtrPropPoint(long src, long dest, long idx) {
      this.src = src;
      this.dest = dest;
      this.idx = idx;
    }
  }


  private Long2ObjectRBTreeMap<AllocPoint> addrOpMap = new Long2ObjectRBTreeMap<AllocPoint>();
  private int allocCount = 0;
  private int deallocCount = 0;

  // idx reversed
  private Long2ObjectRBTreeMap<AllocPair> gAllocSeq = new Long2ObjectRBTreeMap<AllocPair>(new Comparator<Long>() {
    @Override
    public int compare(Long o1, Long o2) {
      return o2.compareTo(o1);
    }
  });

  // idx set reversed  large --> small, left --> right
  private Long2ObjectRBTreeMap<TreeSet<PtrPropPoint>> ppropMap = new Long2ObjectRBTreeMap<TreeSet<PtrPropPoint>>();

  public void allocated(AllocNode allocNode) {
    AllocPoint ap = addrOpMap.get(allocNode.addr);
    if (ap == null) {
      ap = new AllocPoint();
      addrOpMap.put(allocNode.addr, ap);
    }
    ap.allocSeq.put(allocNode.gid, allocNode);//JEFF
    allocCount++;
  }

  public void deallocated(DeallocNode deallocNode) {
    AllocPoint ap = addrOpMap.get(deallocNode.addr);
    if (ap == null) {
      ap = new AllocPoint();
      addrOpMap.put(deallocNode.addr, ap);
    }
    ap.deallocQ.add(deallocNode);
    deallocCount++;
  }

  // must be called before ptrprop
  public void pairDealloc() {
    gAllocSeq.clear();
    if (allocCount != deallocCount) {
      LOG.debug("Alloc({}) != Dealloc({})", allocCount, deallocCount);
    }
    int pairCount = 0;
    for (Long2ObjectMap.Entry<AllocPoint> e1 : addrOpMap.long2ObjectEntrySet()) {
      AllocPoint ap = e1.getValue();
      ArrayList<DeallocNode> deLs = ap.deallocQ;
      for (DeallocNode dn : deLs) {
        Long2ObjectSortedMap<AllocNode> aLs = ap.allocSeq.headMap(dn.gid);//JEFF
        if (aLs.isEmpty()) {
          LOG.debug(" Empty alloc for dealloc idx:{} addr: {}, tid {}", dn.gid, dn.addr, dn.tid);//JEFF
        } else {
          AllocNode an = aLs.get(aLs.lastLongKey());
          if (an.addr == dn.addr) {
            dn.length = an.length;
            gAllocSeq.put(an.gid, new AllocPair(an, dn));//JEFF
            pairCount++;
          } else {
            LOG.error("most close alloc addr unmatched ${}  vs ${}", an, dn);
          }
        }
      }
    }
    addrOpMap.clear();
    if (pairCount != allocCount && pairCount != deallocCount) {
      LOG.debug("Matched pair({}) != alloc({}) and dealloc({})",
          pairCount, allocCount, deallocCount);
    }
  }

  // after post process
  public void ptrprop(PtrPropNode pn) {
//    System.out.println(pn);
    TreeSet<PtrPropPoint> ppSet = ppropMap.get(pn.dest);
    if (ppSet == null) {
      ppSet = new TreeSet<PtrPropPoint>(new Comparator<PtrPropPoint>() {
        @Override
        public int compare(PtrPropPoint o1, PtrPropPoint o2) {
          return (o2.idx < o1.idx) ? -1 : ((o1.idx == o2.idx) ? 0 : 1);
        }
      });
      ppropMap.put(pn.dest, ppSet);
    }
    PtrPropPoint pp = new PtrPropPoint(pn.src, pn.dest, pn.idx);
    while (!ppSet.add(pp)) {
      pp.idx++;
    }
    // matching alloc pair
    Long2ObjectSortedMap<AllocPair> beforeMap = gAllocSeq.tailMap(pp.idx);
    for (Map.Entry<Long, AllocPair> e: beforeMap.entrySet()) {
      AllocPair ap = e.getValue();
      if (ap.allocEvent.addr == pp.src) {
        pp.allocPair = ap;
        break;
      }
    }
  }

  public AllocPair searchAllocPair(MemAccNode accNode) {
    // trace back
    if (accNode.ptr != 0) {
      TreeSet<PtrPropPoint> ppSet = ppropMap.get(accNode.ptr);
      if (ppSet != null) {
        PtrPropPoint srcPP = null;
        for (PtrPropPoint pp : ppSet) {
          if (pp.idx < accNode.gid) {//JEFF idx
            srcPP = pp;
            break;
          }
        }
        if (srcPP != null) {
          boolean keep = true;
          while (keep && srcPP.allocPair == null) {
            ppSet = ppropMap.get(srcPP.src);
            keep = ppSet != null;
            if (keep) {
              for (PtrPropPoint pp : ppSet) {
                if (pp.idx < srcPP.idx) {
                  srcPP = pp;
                  break;
                }
              }
            }
          }
          return srcPP.allocPair;
        }
      }
    }
    // second attempt
    Long2ObjectSortedMap<AllocPair> earlyMap = gAllocSeq.tailMap(accNode.gid);//JEFF idx
    for (Map.Entry<Long, AllocPair> e: earlyMap.entrySet()) {
      AllocNode an = e.getValue().allocEvent;
      if (an.addr <= accNode.addr && accNode.addr < an.addr + an.length)
        return e.getValue();
    }
    // failed
    return null;
  }

}






























