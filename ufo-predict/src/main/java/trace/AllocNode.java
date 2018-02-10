package trace;

public class AllocNode extends AbstractNode {

	public final static AllocNode FAKE = new AllocNode((short) -1,-1,-1,-1);

  public static NodeType TYPE = NodeType.ALLOC;

  public final long addr;
  public final int length;
  public final long pc;
  public AllocNode(short tid, long pc_, long a, int len) {
    super(tid);
    addr = a;
    length = len;
    pc = pc_;
  }


  public String toString() {
    return "gid: "+gid + " #" + tid  + "   pc:0x" + Long.toHexString(pc) + " Alloc  addr:" +  addr + "  fsize:" + length;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AllocNode allocNode = (AllocNode) o;

    if (addr != allocNode.addr) return false;
    if (length != allocNode.length) return false;
    if (pc != allocNode.pc) return false;
    return gid == allocNode.gid;
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
