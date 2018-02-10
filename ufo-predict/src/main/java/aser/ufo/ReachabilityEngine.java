/*******************************************************************************
 * Copyright (c) 2013 University of Illinois
 * <p/>
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <p/>
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * <p/>
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package aser.ufo;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;

/*
 * property: never call addEdge after canReach
 * TODO: must be optimized to handle big graph
 */
public class ReachabilityEngine {

  boolean isDone = false;
  private int counter = 0;
  //private boolean[][] reachmx;

  Int2IntOpenHashMap idMap = new Int2IntOpenHashMap(UFO.INITSZ_L * 2);

  private int M = 100000;//five Os
  LongOpenHashSet cachedNoReachSet = new LongOpenHashSet(UFO.INITSZ_L * 2);

  Int2ObjectOpenHashMap<IntOpenHashSet>
      edgeSetMap = new Int2ObjectOpenHashMap<IntOpenHashSet>(UFO.INITSZ_L * 2);

  public ReachabilityEngine() {
    idMap.defaultReturnValue(-1);
  }

  public synchronized void addEdge(int gid1, int gid2) {
    int i1 = getId(gid1);
    int i2 = getId(gid2);

    addInternalEdge(i1, i2);
  }

  private void addInternalEdge(int i1, int i2) {
    IntOpenHashSet s = edgeSetMap.get(i1);
    if (s == null) {
      s = new IntOpenHashSet(UFO.INITSZ_S);
      edgeSetMap.put(i1, s);
    }
    s.add(i2);
  }

  public boolean deleteEdge(int i1, int i2) {
    i1 = getId(i1);
    i2 = getId(i2);

    IntOpenHashSet s = edgeSetMap.get(i1);
    if (s == null) {
      s = new IntOpenHashSet(UFO.INITSZ_S);
      edgeSetMap.put(i1, s);
      return false;
    }
    if (s.contains(i2)) {
      s.remove(i2);
      return true;
    }
    return false;
  }

  private int getId(int id) {

    int ID = idMap.get(id);
    if (ID == -1) {
      ID = counter++;
      idMap.put(id, ID);//oh, forgot to do this
    }
    return ID;
  }

  public void allEdgeAdded() {
    if (!isDone) {
      //compute();
      isDone = true;
    }
  }

  private boolean hasEdge(int i1, int i2) {
    IntOpenHashSet s = edgeSetMap.get(i1);
    if (s == null) {
      s = new IntOpenHashSet(UFO.INITSZ_S);
      edgeSetMap.put(i1, s);
      return false;
    } else
      return s.contains(i2);
  }

  public boolean canReach(int i1, int i2) {
    try {
      //must have corresponding real taskId

      //what if idMap does not contain taskId?

      i1 = idMap.get(i1);
      i2 = idMap.get(i2);

      //return reachmx[i1][i2];
      long SIG = i1 * M + i2;
      if (cachedNoReachSet.contains(SIG))
        return false;
      else if (hasEdge(i1, i2))
        return true;
      else {
        //DFS - without cache
        ArrayDeque<Integer> stack = new ArrayDeque<Integer>();
        LongOpenHashSet visitedNodes = new LongOpenHashSet(UFO.INITSZ_S);
        stack.push(i1);

        while (!stack.isEmpty()) {

          int i1_ = stack.pop();
          visitedNodes.add(i1_);

          if (!hasEdge(i1, i1_))
            addInternalEdge(i1, i1_);

          if (i1_ == i2) {
            return true;
          } else {
            if (hasEdge(i1_, i2)) {
              addInternalEdge(i1, i2);
              return true;
            } else {
              for (int i1__ : edgeSetMap.get(i1_)) {
                //System.out.print("DEBUG: "+i1+" "+i1_+" "+ i1__+"\n");
                long sig = i1__ * M + i2;
                if (!visitedNodes.contains(i1__) && !cachedNoReachSet.contains(sig))
                  stack.push(i1__);
              }
            }
          }
        }

        cachedNoReachSet.add(SIG);
        return false;
      }

    } catch (Exception e) {
      return false;
    }
  }
}
