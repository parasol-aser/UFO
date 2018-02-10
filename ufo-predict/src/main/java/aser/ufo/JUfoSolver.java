package aser.ufo;

import aser.ufo.misc.Pair;
import aser.ufo.trace.Indexer;
import config.Configuration;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import trace.*;
import z3.Z3Run;

import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jeffhuang
 *
 */
public class JUfoSolver implements UfoSolver {
  protected AtomicInteger taskId = new AtomicInteger(0);// constraint taskId

  protected Configuration config;

  protected ReachabilityEngine reachEngine;// TODO: do segmentation on this

  // constraints below
  protected String constrDeclare;
  protected String constrMHB;
  protected String constrSync;
  protected String constrCasual;

  public static final String CONS_SETLOGIC = "(set-logic QF_IDL)\n";// use integer difference logic
  public static final String CONS_GETMODEL =  "(check-sat)\n(get-model)\n(exit)";

  public JUfoSolver(Configuration config) {
    this.config = config;
  }

  @Override
  public IntArrayList searchUafSchedule(Pair<DeallocNode, MemAccNode> p) {
    MemAccNode accNode = p.value;
    DeallocNode deNode = p.key;
//    String casualConstraint = pkt.causalConstr;

    // if(gid1<gid2)
//     { if(reachEngine.canReach(gid1, gid2))
    // return false;
    // }
    // else
    // {
    // if(reachEngine.canReach(gid2, gid1))
    // return false;
    // }

// "x" + gid
    String varAcc = makeVariable(accNode.gid);
    String varDe = makeVariable(deNode.gid);

    String csb = CONS_SETLOGIC + constrDeclare + constrMHB + constrSync + constrCasual +
        "(assert (< " + varDe + " " + varAcc + " ))" +
        CONS_GETMODEL;

    Z3Run task = new Z3Run(config, taskId.getAndIncrement());
    return task.buildSchedule(csb);
  }

  /**
   * add program order constraints
   *
   * @param map
   */
  @Override
  public void buildIntraThrConstr(Short2ObjectOpenHashMap<ArrayList<AbstractNode>> map) {
    // create reachability engine
    reachEngine = new ReachabilityEngine();
    StringBuilder sb = new StringBuilder(UFO.INITSZ_L * 5);
    for (ArrayList<AbstractNode> nodes : map.values()) {
      int lastGID = nodes.get(0).gid;
      String lastVar = makeVariable(lastGID);
      for (int i = 1; i < nodes.size(); i++) {
        int thisGID = nodes.get(i).gid;
        String var = makeVariable(thisGID);
        sb.append("(assert(< ").append(lastVar).append(' ').append(var).append("))\n");
        // the order is added to reachability engine for quick testing
        reachEngine.addEdge(lastGID, thisGID);
        lastGID = thisGID;
        lastVar = var;
      }
    }
    constrMHB = sb.toString();
  }

  @Override
  public void buildCausalConstrOpt(Indexer index) {

    ArrayList<ReadNode> allReadNodes = index.getTSSeqRead();
    Long2ObjectOpenHashMap<ArrayList<WriteNode>> indexedWriteNodes = index.getTSAddr2SeqWrite();
    Long2LongOpenHashMap initValueMap = index.getTSInitWrites();

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
        if (wnode.value== rnode.value && !canReach(rnode, wnode)) {
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
            if (!writenodes_value_match.contains(wnode2) && !canReach(wnode2, wnode1)
                && !canReach(rnode, wnode2)) {
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
        }

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

      } else {
        // make sure it reads the initial write
        long rValue = rnode.value;
        long initValue = initValueMap.get(rnode.addr);

        if (initValue != -1 && rValue == initValue) {
          String var_r = makeVariable(rnode.gid);

          for (WriteNode wnode3 : writenodes) {
            if (wnode3.tid != rnode.tid && !canReach(rnode, wnode3)) {
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


  //  disable nLock engine
  @Override
  public void buildSyncConstr(Indexer index) {
    StringBuilder sb = new StringBuilder(UFO.INITSZ_S * 10);

    Long2ObjectOpenHashMap<ArrayList<ISyncNode>> syncNodesMap = index.get_syncNodesMap();
    Short2ObjectOpenHashMap<AbstractNode> firstNodes = index.getTidFirstNode();
    Short2ObjectOpenHashMap<AbstractNode> lastNodes = index.getTidLastNode();

    // thread first node - last node
    for (ArrayList<ISyncNode> nodes : syncNodesMap.values()) {
      ArrayList<LockPair> lockPairs = new ArrayList<LockPair>(UFO.INITSZ_S);

      Short2ObjectOpenHashMap<Stack<ISyncNode>> threadSyncStack =
          new Short2ObjectOpenHashMap<Stack<ISyncNode>>(UFO.INITSZ_S / 2);
      // during recording
      // should after wait, before notify
      // after nLock, before nUnlock

      for (ISyncNode node : nodes) {
        int thisGID = node.gid;
        String var = makeVariable(thisGID);

        if (node instanceof TStartNode) {
          short tid = ((TStartNode) node).tidKid;
          AbstractNode fnode = firstNodes.get(tid);
          if (fnode != null) {
            int fGID = fnode.gid;
            String fvar = makeVariable(fGID);

            // start-cBegin ordering
            sb.append("(assert (< ").append(var).append(' ').append(fvar).append(" ))\n");

            reachEngine.addEdge(thisGID, fGID);

          }
        } else if (node instanceof TJoinNode) {
          short tid = ((TJoinNode) node).tid_join;
          AbstractNode lnode = lastNodes.get(tid);
          if (lnode != null) {
            int lGID = lnode.gid;
            String lvar = makeVariable(lGID);

            // cEnd-join ordering
            sb.append("(assert (< ").append(lvar).append(' ').append(var).append(" ))\n");
            reachEngine.addEdge(lGID, thisGID);

          }

        } else if (node instanceof LockNode) {
          short tid = ((LockNode) node).tid;

          Stack<ISyncNode> stack = threadSyncStack.get(tid);
          if (stack == null) {
            stack = new Stack<ISyncNode>();
            threadSyncStack.put(tid, stack);
          }

          stack.push(node);
        } else if (node instanceof UnlockNode) {
          short tid = ((UnlockNode) node).tid;
          Stack<ISyncNode> stack = threadSyncStack.get(tid);

          // assert(stack.fsize()>0);//this is possible when segmented
          if (stack == null) {
            stack = new Stack<ISyncNode>();
            threadSyncStack.put(tid, stack);
          }

          // pair nLock/nUnlock nodes
          if (stack.isEmpty()) {
            LockPair lp = new LockPair(null, node);
            lockPairs.add(lp);
          } else if (stack.size() == 1) {
            LockPair lp = new LockPair(stack.pop(), node);
            lockPairs.add(lp);
          } else
            stack.pop();// handle reentrant nLock here

        }
      } // foreach

      // check threadSyncStack
      for (Stack<ISyncNode> stack : threadSyncStack.values()) {
        // handle reentrant nLock here, only pop the
        // first locking node
        if (stack.size() > 0) {
          ISyncNode node = stack.firstElement();
          LockPair lp = new LockPair(node, null);
          lockPairs.add(lp);
        }
      }

      // Now construct the nLock/nUnlock constraints
      appendLockConstrOpt(sb, lockPairs);
    } // foreach nLock
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
              && !canReach(curLP.nLock, lp.nLock)) {
            flexLockPairs.add(lp);
          }
        } else if (lp.nUnlock != null) {
          if (lp.nUnlock.tid != curLockTid
              && !canReach(curLP.nLock, lp.nUnlock)) {
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

  @Override
  public void declareVariables(ArrayList<AbstractNode> trace) {
    StringBuilder sb = new StringBuilder(trace.size() * 30);

    // CONS_ASSERT = "(assert (distinct ";
    for (AbstractNode node : trace) {
      int GID = node.gid;
      String var = makeVariable(GID);
      sb.append("(declare-const ").append(var).append(" Int)\n");
    }
    constrDeclare = sb.toString();
  }

  /**
   * @return true if node1 can reach node2 from the ordering relation
   */
  @Override
  public boolean canReach(AbstractNode node1, AbstractNode node2) {
    int gid1 = node1.gid;
    int gid2 = node2.gid;

    return reachEngine.canReach(gid1, gid2);

  }
  protected static String makeVariable(int GID) {
    return "x" + GID;
  }

  public void reset() {
    this.constrDeclare = null;
    this.constrMHB = null;
    this.constrSync = null;
    this.constrCasual = null;
    this.taskId.set(0);
    this.reachEngine = new ReachabilityEngine();
  }
}
