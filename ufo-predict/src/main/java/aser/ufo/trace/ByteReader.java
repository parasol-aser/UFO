package aser.ufo.trace;

import java.io.IOException;

public interface ByteReader {

  void init(FileInfo fileInfo) throws IOException;

  int read() throws IOException;
  void finish(FileInfo fileInfo) throws IOException;

}
