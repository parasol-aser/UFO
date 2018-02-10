package aser.ufo;

import aser.ufo.misc.*;
import aser.ufo.trace.EventLoader;
import aser.ufo.trace.Indexer;
import com.google.common.collect.Lists;
import config.Configuration;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trace.AbstractNode;
import trace.DeallocNode;
import trace.MemAccNode;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * one session for one execution
 */
public class Session {

  protected static final Logger LOG = LoggerFactory.getLogger(Session.class);

  // load trace and build index, do not construct or solve constraints
  // for test
  private static final boolean LOAD_ONLY = false;

  public final Configuration config;
  public final EventLoader traceLoader;
  public final SimpleSolver solver;
  //  public final UfoSolver solver2;
  public Addr2line addr2line;
  public final ExecutorService exe;
  protected int windowSize;

  PrintWriter writerD;
  PrintWriter writerB;
  int sessionID;
  int uafID;

  public Session(Configuration c) throws Exception {
    config = c;
    exe = Executors.newFixedThreadPool(UFO.PAR_LEVEL);
    traceLoader = new EventLoader(exe, config.traceDir);
//    solver = new JUfoSolver(config);
    solver = new SimpleSolver(config);
  }

  public void init() {

    addr2line = new Addr2line(traceLoader.getModuleList());
    windowSize = (int) config.window_size;
    if (windowSize < 10) {
      windowSize = (int) (UFO.MAX_MEM_SIZE * 0.9 / UFO.AVG_EVENT / traceLoader.fileInfoMap.size()
          // half mem for events, half for z3
          / 0.7);
      LOG.info("Suggested window size {}", windowSize);
    } 
//    else {
//      LOG.info("window size {}", windowSize);
//    }
    traceLoader.init(windowSize);

    try {
      writerB = new PrintWriter(new FileWriter("uaf_schedules.txt", true));// {
      writerD = new PrintWriter(new FileWriter("uaf_list.txt", true)) {
        public PrintWriter append(CharSequence csq) {
          super.append(csq);
          writerB.append(csq);
          System.out.append(csq);
          return this;
        }

        public PrintWriter append(char c) {
          super.write(c);
          writerB.append(c);
          System.out.append(c);
          return this;
        }
      };
    } catch (IOException e) {
      LOG.error("could not create log file", e);
    }
  }

  public void start() {
    sessionID = 0;
    uafID = 0;

    while (traceLoader.hasNext()) {
      sessionID++;
      Indexer indexer = new Indexer();
      traceLoader.populateIndexer(indexer);

      int sac = indexer.metaInfo.sharedAddrs.size();
      if (sac < 1 && !LOAD_ONLY)
        continue;
      int cac = indexer.getTSAcc2Dealloc().size();
      if (cac < 1 && !LOAD_ONLY)
        continue;

//      List<Pair<DeallocNode, MemAccNode>> candidateUafLs = findCandidateUafLs2(indexer);
      List<Pair<DeallocNode, MemAccNode>> candidateUafLs = findCandidateUafLs(indexer);
      if (candidateUafLs.isEmpty() && !LOAD_ONLY)
        continue;

      if (LOAD_ONLY) {
        pause(4, sessionID + "#   sac " + sac  + "     all " + indexer.getAllNodeSeq().size());
        System.out.println(indexer.metaInfo);
        solver.reset();
        System.gc();
        continue;
      }

      prepareConstraints(indexer);

      LOG.info("Shared address: {} \t Conflicting access: {} \t candidateUafLs: {}", sac, cac, candidateUafLs.size());

      writerD.append("#" + sessionID + " Session")
          .append("   candidateUafLs: " + candidateUafLs.size()).append('\n');

      if (candidateUafLs.size() > UFO.PAR_LEVEL) {
        List<List<Pair<DeallocNode, MemAccNode>>> lss = Lists.partition(candidateUafLs, UFO.PAR_LEVEL);
        for (List<Pair<DeallocNode, MemAccNode>> ls : lss) {
          solveUaf(indexer, ls);
        }
      } else {
        solveUaf(indexer, candidateUafLs);
      }

      solver.reset(); // release constraints for this round
      writerD.append("\r\n");
    } // while

    exe.shutdown();
    try {
      writerD.close();
      exe.awaitTermination(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      LOG.error(" error finishing ", e);
    }
    exe.shutdownNow();
  }

  protected void prepareConstraints(Indexer indexer) {
    solver.setReachEngine(indexer.getReachEngine());

    solver.declareVariables(indexer.getAllNodeSeq());
    // start < tid_first
    solver.buildSyncConstr(indexer);
    // tid: a1 < a2 < a3
    solver.buildIntraThrConstr(indexer.getTSTid2sqeNodes());
    
//    indexer.prepareNorm(); // early release index, give memory to z3
  }


  protected void solveUaf(Indexer indexer, List<Pair<DeallocNode, MemAccNode>> candidateUafLs) {
//    filterKnownUaf(candidateUafLs);

    List<RawUaf> uafLs = solveUafConstr(candidateUafLs);
    if (uafLs.size() < 1)
      return;
    LOG.info("Solved UAF: {}", uafLs.size());
    if (uafLs.size() < 1)
      return;
    writerD.append("Solved UAF: " + uafLs.size()).append("\r\n\r\n");
//        uafLs = trimFP(uafLs, candidateUafLs);
    outputUafLs(uafLs, indexer);
  }

  protected static void pause(int sec, String msg) {
    try {
      System.out.println("Waiting...");
      System.out.println(msg);
      System.err.flush();
      Thread.sleep(sec * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
  private void filterKnownUaf(List<Pair<DeallocNode, MemAccNode>> candidateUafLs) {
    LongArrayList pcLs = new LongArrayList(candidateUafLs.size() * 2);
    for (Pair<DeallocNode, MemAccNode> p : candidateUafLs) {
      pcLs.push(p.key.pc);
      pcLs.push(p.value.pc);
    }
    Long2ObjectOpenHashMap<AddrInfo> srcInfo = addr2line.sourceInfo(pcLs);
    Iterator<Pair<DeallocNode, MemAccNode>> iter = candidateUafLs.iterator();
    while (iter.hasNext()) {
      Pair<DeallocNode, MemAccNode> p = iter.next();
      AddrInfo accAddr = srcInfo.get(p.value.pc);
      Pair<AddrInfo, AddrInfo> addrp = new Pair<AddrInfo, AddrInfo>(srcInfo.get(p.key.pc), accAddr);
      if (knownUAF.contains(addrp)) {
        iter.remove();
        System.out.println("filter known " + accAddr);
      }
    }
  }

  private HashSet<Pair<AddrInfo, AddrInfo>> knownUAF = new HashSet<Pair<AddrInfo, AddrInfo>>(250);

  public void outputUafLs(List<RawUaf> uafLs, Indexer indexer) {
    LOG.info("Use-After-Free bugs: {}", uafLs.size());
    for (RawUaf uaf : uafLs) {
//        addr2line.appendDescription(System.out, nodes);

      AddrInfo accAddr = addr2line.sourceInfo(uaf.accNode.pc);
      AddrInfo deAddr = addr2line.sourceInfo(uaf.deallocNode.pc);
      Pair<AddrInfo, AddrInfo> pair = new Pair<AddrInfo, AddrInfo>(deAddr, accAddr);
      if (knownUAF.contains(pair)) {
        //System.out.println("Skip known access violation at " + accAddr.toString() + "   ");
        continue;
      }
      knownUAF.add(pair);
      uafID++;
      if (uaf instanceof RawUaFCpx) {
        writerD.append("\r\n!!!!!!!!!1 Real UaF\r\n");
      }
      System.out.println("#" + uafID + "  UAF");
      System.out.print(Addr2line.toString(uaf.deallocNode));
      System.out.print(" => ");
      System.out.println(deAddr);
      System.out.print(Addr2line.toString(uaf.accNode));
      System.out.print(" => ");
      System.out.println(accAddr);
      System.out.println();

      writerD.append("#" + uafID + "  UAF").append("\r\n");
      writerD.append(Addr2line.toString(uaf.deallocNode)).append(" => ");
      writerD.append(deAddr.toString()).append("\r\n");
      writerD.append(Addr2line.toString(uaf.accNode)).append(" => ");
      writerD.append(accAddr.toString()).append("\r\n\r\n");

      writerD.append("\r\n------- free call stack  \r\n");
      writeCallStack(indexer, uaf.deallocNode);

      writerD.append("\r\n------- use call stack  \r\n");
      writeCallStack(indexer, uaf.accNode);


      writerB.append("\r\n--------------------  schedule " + uafID + " (" + config.appname + ") ----------------------------\r\n");
//      addr2line.appendDescription(writerD, nodes);

      ArrayList<AbstractNode> nodes = indexer.normalize1st(uaf);
      Long2ObjectOpenHashMap<AddrInfo> srcInfo = addr2line.sourceInfo(nodes);

      if (srcInfo.size() == 0) {
        writerB.append("Could not find source code line info\r\n");
        for (AbstractNode n : nodes) {
          writerB.append(Addr2line.toString(n)).append('\n');
        }
      } else {
        for (AbstractNode n : nodes) {
          if (n == uaf.accNode || n == uaf.deallocNode) {
            writerB.append("\r\n!!!!!!!!!!!!!!!!!!!");
          } else {
            short tid = n.tid;
            while (tid != 0) {
              writerD.append("   ");
              tid--;
            }
          }
          writerB.append(Addr2line.toString(n));
          AddrInfo lineInfo = srcInfo.get(Addr2line.getPC(n));
          if (lineInfo != null)
            writerB.append(" ==> ").append(lineInfo.toString());
          writerB.append('\n');
        }
      }

      writerB.append("--------------------------------------------------------------\r\n");
    }
  }

  public void writeCallStack(Indexer indexer, AbstractNode node) {
	    long thisPC = Addr2line.getPC(node);

    LongArrayList callStack = indexer.buildCallStack(node);
    if (callStack == null || callStack.size() < 1)
      {
    	//JEFF
    	//if we did not record func entry and exit
    	AddrInfo ai = addr2line.getAddrInfoFromPC(thisPC);
    	if(ai!=null)
    	{
    	writerD.append("  ");
        writerD.append(ai.toString()).append('\n');
    	}
        return;
      };
    if (thisPC > 0) {
      callStack.push(thisPC);
    }
    Long2ObjectOpenHashMap<AddrInfo> srcInfo = addr2line.sourceInfo(callStack);
    int pad = 0;
    LongListIterator iter =  callStack.listIterator(callStack.size());
    while (iter.hasPrevious()) {
      int i = 0;
      long pc = iter.previousLong();
      AddrInfo ai = srcInfo.get(pc);
      if (ai == null)
        continue;
      while (i++ != pad)
        writerD.append("  ");
      writerD.append(ai.toString()).append(" pc: 0x"+Long.toHexString(ai.addr)).append('\n');//JEFF
      pad++;
    }
  }

  public int getWindowSize() {
    return windowSize;
  }


  public static List<RawUaf> trimFP(List<RawUaf> uafs, List<Pair<DeallocNode, MemAccNode>> candidates) {
    TreeMap<Long, DeallocNode> tree = new TreeMap<Long, DeallocNode>();
    for (Pair<DeallocNode, MemAccNode> p : candidates) {
      tree.put(p.key.addr, p.key);
    }
    List<RawUaf> fLs = new ArrayList<RawUaf>(uafs.size());
    for (RawUaf uaf : uafs) {
      final long addr = uaf.accNode.getAddr();
      Map.Entry<Long, DeallocNode> e = tree.floorEntry(addr);
      if (e == null) {
        LOG.error("Unmapped addr {}", addr);
        continue;
      }
      DeallocNode deNode = e.getValue();
      if (deNode.addr + deNode.length <= addr) {
        LOG.error("overflow acc {}, allocation: {} -> {}", addr, deNode.addr, deNode.length);
        continue;
      }
      Map.Entry<Long, DeallocNode> e2 = tree.floorEntry(deNode.addr);
      if (e2 == null)
        continue;
      DeallocNode deNode2 = e.getValue();
      if (addr < deNode2.addr + deNode2.length) {
        LOG.info("Overlapped allocation {} -> {} vs {} -> {}, acc on {}",
            deNode2.addr, deNode2.length, deNode.addr, deNode.length, addr);
        continue;
      }
      fLs.add(uaf);
    }
    return fLs;
  }

  public List<Pair<DeallocNode, MemAccNode>> findCandidateUafLs(final Indexer index) {
    HashMap<MemAccNode, DeallocNode> acc2Delloc = index.getTSAcc2Dealloc();
    Set<Map.Entry<MemAccNode, DeallocNode>> acc2DellocSets = acc2Delloc.entrySet();
    CompletionService<Pair<DeallocNode, MemAccNode>> cexe = new ExecutorCompletionService<Pair<DeallocNode, MemAccNode>>(exe);
    for (Map.Entry<MemAccNode, DeallocNode> e : acc2DellocSets) {
      final MemAccNode accNode = e.getKey();
      final DeallocNode deNode = e.getValue();
      cexe.submit(new Callable<Pair<DeallocNode, MemAccNode>>() {
        public Pair<DeallocNode, MemAccNode> call() throws Exception {
          if (solver.canReach(accNode, deNode)
              || solver.canReach(deNode, accNode))
            return null;
          else
            return new Pair<DeallocNode, MemAccNode>(deNode, accNode);
        }
      });
    } // foreach

    int count = acc2DellocSets.size();
    final ArrayList<Pair<DeallocNode, MemAccNode>> causalConstrLs = new ArrayList<Pair<DeallocNode, MemAccNode>>(count);
    try {
      while (count-- > 0) {
        Future<Pair<DeallocNode, MemAccNode>> f = cexe.take(); //blocks if none available
        Pair<DeallocNode, MemAccNode> pkt = f.get();
        if (pkt != null)
          causalConstrLs.add(pkt);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return causalConstrLs;
  }

  public List<Pair<DeallocNode, MemAccNode>> findCandidateUafLs2(final Indexer index) {
    LOG.warn("Unsafe findCandidateUafLs2");
    HashMap<MemAccNode, DeallocNode> acc2Delloc = index.getTSAcc2Dealloc();

    HashMap<DeallocNode, HashSet<MemAccNode>> de2acc = new HashMap<DeallocNode, HashSet<MemAccNode>>(acc2Delloc.size());
    for (Map.Entry<MemAccNode, DeallocNode> e : acc2Delloc.entrySet()) {
      final MemAccNode accNode = e.getKey();
      final DeallocNode deNode = e.getValue();
      HashSet<MemAccNode> accSet = de2acc.get(deNode);
      if (accSet == null) {
        accSet = new HashSet<MemAccNode>(60);
        de2acc.put(deNode, accSet);
      }
      accSet.add(accNode);
    }

    int count = 0;
    CompletionService<Pair<DeallocNode, MemAccNode>> cexe = new ExecutorCompletionService<Pair<DeallocNode, MemAccNode>>(exe);
    for (Map.Entry<DeallocNode, HashSet<MemAccNode>> e : de2acc.entrySet()) {
      HashSet<MemAccNode> accSet = e.getValue();
      if (accSet == null || accSet.size() < 1)
        continue;

      final DeallocNode deNode = e.getKey();
      final MemAccNode accNode = accSet.iterator().next();

      cexe.submit(new Callable<Pair<DeallocNode, MemAccNode>>() {
        public Pair<DeallocNode, MemAccNode> call() throws Exception {
          if (solver.canReach(accNode, deNode)
              || solver.canReach(deNode, accNode))
            return null;
          else
            return new Pair<DeallocNode, MemAccNode>(deNode, accNode);
        }
      });
      count++;
    } // foreach
    if (count < 1)
      return Collections.emptyList();

    final ArrayList<Pair<DeallocNode, MemAccNode>> causalConstrLs = new ArrayList<Pair<DeallocNode, MemAccNode>>(count);
    try {
      while (count-- > 0) {
        Future<Pair<DeallocNode, MemAccNode>> f = cexe.take(); //blocks if none available
        Pair<DeallocNode, MemAccNode> pkt = f.get();
        if (pkt != null)
          causalConstrLs.add(pkt);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return causalConstrLs;
  }


  public List<RawUaf> solveUafConstr(List<Pair<DeallocNode, MemAccNode>> causalConstrLs) {

    CompletionService<RawUaf> cexe = new ExecutorCompletionService<RawUaf>(exe);
    for (final Pair<DeallocNode, MemAccNode> pair : causalConstrLs) {
      cexe.submit(new Callable<RawUaf>() {
        public RawUaf call() throws Exception {
        	LongArrayList bugSchedule = solver.searchUafSchedule(pair);
          if (bugSchedule != null)
            return new RawUaf(pair.value, pair.key, bugSchedule);
          else return null;
        }
      });
    }

    int count = causalConstrLs.size();
    ArrayList<RawUaf> ls = new ArrayList<RawUaf>(count);
    try {
      while (count-- > 0) {
        Future<RawUaf> f = cexe.take(); //blocks if none available
        RawUaf uaf = f.get();
        if (uaf != null)
          ls.add(uaf);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return ls;
  }
}
