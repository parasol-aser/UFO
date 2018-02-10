package aser.ufo.misc;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import trace.DeallocNode;
import trace.MemAccNode;

public class RawUaf {
  public final MemAccNode accNode;
  public final DeallocNode deallocNode;
  public final LongArrayList schedule;

  public RawUaf(MemAccNode accNode, DeallocNode deallocNode, LongArrayList schedule) {
    this.accNode = accNode;
    this.deallocNode = deallocNode;
    this.schedule = schedule;
  }
}
