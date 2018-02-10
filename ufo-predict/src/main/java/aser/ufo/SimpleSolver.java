package aser.ufo;

import aser.ufo.misc.Pair;
import aser.ufo.trace.AllocaPair;
import aser.ufo.trace.Indexer;
import config.Configuration;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import trace.*;
import z3.Z3FastRun;
import z3.Z3Run;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleSolver implements UfoSolver {

  public int ct_vals = 0;
  public IntArrayList ct_constr = new IntArrayList(200);

  protected AtomicInteger taskId = new AtomicInteger(0);// constraint taskId

  protected Configuration config;

  protected NewReachEngine reachEngine;

  // constraints below
  protected String constrDeclare;
  protected String constrMHB;
  protected String constrSync;
  protected String constrCasual="";

  public static final String CONS_SETLOGIC = "(set-logic QF_IDL)\n";// use integer difference logic
  public static final String CONS_CHECK_GETMODEL =  "(check-sat)\n(get-model)\n(exit)";
  public static final String CONS_CHECK =  "(check-sat)(exit)";

  public SimpleSolver(Configuration config) {
    this.config = config;
  }

  @Override
  public void declareVariables(ArrayList<AbstractNode> trace) {
    StringBuilder sb = new StringBuilder(trace.size() * 30);
    for (AbstractNode node : trace) {
      long GID = node.gid;
      String var = makeVariable(GID);
      sb.append("(declare-const ").append(var).append(" Int)\n");
      ct_vals++;
    }
    constrDeclare = sb.toString();
    reachEngine = new NewReachEngine();
  }

  @Override
  public void buildIntraThrConstr(Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map) {
    StringBuilder sb = new StringBuilder(UFO.INITSZ_L * 10);
    for (ArrayList<AbstractNode> nodes : map.values()) {
      // at least cBegin/cEnd
      if (nodes == null || nodes.size() < 3)
        continue;
      AbstractNode lastN = nodes.get(0);
      String lastVar = makeVariable(lastN.gid);
      for (int i = 1; i < nodes.size(); i++) {
        AbstractNode curN = nodes.get(i);
        String var = makeVariable(curN.gid);
        sb.append("(assert(< ").append(lastVar).append(' ').append(var).append("))\n");
        // no need to add edge for events from the same thread
        //reachEngine.addEdge(lastN, curN);
        lastVar = var;
      }
    }
    constrMHB = sb.toString();
  }


  public void setReachEngine(NewReachEngine reachEngine) {
    this.reachEngine = reachEngine;
  }

  @Override
  public void buildSyncConstr(Indexer index) {
    StringBuilder sb = new StringBuilder(UFO.INITSZ_S * 10);

    Short2ObjectOpenHashMap<AbstractNode> firstNodes = index.getTidFirstNode();
    Short2ObjectOpenHashMap<AbstractNode> lastNodes = index.getTidLastNode();

    ArrayList<TStartNode> thrStartNodeList = NewReachEngine.thrStartNodeList;
    for (TStartNode node : thrStartNodeList) {
      short tid = node.tidKid;
      AbstractNode fnode = firstNodes.get(tid);
      if (fnode != null) {
        sb.append("(assert (< ").append(makeVariable(node.gid)).append(' ').append(makeVariable(fnode.gid)).append(" ))\n");
      }
    }
    ArrayList<TJoinNode> joinNodeList = NewReachEngine.joinNodeList;
    for (TJoinNode node : joinNodeList) {
      short tid = node.tid_join;
      AbstractNode lnode = lastNodes.get(tid);
      if (lnode != null) {
        sb.append("(assert (< ").append(makeVariable(lnode.gid)).append(' ').append(makeVariable(node.gid)).append(" ))\n");
      }
    }


    Long2ObjectOpenHashMap<ArrayList<LockPair>> addr2LpLs = index.getAddr2LockPairLs();
    StringBuilder constr = new StringBuilder(256);

    for (Long2ObjectMap.Entry<ArrayList<LockPair>> e1 : addr2LpLs.long2ObjectEntrySet()) {
      long lockID = e1.getLongKey();
      ArrayList<LockPair> lpLs = e1.getValue();

      for (int i = 0; i < lpLs.size(); i++) {
        for (int j = 0; j < lpLs.size(); j++) {
          if (j == i)
            continue;

          LockPair p1 = lpLs.get(i);
          ISyncNode p1LK = p1.nLock;
          ISyncNode p1UN = p1.nUnlock;
          if (p1LK == null)
            continue;
          short p1LKTid = p1LK.tid;

          LockPair p2 = lpLs.get(j);
          ISyncNode p2LK = p2.nLock;
          ISyncNode p2UN = p2.nUnlock;

          if (p2LK == null
              || p2LK.tid == p1LKTid)
            continue;

          if (reachEngine.canReach(p1LK, p2LK)
              || reachEngine.canReach(p2LK, p1LK))
            continue;
          // parallel lock pairs

          constr.setLength(0);
          int count = 0;
          if (p1UN != null) {
            constr.append(" (< ").append(makeVariable(p1UN.gid)).append(' ')
                .append(makeVariable(p2LK.gid)).append(' ').append(')');
            count++;
          }
          if (p2UN != null) {
            constr.append(" (< ").append(makeVariable(p2UN.gid)).append(' ')
                .append(makeVariable(p1LK.gid)).append(' ').append(')');
            count++;
          }

          if (count == 1) {
            sb.append("(assert ").append(constr).append(')').append('\n');
          }
          if (count == 2) {
            sb.append("(assert (or").append(constr).append("))").append('\n');
          }

        } // for 2
      } // for 1

    } // for one lock

    constrSync = sb.toString();
  }


  protected void appendLockConstrOpt(StringBuilder sb, ArrayList<LockPair> lockPairs) {

    // obtain each thread's last lockpair
    Short2ObjectOpenHashMap<LockPair> lastLockPairMap = new Short2ObjectOpenHashMap<LockPair>(UFO.INITSZ_S / 2);

    for (int i = 0; i < lockPairs.size(); i++) {
      LockPair curLP = lockPairs.get(i);
      String varCurLock;
      String varCurUnlock = "";

      if (curLP.nLock == null)//
        continue;
      else
        varCurLock = makeVariable(curLP.nLock.gid);

      if (curLP.nUnlock != null)
        varCurUnlock = makeVariable(curLP.nUnlock.gid);

      short curLockTid = curLP.nLock.tid;
      LockPair lp1_pre = lastLockPairMap.get(curLockTid);

      ArrayList<LockPair> flexLockPairs = new ArrayList<LockPair>(UFO.INITSZ_S);

      // find all lps that are from a different thread, and have no
      // happens-after relation with curLP
      // could further optimize by consider nLock regions per thread
      for (LockPair lp : lockPairs) {
        if (lp.nLock != null) {
          if (lp.nLock.tid != curLockTid
              && !reachEngine.canReach(curLP.nLock, lp.nLock)) {
            flexLockPairs.add(lp);
          }
        } else if (lp.nUnlock != null) {
          if (lp.nUnlock.tid != curLockTid
              && !reachEngine.canReach(curLP.nLock, lp.nUnlock)) {
            flexLockPairs.add(lp);
          }
        }
      }// for

      if (flexLockPairs.size() > 0) {

        // for each nLock pair lp2 in flexLockPairs
        // it is either before curLP or after curLP
        for (LockPair lp2 : flexLockPairs) {
          if (lp2.nUnlock == null || lp2.nLock == null && lp1_pre != null)// impossible
            // to
            // match
            // lp2
            continue;

          String var_lp2_b = "";
          String var_lp2_a = "";

          var_lp2_b = makeVariable(lp2.nUnlock.gid);

          if (lp2.nLock != null)
            var_lp2_a = makeVariable(lp2.nLock.gid);

          String cons_b;

          // lp1_b==null, lp2_a=null
          if (curLP.nUnlock == null || lp2.nLock == null) {
            cons_b = "(< " + var_lp2_b + " " + varCurLock + ")";
            // the trace may not be well-formed due to segmentation
            if (curLP.nLock.gid < lp2.nUnlock.gid)
              cons_b = "";
          } else {
            cons_b = "(or (< " + var_lp2_b + " " + varCurLock + ") (< " + varCurUnlock + " " + var_lp2_a + "))";
          }
          if (!cons_b.isEmpty())
            sb.append("(assert ").append(cons_b).append(")\n");
        }
      }
      lastLockPairMap.put(curLP.nLock.tid, curLP);
    }
  }

  
  Indexer currentIndexer;
  public void setCurrentIndexer(Indexer indexer)
  {
	  this.currentIndexer = indexer;
  }
  
  
  @Override
  public void buildCausalConstrOpt(ArrayList<ReadNode> allReadNodes) {

    Long2ObjectOpenHashMap<ArrayList<WriteNode>> indexedWriteNodes = currentIndexer.getTSAddr2SeqWrite();
    Long2LongOpenHashMap initValueMap = currentIndexer.getTSInitWrites();

    StringBuilder csb = new StringBuilder(1024);

    // for every read node in the set
    // make sure it is matched with a write written the same value
    for (ReadNode rnode : allReadNodes) {
      // filter out itself --
      if (-1 == rnode.tid)
        continue;

      // get all write nodes on the address
      ArrayList<WriteNode> writenodes = indexedWriteNodes.get(rnode.addr);
      // no write to array field?
      // Yes, it could be: java.io.PrintStream out
      if (writenodes == null || writenodes.size() < 1)
        continue;

      WriteNode preNode = null;//

      // get all write nodes on the address & write the same value
      ArrayList<WriteNode> writenodes_value_match = new ArrayList<WriteNode>(64);
      for (WriteNode wnode : writenodes) {
        if (wnode.value== rnode.value && !reachEngine.canReach(rnode, wnode)) {
          if (wnode.tid != rnode.tid)
            writenodes_value_match.add(wnode);
          else {
            if (preNode == null || (preNode.gid < wnode.gid && wnode.gid < rnode.gid))
              preNode = wnode;
          }
        }
      }

      if (writenodes_value_match.size() > 0) {
        if (preNode != null)
          writenodes_value_match.add(preNode);

        // TODO: consider the case when preNode is not null

        String var_r = makeVariable(rnode.gid);

        String cons_a = "";
        String cons_a_end = "";

        String cons_b = "";
        String cons_b_end = "";

        // make sure all the nodes that x depends on read the same value

        for (int j = 0; j < writenodes_value_match.size(); j++) {
          WriteNode wnode1 = writenodes_value_match.get(j);
          String var_w1 = makeVariable(wnode1.gid);

          String cons_b_ = "(< " + var_w1 + " " + var_r + ")\n";

          String cons_c = "";
          String cons_c_end = "";
          String last_cons_d = null;
          for (WriteNode wnode2 : writenodes) {
            if (!writenodes_value_match.contains(wnode2) && !reachEngine.canReach(wnode2, wnode1)
                && !reachEngine.canReach(rnode, wnode2)) {
              String var_w2 = makeVariable(wnode2.gid);

              if (last_cons_d != null) {
                cons_c += "(and " + last_cons_d;
                cons_c_end += " )";
              }
              last_cons_d = "(or (< " + var_r + " " + var_w2 + ")"
                  + " (< " + var_w2 + " " + var_w1 + " ))\n";
            }
          }
          if (last_cons_d != null) {
            cons_c += last_cons_d;
          }
          cons_c = cons_c + cons_c_end;

          if (cons_c.length() > 0)
            cons_b_ = "(and " + cons_b_ + " " + cons_c + " )\n";

          if (j + 1 < writenodes_value_match.size()) {
            cons_b += "(or " + cons_b_;
            cons_b_end += " )";

            cons_a += "(and (< " + var_r + " " + var_w1 + " )\n";
            cons_a_end += " )";
          } else {
            cons_b += cons_b_;
            cons_a += "(< " + var_r + " " + var_w1 + " )\n";
          }
        }// for each val write

        cons_b += cons_b_end;

        long rValue = rnode.value;
        long initValue = initValueMap.get(rnode.addr);

        // it's possible that we don't have initial value for static
        // variable
        // so we allow init value to be zero or null? -- null is turned
        // into 0 by System.identityHashCode
        boolean allowMatchInit = true;
        if (initValue == -1) {
          for (WriteNode aWritenodes_value_match : writenodes_value_match) {
            if (aWritenodes_value_match.gid < rnode.gid) {
              allowMatchInit = false;
              break;
            }
          }
        }

        if (initValue == -1 && allowMatchInit || initValue != -1 && rValue == initValue) {
          if (cons_a.length() > 0) {
            cons_a += cons_a_end + "\n";
            csb.append("(assert (or ").append(cons_a).append(' ').append(cons_b).append(" ))\n\n");
          }
        } else {
          csb.append("(assert ").append(cons_b).append(")\n\n");
        }

      } else { // no same val writes
        // make sure it reads the initial write
        long rValue = rnode.value;
        long initValue = initValueMap.get(rnode.addr);

        if (initValue != -1 && rValue == initValue) {
          String var_r = makeVariable(rnode.gid);

          for (WriteNode wnode3 : writenodes) {
            if (wnode3.tid != rnode.tid && !reachEngine.canReach(rnode, wnode3)) {
              String var_w3 = makeVariable(wnode3.gid);

              String cons_e = "(< " + var_r + " " + var_w3 + " )";
              csb.append("(assert \n").append(cons_e).append(" )\n");
            }
          }

        }

      }
    }
    constrCasual = csb.toString();
  }


  public LongArrayList searchUafSchedule(Pair<DeallocNode, MemAccNode> p) {
	  
	  boolean doSolve = true;
	  
    MemAccNode accNode = p.value;
    DeallocNode deNode = p.key;

    //only make sure those reads that
    //accNode and deNode depend on are consistent
    
    ArrayList<ReadNode> allReadNodes = new ArrayList<ReadNode>();
    
   currentIndexer.getTSDependentSeqRead(allReadNodes, accNode);
    currentIndexer.getTSDependentSeqRead(allReadNodes, deNode);

    buildCausalConstrOpt(allReadNodes);

    
    String varAcc = makeVariable(accNode.gid);
    String varDe = makeVariable(deNode.gid);

    String csb = CONS_SETLOGIC + constrDeclare + constrMHB + constrSync + constrCasual +
        "(assert (< " + varDe + " " + varAcc + " ))" +
        CONS_CHECK_GETMODEL;

    synchronized (ct_constr) {
      ct_constr.push(UFO.countMatches(csb, "assert"));
    }

    if (!doSolve)
      return null;

    Z3Run task = new Z3Run(config, taskId.getAndIncrement());
    LongArrayList ls =  task.buildSchedule(csb);
    return ls;
  }


  public boolean mustUaF(MemAccNode accNode, HashSet<AllocaPair> pairs) {
    return mustUaF(true, accNode, pairs);
  }

  public boolean mustUaF(boolean doSolve, MemAccNode accNode, HashSet<AllocaPair> pairs) {

    String varAcc = makeVariable(accNode.gid);
    StringBuilder sb = new StringBuilder(pairs.size() * 20);
    sb.append(CONS_SETLOGIC).append(constrDeclare).append(constrMHB).append(constrSync).append(constrCasual)
      .append("(assert (or ");
    for (AllocaPair pair : pairs) {
      String varAlloc = makeVariable(pair.allocNode.gid);
      String varDe = makeVariable(pair.deallocNode.gid);
      sb.append("(and (< ").append(varAlloc).append(" ").append(varAcc)
          .append(") (< ").append(varAcc).append(" ").append(varDe).append("))");
    }
    sb.append("))")
        .append(CONS_CHECK);
    String str = sb.toString();
    synchronized (ct_constr) {
      ct_constr.push(UFO.countMatches(str, "assert"));
    }

    if (!doSolve)
      return false;

    Z3FastRun task = new Z3FastRun(config, taskId.getAndIncrement());
    return task.feasible(str);
  }


  @Override
  public boolean canReach(AbstractNode node1, AbstractNode node2) {
    return reachEngine.canReach(node1, node2);
  }

  protected static String makeVariable(long GID) {
    return "x" + GID;
  }

  public void reset() {
    this.constrDeclare = null;
    this.constrMHB = null;
    this.constrSync = null;
    this.constrCasual = null;
    this.taskId.set(0);
    this.reachEngine = null;
  }
}
