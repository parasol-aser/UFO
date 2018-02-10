package aser.ufo.misc;

import java.util.ArrayList;

public class CModuleList {
	
  private ArrayList<CModuleSection> modules = new ArrayList<CModuleSection>(20);

  private CModuleSection mainExe;
  public boolean excludeLib = false;

  public void addMainExe(CModuleSection m) {
    mainExe = m;
    modules.add(m);
  }


  public void add(CModuleSection m) {
    modules.add(m);
  }

  public int size() {
    return modules.size();
  }

  public Pair<String, Long> findNameAndOffset(long pc) {
    for (CModuleSection m : modules) {
      if (m.begin <= pc && pc < m.end) {
//        System.out.println(_m + "    " + m);
        return new Pair<String, Long>(m.name, pc - m.base);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "CModuleList{" +
        "modules=" + modules +
        '}';
  }
}
