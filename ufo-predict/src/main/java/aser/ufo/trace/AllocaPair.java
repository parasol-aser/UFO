package aser.ufo.trace;

import trace.AllocNode;
import trace.DeallocNode;

import java.util.HashSet;

public class AllocaPair {

  public final AllocNode allocNode;

  // accesses from other threads that belong to this group
//  public final LinkedHashSet<MemAccNode> accNodeCache = new LinkedHashSet<MemAccNode>(UFO.INITSZ_S);
  // key gid
//  public final IntOpenHashSet accNodeCache = null;

  public DeallocNode deallocNode = null;
  public HashSet<DeallocNode> possibleDealloc = null;

  public AllocaPair(AllocNode allocNode) {
    this.allocNode = allocNode;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AllocaPair pair = (AllocaPair) o;

    if (!allocNode.equals(pair.allocNode)) return false;
    if (deallocNode != null ? !deallocNode.equals(pair.deallocNode) : pair.deallocNode != null) return false;
    return possibleDealloc != null ? possibleDealloc.equals(pair.possibleDealloc) : pair.possibleDealloc == null;

  }

  @Override
  public int hashCode() {
    int result = allocNode.hashCode();
    result = 31 * result + (deallocNode != null ? deallocNode.hashCode() : 0);
    result = 31 * result + (possibleDealloc != null ? possibleDealloc.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "AllocaPair{" +
        "allocNode=" + allocNode +
        ", deallocNode=" + deallocNode +
        ", possibleDealloc=" + possibleDealloc +
        '}';
  }
}
