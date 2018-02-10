package aser.ufo.trace;

public final class Bytes {

  private Bytes() {
  }

  // compatible with ByteBuffer
  public static byte[] ints2bytes(int[] src) {
    int srcLength = src.length;
    byte[] dst = new byte[srcLength << 2];

    for (int i = 0; i < srcLength; i++) {
      int x = src[i];
      int j = i << 2;
      dst[j++] = (byte) ((x >>> 24) & 0xff);
      dst[j++] = (byte) ((x >>> 16) & 0xff);
      dst[j++] = (byte) ((x >>> 8) & 0xff);
      dst[j++] = (byte) ((x >>> 0) & 0xff);
    }
    return dst;
  }

  public static int[] bytes2ints(byte[] src) {
    int srcLength = src.length;
    int[] dst = new int[srcLength >> 2];
    for (int i = 0, bi = 0; i < dst.length; i++) {
      dst[i] = 0;
      int t = 0x000000FF & src[bi++];
      dst[i] = dst[i] | (t << 24);

      t = 0x000000FF & src[bi++];
      dst[i] = dst[i] | (t << 16);

      t = 0x000000FF & src[bi++];
      dst[i] = dst[i] | (t << 8);

      t = 0x000000FF & src[bi++];
      dst[i] = dst[i] | (t);
    }
    return dst;
  }

  public static final class shorts {
    public static final short add(byte a, byte b) {
      return (short) (((short) a << 8) | (short) b & 0xff);
    }

    public static final byte part1(short c) {
      return (byte) ((c >> 8) & 0xff);
    }

    public static final byte part2(short c) {
      return (byte) (c & 0xff);
    }


    public static final void fill(byte[] arr, int start, short c) {
      arr[start++] = part1(c);
      arr[1] = part2(c);
    }

    public static final short add(byte[] arr, int start) {
      return add(arr[start++], arr[start]);
    }
  }

  public static final class ints {

    public static final int _Ladd(byte b3, byte b2, byte b1, byte b0) {
      return (((b3) << 24) |
          ((b2 & 0xff) << 16) |
          ((b1 & 0xff) << 8) |
          ((b0 & 0xff)));
    }

    public static final int add(short a, short b) {
      return ((int) a << 16) | ((int) b & 0xFFFF);
    }

    public static final short part1(int c) {
      return (short) (c >> 16);
    }

    public static final short part2(int c) {
      return (short) c;
    }

    public static final void fill(byte[] arr, int start, int c) {
      arr[start++] = shorts.part1(part1(c));
      arr[start++] = shorts.part2(part1(c));
      arr[start++] = shorts.part1(part2(c));
      arr[start++] = shorts.part2(part2(c));
    }

    public static final int add(byte[] arr, int start) {
      return add(shorts.add(arr[start++], arr[start++]),
          shorts.add(arr[start++], arr[start]));
    }
  }


  public static final class longs {
    public static final long _Ladd(byte[] arr, int start) {
      return _Ladd(
          arr[start + 7],
          arr[start + 6],
          arr[start + 5],
          arr[start + 4],
          arr[start + 3],
          arr[start + 2],
          arr[start + 1],
          arr[start]
      );
    }

    public static final long _Ladd(byte b7, byte b6, byte b5, byte b4,
                                   byte b3, byte b2, byte b1, byte b0) {
      return ((((long) b7) << 56) |
          (((long) b6 & 0xff) << 48) |
          (((long) b5 & 0xff) << 40) |
          (((long) b4 & 0xff) << 32) |
          (((long) b3 & 0xff) << 24) |
          (((long) b2 & 0xff) << 16) |
          (((long) b1 & 0xff) << 8) |
          (((long) b0 & 0xff)));
    }

    public static final long add(int a, int b) {
      return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    public static final int part1(long c) {
      return (int) (c >> 32);
    }

    public static final int part2(long c) {
      return (int) c;
    }

    public static final void fill(byte[] arr, int start, long c) {
      arr[start++] = shorts.part1(ints.part1(part1(c)));
      arr[start++] = shorts.part2(ints.part1(part1(c)));
      arr[start++] = shorts.part1(ints.part2(part1(c)));
      arr[start++] = shorts.part2(ints.part2(part1(c)));
      arr[start++] = shorts.part1(ints.part1(part2(c)));
      arr[start++] = shorts.part2(ints.part1(part2(c)));
      arr[start++] = shorts.part1(ints.part2(part2(c)));
      arr[start++] = shorts.part2(ints.part2(part2(c)));
    }

    public static final long add(byte[] arr, int start) {
      return add(ints.add(arr, start),
          ints.add(arr, start + 4));
    }
  }


  public static final int add(byte b1, byte b2, byte b3, byte b4) {
    return ints.add(shorts.add(b1, b2), shorts.add(b3, b4));
  }

  public static final byte part1(int a) {
    return shorts.part1(ints.part1(a));
  }

  public static final byte part2(int a) {
    return shorts.part2(ints.part1(a));
  }

  public static final byte part3(int a) {
    return shorts.part1(ints.part1(a));
  }

  public static final byte part4(int a) {
    return shorts.part2(ints.part1(a));
  }

  public static final byte part(int a, int index) {
    switch (index) {
      case 0:
        return part1(a);
      case 1:
        return part2(a);
      case 2:
        return part3(a);
      case 3:
        return part4(a);
      default:
        throw new ArrayIndexOutOfBoundsException();
    }
  }

  public static final long add(short b1, short b2, short b3, short b4) {
    return longs.add(ints.add(b1, b2), ints.add(b3, b4));
  }

  public static final short part1(long a) {
    return ints.part1(longs.part1(a));
  }

  public static final short part2(long a) {
    return ints.part2(longs.part1(a));
  }

  public static final short part3(long a) {
    return ints.part1(longs.part1(a));
  }

  public static final short part4(long a) {
    return ints.part2(longs.part1(a));
  }

  public static final short part(long a, int index) {
    switch (index) {
      case 0:
        return part1(a);
      case 1:
        return part2(a);
      case 2:
        return part3(a);
      case 3:
        return part4(a);
      default:
        throw new ArrayIndexOutOfBoundsException();
    }
  }


  public static final long add(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
    return longs.add(
        ints.add(shorts.add(b1, b2),
            shorts.add(b3, b4)),

        ints.add(shorts.add(b5, b6),
            shorts.add(b7, b8)));
  }

  public static final byte byte1(long a) {
    return shorts.part1(ints.part1(longs.part1(a)));
  }

  public static final byte byte2(long a) {
    return shorts.part2(ints.part1(longs.part1(a)));
  }

  public static final byte byte3(long a) {
    return shorts.part1(ints.part2(longs.part1(a)));
  }

  public static final byte byte4(long a) {
    return shorts.part2(ints.part2(longs.part1(a)));
  }

  public static final byte byte5(long a) {
    return shorts.part1(ints.part1(longs.part2(a)));
  }

  public static final byte byte6(long a) {
    return shorts.part2(ints.part1(longs.part2(a)));
  }

  public static final byte byte7(long a) {
    return shorts.part1(ints.part2(longs.part2(a)));
  }

  public static final byte byte8(long a) {
    return shorts.part2(ints.part2(longs.part2(a)));
  }

  public static final byte byteAt(long c, int index) {
    switch (index) {
      case 0:
        return byte1(c);
      case 1:
        return byte2(c);
      case 2:
        return byte3(c);
      case 3:
        return byte4(c);
      case 4:
        return byte5(c);
      case 5:
        return byte6(c);
      case 6:
        return byte7(c);
      case 7:
        return byte8(c);
      default:
        throw new ArrayIndexOutOfBoundsException();
    }
  }
}