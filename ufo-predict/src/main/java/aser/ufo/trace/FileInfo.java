package aser.ufo.trace;

import java.io.File;
import java.io.Serializable;

/**
 * thread local trace file info, updated at each iteration
 *
 */
public class FileInfo implements Serializable {

  public final File file;
  public final long fsize;
  public final short tid;
  public long fileOffset;
  public long lastFileOffset;
  public boolean enabled;
//  public int bufferOffset;// unzipped


  public FileInfo(File file, long size, short tid) {
    this.file = file;
    this.fsize = size;
    this.tid = tid;
    fileOffset = 0;
    lastFileOffset = 0;
    enabled = false;
//    bufferOffset = 0;
  }

  @Override
  public String toString() {
    return "FileInfo{" +
        "file=" + file +
        ", fsize=" + fsize +
        ", tid=" + tid +
        ", fileOffset=" + fileOffset +
        '}';
  }

  public LoadingTask2  loader;
public LoadingTask2 getEventLoader(long limit) {
	if(loader==null)
		loader = new LoadingTask2(this,limit);
	return loader;
}
}
