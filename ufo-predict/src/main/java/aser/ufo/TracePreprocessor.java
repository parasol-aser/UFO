package aser.ufo;

import aser.ufo.trace.Bytes;
import org.xerial.snappy.Snappy;

import javax.net.ssl.StandardConstants;
import java.io.*;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatFlagsException;
import java.util.concurrent.*;

public class TracePreprocessor {

  public static final long VERSION = 20161001;
//  private static final Logger LOG = LoggerFactory.getLogger(TracePreprocessor.class);

  public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
    if (args == null || args.length != 2) {
      System.out.println("<Usage> $in_dir $out_dir");
      return;
    }
    File dirZipped = new File(args[0]);
    if (!dirZipped.isDirectory()) {
      System.out.println("\"" + args[0] + "\" Not a directory!");
      return;
    }
    File dirUnzipped = new File(args[1]);
    if (dirUnzipped.isFile()) {
      System.out.println("\"" + dirUnzipped + "\" already exists a file named \"" + args[1] + "\"");
    }

    if (!dirUnzipped.isDirectory())
      System.out.println("Creating: \"" + args[1] + "\": " + dirUnzipped.mkdir());

    File[] unzipFs = dirUnzipped.listFiles();
    if (unzipFs != null && unzipFs.length > 0) {
      System.out.println("\"" + args[1] + "\" is not empty, all existing file will be deleted!!!");
      for (File f : unzipFs) {
        f.delete();
      }
    }
    File[] fTraces = dirZipped.listFiles();
    if (fTraces == null || fTraces.length < 1) {
      System.out.println("Not enough trace file: " + Arrays.toString(fTraces));
      return;
    }
    long totalSz = 0;
    for (final File fz : fTraces) {
      if (fz.isFile())
        totalSz += fz.length();
    }
    ExecutorService exe = Executors.newFixedThreadPool(Math.min(fTraces.length, UFO.PAR_LEVEL));
    CompletionService<Long> cexe = new ExecutorCompletionService<Long>(exe);
    System.out.println("Start to uncompress " + fTraces.length + " trace files from " + args[0]);
    final ArrayList<Exception> exLs = new ArrayList<Exception>(8);
    long totalIn = 0;
    int count = 0;
    for (final File fz : fTraces) {
      if (!fz.isFile()) {
        System.out.println(fz + " is not a file, skip");
        continue;
      }
      String fname = fz.getName();
      if (UFO.MODULE_TXT.equals(fname)
          || UFO.STAT_TXT.equals(fname)
          || UFO.STAT_CSV.equals(fname)) {
        Files.copy(fz.toPath(), new File(dirUnzipped.getPath() + File.separatorChar + UFO.MODULE_TXT).toPath(), StandardCopyOption.REPLACE_EXISTING);
        continue;
      }
      totalIn += fz.length();
      final File fout = new File(dirUnzipped.getPath() + File.separatorChar + fz.getName());
      cexe.submit(new Callable<Long>() {
        public Long call() throws Exception {
          try {
            unzipBlocks(fz, fout);
          } catch (Exception e) {
            exLs.add(e);
          }
          return fout.length();
        }
      });
      count++;
    }
    long totalOut = 0;
    while (count-- > 0) {
      Future<Long> f = cexe.take();
      Long flen = f.get();
      if (flen != null && flen > 0) {
//        System.out.println(flen);
        totalOut += flen;
      }
    }
    if (!exLs.isEmpty()) {
      for (Exception e : exLs)
        e.printStackTrace();
      dirUnzipped.delete();
    }

    System.out.printf("Done: %d (%f MB) -> %d (%f MB). Rate: %f%%\r\n",
        totalIn, ((float)totalIn / 1024 / 1024),
        totalOut,  ((float)totalOut / 1024 / 1024),
        (100.0 * totalIn / totalOut));

    exe.shutdown();
    exe.awaitTermination(5, TimeUnit.SECONDS);
    exe.shutdownNow();
//    File out = new File("sample_trace/test2/3");
//    out.createNewFile();
//    unzipBlocks(new File("sample_trace/test/3"), out);

  }

  public static void unzipBlocks(File fin, File fout) throws Exception {

    FileInputStream fsIn = null;
    OutputStream fsOut = null;
    try {
      fsIn = new FileInputStream(fin);
      fsOut = new BufferedOutputStream(new FileOutputStream(fout), 8388600);


      // verify header
      int typeIdx = fsIn.read();
      if (typeIdx != 13)
        throw new IllegalFormatFlagsException("[" + fin.getAbsolutePath()
            + "]  Wrong Type ID, should be 13, actual: " + typeIdx);

      long version = getLong64b_Move(fsIn);
      if (version != VERSION)
        throw new IllegalFormatFlagsException("Version " + version + " not supported");

      // copy header
      fsIn.getChannel().position(0);
      byte[] headBytes = new byte[23];
      int brd = fsIn.read(headBytes);
      if (brd != 23)
        throw new RuntimeException(
            "Could not read enough data," +
                " required 23  actual " + brd);
      fsOut.write(headBytes);


      fsIn.getChannel().position(23);

      byte[] rawBytes = null;
      byte[] unzippedBytes = null;

      int rawLen = getInt_Move(fsIn);

      BufferedInputStream bufIn = new BufferedInputStream(fsIn, 8388600);
      while (rawLen != -1) {
        if (rawBytes == null || rawBytes.length < rawLen)
          rawBytes = new byte[rawLen];

        brd = bufIn.read(rawBytes, 0, rawLen);
        if (brd < 0 || brd != rawLen) {
//          fout.delete();
          throw new IOException("[" + fin.getAbsolutePath()
              + "] Could not read enough data," +
                  " requested: " + rawLen + "  actual: " + brd);
        }
        int pUnzipLen = Snappy.uncompressedLength(rawBytes, 0, rawLen);
        if (unzippedBytes == null || unzippedBytes.length < pUnzipLen) {
          unzippedBytes = new byte[pUnzipLen];
        }

        int actualUnzippedLen = Snappy.uncompress(rawBytes, 0, rawLen, unzippedBytes, 0);

        fsOut.write(unzippedBytes, 0, actualUnzippedLen);

        rawLen = getInt_Move(bufIn);
      }
      fsOut.flush();
    } catch (Exception e) {
//      fout.delete();
      throw e;
    } finally {
      try {
        if (fsIn != null)
          fsIn.close();
        if (fsOut != null)
          fsOut.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static long getLong64b_Move(InputStream breader) throws IOException {
    byte b0 = (byte) breader.read();
    byte b1 = (byte) breader.read();
    byte b2 = (byte) breader.read();
    byte b3 = (byte) breader.read();
    byte b4 = (byte) breader.read();
    byte b5 = (byte) breader.read();
    byte b6 = (byte) breader.read();
    byte b7 = (byte) breader.read();
    return Bytes.longs._Ladd(b7, b6, b5, b4, b3, b2, b1, b0);
  }

  private static int getInt_Move(InputStream breader) throws IOException {
    byte b1;
    byte b2;
    byte b3;
    byte b4;
    int i = breader.read();
    if (i == -1)
      return -1;
    b1 = (byte) i;

    i = breader.read();
    if (i == -1)
      return -1;
    b2 = (byte) i;

    i = breader.read();
    if (i == -1)
      return -1;
    b3 = (byte) i;

    i = breader.read();
    if (i == -1)
      return -1;
    b4 = (byte) i;
    return Bytes.ints._Ladd(b4, b3, b2, b1);
  }

}
