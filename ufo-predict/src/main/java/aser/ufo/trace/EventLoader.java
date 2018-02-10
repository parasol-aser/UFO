package aser.ufo.trace;

import aser.ufo.NewReachEngine;
import aser.ufo.UFO;
import aser.ufo.VectorClock;
import aser.ufo.misc.CModuleSection;
import aser.ufo.misc.CModuleList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class EventLoader {

  private static final Logger LOG = LoggerFactory.getLogger(EventLoader.class);

  public final ExecutorService exe;
  public final String folderName;
  public final Short2ObjectRBTreeMap<FileInfo> fileInfoMap;
  private final ShortOpenHashSet aliveTids = new ShortOpenHashSet(80);
  private int totalNumOfThreads;
  private ShortOpenHashSet allThreads = new ShortOpenHashSet(80);;

  // total size
  private int windowSize;
  //  public static final short MAIN_TID = 0;
  public final CModuleList moduleList = new CModuleList();

  public EventLoader(ExecutorService exe, String folderName) {
    this.exe = exe;
    this.folderName = folderName;
    fileInfoMap = new Short2ObjectRBTreeMap<FileInfo>();
  }

  public void init(int wsz) {

    windowSize = wsz;

    final File dir = new File(folderName);
    if (!dir.isDirectory()) {
      throw new RuntimeException("Could not find folder " + folderName);
    }
    File[] traces = dir.listFiles(new FileFilter() {
      public boolean accept(File f) {
        if (!f.canRead()) {
          throw new IllegalArgumentException("Could not read file " + f + "  at " + dir);
        }
        return f.isFile();
      }
    });
    if (traces == null)
      throw new IllegalArgumentException("No trace file found at " + folderName);
//    int flimt = 2;
    
    
    for (File f : traces) {
      if (UFO.MODULE_TXT.equals(f.getName())) {
        loadCModuleInfo(f);
        continue;
      } else if (UFO.STAT_TXT.equals(f.getName())){
        continue;
      } else if (UFO.STAT_CSV.equals(f.getName())){
        continue;
      }
      short tid = Short.parseShort(f.getName());
      long sz = f.length();
      FileInfo fi = new FileInfo(f, sz, tid);
      fileInfoMap.put(tid, fi);
//      if (flimt-- <= 0)
//        break;
      
      allThreads.add(tid);
    }
    totalNumOfThreads = allThreads.size();

    if (moduleList.size() < 1)
      LOG.error("Empty module info " + moduleList);


    short mainTid = fileInfoMap.firstShortKey();
    aliveTids.add(mainTid);
    fileInfoMap.get(mainTid).enabled = true;
  }

//  protected AtomicInteger gidGen = new AtomicInteger(1);

  public int validCount() {
    int enabled = 0;
    for (FileInfo fi : fileInfoMap.values()) {
      if (fi.enabled)
        enabled++;
    }
    return enabled;
  }

  public boolean hasNext() {
    return validCount() > 0;
  }

//  public void loadCModuleInfo(File f) {
//    BufferedReader reader = null;
//    try {
//      reader = new BufferedReader(new FileReader(f));
//      String line;
//      while (null != (line = reader.readLine())) {
//        String[] info = line.split(" ");
//        if (info.length != 4) {
//          throw new IllegalArgumentException("module info format error " + f);
//        }
//        long base = Long.parseLong(info[1].trim(), 16);
//        long begin = Long.parseLong(info[2].trim(), 16);
//        long end = Long.parseLong(info[3].trim(), 16);
//        CModuleSection m = new CModuleSection(info[0].trim(), base, begin, end);
//        this.moduleList.add(m);
//      }
//      reader.close();
//    } catch (Exception e) {
//      throw new RuntimeException(e);
//    } finally {
//      if (reader != null) {
//        try {
//          reader.close();
//        } catch (IOException e) {
//          e.printStackTrace();
//        }
//      }
//    }
//  }

  void loadCModuleInfo(File f ) {
    BufferedReader reader = null;
    boolean mainExeLoaded = false;
    try {
      reader = new BufferedReader(new FileReader(f));
      String line = reader.readLine();
      while (null != line) {
        String[] infoLs = line.split("\\|");
//        if (infoLs.length < 8)
//          break;
        //if ((infoLs.length - 2 ) % 3 != 0) throw new IllegalArgumentException("module info format error " + f);
        int idx = 0;
        String moduleFullName = infoLs[idx++]; // name with path
        
        try {
        long base = java.lang.Long.parseLong(infoLs[idx++].trim(), 16);
        //JEFF
        long max = java.lang.Long.parseLong(infoLs[idx++].trim(), 16);
        
        if(max>base)
        {
	        CModuleSection m = new CModuleSection(moduleFullName, base, max);
	        moduleList.add(m);
        }
        }catch(Exception e)
        {
        		e.printStackTrace();
        }

//        while (idx < infoLs.length) {
//          boolean _isExe = Integer.parseInt(infoLs[idx++].trim()) > 0;
//          long _begin = java.lang.Long.parseLong(infoLs[idx++].trim(), 16);
//          long _end = java.lang.Long.parseLong(infoLs[idx++].trim(), 16);
//
//          CModuleSection m = new CModuleSection(moduleFullName, base, _isExe, _begin, _end);
//
//          if (!mainExeLoaded) {
//            moduleList.addMainExe(m);
//            mainExeLoaded = true;
//          }
//          moduleList.add(m);
//        }
        line = reader.readLine();
      }
      reader.close();
    } catch(Exception e){
      throw new RuntimeException(e);
    } finally {
      if (reader != null) try {
        reader.close();
      } catch (Throwable ex) {
        ex.printStackTrace();
      }
    }
  }

  private ShortOpenHashSet addTLSeq(long limit, Indexer mIdx, ShortOpenHashSet tids) {
    ShortOpenHashSet newTids = new ShortOpenHashSet(3);
    for (short tid : tids) {
      FileInfo fi = fileInfoMap.get(tid);
      if (fi == null || !fi.enabled)
        continue;
//      TLEventSeq seq = new LoadingTask(fi, limit, gidGen).load();
       LoadingTask2 loader = fi.getEventLoader(limit);
       
      TLEventSeq seq =loader.load();
      TLHeader h = seq.header;
      if (h != null) {
//        LOG.debug(">>> Loading:{}  version:{}  time:{}",
//            h.tid, h.version, h.timeStart);
      }
      //LOG.debug(seq.stat.toString());
      if (seq.events != null && seq.events.size() > 0) {
        mIdx.addTidSeq(seq.tid, seq.events);
        //LOG.debug("{} events loaded from thread {}", seq.events.size(), seq.tid);
      } else {
        fileInfoMap.remove(seq.tid);
      }
      if (seq.newTids.size() < 1)
        continue;

      for (short ntid : seq.newTids) {
        FileInfo nfi = fileInfoMap.get(ntid);
        if (nfi != null) {
          nfi.enabled = true;
        }
      }

      newTids.addAll(seq.newTids);

    } // for
    return newTids;
  }

  public void populateIndexer(Indexer mIdx) {
    int tidCount = 0;
    final int ptLimit = windowSize;// / (aliveTids.size() * 6 + fileInfoMap.size() * 4 ) * 10;
//    System.out.println(aliveTids.size() + "   " + fileInfoMap.size());

    // load all known threads,
    // if a thread is dead (not alive but still in the aliveTids), its file offset will be the cEnd and thus it won't be loaded
    //LOG.debug(">>>>>>>>>>> alive:{},  ptLimit {}", aliveTids.size(),  ptLimit);
    ShortOpenHashSet newTids = addTLSeq(ptLimit, mIdx, aliveTids);
    tidCount += aliveTids.size();

    newTids.removeAll(aliveTids);
    aliveTids.addAll(newTids);

    // if found new, load
    while (newTids.size() > 0) {
      //LOG.debug(">>>>>>>>>>> trace limit {}, alive{}", windowSize, aliveTids.size());
      ShortOpenHashSet nextTids = addTLSeq(ptLimit, mIdx, newTids);
      tidCount += newTids.size();

      nextTids.removeAll(aliveTids);
      aliveTids.addAll(nextTids);
      newTids = nextTids;
    } // while

//    // disable finished trace file
//    for (FileInfo fi : fileInfoMap.values()) {
//      if (fi.fileOffset >= fi.fsize - 7) {
//        fi.enabled = false;
//      }
//    }

    // remove EOF trace file
    Iterator<Map.Entry<Short, FileInfo>> iter = fileInfoMap.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<Short, FileInfo> e = iter.next();
      FileInfo info = e.getValue();
      if (info.fileOffset >= info.fsize - 5) {
        iter.remove();
        //aliveTids.remove(info.tid);//JEFF 
      }
    }

    mIdx.metaInfo.tidCount = tidCount;
    //JEFF
    //1. set the number of threads
    //2. assign index to each thread
    
    
    
//    if (tidCount < 2) {
//      mIdx.metaInfo.sharedAddrs = EMPTY_LSET;
//    }
//    else
      mIdx.postProcess();
//    if (mIdx.metaInfo.tidRawNodesCounts.size() != tidCount)
//      throw new RuntimeException(mIdx.metaInfo.tidRawNodesCounts.size() + "    " + tidCount);

    //LOG.debug("Total events in indexer ({} threads): {}", tidCount, mIdx.getAllNodeSeq().size());
  }

  public int getWindowSize() {
    return windowSize;
  }

  private static final LongOpenHashSet EMPTY_LSET = new LongOpenHashSet();



//  static long gidGen = 0; // a global taskId (gid) representing their order in the trace,

  public CModuleList getModuleList() {
    return moduleList;
  }

public void preprocessWaitNotify() {
	
	
	long time1 = System.currentTimeMillis();
	
	ShortOpenHashSet aliveTids_ = new ShortOpenHashSet(80);
		  for (short tid : allThreads) {
		      FileInfo fi = fileInfoMap.get(tid);
		      if (fi!=null)
		      {
		    	  	TLEventSeq seq = new NewLoadingTask(fi).load();
		    	  	
		    	  	
		    	  	LOG.info("tid: "+ tid + " Total Events:  "+ seq.numOfEvents);
		    	  	if(seq.numOfEvents>0)
		    	  		aliveTids_.add(tid);
		    	  	
		    	  	//reset file info so that it can be reused again
		    	  	fi.fileOffset=0;
		    	  	fi.lastFileOffset=0;
		      }
		  }  
		  
		  //LOG.info("Total Events:  "+ TLEventSeq.stat.c_total);
  		  //LOG.info("Num of ISYNC nodes {}", TLEventSeq.stat.c_isync);

  		long time2 = System.currentTimeMillis();

  	    System.out.println("Total loading time:  "+ (time2-time1)+"ms");
  	    
  	    NewReachEngine.setThreadIdsVectorClock(aliveTids_.toShortArray());
		NewReachEngine.postprocessing();
	  	   
		System.out.println("Total reachability analysis time:  "+ (System.currentTimeMillis()-time2)+"ms");

}
}
