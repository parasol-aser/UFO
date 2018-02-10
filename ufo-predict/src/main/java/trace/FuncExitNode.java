package trace;

public class FuncExitNode extends AbstractNode {

  public FuncExitNode(short tid) {
    super(tid);
  }
  
  @Override
  public String toString() {
    return "gid: "+gid + " #" + tid +"   FuncExit";
  }
}
