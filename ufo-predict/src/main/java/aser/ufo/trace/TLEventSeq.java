package aser.ufo.trace;

import aser.ufo.trace.TLHeader;
import aser.ufo.trace.TLStat;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import trace.AbstractNode;

import java.util.ArrayList;

public class TLEventSeq {

  public final short tid;
  public final ShortOpenHashSet newTids = new ShortOpenHashSet(40);
//  public final ShortOpenHashSet endedTids = new ShortOpenHashSet(60);
  public TLHeader header;
  public ArrayList<AbstractNode> events;
  public final static TLStat stat = new TLStat();
  public int numOfEvents;

  public TLEventSeq(short tid) {
    this.tid = tid;
  }
}
