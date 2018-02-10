package trace;

public class DeallocNode extends AbstractNode {

  public static NodeType TYPE = NodeType.DEALLOC;

  public final long addr;
  public        int length;
  public final long pc;

  public DeallocNode(short tid, long p, long a,int len) {
    super(tid);
    addr = a;
    pc = p;
    length = len;
  }

  public String toString() {
    return "gid: "+gid + " #" + tid  + "   pc:0x" + Long.toHexString(pc)  + "   Free addr:" +  addr + "  fsize:" + length;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    DeallocNode that = (DeallocNode) o;

    if (addr != that.addr) return false;
    if (pc != that.pc) return false;
    return gid == that.gid;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (int) (addr ^ (addr >>> 32));
    result = 31 * result + length;
    result = 31 * result + (int) (pc ^ (pc >>> 32));
//    result = 31 * result + (int) (idx ^ (idx >>> 32));
    return result;
  }
}
