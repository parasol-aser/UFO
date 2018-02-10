package aser.ufo.misc;

import it.unimi.dsi.fastutil.longs.*;
import trace.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import config.Configuration;

/**
 *
 * addr2line util wrapper
 */
public class Addr2line {

  private static final String ADDR2LINE_LINUX = "addr2line --demangle=gnu -e ";//"atos -o ";//"addr2line --demangle=gnu -e ";
  private static final String ADDR2LINE_APPLE = "atos -o ";//"atos -o ";//"addr2line --demangle=gnu -e ";
  private static final String ADDR2LINE_LLVM = "llvm-symbolizer -color -pretty-print -obj=";//"atos -o ";//"addr2line --demangle=gnu -e ";

  private static String OSTYPE = System.getProperty("os.name");
  private static String ADDR2LINE;
public static final int TRIM_LEN = 50;

  public final CModuleList moduleList;

  public Addr2line(CModuleList moduleList) {
    this.moduleList = moduleList;
    
    if(Configuration.symbolizer.equals("llvm"))
    		ADDR2LINE = ADDR2LINE_LLVM;
    else if(OSTYPE==null||OSTYPE.indexOf("Mac")>=0)
    		ADDR2LINE = ADDR2LINE_APPLE;
    else
    		ADDR2LINE = ADDR2LINE_LINUX;
  }

  public AddrInfo sourceInfo(long pc) {
    Pair<String, Long> info = moduleList.findNameAndOffset(pc);
    if (info == null)
      return new AddrInfo(pc, "UNK", "UNK", -1);
    StringBuilder cmd = new StringBuilder(100);
    cmd.append("addr2line -p -f -e ").append(info.key).append(' ')
        .append('0').append('x').append(Long.toHexString(info.value));
    LongArrayList _l = new LongArrayList(1);
    _l.add(pc);
    ArrayList<AddrInfo> strs = exe(cmd.toString(), _l);
    return strs.get(0);
//    LongArrayList ls = new LongArrayList();
//    ls.add(pc);
//    return sourceInfo(ls).get(pc);
  }

  
  public AddrInfo getAddrInfoFromPC(long realPC)
  {
	  AddrInfo ai = null;
	  
      Pair<String, Long> info = moduleList.findNameAndOffset(realPC);
      if (info!= null)
      {
      String moduleName = info.key;
      long offsetPC = info.value;
      StringBuilder cmd = new StringBuilder();
      cmd.append(ADDR2LINE).append(moduleName).append(' ');
      cmd.append('0').append('x').append(Long.toHexString(offsetPC)).append(' ');
      
      try {
          Process process = Runtime.getRuntime().exec(cmd.toString());
          BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
          String line;
          if (null != (line = inputStream.readLine())) {
             ai = parseAddrInfo(offsetPC, line);
          }
          inputStream.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return ai;
      
  }
  /**
   * returned map is unordered
   * @param pcLs
   * @return
   */
  public Long2ObjectOpenHashMap<AddrInfo> sourceInfo(LongCollection pcLs) {

    HashMap<String, Long2LongLinkedOpenHashMap> modules = new HashMap<String, Long2LongLinkedOpenHashMap>(120);
    Long2LongOpenHashMap pcBackMap = new Long2LongOpenHashMap(pcLs.size());

    for (long realPC : pcLs) {
      Pair<String, Long> info = moduleList.findNameAndOffset(realPC);
      if (info == null)
      {
    	  	System.out.println("Failed to find symbol for pc: 0x"+Long.toHexString(realPC));
    	  	continue;
      }
      
      String moduleName = info.key;
      long offsetPC = info.value;
      Long2LongLinkedOpenHashMap pOffsetMap = modules.get(moduleName);
      if (pOffsetMap == null) {
        pOffsetMap = new Long2LongLinkedOpenHashMap(pcLs.size() / 5);
        modules.put(moduleName, pOffsetMap);
      }
      pOffsetMap.put(realPC, offsetPC);
      pcBackMap.put(offsetPC, realPC);
    }

    Long2ObjectOpenHashMap<AddrInfo> srcInfo = new Long2ObjectOpenHashMap<AddrInfo>(pcLs.size());

    for (String moduleName : modules.keySet()) {
      Long2LongLinkedOpenHashMap pOffsetMap = modules.get(moduleName);
      if (pOffsetMap == null || pOffsetMap.size() < 1)
        continue;
      StringBuilder cmd = new StringBuilder(100 + 30 * pOffsetMap.size());
      
      
      

      ArrayList<AddrInfo> srcLs;
      LongCollection offsetPCSeq = pOffsetMap.values();

      

      

      if(Configuration.symbolizer.equals("llvm"))
      {
    	  	//LLVM-SYMBOLIZER
    	  		cmd.append(ADDR2LINE).append(moduleName);

    	      try {
    	        srcLs = exeLLVM(cmd.toString(), offsetPCSeq);
    	      } catch (Exception e) {
    	        e.printStackTrace();
    	        continue;
    	      }
    	  		
      }
      else
      {
    	  	cmd.append(ADDR2LINE).append(moduleName).append(' ');
    	  
	      for (long offsetPC : offsetPCSeq) {
	        cmd.append('0').append('x').append(Long.toHexString(offsetPC)).append(' ');
	      }
	      try {
	        srcLs = exe(cmd.toString(), offsetPCSeq);
	      } catch (Exception e) {
	        e.printStackTrace();
	        continue;
	      }
      }


      int i = 0;
      LongIterator iter = pOffsetMap.keySet().iterator();
      while (iter.hasNext()) {
//        long offsetPC = iter.nextLong();
//        long realPC = pcBackMap.get(offsetPC);
//        srcInfo.put(realPC, srcLs.get(i));
        srcInfo.put(iter.nextLong(), srcLs.get(i));
        i++;
      }

    }
    return srcInfo;
  }

  public static long getPC(AbstractNode node) {
    if (node instanceof AllocNode) {
      return ((AllocNode) node).pc;
    } else if (node instanceof DeallocNode) {
      return ((DeallocNode) node).pc;
    } else if (node instanceof LockNode) {
      return ((LockNode) node).pc;
    } else if (node instanceof UnlockNode) {
      return ((UnlockNode) node).pc;
    } else if (node instanceof TStartNode) {
      return ((TStartNode) node).pc;
    } else if (node instanceof UnlockNode) {
      return ((TJoinNode) node).pc;
    } else if (node instanceof MemAccNode)
      return ((MemAccNode) node).pc;
    else
      return Long.MIN_VALUE;
  }

  private static String _Name(AbstractNode node) {
    try {
      return node.getClass().getDeclaredField("TYPE").get(null).toString();
    } catch (Exception e) {
      e.printStackTrace();
      return "UNK";
    }
  }
  public static String toString(AbstractNode node) {
    long addr = Long.MIN_VALUE;
    int len = Integer.MIN_VALUE;
    if (node instanceof AllocNode) {
      addr = ((AllocNode) node).addr;
      len = ((AllocNode)node).length;
    } else if (node instanceof DeallocNode) {
      addr = ((DeallocNode) node).addr;
      len = ((DeallocNode)node).length;
    }
    if (len > -10) {
      return String.format("#%d %s %d:%d", node.tid, _Name(node), addr, len);
    }

    if (node instanceof LockNode)
      addr = ((LockNode)node).lockID;
    else if (node instanceof UnlockNode)
      addr = ((UnlockNode)node).lockID;
    else if (node instanceof MemAccNode)
      addr = ((MemAccNode)node).getAddr();

    if (addr > 0)
      return String.format("#%d %s %d", node.tid, _Name(node), addr);
    else
      return String.format("#%d %s", node.tid, _Name(node));
  }

  public Long2ObjectOpenHashMap<AddrInfo> sourceInfo(ArrayList<AbstractNode> nodes) {
    LongArrayList pcLs = new LongArrayList(nodes.size());
    for (AbstractNode node : nodes) {
      long pc = getPC(node);
      if (pc >= 0)
        pcLs.add(pc);
    }// foreach node

    return sourceInfo(pcLs);
  }
  public void appendDescription(Appendable out, ArrayList<AbstractNode> nodes) {
    LongArrayList pcLs = new LongArrayList(nodes.size());
    for (AbstractNode node : nodes) {
      long pc = getPC(node);
      if (pc >= 0)
        pcLs.add(pc);
    }// foreach node

    Long2ObjectOpenHashMap<AddrInfo> srcInfo = sourceInfo(pcLs);

    try { // zip and append
      if (srcInfo.size() == 0) {
        out.append("Could not find source code line info\r\n");
        for (AbstractNode n : nodes) {
          out.append(toString(n)).append('\n');
        }
      } else {
        for (AbstractNode n : nodes) {
          short tid = n.tid;
          while (tid != 0) {
            out.append("   ");
            tid--;
          }
          out.append(toString(n));
          AddrInfo lineInfo = srcInfo.get(getPC(n));
          if (lineInfo != null)
            out.append("   ==>    ").append(lineInfo.toString());
          out.append('\n');
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ArrayList<AddrInfo> exeLLVM(String cmd, LongCollection offsetPCSeq)
  {
	  try {
	  Process process = Runtime.getRuntime().exec(cmd);
	  OutputStream stdin = process.getOutputStream();
	  InputStream stdout = process.getInputStream();
	  BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
	  BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
	  ArrayList<AddrInfo> list = new ArrayList<AddrInfo>();
      
      for (long offsetPC : offsetPCSeq) 
      {
		  writer.write("0x"+Long.toHexString(offsetPC)+"\n");
		  writer.flush();
      }
	  
	 
	  LongIterator iter = offsetPCSeq.iterator();
      while (iter.hasNext())
    	  { 
    	  		String line = reader.readLine();
	    	  if(!line.isEmpty())
	    	  {
	    		  AddrInfo ai = parseAddrInfo(iter.nextLong(), line);
	    	      list.add(ai);
	    	  }
    	  
      }
      
      writer.close();
      reader.close();
      
      return list;
	  } catch (IOException e) {
	      throw new RuntimeException(e);
	    }
  }
  public static ArrayList<AddrInfo> exe(String cmd, LongCollection addrLs) {
    ArrayList<AddrInfo> list = new ArrayList<AddrInfo>(addrLs.size());
    String line;
    LongIterator iter = addrLs.iterator();
    try {
      Process process = Runtime.getRuntime().exec(cmd);
      BufferedReader inputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
      while (null != (line = inputStream.readLine())) {
        AddrInfo ai = parseAddrInfo(iter.nextLong(), line);
        list.add(ai);
      }
      inputStream.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (list.size() != addrLs.size()) {
      throw new RuntimeException("Failed to addr2line: given addr: " + addrLs.size() + "  mapped: " + list.size() + "  cmd: " + cmd);
    }
    return list;
  }


//  public static void main(String[] args) {
//    String s1 = "__insertion_sort_3<std::__1::__less<double, double> &, double *> at ./out/ufo/../../buildtools/third_party/libc++/trunk/include/algorithm:3734 (discriminator 3)";
//    String s2 = "Layer at ./out/ufo/../../cc/layers/layer.cc:99";
//    String s21 = "Layer at ./out/layer.cc:?";
//    String s3 = "?? ??:0";
//    String s4 = "at< at, at > at:9";
//    String s5 = "at< at, at > at a/b at /c.h:9";
//
//    System.out.println(parseAddrInfo(-1, s21));
//  }

  public static AddrInfo parseAddrInfo(long addr, String line) {

    int idx1 = line.indexOf(" at ");
    if (idx1 < 1) {
      if (line.startsWith("?"))
        return new AddrInfo(addr, "?", "?", -1);
      else return new AddrInfo(addr, line, "?", -1);
    }
    int idx = idx1;
    int idx2 = line.indexOf(" at ", idx1 + 4);
    while (idx2 > 0) {
      /**
       *    at_1  /   at_2   : at_1 -> delimiter
       *    at_1  at_2   /   : go on
       *    at_1  at_2       : at_2 -> delimiter
       */
      int idxSp = line.indexOf('/', idx1);
      if (idx1 < idxSp && idxSp < idx2) {
        idx = idx1;
        break;
      } else if (idx2 < idxSp) {
        idx1 = idx2;
        idx2 = line.indexOf(" at ", idx2 + 4);
        if (idx2 < 0) {
          idx = idx1;
          break;
        }
      } else {
//        return new AddrInfo(addr, line, "", -1);
        // let's consider "at" as part of the function name
        idx = idx2;
        break;
      }
    }
    String fn = line.substring(0, idx);

    String strR = line.substring(idx + 4);
    int ln;
    if (strR.startsWith("?")) {// no file? no line
      return new AddrInfo(addr, "?", fn, -1);
    }

    idx1 = strR.lastIndexOf(':');
    if (idx < 0) {
      return new AddrInfo(addr, shorten(strR), fn, -1);
    } else {
      if (strR.startsWith("?", idx1 + 1))
        ln = -1;
      else {
        //remove discriminator
        idx2 = strR.indexOf(' ', idx1 + 1);
        if (idx2 < 0)
          idx2 = strR.length();
        try {
          ln = Integer.parseInt(strR.substring(idx1 + 1, idx2));
        } catch (Exception e) {
          ln = -1;
          System.err.println(strR+ "  number parsing " + e);
        }
      }
    }
    String file = shorten(strR.substring(0, idx1));
    return new AddrInfo(addr, file, fn, ln);
  }

  public static String shorten(String lineInfo) {
    if (lineInfo.startsWith("?"))
      return lineInfo;
    if (lineInfo.length() < TRIM_LEN)
      return lineInfo;

    StringBuilder sb = new StringBuilder(lineInfo.length());
    String[] parts = lineInfo.split("/");
    if (parts.length > 1) {
      for (int i = parts.length - 1; i >= 0; i--) {
        sb.insert(0, '/').insert(0, parts[i]); // pre append
        if (sb.length() > TRIM_LEN + 10)
          break;
      }
      sb.setCharAt(sb.length() - 1, ' ');
      return sb.toString();
    } else // one big line
      return lineInfo;
  }

}
