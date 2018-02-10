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

public class NotifyAllNode extends IWaitNotifyNode {

	public static NodeType TYPE = NodeType.NOTIFYALL;

	public final long condID;

	public final long pc;

	public NotifyAllNode(long index, short tid, long condID, long pc) {
		super(index,tid);
		this.condID = condID;
		this.pc = pc;
	}

	public long getAddr() {
		return condID;
	}

	public String toString() {
		return "gid: "+ gid +" idx: "+ index+  " #" + tid + " pc:0x" + Long.toHexString(pc) + " notifyall cond " + condID;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		NotifyAllNode lockNode = (NotifyAllNode) o;
		
		if (condID != lockNode.condID) return false;
		if (pc != lockNode.pc) return false;
		return gid==lockNode.gid;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31 * result + (int) (condID ^ (condID >>> 32));
		result = 31 * result + (int) (pc ^ (pc >>> 32));
//		result = 31 * result + (int) (idx ^ (idx >>> 32));
		return result;
	}
}
