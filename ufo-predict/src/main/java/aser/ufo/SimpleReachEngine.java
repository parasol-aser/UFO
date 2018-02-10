package aser.ufo;

import aser.ufo.trace.Bytes;
import it.unimi.dsi.fastutil.shorts.Short2LongMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import trace.AbstractNode;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleReachEngine {

  private static final long NSE = 0;
  Short2ObjectOpenHashMap<Short2LongOpenHashMap> linkSet = new Short2ObjectOpenHashMap<Short2LongOpenHashMap>(UFO.INITSZ_S);

  private static final int CACHE_SZ = UFO.INITSZ_S * 3;
  final LinkedHashMap<Long, Object> cache = new LinkedHashMap<Long, Object>(CACHE_SZ, 0.75F, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<Long, Object> eldest) {
      return size() > CACHE_SZ;
    }
  };

  public void addEdge(AbstractNode from, AbstractNode to) {
    if (from.tid == to.tid)
      return;
    Short2LongOpenHashMap spawn = linkSet.get(from.tid);
    if (spawn == null) {
      spawn = new Short2LongOpenHashMap(UFO.INITSZ_S / 2);
      spawn.defaultReturnValue(NSE);
      linkSet.put(from.tid, spawn);
    }
    spawn.addTo(to.tid, Bytes.longs.add((int)from.gid, (int)to.gid));
  }


  // DFS
  private long searchEdge(short t1, long gid1, short t2) {
    Short2LongOpenHashMap t1f = linkSet.get(t1);
    if (t1f == null)
      return NSE;

    final long gids = t1f.get(t2);
    if (gids != NSE) {
      int gid = Bytes.longs.part1(gids);
      if (gid1 <= gid)
        return gids;
      else return NSE;
    }

    for (Short2LongMap.Entry e : t1f.short2LongEntrySet()) {
      long val = e.getLongValue();
      int curGid = Bytes.longs.part1(val);
      // make sure this gid1 can reach this thread start point
      if (gid1 < curGid)
        continue;
      val = searchEdge(e.getShortKey(), curGid, t2);
      if (val != NSE)
        return val;
    }
    return NSE;
  }

  public boolean canReach(AbstractNode n1, AbstractNode n2) {
    final short t1 = n1.tid;
    final short t2 = n2.tid;
    final long gid1 = n1.gid;
    final long gid2 = n2.gid;
    if (t1 == t2) {
      // gid grows within one thread
      return gid1 <= gid2;
    } else { // diff thread

      final long gids = Bytes.longs.add((int)gid1, (int)gid2);
      synchronized (cache) {
        if (cache.containsKey(gids))
          return false;
      }

      boolean canReach;
      long val = searchEdge(t1, gid1, t2);
      if (val != NSE) {
        int gidTo = Bytes.longs.part2(val);
        canReach = gidTo <= gid2;
      } else canReach = false;

      synchronized (cache) {
        if (!canReach) {
          cache.put(gids, null);
        }
      }
      return canReach;

    } //diff threads
  }
}




