package trace;

public class TEndNode extends ISyncNode {

  public static NodeType TYPE = NodeType.END;

  public final short tidParent;
  public final int eTime;
  public TEndNode(short thisTid, short tidP, int t) {
    super(-1,thisTid);
    tidParent = tidP;
    eTime = t;
  }

  public long getAddr() {
    return tidParent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    TEndNode endNode = (TEndNode) o;

    if (tidParent != endNode.tidParent) return false;
    return eTime == endNode.eTime;

  }

	public String toString() {
		return "gid: "+ gid +" #" + tid + "   end";
	}
	
  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (int) tidParent;
    result = 31 * result + eTime;
    return result;
  }
}
