/*******************************************************************************
 * Copyright (c) 2013 University of Illinois
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
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
package trace;

public class TJoinNode extends ISyncNode {

	public static NodeType TYPE = NodeType.JOIN;

	public final short tid_join;
	public final int eTime;
	public final long pc;

	public long getAddr() {
		return tid_join;
	}

	public TJoinNode(long index, short tid, short addr, int t, long pc) {
		super(index,tid);
		tid_join = addr;
		eTime = t;
		this.pc = pc;
	}

	public String toString() {
		return gid + " #" + tid + "   pc: 0x" + Long.toHexString(pc)  + " Join   <- " + tid_join;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		TJoinNode joinNode = (TJoinNode) o;

		if (tid_join != joinNode.tid_join) return false;
		return eTime == joinNode.eTime && pc == joinNode.pc;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (int) tid_join;
		result = 31 * result + eTime;
		result = 31 * result + (int) (pc ^ (pc >>> 32));
		return result;
	}
}
