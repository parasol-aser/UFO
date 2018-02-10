package aser.ufo.misc;

public class CModuleSection {
  public final String name;
  public final long base;
  public boolean isExe;
  public final long begin;
  public final long end;

  public CModuleSection(String name, long base, long end) {
	    this.name = name;
	    this.base = base;
	    this.begin = base;
	    this.end = end;
	  }
  
  public CModuleSection(String name, long base, boolean isExe, long begin, long end) {
    this.name = name;
    this.base = base;
    this.isExe = isExe;
    this.begin = begin;
    this.end = end;
  }

  @Override
  public String toString() {
    return "CModuleSection{" +
        "name='" + name + '\'' +
        ", base=" + base +
        ", isExe=" + isExe +
        ", begin=" + begin +
        ", end=" + end +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CModuleSection cModule = (CModuleSection) o;

    if (base != cModule.base) return false;
    if (isExe != cModule.isExe) return false;
    if (begin != cModule.begin) return false;
    if (end != cModule.end) return false;
    return name != null ? name.equals(cModule.name) : cModule.name == null;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (int) (base ^ (base >>> 32));
    result = 31 * result + (isExe ? 1 : 0);
    result = 31 * result + (int) (begin ^ (begin >>> 32));
    result = 31 * result + (int) (end ^ (end >>> 32));
    return result;
  }
}
