package aser.ufo;

public class UFO {

  public static final int INITSZ_L = 5 * 1000; // approximately the node number
  public static final int INITSZ_S = 600; // nLock threads number
  public static final int INITSZ_SYNC = 100000; // nLock threads number

  public static final long MAX_MEM_SIZE = (long) Runtime.getRuntime().maxMemory();

  public static final int AVG_EVENT = 5000;

  public static final int PAR_LEVEL = Runtime.getRuntime().availableProcessors();
  public static final String MODULE_TXT = "_module_info.txt";
  public static final String STAT_TXT = "_statistics.txt";
  public static final String STAT_CSV = "_statistics.csv";


  public static int countMatches(String str, String findStr) {
    int lastIndex = 0;
    int count = 0;

    while(lastIndex != -1){

      lastIndex = str.indexOf(findStr,lastIndex);

      if(lastIndex != -1){
        count ++;
        lastIndex += findStr.length();
      }
    }
    return count;
  }
}
