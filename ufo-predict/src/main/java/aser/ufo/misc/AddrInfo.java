package aser.ufo.misc;

public class AddrInfo {

  public final long addr;
  public final String file;
  public final String function;
  public final int line;

  public AddrInfo(long addr, String file, String function, int line) {
    this.addr = addr;
    this.file = file;
    this.function = function;
    this.line = line;
  }

  @Override
  public String toString() {
    return line==-1?file:function + " @ " + file + ":" + line ;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AddrInfo addrInfo = (AddrInfo) o;

    if (addr != addrInfo.addr) return false;
    if (line != addrInfo.line) return false;
    if (file != null ? !file.equals(addrInfo.file) : addrInfo.file != null) return false;
    return function != null ? function.equals(addrInfo.function) : addrInfo.function == null;

  }

  @Override
  public int hashCode() {
    int result = (int) (addr ^ (addr >>> 32));
    result = 31 * result + (file != null ? file.hashCode() : 0);
    result = 31 * result + (function != null ? function.hashCode() : 0);
    result = 31 * result + line;
    return result;
  }
}
