package aser.ufo.misc;

import aser.ufo.trace.AllocaPair;
import trace.MemAccNode;

import java.util.HashSet;

public class RawUaFCpx extends RawUaf {

  public final HashSet<AllocaPair> pairs;

  public RawUaFCpx(MemAccNode accNode, HashSet<AllocaPair> pairs) {
    super(accNode, null, null);
    this.pairs = pairs;
  }
}
