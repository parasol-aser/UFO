package aser.ufo.trace;

import trace.AbstractNode;

import java.util.Date;

public class TLHeader extends AbstractNode {

  public final long version;
  public final Date timeStart;
  public final long data;

  public TLHeader(short tid, long v, long t, long d) {
    super(tid);
    version = v;
    timeStart = new Date(t);
    data = d;
  }

  @Override
  public String toString() {
    return "TLHeader{" +
        "version=" + version +
        ", timeStart=" + timeStart +
        ", data=" + data +
        '}';
  }
}
