package aser.ufo.trace;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 *
 * wrap buffered reader for speed loading
 */
public class BufferedByteReader implements ByteReader{
  private long byteRead = 0;
  private BufferedInputStream bis;

  public void init(FileInfo fileInfo) throws IOException {

    FileInputStream fin = new FileInputStream(fileInfo.file);
    long s = fin.skip(fileInfo.fileOffset);
    if (s != fileInfo.fileOffset)
      throw new RuntimeException("wrong position, need to skip " + fileInfo.fileOffset + "  actual " + s);
    bis = new BufferedInputStream(fin);
  }

  public int read() throws IOException {
    int r = bis.read();
    if (r != -1)
      byteRead++;
    return r;
  }

  public void finish(FileInfo fileInfo) throws IOException {
    fileInfo.lastFileOffset = fileInfo.fileOffset;
    fileInfo.fileOffset += byteRead - 1;//fin.getChannel().position() - 1; // the bnext
    bis.close();
  }


  public long getBytesRead() {
    return byteRead;
  }
}
