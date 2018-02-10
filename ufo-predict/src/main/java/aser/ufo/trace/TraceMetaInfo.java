package aser.ufo.trace;

import aser.ufo.UFO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2IntOpenHashMap;

public class TraceMetaInfo {

  public int tidCount = 0;
  public LongOpenHashSet sharedAddrs;
  public Short2IntOpenHashMap tidRawNodesCounts = new Short2IntOpenHashMap(UFO.INITSZ_S);
  public  int rawNodeCount = 0;

  public long countAllNodes;

  public long countAlloc;
  public long countDealloc;
  public long countTStart;
  public long countTJoin;
  public long countLock;
  public long countUnlock;
  public long countWrite;
  public long countRead;
  public long countFuncCall;

  public void reset() {
    sharedAddrs = null;
    countAllNodes = 0;
  }

  @Override
  public String toString() {

    return countAllNodes == 0 ? "" : "TraceMetaInfo{" +
        "tidCount=" + tidCount +
        ", sharedAddrs=" + (sharedAddrs == null ? 0 : sharedAddrs.size()) +
        ", tidRawNodesCounts=" + tidRawNodesCounts +
        ", countFuncCall=" + countFuncCall +
        ", countAllNodes=" + countAllNodes +
        ", countAlloc=" + countAlloc + " (" + ((float)countAlloc / countAllNodes) +")" +
        ", countDealloc=" + countDealloc + " (" + ((float)countDealloc / countAllNodes) +")" +
        ", countTStart=" + countTStart + " (" + ((float)countTStart / countAllNodes) +")" +
        ", countTJoin=" + countTJoin + " (" + ((float)countTJoin / countAllNodes) +")" +
        ", countLock=" + countLock + " (" + ((float)countLock / countAllNodes) +")" +
        ", countUnlock=" + countUnlock + " (" + ((float)countUnlock / countAllNodes) +")" +
        ", countWrite=" + countWrite + " (" + ((float)countWrite / countAllNodes) +")" +
        ", countRead=" + countRead + " (" + ((float)countRead / countAllNodes) +")" +
        '}';
  }
}
